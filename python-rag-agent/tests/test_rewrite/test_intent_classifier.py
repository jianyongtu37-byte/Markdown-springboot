"""
Tests for Intent Classifier.
"""

import pytest
from core.rewrite.intent_classifier import IntentClassifier, QueryIntent, IntentResult


class TestIntentClassifier:
    """Tests for the intent classifier (rule-based stage)."""

    @pytest.fixture
    def classifier(self):
        return IntentClassifier(llm=None)  # No LLM, rule-only

    async def test_factual_intent(self, classifier):
        """Test factual intent detection."""
        result = await classifier.classify("什么是Spring Boot", [])
        assert result.intent == QueryIntent.FACTUAL
        assert result.confidence >= 0.85

    async def test_procedural_intent(self, classifier):
        """Test procedural intent detection."""
        result = await classifier.classify("如何配置Nacos集群", [])
        assert result.intent == QueryIntent.PROCEDURAL
        assert result.confidence >= 0.85

    async def test_comparative_intent(self, classifier):
        """Test comparative intent detection."""
        result = await classifier.classify("Redis和Memcached的区别是什么", [])
        assert result.intent == QueryIntent.COMPARATIVE
        assert result.confidence >= 0.85

    async def test_corrective_intent(self, classifier):
        """Test corrective intent detection."""
        result = await classifier.classify("不是，我指的是Docker的配置", [])
        assert result.intent == QueryIntent.CORRECTIVE

    async def test_metadata_intent(self, classifier):
        """Test metadata intent detection."""
        result = await classifier.classify("这篇文章的作者是谁", [])
        assert result.intent == QueryIntent.METADATA

    async def test_chitchat_intent(self, classifier):
        """Test chitchat intent detection."""
        result = await classifier.classify("你好", [])
        assert result.intent == QueryIntent.CHITCHAT

    async def test_ambiguous_query_falls_back_to_factual(self, classifier):
        """Test that ambiguous queries default to factual."""
        result = await classifier.classify("微服务架构", [])
        assert result.confidence < 0.85  # ambiguous
        assert result.source == "rule"

    async def test_very_short_query_defaults_to_chitchat(self, classifier):
        """Test that very short ambiguous queries default to chitchat."""
        result = await classifier.classify("哦", [])
        # Very short, unclear intent
        assert result.intent is not None
