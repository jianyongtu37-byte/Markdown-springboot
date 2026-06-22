"""
跨文章问答 Agent
从知识库中检索相关文章片段，基于检索结果生成回答
支持选段提问（highlight）：用户选中文字后提问，优先检索该段落上下文
支持 HyDE 预计算 embedding、子查询分解、多视角检索
"""

import numpy as np
from core.vectorstore import VectorStore
from core.retriever import Retriever
from core.llm import DeepSeekLLM
from typing import Dict, List, Optional, AsyncGenerator


class CrossArticleAgent:
    """跨文章知识问答 Agent"""

    def __init__(self, vector_store: VectorStore, llm: DeepSeekLLM, global_store: Optional[VectorStore] = None):
        self.vector_store = vector_store
        self.retriever = Retriever(vector_store, global_store)
        self.llm = llm
        self._kb_overview: Optional[str] = None  # cached

    def get_kb_overview(self) -> str:
        """构建知识库文章目录概览，使 LLM 能回答元问题（如"知识库覆盖哪些领域"）"""
        if self._kb_overview is not None:
            return self._kb_overview

        articles: Dict[int, str] = {}
        for store in [self.vector_store, self.retriever.global_store]:
            if store is None or not hasattr(store, 'metadata'):
                continue
            for cid, meta in store.metadata.items():
                aid = meta.get("article_id")
                title = meta.get("article_title", "无标题")
                if aid is not None and aid not in articles:
                    articles[aid] = title

        if not articles:
            self._kb_overview = ""
            return ""

        lines = ["知识库文章目录（共 {} 篇）：".format(len(articles))]
        for aid, title in sorted(articles.items()):
            lines.append(f"- [ID:{aid}] {title}")
        self._kb_overview = "\n".join(lines)
        return self._kb_overview

    async def ask(
        self,
        question: str,
        scope: str = "all",
        article_id: Optional[int] = None,
        max_sources: int = 5,
        highlight: Optional[str] = None,
    ) -> Dict:
        """
        跨文章问答（非流式）

        Args:
            question: 用户问题
            scope: 检索范围 ("all", "article", "category", "tag")
            article_id: 指定文章 ID（scope=article 时使用）
            max_sources: 最大来源数
            highlight: 用户选中的文本（选段提问模式）

        Returns:
            {"answer": str, "sources": list, "confidence": float}
        """
        # 1. 构建检索查询（如果有选段，合并选段+问题提升检索精度）
        search_query = self._build_search_query(question, highlight)

        # 2. 检索
        sources = await self.retriever.retrieve(
            search_query, top_k=max_sources, article_id=article_id
        )

        if not sources:
            # 知识库无匹配，用 LLM 通用知识回答
            messages = self._build_general_messages(question)
            answer = await self.llm.chat(messages)
            return {
                "answer": answer,
                "sources": [],
                "confidence": 0.0,
            }

        # 3. 构建上下文和 prompt
        context = self._build_context(sources)
        messages = self._build_messages(question, context, highlight)

        # 4. 生成回答
        answer = await self.llm.chat(messages)

        # 5. 计算置信度
        confidence = self._calculate_confidence(sources)

        return {
            "answer": answer,
            "sources": [
                {
                    "article_id": s["article_id"],
                    "article_title": s["article_title"],
                    "chunk_content": s["content"][:200],
                    "relevance_score": s["relevance_score"],
                    "chunk_index": s["chunk_index"],
                }
                for s in sources
            ],
            "confidence": confidence,
        }

    async def ask_stream(
        self,
        question: str,
        scope: str = "all",
        article_id: Optional[int] = None,
        max_sources: int = 5,
        highlight: Optional[str] = None,
    ) -> AsyncGenerator:
        """
        流式问答

        Yields:
            {"type": "sources", "data": [...]}  — 来源信息
            {"type": "content", "data": str}    — 回答内容片段
            {"type": "done"}                     — 结束标记
        """
        # 1. 构建检索查询
        search_query = self._build_search_query(question, highlight)

        # 2. 检索
        sources = await self.retriever.retrieve(
            search_query, top_k=max_sources, article_id=article_id
        )

        # 3. 先返回来源信息
        yield {
            "type": "sources",
            "data": [
                {
                    "article_id": s["article_id"],
                    "article_title": s["article_title"],
                    "chunk_content": s["content"][:200],
                    "relevance_score": s["relevance_score"],
                }
                for s in sources
            ],
        }

        if not sources:
            # 知识库无匹配，用 LLM 通用知识回答
            messages = self._build_general_messages(question)
            async for chunk in self.llm.chat_stream(messages):
                yield {"type": "content", "data": chunk}
            yield {"type": "done"}
            return

        # 4. 流式生成回答
        context = self._build_context(sources)
        messages = self._build_messages(question, context, highlight)

        async for chunk in self.llm.chat_stream(messages):
            yield {"type": "content", "data": chunk}

        yield {"type": "done"}

    def _build_search_query(
        self, question: str, highlight: Optional[str] = None
    ) -> str:
        """
        构建检索查询
        如果有选段，将选段文本+问题合并，提升检索命中率
        """
        if highlight and highlight.strip():
            # 截取选段前 200 字避免过长
            short_highlight = highlight.strip()[:200]
            return f"{short_highlight} {question}"
        return question

    def _build_messages(
        self,
        question: str,
        context: str,
        highlight: Optional[str] = None,
    ) -> List[Dict]:
        """构建 LLM 对话消息"""
        kb_overview = self.get_kb_overview()
        overview_block = (
            f"你的知识库包含以下文章：\n{kb_overview}\n\n"
            if kb_overview else ""
        )
        system_content = (
            "你是一个智能知识助手，融合了用户专属知识库和通用知识。请按以下优先级回答：\n\n"
            f"{overview_block}"
            "回答策略：\n"
            "1. **优先使用知识库**：如果提供的参考资料中包含答案，务必基于资料回答，标注来源文章标题。\n"
            "2. **知识库不足时补充通用知识**：如果参考资料仅部分相关或不完全覆盖问题，先用知识库内容回答已知部分，再明确标注「📖 以下补充来自通用知识」，用你的通用知识补充完整答案。\n"
            "3. **诚实告知边界**：如果问题与参考资料完全无关且属于高度特定的内部业务问题，诚实告知知识库中未找到，引导用户提供更多信息。\n\n"
            "规则：\n"
            "- 多篇文章有不同观点时，都列出并分析\n"
            "- 回答简洁准确，使用 Markdown 格式\n"
            "- 用户询问\"知识库覆盖哪些领域\"等元问题时，基于以上文章目录作答"
        )

        # 选段提问时，附加选段上下文
        user_content = f"知识库相关内容：\n\n{context}\n\n"
        if highlight and highlight.strip():
            user_content += (
                f"用户选中的文本段落：\n「{highlight.strip()}」\n\n"
            )
            user_content += (
                f"请结合用户选中的文本段落和以上知识库内容，回答以下问题：\n{question}"
            )
        else:
            user_content += f"问题：{question}"

        return [
            {"role": "system", "content": system_content},
            {"role": "user", "content": user_content},
        ]

    def _build_general_messages(self, question: str) -> List[Dict]:
        """构建通用知识问答消息（知识库无匹配时使用）"""
        kb_overview = self.get_kb_overview()
        overview_block = (
            f"但请注意，用户的知识库包含以下文章：\n{kb_overview}\n\n"
            if kb_overview else ""
        )
        system_content = (
            "你是一个智能问答助手。用户的知识库中没有找到与问题直接相关的内容。\n"
            f"{overview_block}"
            "规则：\n"
            "- 如果用户询问知识库覆盖哪些领域、有什么内容等元问题，基于以上文章目录诚实回答\n"
            "- 如果问题超出知识库范围但你可以用通用知识回答，在开头注明：「以下回答来自通用知识，非知识库内容」\n"
            "- 尽量给出准确、有帮助的回答\n"
            "- 如果不确定，诚实说明\n"
            "- 回答简洁准确，使用 Markdown 格式"
        )
        return [
            {"role": "system", "content": system_content},
            {"role": "user", "content": question},
        ]

    def _build_context(self, sources: List[dict]) -> str:
        """将检索结果格式化为上下文"""
        parts = []
        for i, s in enumerate(sources, 1):
            parts.append(
                f"### 来源 {i}：《{s['article_title']}》\n"
                f"相关性：{s['relevance_score']:.2f}\n"
                f"内容：\n{s['content']}"
            )
        return "\n\n---\n\n".join(parts)

    def _calculate_confidence(self, sources: List[dict]) -> float:
        """基于检索结果计算置信度"""
        if not sources:
            return 0.0
        avg_score = sum(s["relevance_score"] for s in sources) / len(sources)
        max_score = max(s["relevance_score"] for s in sources)
        return min(1.0, avg_score * 0.4 + max_score * 0.6)

    async def retrieve_with_embedding(
        self,
        query_embedding: np.ndarray,
        top_k: int = 5,
        article_id: Optional[int] = None,
    ) -> List[dict]:
        """Retrieve using a pre-computed embedding (HyDE)."""
        return await self.retriever.retrieve_with_embedding(
            query="",  # not used for embedding search
            query_embedding=query_embedding,
            top_k=top_k,
            article_id=article_id,
        )

    async def retrieve_multi(
        self,
        queries: List[str],
        top_k: int = 5,
        article_id: Optional[int] = None,
    ) -> List[dict]:
        """Retrieve from multiple sub-queries (decomposition/multi-perspective)."""
        return await self.retriever.retrieve_multi(
            queries=queries,
            top_k=top_k,
            article_id=article_id,
        )

    async def _do_retrieval(
        self,
        search_query: str,
        rewrite_result: object,
        max_sources: int,
        article_id: Optional[int],
    ) -> List[dict]:
        """
        Route retrieval based on rewrite result content.
        - HyDE embedding → retrieve_with_embedding
        - Sub-queries → retrieve_multi
        - Variants → retrieve_multi
        - Default → standard retrieve
        """
        sources = []

        # HyDE path: use pre-computed embedding
        if getattr(rewrite_result, 'search_embedding', None) is not None:
            sources = await self.retrieve_with_embedding(
                query_embedding=rewrite_result.search_embedding,
                top_k=max_sources,
                article_id=article_id,
            )
            if sources:
                return sources

        # Sub-query decomposition path
        sub_queries = getattr(rewrite_result, 'sub_queries', None)
        if sub_queries and len(sub_queries) > 1:
            sources = await self.retrieve_multi(
                queries=sub_queries,
                top_k=max_sources,
                article_id=article_id,
            )
            if sources:
                return sources

        # Multi-perspective variants path
        variants = getattr(rewrite_result, 'variants', None)
        if variants and len(variants) > 1:
            sources = await self.retrieve_multi(
                queries=variants,
                top_k=max_sources,
                article_id=article_id,
            )
            if sources:
                return sources

        # Standard retrieval
        if not sources:
            sources = await self.retriever.retrieve(
                search_query, top_k=max_sources, article_id=article_id
            )

        return sources
