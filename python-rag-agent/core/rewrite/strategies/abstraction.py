"""
Step-Back Abstraction Strategy.

Generates a higher-level, more general question when specific queries
fail to retrieve relevant documents. Used as a retrieval fallback.

Example:
- Specific: "DeepSeek-R1 的 MLA 注意力头数是多少"
- Abstract: "DeepSeek-R1 模型架构参数"
- More abstract: "DeepSeek 大语言模型技术报告"

The abstraction process is iterative: if the first abstraction still fails,
it can generate an even more general version.

Cost: 1 LLM call (~100 token generation), only on retrieval failure
"""

from typing import Dict, List
from dataclasses import dataclass, field

from core.rewrite.strategies.coreference import StrategyOutput
from core.rewrite.strategy_router import StrategyType


class StepBackAbstractionStrategy:
    """
    Generate an abstracted version of the query to improve recall.

    Used when the original (or rewritten) query retrieves no relevant results.
    The idea is to ask a broader question that is more likely to be covered
    in the knowledge base, then answer the specific question from that context.
    """

    def __init__(self, llm: "DeepSeekLLM", config=None):
        self.llm = llm
        self.enabled = getattr(
            config, "REWRITE_ABSTRACTION_RETRY", True
        ) if config else True

    async def execute(self, query: str, history: List[Dict]) -> "StrategyOutput":
        """
        Generate a higher-level abstracted query.

        Returns StrategyOutput with rewritten_query set to the abstracted version.
        """
        messages = [
            {
                "role": "system",
                "content": (
                    "你是一个查询抽象助手。将用户的具体问题抽象为更通用、更高层级的问题。"
                    "抽象后的问题应该更容易在通用知识库中找到相关文档。\n\n"
                    "抽象层级：\n"
                    "Level 1 — 概念抽象：去掉具体参数/数字，提取核心概念\n"
                    "  例：'Spring Boot 3.2的虚拟线程支持如何配置'\n"
                    "     → 'Spring Boot的并发处理机制'\n"
                    "Level 2 — 领域抽象：从具体技术上升到技术领域\n"
                    "  例：'Nacos 2.3的GRPC端口冲突怎么解决'\n"
                    "     → 'Nacos端口配置与网络问题'\n"
                    "Level 3 — 主题抽象：从领域问题上升到通用主题\n"
                    "  例：'Redis集群的槽位迁移失败'\n"
                    "     → 'Redis集群运维与管理'\n\n"
                    "请对以下问题进行Level 1或Level 2抽象。"
                    "只输出抽象后的问题，不要任何解释。"
                ),
            },
            {"role": "user", "content": query},
        ]

        try:
            abstracted = await self.llm.chat(
                messages, temperature=0.3, max_tokens=100
            )
            abstracted = abstracted.strip()
            if not abstracted or abstracted == query:
                abstracted = query
        except Exception:
            abstracted = query

        return StrategyOutput(
            strategy=StrategyType.ABSTRACTION,
            rewritten_query=abstracted,
            search_query=abstracted,
            metadata={
                "original_query": query,
                "abstracted": abstracted != query,
            },
        )

    def should_retry(self, sources: List[Dict]) -> bool:
        """
        Determine if step-back abstraction should be triggered.

        Returns True if:
        - No sources were found
        - OR all sources have very low relevance scores
        """
        if not self.enabled:
            return False
        if not sources:
            return True
        max_score = max(s.get("relevance_score", 0) for s in sources)
        return max_score < 0.45  # below relevance threshold, try abstraction
