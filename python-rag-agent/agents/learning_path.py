"""
学习路径推荐 Agent
基于已有文章，推荐学习阅读顺序
"""

from typing import Dict, List
from core.vectorstore import VectorStore
from core.llm import DeepSeekLLM


class LearningPathAgent:
    """学习路径推荐 Agent"""

    def __init__(self, vector_store: VectorStore, llm: DeepSeekLLM):
        self.vector_store = vector_store
        self.llm = llm

    async def recommend(self, user_id: int, topic: str = "") -> Dict:
        """
        推荐学习路径

        Args:
            user_id: 用户 ID
            topic: 可选，指定学习主题

        Returns:
            {
                "path": [{"step": int, "article_title": str, "reason": str}],
                "summary": str
            }
        """
        # 1. 获取索引中的所有文章信息
        status = self.vector_store.get_status()
        if status["total_articles"] == 0:
            return {
                "path": [],
                "summary": "知识库为空，请先添加一些文章。",
            }

        # 2. 从元数据中提取文章信息
        articles = {}
        for meta in self.vector_store.metadata.values():
            aid = meta["article_id"]
            if aid not in articles:
                articles[aid] = {
                    "id": aid,
                    "title": meta["article_title"],
                    "chunks": 0,
                }
            articles[aid]["chunks"] += 1

        articles_text = "\n".join(
            f"- [{a['id']}] {a['title']} ({a['chunks']} 个知识片段)"
            for a in sorted(articles.values(), key=lambda x: x["title"])
        )

        # 3. 构建 prompt
        topic_hint = f"，学习主题：{topic}" if topic else ""
        messages = [
            {
                "role": "system",
                "content": (
                    "你是一个学习规划顾问。根据用户知识库中的文章列表，"
                    "推荐一个合理的学习阅读路径。\n\n"
                    "请用 JSON 格式输出：\n"
                    '{"path": [{"step": 1, "article_id": 123, "article_title": "标题", "reason": "推荐理由"}], '
                    '"summary": "学习路径总结"}\n\n'
                    "注意：article_id 必须使用列表中提供的实际 ID。"
                ),
            },
            {
                "role": "user",
                "content": (
                    f"我的知识库共有 {status['total_articles']} 篇文章{topic_hint}。\n\n"
                    f"文章列表：\n{articles_text}\n\n"
                    f"请推荐一个合理的学习阅读顺序。"
                ),
            },
        ]

        try:
            import json
            result_text = await self.llm.chat(messages, temperature=0.5)
            result_text = result_text.strip()
            if result_text.startswith("```"):
                result_text = result_text.split("\n", 1)[1].rsplit("```", 1)[0]
            return json.loads(result_text)
        except Exception:
            return {
                "path": [],
                "summary": "分析过程中出现错误，请稍后重试。",
            }
