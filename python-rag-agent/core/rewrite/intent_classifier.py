"""
Intent Classifier — two-stage query intent classification.

Stage 1: Fast keyword/pattern matching (<1ms), confidence >= 0.85 → return
Stage 2: LLM classification (200-500ms) for ambiguous queries

Intent types: factual, procedural, comparative, corrective, metadata, chitchat
"""

from enum import Enum
from typing import Dict, List, Optional
from dataclasses import dataclass

from core.rewrite.chinese_patterns import ChinesePatterns


class QueryIntent(Enum):
    FACTUAL = "factual"           # "什么是X" "X的工作原理"
    PROCEDURAL = "procedural"     # "如何做X" "怎样配置Y"
    COMPARATIVE = "comparative"   # "X和Y的区别" "X比Y更好吗"
    CORRECTIVE = "corrective"     # "不是，我的意思是..." "应该是..."
    METADATA = "metadata"         # "这篇文章是什么时候写的" "作者是谁"
    CHITCHAT = "chitchat"         # "你好" "谢谢" "今天天气怎么样"


@dataclass
class IntentResult:
    intent: QueryIntent
    confidence: float  # 0-1
    reasoning: str = ""
    source: str = ""   # "rule" or "llm"


class IntentClassifier:
    """
    Two-stage intent classifier.
    Stage 1 (rule-based): covers ~80% of queries in <1ms.
    Stage 2 (LLM): handles ambiguous queries.
    """

    # Fine-grained classification patterns beyond the basic ones in ChinesePatterns
    RULE_PATTERNS = {
        QueryIntent.FACTUAL: [
            # "X是什么" / "什么是X"
            (r"是什么|什么是|啥是|啥叫|什么叫.{0,10}$", 0.92),
            # "X的原理" / "X的定义"
            (r"(原理|定义|概念|含义|意思|作用)$", 0.90),
            # "为什么X" / "X的原因"
            (r"^(为什么|为啥)|(原因|背景|来历)$", 0.88),
            # "X是指" / "X指的是"
            (r"是指|指的是|表示", 0.85),
        ],
        QueryIntent.PROCEDURAL: [
            (r"^(如何|怎么|怎样|是不是要)", 0.95),
            (r"(步骤|流程|方法|教程|指南|配置|部署|安装)", 0.90),
            (r"(怎么办|如何处理|如何解决|怎样做|如何做|咋办)", 0.93),
            (r"(有啥办法|有什么办法|该怎么办|应该如何)", 0.91),
        ],
        QueryIntent.COMPARATIVE: [
            (r"(区别|差异|比较|对比|哪个好|哪个更|优缺点)", 0.94),
            (r"(有什么不同|哪一个更好|有什么不一样|差别)", 0.92),
            (r"\bvs\b|\bVS\b", 0.88),
            (r"(相比之下|相较|比起|相对于)", 0.85),
        ],
        QueryIntent.CORRECTIVE: [
            (r"^(不是|不对|错了|搞错|你说错)", 0.95),
            (r"(我指的是|我的意思是|应该说|更正|纠正)", 0.93),
            (r"^(不是这样|不对不对|以上都不是)", 0.91),
        ],
        QueryIntent.METADATA: [
            (r"(作者|谁写|谁创建)", 0.90),
            (r"(日期|时间|什么时候|何时|多久)", 0.88),
            (r"(分类|标签|tag|来源|版本|字数)", 0.87),
            (r"(发布|上传|更新|修改)", 0.80),
        ],
        QueryIntent.CHITCHAT: [
            (r"^(你好|您好|谢谢|多谢|再见|拜拜|嗨|hello|hi)$", 0.99),
            (r"^(早|晚上好|早上好|下午好|晚安)", 0.98),
            (r"^(在吗|在不在|你有空吗)", 0.90),
            (r"^(今天天气|天气怎么样|你能做什么|你是谁)", 0.85),
        ],
    }

    def __init__(self, llm: "DeepSeekLLM" = None):
        self.llm = llm  # for Stage 2 fallback

    async def classify(
        self, query: str, history: List[Dict]
    ) -> IntentResult:
        """
        Classify query intent.

        Returns IntentResult with intent and confidence.
        """
        # Stage 1: Rule-based classification
        rule_result = self._rule_classify(query)
        if rule_result.confidence >= 0.85:
            return rule_result

        # Stage 2: LLM fallback for ambiguous queries
        if self.llm:
            return await self._llm_classify(query, history)

        # No LLM available, return best rule guess
        return rule_result

    def _rule_classify(self, query: str) -> IntentResult:
        """
        Fast keyword/pattern matching.
        Checks all intent patterns and returns the best match.
        """
        best_intent = None
        best_confidence = 0.0

        for intent, patterns in self.RULE_PATTERNS.items():
            for pattern, confidence in patterns:
                import re
                if re.search(pattern, query):
                    if confidence > best_confidence:
                        best_confidence = confidence
                        best_intent = intent

        if best_intent is None:
            # Default to factual for queries with content, chitchat for very short
            if len(query) < 4:
                best_intent = QueryIntent.CHITCHAT
                best_confidence = 0.4
            else:
                best_intent = QueryIntent.FACTUAL
                best_confidence = 0.5

        return IntentResult(
            intent=best_intent,
            confidence=best_confidence,
            reasoning=f"rule match: confidence={best_confidence:.2f}",
            source="rule",
        )

    async def _llm_classify(
        self, query: str, history: List[Dict]
    ) -> IntentResult:
        """
        LLM-based classification for ambiguous queries.
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
                    "将用户查询分类到以下意图之一：\n"
                    "- factual: 询问事实、定义、原理、原因\n"
                    "- procedural: 询问操作步骤、方法、配置、部署\n"
                    "- comparative: 比较、对比、辨析优劣\n"
                    "- corrective: 纠正、澄清之前的表达\n"
                    "- metadata: 询问文章的元信息（作者、日期、标签等）\n"
                    "- chitchat: 闲聊、问候、与知识库无关的对话\n\n"
                    "只输出意图类型单词，不要任何解释。"
                ),
            },
            {
                "role": "user",
                "content": (
                    f"对话历史：{history_summary[:500]}\n"
                    f"用户查询：{query}" if history_summary else
                    f"用户查询：{query}"
                ),
            },
        ]

        try:
            result = await self.llm.chat(
                messages, temperature=0.1, max_tokens=20
            )
            result = result.strip().lower()
            # Map LLM output to intent
            intent_map = {
                "factual": QueryIntent.FACTUAL,
                "procedural": QueryIntent.PROCEDURAL,
                "comparative": QueryIntent.COMPARATIVE,
                "corrective": QueryIntent.CORRECTIVE,
                "metadata": QueryIntent.METADATA,
                "chitchat": QueryIntent.CHITCHAT,
            }
            intent = intent_map.get(result, QueryIntent.FACTUAL)
            return IntentResult(
                intent=intent,
                confidence=0.75,
                reasoning=f"LLM classified as: {result}",
                source="llm",
            )
        except Exception:
            # Fallback to rule-based on LLM failure
            return self._rule_classify(query)
