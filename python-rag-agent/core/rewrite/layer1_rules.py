"""
Layer 1 Rule Engine — sub-millisecond query processing.

Performs: normalization, stopword removal, abbreviation expansion,
simple anaphora resolution (regex-based, no LLM), and compound detection.

Target: <5ms per query.
"""

import re
from typing import List, Dict, Optional, Tuple
from dataclasses import dataclass, field

from core.rewrite.chinese_patterns import ChinesePatterns
from core.rewrite.domain_dict import DomainDict


# ~200 most common Chinese stopwords for search-oriented removal
_STOPWORDS: set = set([
    "的", "了", "在", "是", "我", "有", "和", "就", "不", "人", "都", "一",
    "一个", "上", "也", "很", "到", "说", "要", "去", "你", "会", "着",
    "没有", "看", "好", "自己", "这", "他", "她", "它", "们", "那", "些",
    "所", "为", "所以", "因为", "但是", "然而", "而且", "虽然", "如果",
    "可以", "这个", "那个", "这些", "那些", "这里", "那里", "怎么", "怎样",
    "怎么样", "什么", "为什么", "如何", "吗", "呢", "吧", "啊", "嘛", "呗",
    "的", "地", "得", "着", "了", "过", "之", "与", "及", "或", "但",
    "请问", "想问", "想问一下", "我想问", "我想知道", "帮", "帮忙", "能不能",
    "可不可以", "是否", "应该", "需要", "必须", "一定要", "大概", "大约",
    "可能", "好像", "似乎", "觉得", "认为", "希望", "想", "要", "会", "能",
    "能够", "可以", "应该", "该", "得", "必须", "一定", "真的", "确实",
    "比较", "非常", "很", "太", "挺", "蛮", "好", "有的", "有些", "一点",
    "有点", "一下", "一会儿", "的话", "来讲", "来说", "而言", "总的",
    "大家", "各位", "有人", "谁", "哪位", "每个人", "一切", "所有", "任何",
    "一些", "某种", "其他", "别的", "另外", "那个", "整个", "全部", "整个",
    "方面", "地方", "那里", "哪里", "这边", "那边", "里面", "外面",
    "现在", "今天", "昨天", "明天", "当时", "之前", "之后", "以前", "以后",
    "然后", "接着", "接下来", "首先", "其次", "最后", "最终", "开始", "结束",
    "关于", "对于", "根据", "按照", "通过", "经过", "由于", "为了",
    "呃", "嗯", "啊", "哦", "哈", "呵呵", "就是说", "就是说呢",
])


@dataclass
class LayerResult:
    """Output from a processing layer."""
    current: str                              # query text after this layer
    rewritten_query: str                      # final rewritten query for LLM
    search_query: str                         # search-optimized query for retrieval
    is_final: bool = False                    # resolved at this layer?
    confidence: float = 0.0                   # 0-1
    layer: str = ""                           # "L1", "L2", "L3"
    changes: List[Tuple[str, str]] = field(default_factory=list)
    metadata: Dict = field(default_factory=dict)


class Layer1RuleEngine:
    """
    Rule-based query processing — no async I/O, no LLM calls.

    Responsibilities:
    1. Normalize whitespace and punctuation
    2. Remove search-noise stopwords
    3. Expand abbreviations and domain synonyms
    4. Simple anaphora resolution via regex + history heuristics
    5. Detect compound question structure
    """

    def __init__(self, domain_dict: Optional[DomainDict] = None):
        self.domain_dict = domain_dict or DomainDict()

    def process(self, query: str, history: List[Dict]) -> LayerResult:
        """
        Process a query through Layer 1 rules.

        Returns LayerResult with is_final=True if query is simple enough
        to bypass further processing layers.
        """
        changes = []

        # Step 1: Normalize
        normalized = ChinesePatterns.normalize(query)
        if normalized != query:
            changes.append(("normalize", f"{query!r} → {normalized!r}"))

        # Step 2: Strip URLs
        stripped = ChinesePatterns.strip_urls(normalized)
        if stripped != normalized:
            changes.append(("strip_urls", "removed URLs"))

        # Step 3: Remove stopwords (for search query, keep original for LLM)
        search_query = self._remove_stopwords(stripped)
        search_query_clean = search_query.strip()
        if not search_query_clean:
            search_query_clean = stripped   # don't empty the query entirely

        # Step 4: Expand abbreviations and synonyms
        expanded = self.domain_dict.expand_query_terms(search_query_clean)
        if expanded != search_query_clean:
            changes.append(("domain_expand", f"enriched with domain terms"))

        # Step 5: Simple anaphora resolution (regex + history heuristic)
        resolved = self._resolve_simple_anaphora(expanded, history)
        if resolved != expanded:
            changes.append(("anaphora_resolve", f"resolved pronoun references"))

        # Step 6: Determine if query can be considered resolved
        has_anaphora = ChinesePatterns.has_anaphora(stripped)
        has_compound = ChinesePatterns.has_compound_structure(stripped)
        is_very_short = len(stripped) < 5

        if not has_anaphora and not has_compound and not is_very_short:
            is_final = True
            confidence = 1.0
        elif has_anaphora and resolved != expanded:
            # We resolved something, but may still be incomplete
            is_final = not has_compound
            confidence = 0.6 if has_compound else 0.75
        else:
            is_final = False
            confidence = 0.5

        return LayerResult(
            current=resolved,
            rewritten_query=resolved,
            search_query=expanded,
            is_final=is_final,
            confidence=confidence,
            layer="L1",
            changes=changes,
            metadata={
                "has_anaphora": has_anaphora,
                "has_compound": has_compound,
                "query_length": len(stripped),
            },
        )

    def _remove_stopwords(self, text: str) -> str:
        """Remove Chinese stopwords from query for search optimization."""
        import re
        # Split into segments: Chinese characters, ASCII words, whitespace
        segments = re.findall(r"[一-鿿]+|[A-Za-z0-9]+|[^\s]", text)

        # Filter out segments that are entirely stopwords
        filtered = []
        for seg in segments:
            # Skip pure Chinese stopwords (1-2 chars)
            if seg in _STOPWORDS:
                continue
            # Skip longer segments that are entirely composed of stopword characters
            if all(c in _STOPWORDS for c in seg):
                continue
            filtered.append(seg)

        return " ".join(filtered)

    def _resolve_simple_anaphora(
        self, query: str, history: List[Dict]
    ) -> str:
        """
        Resolve simple anaphora using history heuristics.

        Handles:
        - "它" / "这" / "那" → last entity from history
        - "上面提到的" / "刚才说的" → last topic from history
        - "那怎么样呢？" → recover topic from previous turn
        """
        if not history:
            return query

        # Format recent history as text for entity extraction
        recent = history[-6:]  # last 3 rounds
        history_text = " ".join(
            [m.get("content", "") for m in recent if m.get("content")]
        )

        # Check for demonstrative anaphora
        if ChinesePatterns.ANAPHORA_DEMONSTRATIVE.search(query) or \
           ChinesePatterns.ANAPHORA_SIMPLE.search(query):
            entity = ChinesePatterns.extract_last_entity_from_history(history_text)
            if entity:
                # Replace the pronoun with the entity
                query = ChinesePatterns.ANAPHORA_SIMPLE.sub(entity, query, count=1)
                query = ChinesePatterns.ANAPHORA_DEMONSTRATIVE.sub(entity, query, count=1)

        # Check for discourse deixis: "前面提到的/刚才说的"
        if ChinesePatterns.ANAPHORA_DISCOURSE.search(query):
            entity = ChinesePatterns.extract_last_entity_from_history(history_text)
            if entity:
                query = ChinesePatterns.ANAPHORA_DISCOURSE.sub(
                    f"{entity}相关的", query, count=1
                )

        # Check for ellipsis: "呢/吗/吧" questions without topic
        if ChinesePatterns.ELLIPSIS_PATTERN.search(query) or \
           ChinesePatterns.BARE_INTERROGATIVE.search(query):
            entity = ChinesePatterns.extract_last_entity_from_history(history_text)
            if entity:
                query = f"{entity}{query}"

        # Check for "那/那么..." needing topic recovery
        if ChinesePatterns.ANAPHORA_CONJUNCTION.search(query):
            entity = ChinesePatterns.extract_last_entity_from_history(history_text)
            if entity:
                # Replace "那" with entity at the start
                query = ChinesePatterns.ANAPHORA_CONJUNCTION.sub(
                    f"{entity}\\g<1>\\g<2>", query
                )

        return query
