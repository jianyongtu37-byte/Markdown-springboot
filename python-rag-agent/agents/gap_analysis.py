"""
知识缺口分析 Agent
分析用户知识库中缺失的领域，给出补充建议
"""

from typing import Dict, List, Optional
from core.vectorstore import VectorStore
from core.llm import DeepSeekLLM


class GapAnalysisAgent:
    """知识缺口分析 Agent"""

    def __init__(self, vector_store: VectorStore, llm: DeepSeekLLM):
        self.vector_store = vector_store
        self.llm = llm

    async def analyze(self, user_id: int) -> Dict:
        """
        分析知识库的知识缺口

        Returns:
            {
                "topics": [{"topic": str, "coverage": str, "gaps": [str]}],
                "suggestions": [str],
                "summary": str
            }
        """
        # 1. 获取索引中的所有文章信息
        status = self.vector_store.get_status()
        if status["total_articles"] == 0:
            return {
                "topics": [],
                "suggestions": ["知识库为空，请先添加一些文章。"],
                "summary": "知识库中暂无文章，无法进行分析。",
            }

        # 2. 从元数据中提取文章标题列表
        article_titles = set()
        for meta in self.vector_store.metadata.values():
            article_titles.add(meta["article_title"])

        titles_text = "\n".join(f"- {t}" for t in sorted(article_titles))

        # 3. 调用 LLM 分析知识缺口
        messages = [
            {
                "role": "system",
                "content": (
                    "你是一个知识管理顾问。根据用户知识库中的文章标题列表，"
                    "分析其知识结构，找出可能缺失的领域，并给出补充建议。\n\n"
                    "请用 JSON 格式输出：\n"
                    '{"topics": [{"topic": "领域名称", "coverage": "已有/薄弱/缺失", "gaps": ["具体缺口1", "具体缺口2"]}], '
                    '"suggestions": ["建议1", "建议2"], '
                    '"summary": "总体分析摘要"}'
                ),
            },
            {
                "role": "user",
                "content": (
                    f"我的知识库共有 {status['total_articles']} 篇文章，"
                    f"{status['total_chunks']} 个知识片段。\n\n"
                    f"文章标题列表：\n{titles_text}\n\n"
                    f"请分析我的知识结构和可能的知识缺口。"
                ),
            },
        ]

        try:
            import json
            result_text = await self.llm.chat(messages, temperature=0.5)
            # 尝试解析 JSON
            result_text = result_text.strip()
            if result_text.startswith("```"):
                result_text = result_text.split("\n", 1)[1].rsplit("```", 1)[0]
            return json.loads(result_text)
        except Exception:
            return {
                "topics": [],
                "suggestions": ["分析过程中出现错误，请稍后重试。"],
                "summary": "无法完成分析。",
            }
