"""
查询重写：将模糊的追问改写成独立的完整查询

This is a backward-compatible wrapper around the RewritePipeline.
All existing code that uses QueryRewriter continues to work unchanged.
For new code, use RewritePipeline directly for full metadata access.
"""

from typing import List, Dict

from core.llm import DeepSeekLLM
from core.rewrite.pipeline import RewritePipeline


class QueryRewriter:
    """查询重写器 — thin wrapper around RewritePipeline."""

    def __init__(self, llm: DeepSeekLLM):
        self.llm = llm
        self._pipeline = None  # lazy init

    def _get_pipeline(self) -> RewritePipeline:
        if self._pipeline is None:
            self._pipeline = RewritePipeline(llm=self.llm)
        return self._pipeline

    async def rewrite(self, current_message: str, history: List[Dict]) -> str:
        """
        如果有对话历史，将当前问题改写成独立查询

        示例：
        历史：用户问了"微服务架构"
        当前："那优缺点呢？"
        重写："微服务架构的优缺点"
        """
        if not history:
            return current_message

        try:
            result = await self._get_pipeline().rewrite(
                current_message, history
            )
            rewritten = result.rewritten_query
            return rewritten.strip() if rewritten and rewritten.strip() else current_message
        except Exception:
            return current_message
