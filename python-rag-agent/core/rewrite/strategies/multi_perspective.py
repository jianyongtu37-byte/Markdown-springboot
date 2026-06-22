"""
Multi-Perspective Rewrite Strategy.

Generates 2-3 query variants from different perspectives for ensemble retrieval.
Each variant retrieves independently, and results are merged for better coverage.

Perspectives:
1. Technical terminology (专业术语角度)
2. Problem-solving approach (问题解决角度)
3. Conceptual definition angle (概念定义角度)

Use case: comparison questions, ambiguous queries, or when recall needs boosting.

Cost: 1 LLM call (~200 token generation)
"""

from typing import Dict, List
from dataclasses import dataclass, field

from core.rewrite.strategies.coreference import StrategyOutput
from core.rewrite.strategy_router import StrategyType


class MultiPerspectiveStrategy:
    """Generate multiple query variants for ensemble retrieval."""

    def __init__(self, llm: "DeepSeekLLM", config=None):
        self.llm = llm
        self.max_variants = getattr(config, "REWRITE_MAX_VARIANTS", 3) if config else 3

    async def execute(self, query: str, history: List[Dict]) -> "StrategyOutput":
        """
        Generate query variants from different perspectives.

        Returns StrategyOutput with variants populated.
        rewritten_query is the original query (unchanged for LLM generation).
        """
        # Include history context if relevant
        context = ""
        if history:
            recent = history[-4:]
            context = "对话上下文：\n" + "\n".join(
                [f"{m['role']}: {m['content'][:100]}" for m in recent]
            ) + "\n\n"

        messages = [
            {
                "role": "system",
                "content": (
                    "你是一个查询改写助手。将用户的查询从不同角度改写为2-3个变体：\n"
                    "1. 专业术语角度（使用领域专业术语表达）\n"
                    "2. 问题解决角度（以'如何解决'或'如何处理'开头）\n"
                    "3. 概念定义角度（以'什么是'或'的定义'结尾）\n\n"
                    "每行一个变体，不要编号，不要任何解释。"
                    "如果某个角度不适用（如查询已经是问题解决型），可以只输出2个变体。"
                ),
            },
            {
                "role": "user",
                "content": f"{context}用户查询：{query}",
            },
        ]

        try:
            result = await self.llm.chat(
                messages, temperature=0.5, max_tokens=200
            )
            variants = [
                v.strip() for v in result.strip().split("\n")
                if v.strip() and len(v.strip()) > 3
            ]
            variants = variants[:self.max_variants]

            if not variants:
                variants = [query]
        except Exception:
            variants = [query]

        return StrategyOutput(
            strategy=StrategyType.MULTI_PERSPECTIVE,
            rewritten_query=query,           # original preserved for LLM
            search_query=query,              # fallback
            variants=variants,
            metadata={
                "variant_count": len(variants),
                "variants": variants,
            },
        )
