"""
Rewrite Metrics Collection.

Tracks latency histograms, cache hit rates, strategy distribution,
layer resolution rates, and rewrite gain.

All methods are thread-safe (no locks needed as asyncio is single-threaded).
"""

import time
from collections import defaultdict
from typing import Dict, List, Optional, Tuple
from dataclasses import dataclass, field


@dataclass
class RewriteMetric:
    """Single rewrite event for metrics tracking."""
    original: str
    rewritten: str
    strategy: str
    fidelity: float  # 1-5
    latency_ms: float
    layer_resolved: str = ""
    confidence: float = 0.0
    timestamp: float = field(default_factory=time.time)


class RewriteMetrics:
    """
    Thread-safe metrics collector for the rewrite pipeline.

    Tracks:
    - Latency distributions (P50, P95, P99)
    - Layer hit rates (L1/L2/L3)
    - Strategy usage distribution
    - Average fidelity scores
    - Cache efficiency (from RewriteCache stats)
    """

    MAX_SAMPLES = 10000  # ring buffer size for latencies and fidelities

    def __init__(self):
        self._latencies: List[float] = []
        self._fidelities: List[float] = []
        self._strategy_counts: Dict[str, int] = defaultdict(int)
        self._layer_counts: Dict[str, int] = defaultdict(int)
        self._total_requests: int = 0
        self._start_time: float = time.time()

    def record(self, metric: RewriteMetric):
        """Record a rewrite event."""
        self._total_requests += 1

        # Ring buffer for latencies
        self._latencies.append(metric.latency_ms)
        if len(self._latencies) > self.MAX_SAMPLES:
            self._latencies = self._latencies[-self.MAX_SAMPLES:]

        # Ring buffer for fidelities
        if metric.fidelity > 0:
            self._fidelities.append(metric.fidelity)
            if len(self._fidelities) > self.MAX_SAMPLES:
                self._fidelities = self._fidelities[-self.MAX_SAMPLES:]

        # Counts
        if metric.strategy:
            for s in metric.strategy.split(","):
                s = s.strip()
                if s:
                    self._strategy_counts[s] += 1
        if metric.layer_resolved:
            self._layer_counts[metric.layer_resolved] += 1

    def record_latency(self, layer: str, latency_ms: float):
        """Quick latency-only recording."""
        self._latencies.append(latency_ms)
        if len(self._latencies) > self.MAX_SAMPLES:
            self._latencies = self._latencies[-self.MAX_SAMPLES:]
        self._layer_counts[layer] += 1
        self._total_requests += 1

    def increment(self, counter: str, amount: int = 1):
        """Increment a named counter (for cache hits, etc.)."""
        if counter == "total":
            self._total_requests += amount
        else:
            self._layer_counts[counter] += amount

    def get_snapshot(self) -> dict:
        """Get current metrics snapshot without resetting."""
        return {
            "total_requests": self._total_requests,
            "uptime_seconds": time.time() - self._start_time,
            "requests_per_second": (
                self._total_requests / max(1, time.time() - self._start_time)
            ),
            "latency": {
                "p50_ms": self._percentile(self._latencies, 50),
                "p95_ms": self._percentile(self._latencies, 95),
                "p99_ms": self._percentile(self._latencies, 99),
                "avg_ms": sum(self._latencies) / max(1, len(self._latencies)),
                "min_ms": min(self._latencies) if self._latencies else 0,
                "max_ms": max(self._latencies) if self._latencies else 0,
                "samples": len(self._latencies),
            },
            "fidelity": {
                "avg": sum(self._fidelities) / max(1, len(self._fidelities)),
                "min": min(self._fidelities) if self._fidelities else 0,
                "max": max(self._fidelities) if self._fidelities else 0,
                "samples": len(self._fidelities),
            },
            "layer_distribution": dict(self._layer_counts),
            "strategy_distribution": dict(self._strategy_counts),
        }

    def reset(self):
        """Reset all metrics (for testing)."""
        self._latencies.clear()
        self._fidelities.clear()
        self._strategy_counts.clear()
        self._layer_counts.clear()
        self._total_requests = 0
        self._start_time = time.time()

    @staticmethod
    def _percentile(data: List[float], p: float) -> float:
        """Calculate percentile from sorted data."""
        if not data:
            return 0.0
        sorted_data = sorted(data)
        k = (len(sorted_data) - 1) * p / 100.0
        f = int(k)
        c = f + 1 if f + 1 < len(sorted_data) else f
        return sorted_data[f] + (k - f) * (sorted_data[c] - sorted_data[f])
