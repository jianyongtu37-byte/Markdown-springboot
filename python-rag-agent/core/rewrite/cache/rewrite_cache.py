"""
Redis-backed rewrite result cache with tiered TTL.

Cache key: MD5(user_id:session_id:query:history_fingerprint)
TTL tiers: L1=30min, L2=10min, L3=5min, Hot=60min

Graceful fallback: if Redis is unavailable, cache silently skips (no crash).
"""

import hashlib
import json
import time
from typing import Dict, List, Optional
from dataclasses import dataclass, field


@dataclass
class RewriteCacheEntry:
    """Serializable cache entry for a rewrite result."""
    rewritten_query: str
    search_query: str
    layer_resolved: str
    confidence: float
    strategy_used: List[str] = field(default_factory=list)
    created_at: float = field(default_factory=time.time)
    ttl: int = 300  # seconds


class RewriteCache:
    """Redis-backed rewrite cache. Falls back to in-memory LRU if Redis unavailable."""

    KEY_PREFIX = "rag:rewrite:"
    MAX_MEMORY_ENTRIES = 1000  # fallback LRU limit

    def __init__(self, redis_client=None):
        """
        Args:
            redis_client: redis.Redis instance or None (will try to connect)
        """
        self._redis = redis_client
        self._memory_fallback: Dict[str, RewriteCacheEntry] = {}
        self._memory_hits = 0
        self._total_requests = 0
        if self._redis is None:
            self._try_connect()

    def _try_connect(self):
        """Try to connect to Redis. Silently fall back to memory if unavailable."""
        try:
            import redis
            from config import Config
            self._redis = redis.Redis(
                host=Config.MYSQL_HOST,  # Redis likely on same host
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

    def _cache_key(
        self, query: str, history: List[Dict],
        user_id: Optional[int] = None, session_id: Optional[str] = None
    ) -> str:
        """Compute deterministic cache key."""
        # Fingerprint: last N assistant messages, content only
        assistant_msgs = [
            m.get("content", "") for m in history
            if m.get("role") == "assistant"
        ]
        fingerprint = "|".join(assistant_msgs[-5:])  # last 5 turns
        raw = f"{user_id or ''}:{session_id or ''}:{query}:{fingerprint}"
        return hashlib.md5(raw.encode("utf-8")).hexdigest()

    async def get(self, query: str, history: List[Dict],
                  user_id: Optional[int] = None,
                  session_id: Optional[str] = None) -> Optional[RewriteCacheEntry]:
        """Get cached rewrite result. Returns None on cache miss."""
        self._total_requests += 1
        key = self._cache_key(query, history, user_id, session_id)

        if self._redis_available():
            try:
                data = self._redis.get(f"{self.KEY_PREFIX}{key}")
                if data:
                    entry_dict = json.loads(data)
                    self._memory_hits += 1
                    return RewriteCacheEntry(**entry_dict)
            except Exception:
                pass

        # Fallback to in-memory
        entry = self._memory_fallback.get(key)
        if entry and time.time() - entry.created_at < entry.ttl:
            self._memory_hits += 1
            return entry
        elif entry:
            del self._memory_fallback[key]  # expired
        return None

    async def set(self, query: str, history: List[Dict],
                  entry: RewriteCacheEntry,
                  user_id: Optional[int] = None,
                  session_id: Optional[str] = None):
        """Store a rewrite result in cache."""
        key = self._cache_key(query, history, user_id, session_id)
        entry.created_at = time.time()

        if self._redis_available():
            try:
                self._redis.setex(
                    f"{self.KEY_PREFIX}{key}",
                    entry.ttl,
                    json.dumps({
                        "rewritten_query": entry.rewritten_query,
                        "search_query": entry.search_query,
                        "layer_resolved": entry.layer_resolved,
                        "confidence": entry.confidence,
                        "strategy_used": entry.strategy_used,
                        "created_at": entry.created_at,
                        "ttl": entry.ttl,
                    }, ensure_ascii=False),
                )
                return
            except Exception:
                pass

        # Fallback to in-memory
        if len(self._memory_fallback) >= self.MAX_MEMORY_ENTRIES:
            # Evict oldest 10%
            sorted_keys = sorted(
                self._memory_fallback.keys(),
                key=lambda k: self._memory_fallback[k].created_at,
            )
            for old_key in sorted_keys[: len(sorted_keys) // 10]:
                del self._memory_fallback[old_key]
        self._memory_fallback[key] = entry

    async def invalidate_session(self, user_id: int, session_id: str):
        """Clear all cached rewrites for a session. Best-effort."""
        pattern = f"{self.KEY_PREFIX}{hashlib.md5(f'{user_id}:{session_id}:'.encode()).hexdigest()}*"
        if self._redis_available():
            try:
                keys = self._redis.keys(pattern)
                if keys:
                    self._redis.delete(*keys)
            except Exception:
                pass
        # Also clear from memory fallback
        to_remove = [
            k for k in self._memory_fallback
            if k.startswith(hashlib.md5(f"{user_id}:{session_id}:".encode()).hexdigest())
        ]
        for k in to_remove:
            del self._memory_fallback[k]

    @property
    def hit_rate(self) -> float:
        """Cache hit rate (0-1)."""
        if self._total_requests == 0:
            return 0.0
        return self._memory_hits / self._total_requests

    def get_stats(self) -> dict:
        """Get cache statistics."""
        return {
            "hit_rate": self.hit_rate,
            "hits": self._memory_hits,
            "total_requests": self._total_requests,
            "memory_entries": len(self._memory_fallback),
            "redis_available": self._redis_available(),
        }
