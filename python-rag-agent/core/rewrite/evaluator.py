"""
Quality Evaluator — async rewrite quality assessment.

Runs as a fire-and-forget coroutine after each L3 rewrite.
Scores fidelity (how well the rewrite preserves meaning) via LLM-as-Judge.

Sampling: only evaluates 10% of queries by default to control costs.
"""

import asyncio
import random
import time
from typing import Dict, List, Optional
from dataclasses import dataclass, field


@dataclass
class EvalResult:
    """Single evaluation result."""
    original: str
    rewritten: str
    fidelity_score: float  # 1-5
    strategy: str
    latency_ms: float
    timestamp: float = field(default_factory=time.time)


class QualityEvaluator:
    """
    Async rewrite quality evaluator.

    Fidelity scoring via LLM-as-Judge:
    - 5: 改写完全保留原意，补充了必要上下文，更适合搜索
    - 4: 改写保留了原意，有少量不重要的遗漏
    - 3: 改写基本保留原意，但遗漏了部分重要信息
    - 2: 改写改变了原意或引入了无关内容
    - 1: 改写完全偏离原意
    """

    def __init__(self, llm: "DeepSeekLLM" = None, config=None):
        self.llm = llm
        self.sample_rate = getattr(config, "REWRITE_EVAL_SAMPLE_RATE", 0.1) if config else 0.1
        self.enabled = getattr(config, "REWRITE_EVAL_ENABLED", True) if config else True

        # Store recent evaluations for inspection
        self._recent: List[EvalResult] = []
        self._max_recent = getattr(config, "REWRITE_EVAL_MAX_SAMPLES", 10000) if config else 10000
        self._count = 0
        self._evaluated_count = 0

    async def evaluate(
        self,
        original: str,
        rewritten: str,
        history: List[Dict],
        strategy: str = "",
        latency_ms: float = 0.0,
    ) -> Optional[EvalResult]:
        """
        Evaluate a rewrite. Only samples based on sample_rate.

        Runs as fire-and-forget (non-blocking on critical path).
        Returns EvalResult or None if skipped.
        """
        self._count += 1

        # Skip if evaluation disabled
        if not self.enabled:
            return None

        # Skip if no LLM available
        if not self.llm:
            return None

        # Sampling: only evaluate sample_rate fraction
        if random.random() > self.sample_rate:
            return None

        # Skip if rewrite didn't change anything (trivial)
        if rewritten == original or not rewritten:
            return None

        self._evaluated_count += 1
        fidelity = await self._score_fidelity(original, rewritten, history)

        result = EvalResult(
            original=original,
            rewritten=rewritten,
            fidelity_score=fidelity,
            strategy=strategy,
            latency_ms=latency_ms,
        )

        # Store in ring buffer
        self._recent.append(result)
        if len(self._recent) > self._max_recent:
            self._recent = self._recent[-self._max_recent:]

        return result

    async def _score_fidelity(
        self, original: str, rewritten: str, history: List[Dict]
    ) -> float:
        """
        LLM-as-Judge fidelity scoring.

        1-5 scale: how well the rewrite preserves the original meaning
        while making it better for search.
        """
        history_summary = ""
        if history:
            recent = history[-4:]
            history_summary = "\n".join(
                [f"{m['role']}: {m['content'][:100]}" for m in recent]
            )

        messages = [
            {
                "role": "system",
                "content": (
                    "评分任务：评估查询改写质量。\n\n"
                    "评分标准（1-5分）：\n"
                    "- 5: 改写完全保留原意，补充了必要上下文，更适合搜索\n"
                    "- 4: 改写保留了原意，有少量不重要的遗漏\n"
                    "- 3: 改写基本保留原意，但遗漏了部分重要信息\n"
                    "- 2: 改写改变了原意或引入了无关内容\n"
                    "- 1: 改写完全偏离原意\n\n"
                    "只输出数字分数（1-5），不要任何解释。"
                ),
            },
            {
                "role": "user",
                "content": (
                    f"原始查询：{original}\n"
                    f"改写查询：{rewritten}\n"
                    f"对话历史：{history_summary[:500]}"
                ),
            },
        ]

        try:
            result = await self.llm.chat(messages, temperature=0.1, max_tokens=5)
            score = float(result.strip())
            return max(1.0, min(5.0, score))
        except Exception:
            # Default: assume moderate quality
            return 3.0

    def get_recent_samples(self, limit: int = 20) -> List[dict]:
        """Get recent evaluation samples for manual review."""
        recent = self._recent[-limit:]
        return [
            {
                "original": r.original,
                "rewritten": r.rewritten,
                "fidelity_score": r.fidelity_score,
                "strategy": r.strategy,
                "latency_ms": r.latency_ms,
                "timestamp": r.timestamp,
            }
            for r in reversed(recent)
        ]

    @property
    def sample_count(self) -> int:
        return self._evaluated_count

    @property
    def total_count(self) -> int:
        return self._count

    @property
    def avg_fidelity(self) -> float:
        if not self._recent:
            return 0.0
        return sum(r.fidelity_score for r in self._recent) / len(self._recent)
