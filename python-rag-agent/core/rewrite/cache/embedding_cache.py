"""
Embedding vector cache — avoid re-encoding identical text.

Key: MD5(text) → embedding bytes (Redis) or numpy array (memory).
TTL: 1 hour default.

This is valuable because the same query (or a HyDE-generated text)
may be re-encoded multiple times across different retrieval paths.
"""

import hashlib
import json
import time
import numpy as np
from typing import Dict, Optional


class EmbeddingCache:
    """Cache for embedding vectors. Redis-backed with in-memory fallback."""

    KEY_PREFIX = "rag:emb:"
    MAX_MEMORY_ENTRIES = 2000

    def __init__(self, redis_client=None):
        self._redis = redis_client
        self._memory: Dict[str, np.ndarray] = {}
        self._memory_timestamps: Dict[str, float] = {}
        self._hits = 0
        self._misses = 0
        if self._redis is None:
            self._try_connect()

    def _try_connect(self):
        try:
            import redis
            from config import Config
            self._redis = redis.Redis(
                host=Config.MYSQL_HOST,
                port=6379,
                db=getattr(Config, "REWRITE_CACHE_REDIS_DB", 3),
                socket_connect_timeout=1,
                socket_timeout=1,
            )
            self._redis.ping()
        except Exception:
            self._redis = None

    def _redis_available(self) -> bool:
        if self._redis is None:
            return False
        try:
            self._redis.ping()
            return True
        except Exception:
            return False

    @staticmethod
    def _key(text: str) -> str:
        return hashlib.md5(text.encode("utf-8")).hexdigest()

    async def get(self, text: str) -> Optional[np.ndarray]:
        """Get cached embedding for text. Returns None on miss."""
        key = self._key(text)

        # Check memory first (fast path)
        if key in self._memory:
            self._hits += 1
            self._memory_timestamps[key] = time.time()
            return self._memory[key]

        # Check Redis
        if self._redis_available():
            try:
                data = self._redis.get(f"{self.KEY_PREFIX}{key}")
                if data:
                    arr = np.frombuffer(data, dtype=np.float32)
                    # Cache in memory for subsequent fast access
                    self._memory[key] = arr
                    self._memory_timestamps[key] = time.time()
                    self._hits += 1
                    return arr
            except Exception:
                pass

        self._misses += 1
        return None

    async def set(self, text: str, embedding: np.ndarray, ttl: int = 3600):
        """Cache an embedding vector."""
        key = self._key(text)

        # Store in memory
        self._memory[key] = embedding.copy()
        self._memory_timestamps[key] = time.time()

        # Evict if over limit
        if len(self._memory) > self.MAX_MEMORY_ENTRIES:
            sorted_keys = sorted(
                self._memory_timestamps.keys(),
                key=lambda k: self._memory_timestamps[k],
            )
            for old_key in sorted_keys[: len(sorted_keys) // 10]:
                del self._memory[old_key]
                del self._memory_timestamps[old_key]

        # Store in Redis
        if self._redis_available():
            try:
                self._redis.setex(
                    f"{self.KEY_PREFIX}{key}",
                    ttl,
                    embedding.astype(np.float32).tobytes(),
                )
            except Exception:
                pass

    @property
    def hit_rate(self) -> float:
        total = self._hits + self._misses
        return self._hits / total if total > 0 else 0.0

    def get_stats(self) -> dict:
        return {
            "hit_rate": self.hit_rate,
            "hits": self._hits,
            "misses": self._misses,
            "memory_entries": len(self._memory),
            "redis_available": self._redis_available(),
        }
