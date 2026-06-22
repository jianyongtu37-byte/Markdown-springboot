"""
检索器：向量检索 + 重排序
支持双索引检索（用户私有索引 + 全局公共索引）
支持多查询检索和预计算 embedding 检索
"""

import numpy as np
from typing import List, Tuple, Optional
from core.vectorstore import VectorStore
from config import Config


class Retriever:
    """语义检索器"""

    # 相关性阈值：低于此分数的结果视为不相关
    RELEVANCE_THRESHOLD = Config.RELEVANCE_THRESHOLD

    def __init__(self, vector_store: VectorStore, global_store: Optional[VectorStore] = None):
        self.vector_store = vector_store
        self.global_store = global_store

    async def retrieve(
        self,
        query: str,
        top_k: int = 5,
        article_id: Optional[int] = None,
    ) -> List[dict]:
        """
        检索流程：
        1. 从用户索引 + 全局索引分别检索
        2. 合并去重
        3. 重排序取 top-k

        Args:
            query: 查询文本
            top_k: 最终返回结果数
            article_id: 可选，限定在某篇文章内检索

        Returns:
            [{"article_id", "article_title", "content", "chunk_index", "relevance_score"}, ...]
        """
        # 1. 从用户私有索引检索
        user_candidates = await self.vector_store.search(
            query, top_k=top_k * 4, article_id=article_id
        )

        # 2. 从全局公共索引检索（如果存在且不是单篇文章模式）
        global_candidates = []
        if self.global_store and article_id is None:
            global_candidates = await self.global_store.search(
                query, top_k=top_k * 4
            )

        # 3. 合并去重（用户自己的文章优先，因为可能包含更完整的内容）
        seen_articles = set()
        merged = []
        for meta, score in user_candidates:
            aid = meta["article_id"]
            key = (aid, meta["chunk_index"])
            if key not in seen_articles:
                seen_articles.add(key)
                merged.append((meta, score))

        for meta, score in global_candidates:
            aid = meta["article_id"]
            key = (aid, meta["chunk_index"])
            if key not in seen_articles:
                seen_articles.add(key)
                merged.append((meta, score))

        if not merged:
            return []

        # 4. 重排序
        reranked = self._rerank(query, merged, top_k)

        # 5. 过滤低于相关性阈值的结果
        #    若最高分都不达标，视为知识库无相关内容，返回空列表以触发通用知识回退
        if reranked and reranked[0]["relevance_score"] < self.RELEVANCE_THRESHOLD:
            return []

        return reranked

    def _rerank(
        self,
        query: str,
        candidates: List[Tuple[dict, float]],
        top_k: int,
    ) -> List[dict]:
        """
        重排序策略：
        - 向量相似度（主要）
        - 去重（同一文章的多个 chunk 合并，每篇最多取 top-2）
        """
        # 按文章分组
        article_chunks = {}
        for meta, score in candidates:
            aid = meta["article_id"]
            if aid not in article_chunks:
                article_chunks[aid] = []
            article_chunks[aid].append({**meta, "relevance_score": score})

        # 每篇文章取最相关的 2 个 chunk
        results = []
        for chunks in article_chunks.values():
            results.extend(chunks[:2])

        # 按 score 降序排列，取 top_k
        results.sort(key=lambda x: x["relevance_score"], reverse=True)
        return results[:top_k]

    async def retrieve_with_embedding(
        self,
        query: str,
        query_embedding: np.ndarray,
        top_k: int = 5,
        article_id: Optional[int] = None,
    ) -> List[dict]:
        """
        Retrieve using a pre-computed embedding vector.
        Used by HyDE strategy to search with hypothetical document embeddings.

        Args:
            query: Original query text (for metadata/logging)
            query_embedding: Pre-computed embedding vector
            top_k: Number of results to return
            article_id: Optional article ID to scope search

        Returns:
            List of result dicts with article_id, content, relevance_score, etc.
        """
        # Search user index with pre-computed embedding
        user_candidates = await self.vector_store.search_with_embedding(
            query_embedding, top_k=top_k * 4, article_id=article_id
        )

        # Search global index
        global_candidates = []
        if self.global_store and article_id is None:
            global_candidates = await self.global_store.search_with_embedding(
                query_embedding, top_k=top_k * 4
            )

        # Merge and rerank
        seen_articles = set()
        merged = []
        for meta, score in user_candidates:
            key = (meta["article_id"], meta["chunk_index"])
            if key not in seen_articles:
                seen_articles.add(key)
                merged.append((meta, score))

        for meta, score in global_candidates:
            key = (meta["article_id"], meta["chunk_index"])
            if key not in seen_articles:
                seen_articles.add(key)
                merged.append((meta, score))

        if not merged:
            return []

        reranked = self._rerank(query, merged, top_k)

        if reranked and reranked[0]["relevance_score"] < self.RELEVANCE_THRESHOLD:
            return []

        return reranked

    async def retrieve_multi(
        self,
        queries: List[str],
        top_k: int = 5,
        article_id: Optional[int] = None,
    ) -> List[dict]:
        """
        Retrieve from multiple sub-queries, merge and rerank.
        Used by decomposition and multi-perspective strategies.

        Each query retrieves independently, then results are merged
        with deduplication and reranked by relevance score.

        Args:
            queries: List of query strings to search with
            top_k: Final number of results to return
            article_id: Optional article ID to scope search

        Returns:
            Merged, deduplicated, reranked list of results
        """
        if not queries:
            return []
        if len(queries) == 1:
            return await self.retrieve(queries[0], top_k, article_id)

        # Retrieve from each query
        all_results = []
        for q in queries:
            results = await self.retrieve(q, top_k=top_k, article_id=article_id)
            all_results.extend(results)

        if not all_results:
            return []

        # Deduplicate by article_id + chunk_index
        seen = set()
        unique = []
        for r in all_results:
            key = (r["article_id"], r["chunk_index"])
            if key not in seen:
                seen.add(key)
                unique.append(r)

        # Rerank by relevance score
        unique.sort(key=lambda x: x["relevance_score"], reverse=True)
        return unique[:top_k]
