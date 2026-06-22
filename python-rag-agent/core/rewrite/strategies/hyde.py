"""
HyDE Strategy — Hypothetical Document Embeddings.

Generates a hypothetical answer to the query, then encodes that answer
as the embedding vector for retrieval. This bridges the gap between
short/ambiguous user queries and document-style knowledge base content.

When to use:
- Query is short (<15 chars) and likely uses different vocabulary than docs
- Initial retrieval returned low relevance scores
- Query is factual (asking about a concept)

Cost: 1 LLM call (~200 token generation) + 1 embedding encode
"""

from typing import Dict, List, Optional
from dataclasses import dataclass, field

from core.rewrite.strategies.coreference import StrategyOutput
from core.rewrite.strategy_router import StrategyType


class HyDEStrategy:
    """
    Generate hypothetical document text, then embed it for retrieval.

    The original query is still passed to the LLM for answer generation.
    Only the retrieval step uses the HyDE embedding.
    """

    def __init__(self, llm: "DeepSeekLLM", embedding_manager: "EmbeddingManager" = None, config=None):
        self.llm = llm
        self.embedding = embedding_manager
        self.max_tokens = getattr(config, "REWRITE_HYDE_MAX_TOKENS", 200) if config else 200

    async def execute(self, query: str, history: List[Dict]) -> "StrategyOutput":
        """
        Generate a HyDE embedding for the query.

        Returns StrategyOutput with:
        - rewritten_query: original query (unchanged, for LLM generation)
        - search_query: HyDE generated text (for embedding lookup)
        - search_embedding: pre-computed embedding of HyDE text
        """
        # 1. Generate hypothetical answer
        hyde_text = await self._generate_hyde_text(query, history)
        if not hyde_text:
            return StrategyOutput(
                strategy=StrategyType.HYDE,
                rewritten_query=query,
                search_query=query,
                metadata={"hyde_generated": False},
            )

        # 2. Encode the hypothetical answer
        hyde_embedding = None
        if self.embedding:
            try:
                hyde_embedding = self.embedding.encode_single(hyde_text)
            except Exception:
                pass

        return StrategyOutput(
            strategy=StrategyType.HYDE,
            rewritten_query=query,        # keep original for LLM answer generation
            search_query=hyde_text,       # use HyDE text for retrieval
            search_embedding=hyde_embedding,
            metadata={
                "hyde_text": hyde_text,
                "hyde_generated": True,
                "hyde_length": len(hyde_text),
            },
        )

    async def _generate_hyde_text(
        self, query: str, history: List[Dict]
    ) -> str:
        """Generate a hypothetical document passage that would answer the query."""
        # Include minimal context from history if available
        context_hint = ""
        if history:
            last_assistant = None
            for m in reversed(history):
                if m.get("role") == "assistant":
                    last_assistant = m.get("content", "")[:200]
                    break
            if last_assistant:
                context_hint = f"参考背景：{last_assistant}\n\n"

        messages = [
            {
                "role": "system",
                "content": (
                    "你是一个知识库文档生成助手。"
                    "根据用户的问题，生成一段可能存在于技术知识库中的回答文本（200字以内）。"
                    "这段文本应该使用专业术语、包含关键概念，格式类似于典型的技术文档段落。"
                    "不要直接回答问题，而是描述一个知识库中可能包含相关信息的文档段落。"
                    "只输出生成的段落文本，不要任何解释。"
                ),
            },
            {
                "role": "user",
                "content": f"{context_hint}用户问题：{query}",
            },
        ]

        try:
            result = await self.llm.chat(
                messages, temperature=0.5, max_tokens=self.max_tokens
            )
            return result.strip()[:500]  # cap length
        except Exception:
            return ""

    @staticmethod
    def should_use_hyde(query: str, initial_recall: Optional[List[Dict]] = None) -> bool:
        """
        Determine if HyDE should be triggered.

        Triggers when:
        - Query is short (< 15 chars) and not procedural
        - OR initial retrieval returned low relevance
        """
        if len(query) < 15:
            return True
        if initial_recall is None:
            return False
        if not initial_recall:
            return True
        # Check if top result has low score
        if initial_recall[0].get("relevance_score", 0) < 0.5:
            return True
        return False
