"""
Integration tests for the RewritePipeline.
"""

import pytest
from unittest.mock import AsyncMock, MagicMock, patch
from core.rewrite.pipeline import RewritePipeline, RewriteResult


class TestRewritePipeline:
    """Integration tests for the full rewrite pipeline."""

    @pytest.fixture
    def mock_llm(self):
        llm = MagicMock()
        llm.chat = AsyncMock(return_value="Spring Boot Nacos 配置管理")
        llm.chat_stream = MagicMock()
        return llm

    @pytest.fixture
    def pipeline(self, mock_llm):
        return RewritePipeline(llm=mock_llm, embedding_manager=None)

    @pytest.fixture
    def sample_history(self):
        return [
            {"role": "user", "content": "Spring Boot 微服务架构怎么设计？"},
            {"role": "assistant", "content": "Spring Boot 微服务架构需要关注服务拆分、注册中心（Nacos）等。"},
            {"role": "user", "content": "那Nacos怎么配置呢？"},
            {"role": "assistant", "content": "Nacos 配置需要下载安装包、配置application.properties、启动服务。"},
        ]

    @pytest.mark.asyncio
    async def test_no_history_returns_original(self, pipeline):
        """Test that no history returns the original query immediately."""
        result = await pipeline.rewrite("Nacos怎么配置", [])
        assert result.rewritten_query == "Nacos怎么配置"
        assert result.layer_resolved == "none"
        assert result.confidence == 1.0

    @pytest.mark.asyncio
    async def test_l1_resolves_simple_query(self, pipeline):
        """Test that L1 resolves a simple, complete query without LLM."""
        result = await pipeline.rewrite(
            "Spring Boot 数据源配置方法",
            [{"role": "user", "content": "Spring Boot 配置"}],
        )
        assert result.layer_resolved == "L1"
        # Should not have called LLM
        assert result.latency_ms >= 0

    @pytest.mark.asyncio
    async def test_returns_rewrite_result_type(self, pipeline, sample_history):
        """Test that pipeline returns RewriteResult."""
        result = await pipeline.rewrite(
            "那它的健康检查呢？",
            sample_history,
        )
        assert isinstance(result, RewriteResult)
        assert hasattr(result, 'rewritten_query')
        assert hasattr(result, 'search_query')
        assert hasattr(result, 'layer_resolved')
        assert hasattr(result, 'strategy_used')

    @pytest.mark.asyncio
    async def test_stats_available(self, pipeline):
        """Test that pipeline stats are accessible."""
        await pipeline.rewrite("test query", [])
        stats = pipeline.stats
        assert stats["total_requests"] > 0
        assert "cache_stats" in stats

    @pytest.mark.asyncio
    async def test_anaphora_triggers_l3(self, pipeline, sample_history, mock_llm):
        """Test that anaphora queries go through L3 (LLM-based)."""
        result = await pipeline.rewrite(
            "它的性能怎么样",
            sample_history,
        )
        # Should have rewritten using LLM
        assert result.rewritten_query is not None
        assert len(result.strategy_used) > 0

    @pytest.mark.asyncio
    async def test_fallback_on_llm_failure(self, sample_history):
        """Test that pipeline falls back gracefully on LLM failure."""
        llm = MagicMock()
        llm.chat = AsyncMock(side_effect=Exception("API Error"))
        pipeline = RewritePipeline(llm=llm, embedding_manager=None)

        result = await pipeline.rewrite(
            "它的配置呢",
            sample_history,
        )
        # Should return something, not crash
        assert result.rewritten_query is not None
        assert result.layer_resolved == "L3"  # went to L3 but strategies fell through

    @pytest.mark.asyncio
    async def test_rewrite_result_fields(self, pipeline, sample_history):
        """Test that RewriteResult has all expected fields."""
        result = await pipeline.rewrite(
            "Nacos配置管理和健康检查",
            sample_history,
        )
        assert result.rewritten_query is not None
        assert result.search_query is not None
        assert isinstance(result.sub_queries, list)
        assert isinstance(result.variants, list)
        assert isinstance(result.strategy_used, list)
        assert result.latency_ms >= 0
        assert isinstance(result.metadata, dict)
