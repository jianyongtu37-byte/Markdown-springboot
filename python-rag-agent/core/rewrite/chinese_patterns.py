"""
Chinese-specific regex patterns for query analysis.

Covers: anaphora detection, compound question detection,
intent keyword matching, and ellipsis patterns.
"""

import re
from typing import List, Tuple, Optional


class ChinesePatterns:
    """Compiled regex patterns for Chinese query analysis."""

    # ---- Anaphora patterns (pronouns requiring entity resolution) ----
    # 它/其/这/那/该/此 + optional classifier + optional content + optional particle
    ANAPHORA_SIMPLE = re.compile(
        r"(它|其|这|那|该|此)(个|种|些|类|里|边|儿)?"
    )
    ANAPHORA_DEMONSTRATIVE = re.compile(
        r"(这个|那个|这些|那些|这种|那种|这里|那里)"
    )
    # "前面/上面/刚才/之前 (提到的/说的/讲的) ..."
    ANAPHORA_DISCOURSE = re.compile(
        r"(前面|上面|刚才|之前)(提到的|说的|讲的|写的|讨论的)?"
    )
    # Questions starting with bare "那..." meaning "then/therefore..."
    ANAPHORA_CONJUNCTION = re.compile(r"^(那|那么)(.{0,30})(呢|吗|啊|吧)?[？?]?$")
    # Ellipsis: "呢/吗/吧" suffix without explicit topic
    ELLIPSIS_PATTERN = re.compile(r"^.{0,10}(呢|吗|吧|啊)[？?]?$")
    # Bare interrogative: "怎么样/如何" without subject
    BARE_INTERROGATIVE = re.compile(r"^(怎么样|如何|怎么(样|办|搞|弄))[？?]?$")

    # ---- Compound question detection ----
    # Conjunctions indicating multiple questions
    COMPOUND_CONJUNCTION = re.compile(r"(并且|以及|同时|另外|还|也|还有|加上)")
    # Alternative markers
    COMPOUND_ALTERNATIVE = re.compile(r"(还是|或者|要么)")
    # Multiple clauses separated by semicolons (3+)
    COMPOUND_SEMICOLON = re.compile(r".+[；;].+[；;].+")
    # Multiple question marks
    COMPOUND_MULTI_QUESTION = re.compile(r"[？?]")

    # ---- Intent-specific keyword patterns ----
    INTENT_PROCEDURAL = re.compile(
        r"(如何|怎么|怎样|是不是要|需要怎么|应该如何|如何去做|有啥办法|怎么办)"
    )
    INTENT_COMPARATIVE = re.compile(
        r"(区别|差异|比较|对比|哪个好|哪个更|优缺点|vs|相比|有什么不同|哪一个)"
    )
    INTENT_CORRECTIVE = re.compile(
        r"^(不是|不对|错了|我指的是|我的意思是|应该说|更正|纠正|不是这样|不对不对)"
    )
    INTENT_METADATA = re.compile(
        r"(作者|日期|时间|谁写|什么时候|分类|标签|来源|版本|多大|多少字)"
    )
    INTENT_CHITCHAT = re.compile(
        r"^(你好|您好|谢谢|多谢|再见|拜拜|嗨|hello|hi|早|晚上好|早上好|下午好|在吗|在不在)"
    )
    INTENT_FACTUAL = re.compile(
        r"(是什么|什么是|什么意思|定义|概念|原理|为啥|为什么|原因是|背景)"
    )

    # ---- Entity extraction from history ----
    # Extract noun phrases from assistant responses
    ENTITY_NOUN = re.compile(
        r"(?:是|的|使用|用|配置|部署|开发|实现|调用|基于|通过|关于|作为)"
        r"([一-鿿A-Za-z0-9_+#.-]{1,30})"
    )
    # Named entities in brackets or quotes
    ENTITY_QUOTED = re.compile(r"[《「『\"]([^》」』\"]{1,50})[》」』\"]")

    # ---- Query normalization ----
    # Excessive punctuation
    NORMALIZE_PUNCTUATION = re.compile(r"[！!？?。.,，、；;：:]{2,}")
    # Whitespace normalization
    NORMALIZE_WHITESPACE = re.compile(r"\s+")
    # URLs (strip for search)
    URL_PATTERN = re.compile(r"https?://\S+")

    # ---- Topic continuation detection ----
    TOPIC_CONTINUATION = re.compile(
        r"^(那|那么|所以|因此|这样的话|按你这么说|照这么说)"
    )

    # ---- Helper methods ----

    @classmethod
    def has_anaphora(cls, query: str) -> bool:
        """Check if query contains pronouns needing resolution."""
        return bool(
            cls.ANAPHORA_SIMPLE.search(query)
            or cls.ANAPHORA_DEMONSTRATIVE.search(query)
            or cls.ANAPHORA_DISCOURSE.search(query)
            or cls.ELLIPSIS_PATTERN.search(query)
            or cls.BARE_INTERROGATIVE.search(query)
        )

    @classmethod
    def has_compound_structure(cls, query: str) -> bool:
        """Check if query contains multiple sub-questions."""
        # Count question marks
        qm_count = len(cls.COMPOUND_MULTI_QUESTION.findall(query))
        if qm_count >= 2:
            return True
        # Check for compound conjunctions
        if cls.COMPOUND_CONJUNCTION.search(query):
            return True
        # Check for alternative markers
        if cls.COMPOUND_ALTERNATIVE.search(query):
            return True
        # Check for semicolon-separated clauses
        if cls.COMPOUND_SEMICOLON.search(query):
            return True
        return False

    @classmethod
    def classify_intent_by_rules(cls, query: str) -> Optional[str]:
        """
        Fast rule-based intent classification.
        Returns intent label or None if ambiguous.
        """
        if cls.INTENT_CHITCHAT.search(query):
            return "chitchat"
        if cls.INTENT_CORRECTIVE.search(query):
            return "corrective"
        if cls.INTENT_METADATA.search(query):
            return "metadata"
        if cls.INTENT_COMPARATIVE.search(query):
            return "comparative"
        if cls.INTENT_PROCEDURAL.search(query):
            return "procedural"
        if cls.INTENT_FACTUAL.search(query):
            return "factual"
        return None  # ambiguous, needs LLM

    @classmethod
    def extract_last_entity_from_history(cls, history_text: str) -> Optional[str]:
        """Extract the last-mentioned named entity from conversation history."""
        # Try quoted entities first
        quoted = cls.ENTITY_QUOTED.findall(history_text)
        if quoted:
            return quoted[-1]
        # Try noun phrases
        nouns = cls.ENTITY_NOUN.findall(history_text)
        if nouns:
            return nouns[-1]
        return None

    @classmethod
    def normalize(cls, query: str) -> str:
        """Normalize query text for processing."""
        # Collapse whitespace
        query = cls.NORMALIZE_WHITESPACE.sub(" ", query).strip()
        # Collapse excessive punctuation
        query = cls.NORMALIZE_PUNCTUATION.sub(
            lambda m: m.group(0)[0], query
        )
        return query

    @classmethod
    def strip_urls(cls, text: str) -> str:
        """Remove URLs from text."""
        return cls.URL_PATTERN.sub(" ", text)
