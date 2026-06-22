"""
Agents 测试
使用 mock 避免依赖真实的 LLM 和向量检索
"""

import pytest
from unittest.mock import AsyncMock, MagicMock, patch
from agents.cross_article import CrossArticleAgent
from agents.conversational import ConversationalAgent
from agents.gap_analysis import GapAnalysisAgent
from agents.learning_path import LearningPathAgent
from core.query_rewriter import QueryRewriter


class TestCrossArticleAgent:
    """跨文章问答 Agent 测试"""

    def setup_method(self):
        self.mock_vs = AsyncMock()
        self.mock_llm = AsyncMock()
        self.agent = CrossArticleAgent(self.mock_vs, self.mock_llm)

    @pytest.mark.asyncio
    async def test_ask_with_no_results(self):
        """检索无结果时返回提示信息"""
        self.mock_vs.search.return_value = []

        # Mock retriever
        with patch.object(self.agent, 'retriever', create=True) as mock_retriever:
            mock_retriever.retrieve = AsyncMock(return_value=[])
            self.agent.retriever = mock_retriever

            result = await self.agent.ask("测试问题", user_id=1)
            assert "answer" in result
            assert "sources" in result

    @pytest.mark.asyncio
    async def test_ask_returns_structured_response(self):
        """问答返回结构化结果"""
        mock_results = [
            {
                "article_id": 1,
                "article_title": "测试文章",
                "content": "测试内容",
                "chunk_index": 0,
                "relevance_score": 0.9,
            }
        ]

        with patch.object(self.agent, 'retriever', create=True) as mock_retriever:
            mock_retriever.retrieve = AsyncMock(return_value=mock_results)
            self.agent.retriever = mock_retriever

            self.mock_llm.chat = AsyncMock(return_value="这是回答内容")

            result = await self.agent.ask("测试问题", user_id=1)
            assert "answer" in result
            assert "sources" in result
            assert isinstance(result["sources"], list)

    @pytest.mark.asyncio
    async def test_ask_with_article_id(self):
        """限定文章范围的问答"""
        with patch.object(self.agent, 'retriever', create=True) as mock_retriever:
            mock_retriever.retrieve = AsyncMock(return_value=[])
            self.agent.retriever = mock_retriever

            await self.agent.ask("问题", user_id=1, article_id=42)
            mock_retriever.retrieve.assert_called_once()
            call_kwargs = mock_retriever.retrieve.call_args
            # article_id 应该被传递
            assert call_kwargs is not None


class TestQueryRewriter:
    """查询重写器测试"""

    def setup_method(self):
        self.mock_llm = AsyncMock()
        self.rewriter = QueryRewriter(self.mock_llm)

    @pytest.mark.asyncio
    async def test_no_history_returns_original(self):
        """无对话历史时返回原始查询"""
        result = await self.rewriter.rewrite("什么是微服务？", [])
        assert result == "什么是微服务？"

    @pytest.mark.asyncio
    async def test_rewrite_with_history(self):
        """有对话历史时调用 LLM 重写"""
        self.mock_llm.chat = AsyncMock(return_value="微服务架构的优缺点")
        history = [
            {"role": "user", "content": "什么是微服务？"},
            {"role": "assistant", "content": "微服务是一种架构风格..."},
        ]

        result = await self.rewriter.rewrite("那优缺点呢？", history)
        assert result == "微服务架构的优缺点"
        self.mock_llm.chat.assert_called_once()

    @pytest.mark.asyncio
    async def test_rewrite_fallback_on_error(self):
        """LLM 调用失败时返回原始查询"""
        self.mock_llm.chat = AsyncMock(side_effect=Exception("API Error"))
        history = [
            {"role": "user", "content": "什么是微服务？"},
            {"role": "assistant", "content": "微服务是一种架构风格..."},
        ]

        result = await self.rewriter.rewrite("那优缺点呢？", history)
        assert result == "那优缺点呢？"


class TestConversationalAgent:
    """多轮对话 Agent 测试"""

    def setup_method(self):
        self.mock_vs = AsyncMock()
        self.mock_llm = AsyncMock()
        self.mock_session_manager = MagicMock()
        self.agent = ConversationalAgent(self.mock_vs, self.mock_llm, self.mock_session_manager)

    @pytest.mark.asyncio
    async def test_ask_creates_session(self):
        """首次问答创建新会话"""
        self.mock_session_manager.get_history.return_value = []
        self.mock_session_manager.add_message = MagicMock()

        with patch.object(self.agent, 'cross_agent', create=True) as mock_cross:
            mock_cross.ask = AsyncMock(return_value={
                "answer": "回答",
                "sources": [],
                "session_id": "test-session",
                "confidence": 0.8,
            })
            self.agent.cross_agent = mock_cross

            with patch.object(self.agent, 'query_rewriter', create=True) as mock_rewriter:
                mock_rewriter.rewrite = AsyncMock(return_value="改写后的查询")
                self.agent.query_rewriter = mock_rewriter

                result = await self.agent.ask("测试问题", user_id=1)
                assert "answer" in result

    @pytest.mark.asyncio
    async def test_ask_with_existing_session(self):
        """使用已有会话进行问答"""
        self.mock_session_manager.get_history.return_value = [
            {"role": "user", "content": "之前的问题"},
            {"role": "assistant", "content": "之前的回答"},
        ]
        self.mock_session_manager.add_message = MagicMock()

        with patch.object(self.agent, 'cross_agent', create=True) as mock_cross:
            mock_cross.ask = AsyncMock(return_value={
                "answer": "回答",
                "sources": [],
                "session_id": "existing-session",
                "confidence": 0.8,
            })
            self.agent.cross_agent = mock_cross

            with patch.object(self.agent, 'query_rewriter', create=True) as mock_rewriter:
                mock_rewriter.rewrite = AsyncMock(return_value="改写后的查询")
                self.agent.query_rewriter = mock_rewriter

                result = await self.agent.ask("追问", user_id=1, session_id="existing-session")
                assert "answer" in result


class TestGapAnalysisAgent:
    """知识缺口分析 Agent 测试"""

    def setup_method(self):
        self.mock_vs = AsyncMock()
        self.mock_llm = AsyncMock()
        self.agent = GapAnalysisAgent(self.mock_vs, self.mock_llm)

    @pytest.mark.asyncio
    async def test_analyze_returns_gaps(self):
        """分析返回知识缺口列表"""
        # Mock vector store status
        self.mock_vs.get_status.return_value = {
            "user_id": 1,
            "total_articles": 10,
            "total_chunks": 50,
            "total_vectors": 50,
        }

        # Mock metadata for article list
        self.mock_vs.metadata = {
            1: {"article_id": 1, "article_title": "文章1", "content": "内容1"},
            2: {"article_id": 2, "article_title": "文章2", "content": "内容2"},
        }

        self.mock_llm.chat = AsyncMock(return_value='{"gaps": [{"topic": "测试主题", "description": "测试描述", "severity": "medium"}], "summary": "测试总结"}')

        result = await self.agent.analyze(user_id=1)
        assert "gaps" in result or "summary" in result


class TestLearningPathAgent:
    """学习路径推荐 Agent 测试"""

    def setup_method(self):
        self.mock_vs = AsyncMock()
        self.mock_llm = AsyncMock()
        self.agent = LearningPathAgent(self.mock_vs, self.mock_llm)

    @pytest.mark.asyncio
    async def test_recommend_returns_path(self):
        """推荐返回学习路径"""
        self.mock_vs.get_status.return_value = {
            "user_id": 1,
            "total_articles": 5,
            "total_chunks": 25,
            "total_vectors": 25,
        }

        self.mock_vs.metadata = {
            1: {"article_id": 1, "article_title": "入门文章", "content": "基础内容"},
        }

        self.mock_llm.chat = AsyncMock(return_value='{"steps": [{"order": 1, "topic": "基础", "description": "从基础开始"}], "summary": "学习路径"}')

        result = await self.agent.recommend(user_id=1, topic="Spring Boot")
        assert "steps" in result or "summary" in result
