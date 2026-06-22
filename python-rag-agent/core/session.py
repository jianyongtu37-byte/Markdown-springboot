"""
对话会话管理
基于 Redis 存储对话历史，支持多轮对话上下文
"""

import json
import uuid
import time
from typing import List, Dict, Optional
import redis

from config import Config


class SessionManager:
    """对话会话管理器（Redis 后端）"""

    # 会话过期时间：30 分钟
    SESSION_TTL = 1800
    # 最大历史轮数（每轮=user+assistant）
    MAX_HISTORY_ROUNDS = 10
    # Redis key 前缀
    KEY_PREFIX = "rag:session:"

    def __init__(self, redis_client: Optional[redis.Redis] = None):
        if redis_client:
            self.redis = redis_client
        else:
            try:
                self.redis = redis.Redis(
                    host=getattr(Config, "REDIS_HOST", "localhost"),
                    port=getattr(Config, "REDIS_PORT", 6379),
                    db=getattr(Config, "REDIS_DB", 0),
                    decode_responses=True,
                )
                self.redis.ping()
            except Exception:
                # Redis 不可用时降级为内存存储
                self.redis = None
                self._memory_store: Dict[str, dict] = {}

    def create_session(self, user_id: int) -> str:
        """创建新会话，返回 session_id"""
        session_id = str(uuid.uuid4())
        key = self._key(user_id, session_id)
        data = {
            "session_id": session_id,
            "user_id": user_id,
            "created_at": time.time(),
            "updated_at": time.time(),
            "title": "",
            "messages": [],
        }
        self._save(key, data)
        return session_id

    def get_history(self, user_id: int, session_id: str) -> List[Dict]:
        """获取对话历史"""
        key = self._key(user_id, session_id)
        data = self._load(key)
        if not data:
            return []
        return data.get("messages", [])

    def add_message(
        self, user_id: int, session_id: str, role: str, content: str
    ):
        """添加一条消息到历史"""
        key = self._key(user_id, session_id)
        data = self._load(key)
        if not data:
            # 会话不存在，自动创建
            data = {
                "session_id": session_id,
                "user_id": user_id,
                "created_at": time.time(),
                "updated_at": time.time(),
                "title": "",
                "messages": [],
            }
        # 用第一条用户消息作为会话标题
        if role == "user" and not data.get("title"):
            data["title"] = content[:50]

        data["messages"].append(
            {"role": role, "content": content, "timestamp": time.time()}
        )

        # 限制历史长度
        max_messages = self.MAX_HISTORY_ROUNDS * 2
        if len(data["messages"]) > max_messages:
            data["messages"] = data["messages"][-max_messages:]

        data["updated_at"] = time.time()
        self._save(key, data)

    def clear_session(self, user_id: int, session_id: str) -> bool:
        """清除会话"""
        key = self._key(user_id, session_id)
        if self.redis:
            return bool(self.redis.delete(key))
        else:
            self._memory_store.pop(key, None)
            return True

    def list_sessions(self, user_id: int) -> List[dict]:
        """列出用户的所有活跃会话"""
        if self.redis:
            pattern = f"{self.KEY_PREFIX}{user_id}:*"
            sessions = []
            for key in self.redis.scan_iter(match=pattern):
                data = self._load(key)
                if data:
                    sessions.append(
                        {
                            "session_id": data["session_id"],
                            "created_at": data["created_at"],
                            "updated_at": data["updated_at"],
                            "title": data.get("title", ""),
                            "message_count": len(data.get("messages", [])),
                        }
                    )
            sessions.sort(key=lambda x: x["updated_at"], reverse=True)
            return sessions
        else:
            sessions = []
            for key, data in self._memory_store.items():
                if key.startswith(f"{self.KEY_PREFIX}{user_id}:"):
                    sessions.append(
                        {
                            "session_id": data["session_id"],
                            "created_at": data["created_at"],
                            "updated_at": data["updated_at"],
                            "title": data.get("title", ""),
                            "message_count": len(data.get("messages", [])),
                        }
                    )
            sessions.sort(key=lambda x: x["updated_at"], reverse=True)
            return sessions

    def _key(self, user_id: int, session_id: str) -> str:
        return f"{self.KEY_PREFIX}{user_id}:{session_id}"

    def _save(self, key: str, data: dict):
        """保存会话数据"""
        if self.redis:
            self.redis.setex(key, self.SESSION_TTL, json.dumps(data))
        else:
            self._memory_store[key] = data

    def _load(self, key: str) -> Optional[dict]:
        """加载会话数据"""
        if self.redis:
            raw = self.redis.get(key)
            if raw:
                return json.loads(raw)
            return None
        else:
            return self._memory_store.get(key)
