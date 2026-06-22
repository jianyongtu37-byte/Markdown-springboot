"""
API 路由测试
使用 FastAPI TestClient 测试 HTTP 端点
"""

import pytest
from unittest.mock import AsyncMock, MagicMock, patch
from fastapi.testclient import TestClient


class TestHealthEndpoint:
    """健康检查端点测试"""

    def test_health_check(self):
        """健康检查返回 200"""
        # 延迟导入避免模块级副作用
        from main import app
        client = TestClient(app)
        response = client.get("/health")
        assert response.status_code == 200
        data = response.json()
        assert data["status"] == "ok"


class TestAskEndpoint:
    """问答端点测试"""

    @patch("api.routes.get_vector_store")
    @patch("api.routes.get_llm")
    @patch("api.routes.get_session_manager")
    def test_ask_returns_response(self, mock_sm, mock_llm, mock_vs):
        """POST /api/rag/ask 返回问答结果"""
        from main import app
        client = TestClient(app)

        # Mock dependencies
        mock_vs_instance = AsyncMock()
        mock_vs.return_value = mock_vs_instance

        mock_llm_instance = AsyncMock()
        mock_llm.return_value = mock_llm_instance

        mock_sm_instance = MagicMock()
        mock_sm_instance.get_history.return_value = []
        mock_sm_instance.add_message = MagicMock()
        mock_sm.return_value = mock_sm_instance

        # Mock agent response
        with patch("api.routes.ConversationalAgent") as MockAgent:
            mock_agent = AsyncMock()
            mock_agent.ask = AsyncMock(return_value={
                "answer": "这是回答",
                "sources": [],
                "session_id": "test-session",
                "confidence": 0.8,
            })
            MockAgent.return_value = mock_agent

            response = client.post("/api/rag/ask", json={
                "question": "什么是 Spring Boot？",
                "user_id": 1,
            })

            assert response.status_code == 200
            data = response.json()
            assert "answer" in data

    def test_ask_missing_question(self):
        """缺少 question 字段返回 422"""
        from main import app
        client = TestClient(app)

        response = client.post("/api/rag/ask", json={
            "user_id": 1,
        })
        assert response.status_code == 422


class TestSessionEndpoints:
    """会话管理端点测试"""

    @patch("api.routes.get_session_manager")
    def test_list_sessions(self, mock_sm):
        """GET /api/rag/sessions/{user_id} 返回会话列表"""
        from main import app
        client = TestClient(app)

        mock_sm_instance = MagicMock()
        mock_sm_instance.list_sessions.return_value = [
            {"session_id": "s1", "message_count": 4},
            {"session_id": "s2", "message_count": 2},
        ]
        mock_sm.return_value = mock_sm_instance

        response = client.get("/api/rag/sessions/1")
        assert response.status_code == 200
        data = response.json()
        assert "sessions" in data
        assert len(data["sessions"]) == 2

    @patch("api.routes.get_session_manager")
    def test_get_session_history(self, mock_sm):
        """GET /api/rag/sessions/{user_id}/{session_id}/history 返回对话历史"""
        from main import app
        client = TestClient(app)

        mock_sm_instance = MagicMock()
        mock_sm_instance.get_history.return_value = [
            {"role": "user", "content": "问题"},
            {"role": "assistant", "content": "回答"},
        ]
        mock_sm.return_value = mock_sm_instance

        response = client.get("/api/rag/sessions/1/test-session/history")
        assert response.status_code == 200
        data = response.json()
        assert data["session_id"] == "test-session"
        assert len(data["messages"]) == 2

    @patch("api.routes.get_session_manager")
    def test_clear_session(self, mock_sm):
        """DELETE /api/rag/sessions/{user_id}/{session_id} 清除会话"""
        from main import app
        client = TestClient(app)

        mock_sm_instance = MagicMock()
        mock_sm_instance.clear_session.return_value = True
        mock_sm.return_value = mock_sm_instance

        response = client.delete("/api/rag/sessions/1/test-session")
        assert response.status_code == 200
        data = response.json()
        assert data["status"] == "cleared"


class TestIndexEndpoints:
    """索引管理端点测试"""

    @patch("api.routes.get_vector_store")
    def test_get_index_status(self, mock_vs):
        """GET /api/rag/status/{user_id} 返回索引状态"""
        from main import app
        client = TestClient(app)

        mock_vs_instance = MagicMock()
        mock_vs_instance.get_status.return_value = {
            "user_id": 1,
            "total_articles": 5,
            "total_chunks": 25,
            "total_vectors": 25,
        }
        mock_vs.return_value = mock_vs_instance

        response = client.get("/api/rag/status/1")
        assert response.status_code == 200
        data = response.json()
        assert data["total_articles"] == 5
        assert data["total_chunks"] == 25

    @patch("api.routes.get_vector_store")
    @patch("api.routes.get_chunker")
    def test_sync_article(self, mock_chunker, mock_vs):
        """POST /api/rag/article/sync 同步文章"""
        from main import app
        client = TestClient(app)

        mock_chunker_instance = MagicMock()
        mock_chunker_instance.chunk.return_value = [
            {"content": "分块1", "index": 0},
            {"content": "分块2", "index": 1},
        ]
        mock_chunker.return_value = mock_chunker_instance

        mock_vs_instance = AsyncMock()
        mock_vs.return_value = mock_vs_instance

        response = client.post("/api/rag/article/sync", json={
            "user_id": 1,
            "article_id": 42,
            "article_title": "测试文章",
            "content": "# 测试\n\n这是测试内容。",
        })

        assert response.status_code == 200
        data = response.json()
        assert data["status"] == "synced"
        assert data["article_id"] == 42
        assert data["chunks_count"] == 2

    @patch("api.routes.get_vector_store")
    def test_remove_article(self, mock_vs):
        """DELETE /api/rag/article/{user_id}/{article_id} 删除文章索引"""
        from main import app
        client = TestClient(app)

        mock_vs_instance = AsyncMock()
        mock_vs.return_value = mock_vs_instance

        response = client.delete("/api/rag/article/1/42")
        assert response.status_code == 200
        data = response.json()
        assert data["status"] == "removed"
        assert data["article_id"] == 42


class TestStreamEndpoint:
    """流式端点测试"""

    @patch("api.routes.get_vector_store")
    @patch("api.routes.get_llm")
    @patch("api.routes.get_session_manager")
    def test_ask_stream_returns_sse(self, mock_sm, mock_llm, mock_vs):
        """POST /api/rag/ask/stream 返回 SSE 流"""
        from main import app
        client = TestClient(app)

        mock_vs.return_value = AsyncMock()
        mock_llm.return_value = AsyncMock()
        mock_sm.return_value = MagicMock()

        async def mock_ask_stream(**kwargs):
            yield {"type": "token", "content": "你"}
            yield {"type": "token", "content": "好"}
            yield {"type": "sources", "sources": []}
            yield {"type": "done"}

        with patch("api.routes.ConversationalAgent") as MockAgent:
            mock_agent = AsyncMock()
            mock_agent.ask_stream = mock_ask_stream
            MockAgent.return_value = mock_agent

            response = client.post("/api/rag/ask/stream", json={
                "question": "你好",
                "user_id": 1,
            })

            assert response.status_code == 200
            assert "text/event-stream" in response.headers["content-type"]
