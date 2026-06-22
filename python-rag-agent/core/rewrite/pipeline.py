"""
RewritePipeline — central orchestrator for multi-layer query rewriting.

Flow: cache check → L1 (rules) → L2 (keywords) → L3 (LLM strategies)

L3 strategies are wired in during Phase 2 (intent) and Phase 3 (advanced).
Phase 1 pipes L1 → L2 → fallback to original LLM coreference.
"""

import asyncio
import time
from typing import Dict, List, Optional, Union

from config import Config
from core.rewrite.layer1_rules import Layer1RuleEngine, LayerResult
from core.rewrite.layer2_keywords import Layer2KeywordProcessor
from core.rewrite.domain_dict import DomainDict
from core.rewrite.cache.rewrite_cache import RewriteCache, RewriteCacheEntry
from core.rewrite.cache.embedding_cache import EmbeddingCache
from core.rewrite.intent_classifier import IntentClassifier
from core.rewrite.strategy_router import StrategyRouter, StrategyType
from core.rewrite.evaluator import QualityEvaluator
from core.rewrite.metrics import RewriteMetrics


class RewriteResult:
    """Full rewrite result with all metadata."""

    __slots__ = (
        "rewritten_query", "search_query", "search_embedding",
        "sub_queries", "variants", "strategy_used", "layer_resolved",
        "confidence", "latency_ms", "metadata",
    )

    def __init__(
        self,
        rewritten_query: str,
        search_query: str = "",
        search_embedding: "np.ndarray | None" = None,
        sub_queries: Optional[List[str]] = None,
        variants: Optional[List[str]] = None,
        strategy_used: Optional[List[str]] = None,
        layer_resolved: str = "",
        confidence: float = 0.0,
        latency_ms: float = 0.0,
        metadata: Optional[Dict] = None,
    ):
        self.rewritten_query = rewritten_query
        self.search_query = search_query or rewritten_query
        self.search_embedding = search_embedding
        self.sub_queries = sub_queries or []
        self.variants = variants or []
        self.strategy_used = strategy_used or []
        self.layer_resolved = layer_resolved
        self.confidence = confidence
        self.latency_ms = latency_ms
        self.metadata = metadata or {}


class RewritePipeline:
    """
    Enterprise query rewrite pipeline with layered processing.

    Usage:
        pipeline = RewritePipeline(llm, embedding_manager)
        result = await pipeline.rewrite(query, history, user_id, session_id)

    Phase 1: L1 → L2 → fallback to LLM coreference
    Phase 2+: L1 → L2 → intent classification → strategy routing → parallel execution
    """

    def __init__(
        self,
        llm: "DeepSeekLLM",
        embedding_manager: "EmbeddingManager" = None,
        domain_dict: Optional[DomainDict] = None,
        rewrite_cache: Optional[RewriteCache] = None,
        embedding_cache: Optional[EmbeddingCache] = None,
    ):
        self.llm = llm
        self.embedding = embedding_manager

        # Core components (Phase 1)
        self._domain_dict = domain_dict or DomainDict()
        self._layer1 = Layer1RuleEngine(self._domain_dict)
        self._layer2 = Layer2KeywordProcessor(self._domain_dict)
        self._cache = rewrite_cache or RewriteCache()
        self._emb_cache = embedding_cache or EmbeddingCache()

        # Phase 2 components
        self._intent_classifier = IntentClassifier(llm)
        self._strategy_router = StrategyRouter(Config)
        self._strategies = {}

        # Register coreference strategy (Phase 2)
        from core.rewrite.strategies.coreference import CoreferenceStrategy
        self._register_strategy(
            StrategyType.COREFERENCE.value,
            CoreferenceStrategy(llm, Config),
        )
        # Register keyword expansion strategy (Phase 2, no LLM)
        from core.rewrite.strategies.keyword_expansion import KeywordExpansionStrategy
        self._register_strategy(
            StrategyType.KEYWORD_EXPANSION.value,
            KeywordExpansionStrategy(self._domain_dict),
        )
        # Register Phase 3 strategies
        from core.rewrite.strategies.hyde import HyDEStrategy
        self._register_strategy(
            StrategyType.HYDE.value,
            HyDEStrategy(llm, embedding_manager, Config),
        )
        from core.rewrite.strategies.decomposition import SubQueryDecompositionStrategy
        self._register_strategy(
            StrategyType.DECOMPOSITION.value,
            SubQueryDecompositionStrategy(llm, Config),
        )
        from core.rewrite.strategies.abstraction import StepBackAbstractionStrategy
        self._register_strategy(
            StrategyType.ABSTRACTION.value,
            StepBackAbstractionStrategy(llm, Config),
        )
        from core.rewrite.strategies.multi_perspective import MultiPerspectiveStrategy
        self._register_strategy(
            StrategyType.MULTI_PERSPECTIVE.value,
            MultiPerspectiveStrategy(llm, Config),
        )

        # Phase 4 components
        self._evaluator = QualityEvaluator(llm, Config)
        self._metrics = RewriteMetrics()

        # Stats
        self._l1_resolved = 0
        self._l2_resolved = 0
        self._l3_resolved = 0
        self._cache_hits = 0
        self._total_requests = 0

    def _register_strategy(self, name: str, strategy):
        """Register an L3 rewrite strategy. Called during Phase 2/3 setup."""
        self._strategies[name] = strategy

    # ---- Main entry point ----

    async def rewrite(
        self,
        current_message: str,
        history: List[Dict],
        user_id: Optional[int] = None,
        session_id: Optional[str] = None,
    ) -> RewriteResult:
        """
        Rewrite a query through the layered pipeline.

        Args:
            current_message: The user's latest query
            history: List of {"role": ..., "content": ...} dicts
            user_id: Optional user ID for cache scoping
            session_id: Optional session ID for cache scoping

        Returns:
            RewriteResult with rewritten_query, search_query, etc.
        """
        t_start = time.perf_counter()
        self._total_requests += 1

        # No history? Return original query immediately
        if not history:
            return RewriteResult(
                rewritten_query=current_message,
                search_query=current_message,
                layer_resolved="none",
                confidence=1.0,
                latency_ms=(time.perf_counter() - t_start) * 1000,
            )

        # 1. Cache check
        cached = await self._cache.get(
            current_message, history, user_id, session_id
        )
        if cached:
            self._cache_hits += 1
            return RewriteResult(
                rewritten_query=cached.rewritten_query,
                search_query=cached.search_query,
                strategy_used=cached.strategy_used,
                layer_resolved=cached.layer_resolved,
                confidence=cached.confidence,
                latency_ms=(time.perf_counter() - t_start) * 1000,
            )

        # 2. Layer 1: Rule-based processing
        l1_result = self._layer1.process(current_message, history)
        if l1_result.is_final and l1_result.confidence >= Config.REWRITE_L1_CONFIDENCE_THRESHOLD:
            self._l1_resolved += 1
            await self._cache.set(
                current_message, history,
                RewriteCacheEntry(
                    rewritten_query=l1_result.rewritten_query,
                    search_query=l1_result.search_query,
                    layer_resolved="L1",
                    confidence=l1_result.confidence,
                    ttl=Config.REWRITE_CACHE_TTL_L1,
                ),
                user_id, session_id,
            )
            return RewriteResult(
                rewritten_query=l1_result.rewritten_query,
                search_query=l1_result.search_query,
                layer_resolved="L1",
                confidence=l1_result.confidence,
                latency_ms=(time.perf_counter() - t_start) * 1000,
            )

        # 3. Layer 2: Keyword processing (available if jieba installed)
        l2_result = None
        if self._layer2.is_available:
            l2_result = self._layer2.process(
                l1_result.search_query, history
            )
            if l2_result.is_final and l2_result.confidence >= Config.REWRITE_L2_CONFIDENCE_THRESHOLD:
                self._l2_resolved += 1
                await self._cache.set(
                    current_message, history,
                    RewriteCacheEntry(
                        rewritten_query=l2_result.rewritten_query,
                        search_query=l2_result.search_query,
                        layer_resolved="L2",
                        confidence=l2_result.confidence,
                        ttl=Config.REWRITE_CACHE_TTL_L2,
                    ),
                    user_id, session_id,
                )
                return RewriteResult(
                    rewritten_query=l2_result.rewritten_query,
                    search_query=l2_result.search_query,
                    layer_resolved="L2",
                    confidence=l2_result.confidence,
                    latency_ms=(time.perf_counter() - t_start) * 1000,
                )

        # 4. Layer 3: Intent-driven strategy execution
        self._l3_resolved += 1
        l3_result = await self._execute_l3_strategies(
            l1_result.current if l1_result else current_message,
            history,
        )

        rewritten = l3_result.rewritten_query
        search_query = l3_result.search_query or rewritten
        # Prefer L2 search_query if keyword expansion was done
        if l2_result and l2_result.search_query != l2_result.rewritten_query:
            search_query = l2_result.search_query
        strategy_used = l3_result.strategy_used if hasattr(l3_result, 'strategy_used') else ["coreference"]

        await self._cache.set(
            current_message, history,
            RewriteCacheEntry(
                rewritten_query=rewritten,
                search_query=search_query,
                layer_resolved="L3",
                confidence=l3_result.confidence if hasattr(l3_result, 'confidence') else 0.7,
                strategy_used=strategy_used,
                ttl=Config.REWRITE_CACHE_TTL_L3,
            ),
            user_id, session_id,
        )

        final_result = RewriteResult(
            rewritten_query=rewritten,
            search_query=search_query,
            sub_queries=getattr(l3_result, 'sub_queries', []) or [],
            variants=getattr(l3_result, 'variants', []) or [],
            strategy_used=strategy_used,
            layer_resolved="L3",
            confidence=getattr(l3_result, 'confidence', 0.7) if hasattr(l3_result, 'confidence') else 0.7,
            latency_ms=(time.perf_counter() - t_start) * 1000,
            metadata=getattr(l3_result, 'metadata', {}) if hasattr(l3_result, 'metadata') else {},
        )

        # Async evaluation (fire-and-forget, non-blocking)
        if self._evaluator and self._evaluator.enabled:
            asyncio.create_task(
                self._evaluator.evaluate(
                    original=current_message,
                    rewritten=rewritten,
                    history=history,
                    strategy=",".join(strategy_used),
                    latency_ms=final_result.latency_ms,
                )
            )

        # Record metrics
        if self._metrics:
            try:
                from core.rewrite.metrics import RewriteMetric
                self._metrics.record(RewriteMetric(
                    original=current_message,
                    rewritten=rewritten,
                    strategy=",".join(strategy_used),
                    fidelity=self._evaluator.avg_fidelity if self._evaluator else 0.0,
                    latency_ms=final_result.latency_ms,
                    layer_resolved=final_result.layer_resolved,
                    confidence=final_result.confidence,
                ))
            except Exception:
                pass

        return final_result

    # ---- Layer 3: Intent-driven strategy execution (Phase 2+) ----

    async def _execute_l3_strategies(
        self, query: str, history: List[Dict]
    ) -> "RewriteResult":
        """
        Execute L3 strategies based on intent classification.

        1. Classify intent (rule-first, LLM-fallback)
        2. Route to strategy groups
        3. Execute strategy groups (parallel within groups, sequential across groups)
        4. Merge strategy outputs into a final RewriteResult
        """
        # 1. Classify intent
        intent_result = await self._intent_classifier.classify(query, history)

        # 2. Route to strategy groups
        strategy_groups = self._strategy_router.route(intent_result.intent)

        if not strategy_groups:
            # No strategies to execute (e.g., chitchat)
            return RewriteResult(
                rewritten_query=query,
                search_query=query,
                strategy_used=[],
                layer_resolved="L3",
                confidence=intent_result.confidence,
                metadata={"intent": intent_result.intent.value, "source": intent_result.source},
            )

        # 3. Execute strategies group by group
        merged_output = None
        strategies_used = []
        all_metadata = {"intent": intent_result.intent.value, "source": intent_result.source}

        for group in strategy_groups:
            tasks = []
            for strategy_type in group:
                strategy = self._strategies.get(strategy_type.value)
                if strategy:
                    tasks.append(strategy.execute(query, history))
                    strategies_used.append(strategy_type.value)

            if not tasks:
                continue

            # Execute strategies in this group in parallel
            results = await asyncio.gather(*tasks, return_exceptions=True)

            # Merge results
            for result in results:
                if isinstance(result, Exception):
                    continue
                merged_output = self._merge_strategy_output(merged_output, result)
                if hasattr(result, 'metadata') and result.metadata:
                    all_metadata.update(result.metadata)

        if merged_output is None:
            # All strategies failed, fall back to simple LLM coreference
            rewritten = await self._fallback_llm_rewrite(query, history)
            return RewriteResult(
                rewritten_query=rewritten,
                search_query=rewritten,
                strategy_used=["coreference_fallback"],
                layer_resolved="L3",
                confidence=0.5,
                metadata=all_metadata,
            )
        # Fix: set rewritten_query properly before returning
        final_query = merged_output.rewritten_query if merged_output.rewritten_query else query
        return RewriteResult(
            rewritten_query=final_query,
            search_query=merged_output.search_query if merged_output.search_query else query,
            sub_queries=merged_output.sub_queries if hasattr(merged_output, 'sub_queries') else [],
            variants=merged_output.variants if hasattr(merged_output, 'variants') else [],
            strategy_used=strategies_used,
            layer_resolved="L3",
            confidence=0.7,
            metadata=merged_output.metadata if hasattr(merged_output, 'metadata') else all_metadata,
        )

    def _merge_strategy_output(
        self, merged: "StrategyOutput | None", new: "StrategyOutput"
    ) -> "StrategyOutput":
        """Merge two strategy outputs, preferring non-null fields from new."""
        from core.rewrite.strategies.coreference import StrategyOutput
        if merged is None:
            return new

        # Prefer new rewritten_query if it differs from original
        rewritten = new.rewritten_query if new.rewritten_query else merged.rewritten_query
        # Prefer new search_query (keyword expansion enriches it)
        search = new.search_query if new.search_query else merged.search_query
        # Combine sub-queries
        subs = list(dict.fromkeys(
            (merged.sub_queries or []) + (new.sub_queries or [])
        ))
        # Combine variants
        vars_ = list(dict.fromkeys(
            (merged.variants or []) + (new.variants or [])
        ))
        # Merge metadata
        meta = {**(merged.metadata or {}), **(new.metadata or {})}

        return StrategyOutput(
            strategy=new.strategy,
            rewritten_query=rewritten,
            search_query=search,
            sub_queries=subs,
            variants=vars_,
            metadata=meta,
        )

    async def _fallback_llm_rewrite(
        self, query: str, history: List[Dict]
    ) -> str:
        """Simple LLM coreference rewrite when all strategies fail."""
        max_rounds = getattr(Config, "REWRITE_MAX_HISTORY_ROUNDS", 5)
        recent = history[-(max_rounds * 2):]
        history_text = "\n".join(
            [f"{m['role']}: {m['content']}" for m in recent]
        )

        messages = [
            {
                "role": "system",
                "content": (
                    "你是一个查询改写助手。根据对话历史，将用户的最新问题改写成独立的、完整的搜索查询。"
                    "只输出改写后的查询，不要任何解释。"
                ),
            },
            {
                "role": "user",
                "content": (
                    f"对话历史：\n{history_text}\n\n"
                    f"用户最新问题：{query}\n\n"
                    f"改写后的完整查询："
                ),
            },
        ]

        try:
            rewritten = await self.llm.chat(
                messages, temperature=0.3, max_tokens=100
            )
            return rewritten.strip() if rewritten.strip() else query
        except Exception:
            return query

    # ---- HyDE (wired in Phase 3) ----

    async def rewrite_with_hyde(
        self, query: str, history: List[Dict]
    ) -> RewriteResult:
        """
        Generate a HyDE (Hypothetical Document Embedding) for retrieval.
        Returns a RewriteResult with search_embedding set.
        """
        if "hyde" not in self._strategies:
            # Fallback: inline HyDE if strategy not registered
            return await self._hyde_inline(query)

        strategy = self._strategies["hyde"]
        output = await strategy.execute(query, history)
        return RewriteResult(
            rewritten_query=output.rewritten_query,
            search_query=output.search_query or query,
            search_embedding=output.search_embedding,
            strategy_used=["hyde"],
            layer_resolved="L3",
            confidence=0.65,
            metadata=output.metadata,
        )

    async def _hyde_inline(self, query: str) -> RewriteResult:
        """Inline HyDE implementation (used when strategy not registered)."""
        messages = [
            {
                "role": "system",
                "content": (
                    "你是一个知识库文档生成助手。"
                    "根据用户的问题，生成一段可能存在于知识库中的回答文本。"
                    "这段文本应该是一个典型的技术文档段落，包含关键词和核心概念。"
                    "只输出生成的段落文本，不要任何解释。"
                ),
            },
            {"role": "user", "content": query},
        ]
        try:
            hyde_text = await self.llm.chat(
                messages, temperature=0.5, max_tokens=200
            )
            hyde_text = hyde_text.strip()
            if not hyde_text:
                return RewriteResult(rewritten_query=query, search_query=query)

            embedding = None
            if self.embedding:
                embedding = self.embedding.encode_single(hyde_text)

            return RewriteResult(
                rewritten_query=query,
                search_query=hyde_text,
                search_embedding=embedding,
                strategy_used=["hyde"],
                layer_resolved="L3",
                confidence=0.65,
                metadata={"hyde_text": hyde_text},
            )
        except Exception:
            return RewriteResult(rewritten_query=query, search_query=query)

    # ---- Decomposition (wired in Phase 3) ----

    async def decompose(
        self, query: str, history: List[Dict]
    ) -> List[str]:
        """Break a compound question into sub-queries."""
        if "decomposition" not in self._strategies:
            return [query]

        strategy = self._strategies["decomposition"]
        output = await strategy.execute(query, history)
        return output.sub_queries if output.sub_queries else [query]

    # ---- Abstraction (wired in Phase 3, used as fallback) ----

    async def rewrite_step_back(
        self, query: str, history: List[Dict]
    ) -> str:
        """Generate a higher-level abstracted query for retry."""
        if "abstraction" not in self._strategies:
            return query

        strategy = self._strategies["abstraction"]
        output = await strategy.execute(query, history)
        return output.rewritten_query

    # ---- Stats ----

    @property
    def stats(self) -> dict:
        result = {
            "total_requests": self._total_requests,
            "l1_resolved": self._l1_resolved,
            "l2_resolved": self._l2_resolved,
            "l3_resolved": self._l3_resolved,
            "cache_hits": self._cache_hits,
            "l1_rate": self._l1_resolved / max(1, self._total_requests),
            "l2_rate": self._l2_resolved / max(1, self._total_requests),
            "l3_rate": self._l3_resolved / max(1, self._total_requests),
            "cache_hit_rate": self._cache_hits / max(1, self._total_requests),
            "cache_stats": self._cache.get_stats(),
            "embedding_cache_stats": self._emb_cache.get_stats(),
        }
        if self._metrics:
            result["metrics"] = self._metrics.get_snapshot()
        if self._evaluator:
            result["evaluation"] = {
                "avg_fidelity": self._evaluator.avg_fidelity,
                "samples_evaluated": self._evaluator.sample_count,
            }
        return result

    def get_eval_samples(self, limit: int = 20) -> list:
        """Get recent rewrite evaluation samples."""
        if self._evaluator:
            return self._evaluator.get_recent_samples(limit)
        return []
