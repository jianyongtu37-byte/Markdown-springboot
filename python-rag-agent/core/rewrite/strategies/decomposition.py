"""
Sub-Query Decomposition Strategy.

Breaks compound/complex questions into 2-4 simpler, independent sub-queries.
Each sub-query is used for separate retrieval, then results are merged.

Examples:
- "Spring Boot 2.x 升级到 3.x 需要改哪些配置，有什么风险？"
  → ["Spring Boot 2.x 升级到 3.x 配置迁移指南",
     "Spring Boot 3.x 升级兼容性风险"]
- "Redis 和 Memcached 的区别是什么，各自适用什么场景？"
  → ["Redis 和 Memcached 的区别",
     "Redis 适用场景",
     "Memcached 适用场景"]

Cost: 1 LLM call (~200 token generation)
"""

import re
from typing import Dict, List
from dataclasses import dataclass, field

from core.rewrite.strategies.coreference import StrategyOutput
from core.rewrite.strategy_router import StrategyType


class SubQueryDecompositionStrategy:
    """Decompose complex questions into simpler sub-queries."""

    def __init__(self, llm: "DeepSeekLLM", config=None):
        self.llm = llm
        self.max_sub_queries = getattr(config, "REWRITE_MAX_SUB_QUERIES", 4) if config else 4

    async def execute(self, query: str, history: List[Dict]) -> "StrategyOutput":
        """
        Decompose a compound query into sub-queries.

        Returns StrategyOutput with sub_queries populated.
        rewritten_query is the original query (unchanged, for LLM generation).
        """
        # Quick check: is this likely compound?
        if not self._is_likely_compound(query):
            return StrategyOutput(
                strategy=StrategyType.DECOMPOSITION,
                rewritten_query=query,
                search_query=query,
                sub_queries=[query],
                metadata={"decomposed": False, "reason": "simple_query"},
            )

        history_context = ""
        if history:
            recent = history[-4:]
            history_context = "\n".join(
                [f"{m['role']}: {m['content'][:150]}" for m in recent]
            )

        messages = [
            {
                "role": "system",
                "content": (
                    "你是一个查询分解助手。将用户的复杂问题拆解为2-4个简单的子问题，"
                    "每个子问题都是一个独立的、完整的搜索查询。\n\n"
                    "规则：\n"
                    "1. 如果问题已经很简单，只输出原始问题\n"
                    "2. 对比类问题拆成针对每个对象的独立查询\n"
                    "3. 操作类问题拆成步骤性查询\n"
                    "4. 每个子查询独占一行\n"
                    "5. 输出格式：每行一个子查询，不要编号，不要任何解释。"
                ),
            },
            {
                "role": "user",
                "content": (
                    f"对话历史：{history_context}\n问题：{query}"
                    if history_context else
                    f"问题：{query}"
                ),
            },
        ]

        try:
            result = await self.llm.chat(
                messages, temperature=0.3, max_tokens=200
            )
            sub_queries = [
                q.strip() for q in result.strip().split("\n")
                if q.strip() and len(q.strip()) > 2
            ]
            sub_queries = sub_queries[:self.max_sub_queries]

            if not sub_queries:
                sub_queries = [query]
        except Exception:
            sub_queries = [query]

        return StrategyOutput(
            strategy=StrategyType.DECOMPOSITION,
            rewritten_query=query,           # original for LLM
            search_query=query,              # fallback search
            sub_queries=sub_queries,
            metadata={
                "decomposed": len(sub_queries) > 1,
                "sub_query_count": len(sub_queries),
                "sub_queries": sub_queries,
            },
        )

    def _is_likely_compound(self, query: str) -> bool:
        """Quick check if query likely contains multiple questions."""
        # Multiple question marks
        if query.count("?") + query.count("？") >= 2:
            return True
        # Comparison markers
        if re.search(r"(区别|差异|比较|对比|哪个好|优缺点|vs)", query):
            return True
        # Conjunction markers
        if re.search(r"(并且|以及|同时|另外|还|也|还有|加上|以及)", query):
            return True
        # Alternative markers
        if re.search(r"(还是|或者|要么)", query):
            return True
        # Semicolon-separated clauses (suggesting multiple points)
        if re.search(r"[；;]", query):
            return True
        return False
