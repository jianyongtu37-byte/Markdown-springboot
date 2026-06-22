"""
持续对话 Agent
组合跨文章问答 Agent、会话管理和查询重写，实现多轮对话

Uses RewritePipeline for enterprise-grade query rewriting with
multi-layer processing, intent classification, and strategy routing.
"""

import re
import uuid
import json
from typing import Dict, List, Optional, AsyncGenerator

from agents.cross_article import CrossArticleAgent
from core.vectorstore import VectorStore
from core.llm import DeepSeekLLM
from core.session import SessionManager
from core.query_rewriter import QueryRewriter  # backward compat wrapper
from core.rewrite.pipeline import RewritePipeline
from core.embeddings import EmbeddingManager


# 触发"仅搜个人知识库"的中文关键词
_PERSONAL_SCOPE_PATTERNS = [
    r"我(的|自己)",
    r"总结(我|一下我的|我的)",
    r"整理(我|一下我的|我的)",
    r"(我的|个人|私有|私人|自个儿|本人)的?(笔记|知识库|文章|文档|资料)",
    r"帮我(总结|整理|梳理|回顾|看看)",
    r"(看看|查查|搜一下)(我|我的)",
    r"(我有|我写|我记录|我收藏)(了|过|的)",
    r"^我(想|要|来)(总结|整理|回顾|看看)",
]


class ConversationalAgent:
    """持续对话 Agent — 支持上下文记忆的多轮问答"""

    def __init__(
        self,
        vector_store: VectorStore,
        llm: DeepSeekLLM,
        session_manager: SessionManager,
        global_store: Optional[VectorStore] = None,
        use_new_pipeline: bool = True,
    ):
        self.vector_store = vector_store
        self.llm = llm
        self.session_manager = session_manager
        self.global_store = global_store

        if use_new_pipeline:
            emb = EmbeddingManager.get_instance()
            self.rewrite_pipeline = RewritePipeline(llm, emb)
            self.query_rewriter = QueryRewriter(llm)  # still available for backward compat
        else:
            self.query_rewriter = QueryRewriter(llm)
            self.rewrite_pipeline = None

        self.cross_agent = CrossArticleAgent(vector_store, llm, global_store)

    def _detect_personal_scope(self, question: str, explicit_scope: str) -> bool:
        """判断是否应只搜个人知识库（私有索引）"""
        if explicit_scope == "personal":
            return True
        if explicit_scope != "all":
            return False  # article/category/tag 不做自动判断
        for pattern in _PERSONAL_SCOPE_PATTERNS:
            if re.search(pattern, question):
                return True
        return False

    def _get_retrieval_agent(self, personal_only: bool) -> CrossArticleAgent:
        """获取检索 Agent，personal_only 时不连全局索引"""
        if personal_only:
            return CrossArticleAgent(self.vector_store, self.llm, global_store=None)
        return self.cross_agent

    async def ask(
        self,
        question: str,
        user_id: int,
        session_id: Optional[str] = None,
        article_id: Optional[int] = None,
        scope: str = "all",
        max_sources: int = 5,
        highlight: Optional[str] = None,
    ) -> Dict:
        """
        带上下文记忆的问答

        Args:
            question: 用户问题
            user_id: 用户 ID
            session_id: 会话 ID（可选，不传则自动创建）
            article_id: 指定文章 ID
            scope: 检索范围 (all/personal/article/category/tag)
            max_sources: 最大来源数
            highlight: 选段文本

        Returns:
            {"answer", "sources", "confidence", "session_id", "query_rewritten"}
        """
        # 0. 检测是否只搜个人知识库
        personal_only = self._detect_personal_scope(question, scope)
        if personal_only:
            scope = "personal"

        # 1. 获取或创建会话
        if not session_id:
            session_id = self.session_manager.create_session(user_id)

        history = self.session_manager.get_history(user_id, session_id)

        # 2. 查询重写（有历史时）— use RewritePipeline when available
        query_rewritten = None
        search_query = question
        rewrite_result = None

        if history:
            if self.rewrite_pipeline:
                # Use full pipeline
                rewrite_result = await self.rewrite_pipeline.rewrite(
                    question, history, user_id=user_id, session_id=session_id
                )
                query_rewritten = rewrite_result.rewritten_query
                search_query = rewrite_result.search_query or query_rewritten
            else:
                # Fallback to legacy QueryRewriter
                query_rewritten = await self.query_rewriter.rewrite(
                    question, history
                )
                if query_rewritten != question:
                    search_query = query_rewritten

        # 3. 检索 — personal 模式跳过全局索引
        sources = await self._do_retrieval(
            search_query, rewrite_result, max_sources, article_id, highlight,
            personal_only=personal_only,
        )

        # 4. Step-back abstraction fallback (if no sources found)
        if not sources and self.rewrite_pipeline and hasattr(self.rewrite_pipeline, 'rewrite_step_back'):
            abstracted = await self.rewrite_pipeline.rewrite_step_back(question, history)
            if abstracted and abstracted != question:
                sources = await self._do_retrieval(
                    abstracted, None, max_sources, article_id, highlight,
                    personal_only=personal_only,
                )

        # 5. Generate answer
        result = await self._generate_answer(
            question, sources, highlight
        )

        # 6. 保存对话历史（含 sources 和 confidence）
        self.session_manager.add_message(user_id, session_id, "user", question)
        sources_json = json.dumps(result.get("sources", []), ensure_ascii=False) if result.get("sources") else None
        self.session_manager.add_message(
            user_id, session_id, "assistant", result["answer"],
            sources=sources_json, confidence=result.get("confidence"),
        )

        # 7. 附加元数据
        result["session_id"] = session_id
        result["query_rewritten"] = query_rewritten
        if rewrite_result:
            result["rewrite_details"] = {
                "layer": rewrite_result.layer_resolved,
                "strategy": rewrite_result.strategy_used,
                "confidence": rewrite_result.confidence,
            }
        return result

    async def ask_stream(
        self,
        question: str,
        user_id: int,
        session_id: Optional[str] = None,
        article_id: Optional[int] = None,
        scope: str = "all",
        max_sources: int = 5,
        highlight: Optional[str] = None,
    ) -> AsyncGenerator:
        """
        带上下文记忆的流式问答

        Yields:
            {"type": "session_id", "data": str}
            {"type": "query_rewritten", "data": str}  — 如果有重写
            {"type": "sources", "data": [...]}
            {"type": "content", "data": str}
            {"type": "done"}
        """
        # 0. 检测是否只搜个人知识库
        personal_only = self._detect_personal_scope(question, scope)

        # 1. 获取或创建会话
        if not session_id:
            session_id = self.session_manager.create_session(user_id)

        yield {"type": "session_id", "data": session_id}

        history = self.session_manager.get_history(user_id, session_id)

        # 2. 查询重写 — use RewritePipeline when available
        query_rewritten = None
        search_query = question
        rewrite_result = None

        if history:
            if self.rewrite_pipeline:
                rewrite_result = await self.rewrite_pipeline.rewrite(
                    question, history, user_id=user_id, session_id=session_id
                )
                query_rewritten = rewrite_result.rewritten_query
                search_query = rewrite_result.search_query or query_rewritten
                if query_rewritten != question:
                    yield {"type": "query_rewritten", "data": query_rewritten}
                    yield {"type": "rewrite_details", "data": {
                        "layer": rewrite_result.layer_resolved,
                        "strategy": rewrite_result.strategy_used,
                        "confidence": rewrite_result.confidence,
                    }}
            else:
                query_rewritten = await self.query_rewriter.rewrite(
                    question, history
                )
                if query_rewritten != question:
                    search_query = query_rewritten
                    yield {"type": "query_rewritten", "data": query_rewritten}

        # 3. Retrieve sources using rewrite result routing
        sources = await self._do_retrieval(
            search_query, rewrite_result, max_sources, article_id, highlight,
            personal_only=personal_only,
        )

        # 4. Step-back abstraction fallback
        if not sources and self.rewrite_pipeline:
            abstracted = await self.rewrite_pipeline.rewrite_step_back(question, history)
            if abstracted and abstracted != question:
                sources = await self._do_retrieval(
                    abstracted, None, max_sources, article_id, highlight,
                    personal_only=personal_only,
                )

        # 5. Yield sources
        sources_data = [
            {
                "article_id": s["article_id"],
                "article_title": s["article_title"],
                "chunk_content": s.get("content", "")[:200],
                "relevance_score": s["relevance_score"],
            }
            for s in sources
        ]
        yield {"type": "sources", "data": sources_data}

        # 6. Stream answer generation
        full_answer = ""
        if not sources:
            messages = self.cross_agent._build_general_messages(question)
            async for chunk in self.llm.chat_stream(messages):
                full_answer += chunk
                yield {"type": "content", "data": chunk}
        else:
            context = self.cross_agent._build_context(sources)
            messages = self.cross_agent._build_messages(question, context, highlight)
            async for chunk in self.llm.chat_stream(messages):
                full_answer += chunk
                yield {"type": "content", "data": chunk}

        yield {"type": "done"}

        # 7. 保存对话历史（含 sources 和 confidence）
        self.session_manager.add_message(user_id, session_id, "user", question)
        if full_answer:
            sources_json = json.dumps(sources_data, ensure_ascii=False) if sources_data else None
            confidence = self._calc_confidence(sources) if sources else None
            self.session_manager.add_message(
                user_id, session_id, "assistant", full_answer,
                sources=sources_json, confidence=confidence,
            )

    def get_history(self, user_id: int, session_id: str) -> List[Dict]:
        """获取对话历史"""
        return self.session_manager.get_history(user_id, session_id)

    def clear_session(self, user_id: int, session_id: str) -> bool:
        """清除会话"""
        return self.session_manager.clear_session(user_id, session_id)

    def list_sessions(self, user_id: int) -> List[dict]:
        """列出用户的所有会话"""
        return self.session_manager.list_sessions(user_id)

    async def _do_retrieval(
        self, search_query: str, rewrite_result,
        max_sources: int, article_id: Optional[int],
        highlight: Optional[str],
        personal_only: bool = False,
    ) -> List[dict]:
        """Route retrieval based on rewrite result capabilities."""
        # Build search query with highlight
        actual_query = search_query
        if highlight and highlight.strip():
            actual_query = f"{highlight.strip()[:200]} {search_query}"

        agent = self._get_retrieval_agent(personal_only)

        if rewrite_result is None:
            return await agent.retriever.retrieve(
                actual_query, top_k=max_sources, article_id=article_id
            )

        return await agent._do_retrieval(
            actual_query, rewrite_result, max_sources, article_id
        )

    async def _generate_answer(
        self, question: str, sources: List[dict],
        highlight: Optional[str] = None,
    ) -> dict:
        """Generate an answer with or without sources."""
        if not sources:
            messages = self.cross_agent._build_general_messages(question)
            answer = await self.llm.chat(messages)
            return {"answer": answer, "sources": [], "confidence": 0.0}

        context = self.cross_agent._build_context(sources)
        messages = self.cross_agent._build_messages(question, context, highlight)
        answer = await self.llm.chat(messages)
        confidence = self.cross_agent._calculate_confidence(sources)

        return {
            "answer": answer,
            "sources": [
                {
                    "article_id": s["article_id"],
                    "article_title": s["article_title"],
                    "chunk_content": s.get("content", "")[:200],
                    "relevance_score": s["relevance_score"],
                    "chunk_index": s.get("chunk_index", 0),
                }
                for s in sources
            ],
            "confidence": confidence,
        }

    @staticmethod
    def _calc_confidence(sources: list) -> float:
        """基于检索结果计算置信度"""
        if not sources:
            return 0.0
        scores = [s.get("relevance_score", 0) for s in sources]
        avg_score = sum(scores) / len(scores)
        max_score = max(scores)
        return min(1.0, avg_score * 0.4 + max_score * 0.6)
        """基于检索结果计算置信度"""
        if not sources:
            return 0.0
        scores = [s.get("relevance_score", 0) for s in sources]
        avg_score = sum(scores) / len(scores)
        max_score = max(scores)
        return min(1.0, avg_score * 0.4 + max_score * 0.6)
