"""
Coreference Resolution Strategy — improved multi-turn rewrite.

Key improvements over the original QueryRewriter:
- Full 5-turn history window (was 2 turns)
- Entity chain tracking across conversation turns
- Topic-aware sliding window when history exceeds max turns
- Enhanced system prompt with entity chain + topic summary
"""

import re
from typing import Dict, List
from dataclasses import dataclass, field

from core.rewrite.strategy_router import StrategyType


@dataclass
class StrategyOutput:
    strategy: StrategyType
    rewritten_query: str
    search_query: str = ""
    search_embedding: "np.ndarray | None" = None
    sub_queries: List[str] = field(default_factory=list)
    variants: List[str] = field(default_factory=list)
    metadata: Dict = field(default_factory=dict)


class CoreferenceStrategy:
    """
    Enhanced coreference resolution for multi-turn conversations.

    Uses up to 5 rounds of history with entity chain tracking
    to resolve pronouns, demonstratives, and elliptical questions.
    """

    def __init__(self, llm: "DeepSeekLLM", config=None):
        self.llm = llm
        self.max_rounds = getattr(config, "REWRITE_MAX_HISTORY_ROUNDS", 5) if config else 5

    async def execute(self, query: str, history: List[Dict]) -> StrategyOutput:
        """
        Resolve coreferences in the query based on conversation history.

        Returns StrategyOutput with rewritten_query set.
        """
        if not history:
            return StrategyOutput(
                strategy=StrategyType.COREFERENCE,
                rewritten_query=query,
                search_query=query,
            )

        # Extract entity chain and topic summary
        entity_chain = self._extract_entity_chain(history)
        topic_summary = self._extract_topic_summary(history)

        # Get relevant history window
        history_window = self._get_relevant_history(query, history)

        # Build history text
        history_text = "\n".join(
            [f"{m['role']}: {m['content']}" for m in history_window]
        )

        messages = [
            {
                "role": "system",
                "content": (
                    "你是一个查询改写助手。根据对话历史，将用户的最新问题改写成独立的、完整的搜索查询。\n\n"
                    "改写规则：\n"
                    "1. 将代词（它、这、那、其）替换为历史中提到的具体实体\n"
                    "2. 将省略的主语/宾语补全\n"
                    "3. 保持原问题的意图不变\n"
                    "4. 如果是追问，合并前文的主题\n"
                    "5. 不要添加原始问题中不存在的信息\n\n"
                    f"对话中提到的关键实体：{entity_chain or '无明显实体'}\n"
                    f"对话主题：{topic_summary or '未识别主题'}"
                ),
            },
            {
                "role": "user",
                "content": (
                    f"对话历史：\n{history_text}\n\n"
                    f"用户最新问题：{query}\n\n"
                    f"改写后的完整查询："
                ),
            },
        ]

        try:
            rewritten = await self.llm.chat(
                messages, temperature=0.3, max_tokens=100
            )
            rewritten = rewritten.strip()
            if not rewritten:
                rewritten = query
        except Exception:
            rewritten = query

        return StrategyOutput(
            strategy=StrategyType.COREFERENCE,
            rewritten_query=rewritten,
            search_query=rewritten,
            metadata={
                "entity_chain": entity_chain,
                "topic_summary": topic_summary,
            },
        )

    def _extract_entity_chain(self, history: List[Dict]) -> str:
        """
        Extract a chain of named entities from assistant responses.
        Returns comma-separated entity list in order of mention.
        """
        entities = []
        # Patterns for Chinese named entities
        entity_pattern = re.compile(
            r"[《「『\"]([^》」』\"]{1,50})[》」』\"]"
        )
        code_pattern = re.compile(r"`([^`]+)`")
        tech_pattern = re.compile(
            r"(Spring\s*(Boot|Cloud|Security|MVC|Data)?|"
            r"Nacos|Redis|MySQL|Elasticsearch|Kafka|RabbitMQ|"
            r"Docker|Kubernetes|Nginx|Sentinel|Gateway|Feign|"
            r"DeepSeek|MyBatis[-\s]*Plus|Tomcat|Jetty|Netty|"
            r"Git|Jenkins|Maven|Gradle|JWT|OAuth|GraphQL|"
            r"gRPC|Dubbo|Zookeeper|Consul|Etcd)",
            re.IGNORECASE,
        )

        for msg in history[-10:]:
            content = msg.get("content", "")
            if not content:
                continue

            # Extract quoted entities
            for match in entity_pattern.finditer(content):
                entity = match.group(1)
                if entity not in entities and len(entity) > 1:
                    entities.append(entity)

            # Extract code entities
            for match in code_pattern.finditer(content):
                entity = match.group(1)
                if entity not in entities and len(entity) > 1:
                    entities.append(entity)

            # Extract tech entities
            for match in tech_pattern.finditer(content):
                entity = match.group(0)
                if entity not in entities and len(entity) > 1:
                    entities.append(entity)

        return ", ".join(entities[-10:])  # last 10 unique entities

    def _extract_topic_summary(self, history: List[Dict]) -> str:
        """
        Extract a concise topic summary from the conversation.
        Uses heuristics: first noun phrase in each user query,
        most-repeated terms across the history.
        """
        # Simple heuristic: collect user questions and extract key terms
        user_queries = [
            m.get("content", "") for m in history
            if m.get("role") == "user"
        ]
        if not user_queries:
            return ""

        # Extract potential topics (first meaningful chunk of each query)
        topics = []
        for q in user_queries[-3:]:  # last 3 user queries
            # Remove question particles
            q = re.sub(r"[？?！!。.,，、；;：:]+", " ", q)
            # Take first 20 chars as topic hint
            topic = q[:30].strip()
            if topic:
                topics.append(topic)

        return " | ".join(topics)

    def _get_relevant_history(
        self, query: str, history: List[Dict]
    ) -> List[Dict]:
        """
        Get the most relevant history window.
        Uses topic-aware sliding window: keeps turns that
        mention entities found in the current query.
        """
        max_messages = self.max_rounds * 2

        if len(history) <= max_messages:
            return history

        # Extract keywords from current query for relevance matching
        query_keywords = set(
            w for w in re.findall(r"[一-鿿A-Za-z]+", query)
            if len(w) > 1
        )

        # Score each history message by keyword overlap
        scored = []
        for i, msg in enumerate(history):
            content = msg.get("content", "")
            if not content:
                scored.append((i, 0))
                continue
            msg_words = set(
                w for w in re.findall(r"[一-鿿A-Za-z]+", content)
                if len(w) > 1
            )
            overlap = len(query_keywords & msg_words)
            scored.append((i, overlap))

        # Always include the most recent messages (last 2 turns = 4 messages)
        recent = history[-4:]
        recent_indices = set(range(len(history) - 4, len(history)))

        # Fill remaining slots with highest-scoring earlier messages
        remaining_slots = max_messages - 4
        if remaining_slots > 0:
            earlier = [
                (i, score) for i, score in scored
                if i not in recent_indices and score > 0
            ]
            earlier.sort(key=lambda x: -x[1])
            for i, _ in earlier[:remaining_slots]:
                recent_indices.add(i)

        return [history[i] for i in sorted(recent_indices)]
