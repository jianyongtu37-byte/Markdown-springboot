"""
对话会话管理 — MySQL 后端
持久化存储对话历史，重启不丢失
支持 MySQL 不可用时自动降级为内存存储
"""

import json
import uuid
import time
import logging
from typing import List, Dict, Optional
import pymysql

from config import Config

logger = logging.getLogger("rag.mysql_session")


class MySQLSessionManager:
    """对话会话管理器（MySQL 后端）

    当 MySQL 不可用时自动降级为内存存储，避免服务完全不可用。
    """

    MAX_HISTORY_ROUNDS = 10

    def __init__(self, host=None, port=None, user=None, password=None, database=None):
        self.host = host or getattr(Config, "MYSQL_HOST", "localhost")
        self.port = port or getattr(Config, "MYSQL_PORT", 3306)
        self.user = user or getattr(Config, "MYSQL_USER", "root")
        self.password = password or getattr(Config, "MYSQL_PASSWORD", "root")
        self.database = database or getattr(Config, "MYSQL_DATABASE", "markdown_db")
        self._mysql_available = False
        # 内存降级存储
        self._memory_sessions: Dict[str, dict] = {}  # session_id -> {user_id, title, created_at, updated_at}
        self._memory_messages: Dict[str, list] = {}   # session_id -> [messages]
        self._init_db()

    def _get_conn(self):
        return pymysql.connect(
            host=self.host,
            port=self.port,
            user=self.user,
            password=self.password,
            database=self.database,
            charset="utf8mb4",
            cursorclass=pymysql.cursors.DictCursor,
            autocommit=True,
            connect_timeout=3,
            read_timeout=5,
        )

    def _init_db(self):
        """创建表（如果不存在），并自动升级已有表结构。
        如果 MySQL 不可用，降级为内存存储。
        """
        try:
            conn = self._get_conn()
            try:
                with conn.cursor() as cur:
                    cur.execute("""
                        CREATE TABLE IF NOT EXISTS rag_sessions (
                            session_id VARCHAR(36) PRIMARY KEY,
                            user_id BIGINT NOT NULL,
                            title VARCHAR(200) DEFAULT '',
                            created_at DOUBLE NOT NULL,
                            updated_at DOUBLE NOT NULL,
                            INDEX idx_user_id (user_id),
                            INDEX idx_updated_at (updated_at)
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """)
                    cur.execute("""
                        CREATE TABLE IF NOT EXISTS rag_messages (
                            id BIGINT AUTO_INCREMENT PRIMARY KEY,
                            session_id VARCHAR(36) NOT NULL,
                            role VARCHAR(20) NOT NULL,
                            content TEXT NOT NULL,
                            sources TEXT,
                            confidence DOUBLE DEFAULT NULL,
                            timestamp DOUBLE NOT NULL,
                            INDEX idx_session_id (session_id),
                            FOREIGN KEY (session_id) REFERENCES rag_sessions(session_id) ON DELETE CASCADE
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """)
                    # 自动升级：为已有表添加缺失的列
                    try:
                        cur.execute("ALTER TABLE rag_messages ADD COLUMN sources TEXT AFTER content")
                    except Exception:
                        pass  # 列已存在，忽略
                    try:
                        cur.execute("ALTER TABLE rag_messages ADD COLUMN confidence DOUBLE DEFAULT NULL AFTER sources")
                    except Exception:
                        pass  # 列已存在，忽略
                self._mysql_available = True
                logger.info("MySQL 会话管理器初始化成功 (host=%s:%d)", self.host, self.port)
            finally:
                conn.close()
        except Exception as e:
            logger.warning("MySQL 不可用，会话管理降级为内存存储: %s", e)
            self._mysql_available = False

    def _db_op(self, mysql_fn, fallback_fn):
        """执行 MySQL 操作，失败时自动降级到内存存储"""
        if not self._mysql_available:
            return fallback_fn()
        try:
            return mysql_fn()
        except Exception as e:
            logger.warning("MySQL 操作失败，降级为内存存储: %s", e)
            self._mysql_available = False
            return fallback_fn()

    def create_session(self, user_id: int) -> str:
        """创建新会话"""
        session_id = str(uuid.uuid4())
        now = time.time()

        def mysql_op():
            conn = self._get_conn()
            try:
                with conn.cursor() as cur:
                    cur.execute(
                        "INSERT INTO rag_sessions (session_id, user_id, title, created_at, updated_at) VALUES (%s, %s, %s, %s, %s)",
                        (session_id, user_id, "", now, now),
                    )
            finally:
                conn.close()

        def fallback():
            self._memory_sessions[session_id] = {
                "user_id": user_id,
                "title": "",
                "created_at": now,
                "updated_at": now,
            }
            self._memory_messages[session_id] = []

        self._db_op(mysql_op, fallback)
        return session_id

    def get_history(self, user_id: int, session_id: str) -> List[Dict]:
        """获取对话历史（含 sources 和 confidence）"""
        def mysql_op():
            conn = self._get_conn()
            try:
                with conn.cursor() as cur:
                    cur.execute(
                        "SELECT 1 FROM rag_sessions WHERE session_id=%s AND user_id=%s",
                        (session_id, user_id),
                    )
                    if not cur.fetchone():
                        return []
                    cur.execute(
                        "SELECT role, content, sources, confidence, timestamp FROM rag_messages WHERE session_id=%s ORDER BY id ASC",
                        (session_id,),
                    )
                    rows = cur.fetchall()
                    result = []
                    for r in rows:
                        msg: Dict = {
                            "role": r["role"],
                            "content": r["content"],
                            "timestamp": r["timestamp"],
                        }
                        if r.get("sources"):
                            try:
                                msg["sources"] = json.loads(r["sources"])
                            except (json.JSONDecodeError, TypeError):
                                pass
                        if r.get("confidence") is not None:
                            msg["confidence"] = r["confidence"]
                        result.append(msg)
                    return result
            finally:
                conn.close()

        def fallback():
            session = self._memory_sessions.get(session_id)
            if not session or session["user_id"] != user_id:
                return []
            return list(self._memory_messages.get(session_id, []))

        return self._db_op(mysql_op, fallback)

    def add_message(
        self,
        user_id: int,
        session_id: str,
        role: str,
        content: str,
        sources: Optional[str] = None,
        confidence: Optional[float] = None,
    ):
        """添加一条消息，可选保存 sources 和 confidence"""
        now = time.time()

        def mysql_op():
            conn = self._get_conn()
            try:
                with conn.cursor() as cur:
                    cur.execute("SELECT user_id FROM rag_sessions WHERE session_id=%s", (session_id,))
                    row = cur.fetchone()
                    if row:
                        if row["user_id"] != user_id:
                            raise ValueError("无权向该会话写入消息")
                    else:
                        cur.execute(
                            "INSERT INTO rag_sessions (session_id, user_id, title, created_at, updated_at) VALUES (%s, %s, %s, %s, %s)",
                            (session_id, user_id, "", now, now),
                        )
                    if role == "user":
                        cur.execute("SELECT title FROM rag_sessions WHERE session_id=%s", (session_id,))
                        row = cur.fetchone()
                        if row and not row["title"]:
                            cur.execute(
                                "UPDATE rag_sessions SET title=%s WHERE session_id=%s",
                                (content[:50], session_id),
                            )
                    cur.execute(
                        "INSERT INTO rag_messages (session_id, role, content, sources, confidence, timestamp) VALUES (%s, %s, %s, %s, %s, %s)",
                        (session_id, role, content, sources, confidence, now),
                    )
                    cur.execute(
                        "UPDATE rag_sessions SET updated_at=%s WHERE session_id=%s",
                        (now, session_id),
                    )
                    cur.execute(
                        "SELECT COUNT(*) AS cnt FROM rag_messages WHERE session_id=%s", (session_id,)
                    )
                    cnt = cur.fetchone()["cnt"]
                    max_msgs = self.MAX_HISTORY_ROUNDS * 2
                    if cnt > max_msgs:
                        cur.execute(
                            "DELETE FROM rag_messages WHERE session_id=%s ORDER BY id ASC LIMIT %s",
                            (session_id, cnt - max_msgs),
                        )
            finally:
                conn.close()

        def fallback():
            if session_id not in self._memory_sessions:
                self._memory_sessions[session_id] = {
                    "user_id": user_id,
                    "title": "",
                    "created_at": now,
                    "updated_at": now,
                }
                self._memory_messages[session_id] = []
            session = self._memory_sessions[session_id]
            if session["user_id"] != user_id:
                raise ValueError("无权向该会话写入消息")
            if role == "user" and not session["title"]:
                session["title"] = content[:50]
            msg = {"role": role, "content": content, "timestamp": now}
            if sources is not None:
                msg["sources"] = sources
            if confidence is not None:
                msg["confidence"] = confidence
            self._memory_messages[session_id].append(msg)
            session["updated_at"] = now
            max_msgs = self.MAX_HISTORY_ROUNDS * 2
            if len(self._memory_messages[session_id]) > max_msgs:
                self._memory_messages[session_id] = self._memory_messages[session_id][-max_msgs:]

        self._db_op(mysql_op, fallback)

    def clear_session(self, user_id: int, session_id: str) -> bool:
        """清除会话"""
        def mysql_op():
            conn = self._get_conn()
            try:
                with conn.cursor() as cur:
                    cur.execute(
                        "SELECT 1 FROM rag_sessions WHERE session_id=%s AND user_id=%s",
                        (session_id, user_id),
                    )
                    if not cur.fetchone():
                        return False
                    cur.execute("DELETE FROM rag_messages WHERE session_id=%s", (session_id,))
                    cur.execute("DELETE FROM rag_sessions WHERE session_id=%s AND user_id=%s", (session_id, user_id))
                    return cur.rowcount > 0
            finally:
                conn.close()

        def fallback():
            session = self._memory_sessions.get(session_id)
            if not session or session["user_id"] != user_id:
                return False
            del self._memory_sessions[session_id]
            self._memory_messages.pop(session_id, None)
            return True

        return self._db_op(mysql_op, fallback)

    def list_sessions(self, user_id: int) -> List[dict]:
        """列出用户的所有会话"""
        def mysql_op():
            conn = self._get_conn()
            try:
                with conn.cursor() as cur:
                    cur.execute(
                        """SELECT s.session_id, s.created_at, s.updated_at, s.title,
                                  (SELECT COUNT(*) FROM rag_messages m WHERE m.session_id = s.session_id) AS message_count
                           FROM rag_sessions s
                           WHERE s.user_id=%s
                           ORDER BY s.updated_at DESC""",
                        (user_id,),
                    )
                    rows = cur.fetchall()
                    return [
                        {
                            "session_id": r["session_id"],
                            "created_at": r["created_at"],
                            "updated_at": r["updated_at"],
                            "title": r["title"] or "",
                            "message_count": r["message_count"],
                        }
                        for r in rows
                    ]
            finally:
                conn.close()

        def fallback():
            return [
                {
                    "session_id": sid,
                    "created_at": s["created_at"],
                    "updated_at": s["updated_at"],
                    "title": s.get("title") or "",
                    "message_count": len(self._memory_messages.get(sid, [])),
                }
                for sid, s in sorted(
                    self._memory_sessions.items(),
                    key=lambda x: x[1]["updated_at"],
                    reverse=True,
                )
                if s["user_id"] == user_id
            ]

        return self._db_op(mysql_op, fallback)
