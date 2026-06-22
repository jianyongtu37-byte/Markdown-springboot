"""
Tests for Layer 1 Rule Engine.
"""

import pytest
from core.rewrite.layer1_rules import Layer1RuleEngine, LayerResult
from core.rewrite.domain_dict import DomainDict


class TestLayer1RuleEngine:
    """Tests for the L1 rule engine."""

    @pytest.fixture
    def engine(self):
        return Layer1RuleEngine()

    @pytest.fixture
    def sample_history(self):
        return [
            {"role": "user", "content": "Spring Boot 微服务架构怎么设计？"},
            {"role": "assistant", "content": "Spring Boot 微服务架构需要关注服务拆分、服务注册与发现（使用《Nacos》）、配置管理等。"},
            {"role": "user", "content": "它的配置管理怎么做？"},
        ]

    def test_normalize(self, engine):
        """Test whitespace and punctuation normalization."""
        result = engine.process(
            "  如何  配置   Nacos   ？？？  ",
            [],
        )
        assert "配置" in result.search_query
        assert "Nacos" in result.search_query

    def test_no_history_returns_with_confidence(self, engine):
        """Test that simple queries without history get high confidence."""
        result = engine.process(
            "Spring Boot 如何配置数据源",
            [],
        )
        assert result.is_final is True
        assert result.confidence >= 0.9

    def test_stopword_removal(self, engine):
        """Test that Chinese stopwords are removed from search query."""
        result = engine.process(
            "我想问一下那个Spring Boot的项目怎么部署到服务器上的呢",
            [],
        )
        # Stopwords like 我想问一下, 那个, 的, 呢 should be removed
        search = result.search_query
        assert "Spring" in search or "Spring Boot" in search
        assert "部署" in search
        assert "服务器" in search

    def test_anaphora_detection(self, engine):
        """Test that anaphora is detected in the result."""
        result = engine.process(
            "它的配置管理怎么做？",
            [{"role": "assistant", "content": "使用《Nacos》作为配置中心..."}],
        )
        assert result.metadata["has_anaphora"] is True

    def test_compound_detection(self, engine):
        """Test compound question detection."""
        result = engine.process(
            "Spring Boot 配置数据源并且还要配置Redis缓存",
            [],
        )
        assert result.metadata["has_compound"] is True

    def test_simple_anaphora_resolution(self, engine, sample_history):
        """Test that simple anaphora is resolved."""
        result = engine.process("它的配置管理怎么做？", sample_history)
        # The pronoun 它 should be resolved
        # The entity Nacos should appear from history
        assert len(result.current) > 5

    def test_domain_abbreviation_expansion(self, engine):
        """Test domain abbreviation expansion."""
        result = engine.process("ES 怎么配置", [])
        search = result.search_query
        assert "Elasticsearch" in search or "es" in search.lower()

    def test_very_short_query(self, engine):
        """Test very short query handling."""
        result = engine.process("它呢", [])
        assert result.is_final is False  # too ambiguous
        assert result.confidence < 0.9
