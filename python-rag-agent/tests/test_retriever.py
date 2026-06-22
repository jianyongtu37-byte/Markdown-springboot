"""
Retriever 检索器测试
使用 mock 避免依赖真实的 FAISS 索引和 embedding 模型
"""

import pytest
from unittest.mock import AsyncMock, MagicMock, patch
from core.retriever import Retriever


class TestRetriever:
    """检索器单元测试"""

    def setup_method(self):
        self.mock_vs = AsyncMock()
        self.retriever = Retriever(self.mock_vs)

    @pytest.mark.asyncio
    async def test_empty_index_returns_empty(self):
        """空索引返回空结果"""
        self.mock_vs.search.return_value = []
        results = await self.retriever.retrieve("测试查询")
        assert results == []

    @pytest.mark.asyncio
    async def test_retrieve_returns_top_k(self):
        """检索返回指定数量的结果"""
        # 模拟向量检索返回 6 个候选
        candidates = [
            ({"article_id": 1, "article_title": "文章1", "content": "内容1", "chunk_index": 0}, 0.9),
            ({"article_id": 2, "article_title": "文章2", "content": "内容2", "chunk_index": 0}, 0.8),
            ({"article_id": 3, "article_title": "文章3", "content": "内容3", "chunk_index": 0}, 0.7),
            ({"article_id": 4, "article_title": "文章4", "content": "内容4", "chunk_index": 0}, 0.6),
            ({"article_id": 5, "article_title": "文章5", "content": "内容5", "chunk_index": 0}, 0.5),
            ({"article_id": 6, "article_title": "文章6", "content": "内容6", "chunk_index": 0}, 0.4),
        ]
        self.mock_vs.search.return_value = candidates

        results = await self.retriever.retrieve("测试查询", top_k=3)
        assert len(results) <= 3

    @pytest.mark.asyncio
    async def test_rerank_deduplicates_articles(self):
        """重排序时同一文章最多取 2 个 chunk"""
        candidates = [
            ({"article_id": 1, "article_title": "文章1", "content": "内容A", "chunk_index": 0}, 0.95),
            ({"article_id": 1, "article_title": "文章1", "content": "内容B", "chunk_index": 1}, 0.90),
            ({"article_id": 1, "article_title": "文章1", "content": "内容C", "chunk_index": 2}, 0.85),
            ({"article_id": 2, "article_title": "文章2", "content": "内容D", "chunk_index": 0}, 0.80),
        ]
        self.mock_vs.search.return_value = candidates

        results = await self.retriever.retrieve("测试查询", top_k=10)
        # 文章1 最多出现 2 次
        article_1_count = sum(1 for r in results if r["article_id"] == 1)
        assert article_1_count <= 2

    @pytest.mark.asyncio
    async def test_results_sorted_by_score(self):
        """结果按相关性分数降序排列"""
        candidates = [
            ({"article_id": 1, "article_title": "文章1", "content": "内容1", "chunk_index": 0}, 0.5),
            ({"article_id": 2, "article_title": "文章2", "content": "内容2", "chunk_index": 0}, 0.9),
            ({"article_id": 3, "article_title": "文章3", "content": "内容3", "chunk_index": 0}, 0.7),
        ]
        self.mock_vs.search.return_value = candidates

        results = await self.retriever.retrieve("测试查询", top_k=3)
        scores = [r["relevance_score"] for r in results]
        assert scores == sorted(scores, reverse=True)

    @pytest.mark.asyncio
    async def test_article_id_filter_passed(self):
        """article_id 参数正确传递给向量检索"""
        self.mock_vs.search.return_value = []
        await self.retriever.retrieve("查询", article_id=42)
        self.mock_vs.search.assert_called_once()
        call_kwargs = self.mock_vs.search.call_args
        assert call_kwargs[1].get("article_id") == 42 or (len(call_kwargs[0]) > 2 and call_kwargs[0][2] == 42)

    @pytest.mark.asyncio
    async def test_retrieve_preserves_metadata(self):
        """检索结果保留完整的元数据"""
        candidates = [
            ({"article_id": 1, "article_title": "测试文章", "content": "测试内容", "chunk_index": 3}, 0.9),
        ]
        self.mock_vs.search.return_value = candidates

        results = await self.retriever.retrieve("查询", top_k=1)
        assert len(results) == 1
        assert results[0]["article_id"] == 1
        assert results[0]["article_title"] == "测试文章"
        assert results[0]["content"] == "测试内容"
        assert results[0]["chunk_index"] == 3
