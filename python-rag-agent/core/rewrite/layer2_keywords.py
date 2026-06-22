"""
Layer 2 Keyword Processor — jieba-based keyword extraction and expansion.

Performs: Chinese word segmentation, TF-IDF keyword extraction,
history context keyword extraction, and domain-aware keyword expansion.

Target: <50ms per query (no LLM calls).
"""

import re
from typing import List, Dict, Optional, Tuple
from dataclasses import dataclass, field

from core.rewrite.domain_dict import DomainDict

# Lazy import — jieba is heavy and may not be installed in all environments
_jieba_loaded = False
_jieba = None
_jieba_analyse = None


def _ensure_jieba():
    """Lazy-load jieba to avoid import errors when not installed."""
    global _jieba_loaded, _jieba, _jieba_analyse
    if not _jieba_loaded:
        try:
            import jieba
            import jieba.analyse
            _jieba = jieba
            _jieba_analyse = jieba.analyse
        except ImportError:
            _jieba = None
            _jieba_analyse = None
        _jieba_loaded = True


# Common Chinese tech spelling corrections
_SPELL_CORRECTIONS: Dict[str, str] = {
    "弹性搜索": "Elasticsearch",
    "深度求索": "DeepSeek",
    "斯普瑞": "Spring",
    "斯普林": "Spring",
    "那考斯": "Nacos",
    "瑞迪斯": "Redis",
    "麦塞口": "MySQL",
    "道克": "Docker",
    "道可": "Docker",
    "库博": "Kubernetes",
    "库博耐提斯": "Kubernetes",
    "微服务架构": "微服务",
}


class Layer2KeywordProcessor:
    """
    Keyword-based query processing — jieba TF-IDF + domain expansion.

    Layer 2 does NOT alter the LLM-facing query. It enriches the
    search_query used for vector retrieval with expanded keywords.
    """

    def __init__(self, domain_dict: Optional[DomainDict] = None):
        self.domain_dict = domain_dict or DomainDict()
        _ensure_jieba()

    @property
    def is_available(self) -> bool:
        """Check if jieba is installed and usable."""
        _ensure_jieba()
        return _jieba is not None

    def process(self, query: str, history: List[Dict]) -> "LayerResult":
        """
        Extract keywords and enrich search query.

        Returns LayerResult where search_query contains expanded keywords.
        The rewritten_query field is unchanged (L2 doesn't modify LLM query).
        """
        from core.rewrite.layer1_rules import LayerResult

        changes = []

        # Step 1: Extract keywords from current query
        query_keywords = self._extract_keywords(query, top_k=5)

        # Step 2: Extract keywords from recent history context
        history_keywords = self._extract_history_keywords(history)

        # Step 3: Merge and expand with domain dictionary
        all_keywords = list(dict.fromkeys(query_keywords + history_keywords))
        expanded = self._domain_expand(all_keywords)

        # Step 4: Build search-optimized query
        # Append expanded keywords to the original search query
        expansion_terms = expanded[:3]  # limit to top 3
        if expansion_terms:
            search_query = query + " " + " ".join(expansion_terms)
            changes.append(("keyword_expansion", f"+{expansion_terms}"))
        else:
            search_query = query

        # Step 5: Confidence estimation
        # High confidence if keywords from query overlap strongly with history topics
        confidence = self._calc_confidence(query_keywords, history_keywords)
        is_final = confidence > 0.8

        return LayerResult(
            current=query,
            rewritten_query=query,             # L2 doesn't modify LLM query
            search_query=search_query,          # enriched for retrieval
            is_final=is_final,
            confidence=confidence,
            layer="L2",
            changes=changes,
            metadata={
                "query_keywords": query_keywords,
                "history_keywords": history_keywords,
                "expansion_terms": expansion_terms,
            },
        )

    def _extract_keywords(self, text: str, top_k: int = 5) -> List[str]:
        """Extract top-K keywords using jieba TF-IDF."""
        if not self.is_available:
            return self._fallback_keyword_extract(text)[:top_k]

        try:
            kw_with_weights = _jieba_analyse.extract_tags(
                text, topK=top_k, withWeight=True
            )
            return [kw for kw, _ in kw_with_weights]
        except Exception:
            return self._fallback_keyword_extract(text)[:top_k]

    def _extract_history_keywords(self, history: List[Dict]) -> List[str]:
        """Extract keywords from recent conversation history."""
        if not history:
            return []

        # Focus on last 2 rounds (4 messages)
        recent = history[-4:]
        history_text = " ".join(
            [m.get("content", "") for m in recent if m.get("content")]
        )
        if not history_text.strip():
            return []

        return self._extract_keywords(history_text, top_k=3)

    def _domain_expand(self, keywords: List[str]) -> List[str]:
        """
        Expand keywords with domain synonyms.
        Returns expanded keywords with originals first, then domain expansions.
        """
        expanded = []
        seen = set()
        for kw in keywords:
            if kw not in seen and len(kw) > 1:
                seen.add(kw)
                expanded.append(kw)

        if self.domain_dict.is_empty():
            return expanded

        for kw in keywords:
            # Get canonical form
            canonical = self.domain_dict.get_canonical(kw)
            if canonical and canonical not in seen:
                seen.add(canonical)
                expanded.append(canonical)
            # Get synonyms
            for syn in self.domain_dict.get_synonyms(kw):
                if syn not in seen:
                    seen.add(syn)
                    expanded.append(syn)

        return expanded

    def _calc_confidence(
        self, query_keywords: List[str], history_keywords: List[str]
    ) -> float:
        """Estimate confidence based on keyword overlap with history."""
        if not history_keywords:
            return 0.5  # no history, medium confidence
        if not query_keywords:
            return 0.3  # no keywords extracted, low confidence

        query_set = set(query_keywords)
        history_set = set(history_keywords)
        overlap = query_set & history_set

        if not overlap:
            return 0.3
        # Jaccard-like similarity
        jaccard = len(overlap) / len(query_set | history_set)
        return min(1.0, jaccard + 0.3)

    def _fallback_keyword_extract(self, text: str) -> List[str]:
        """
        Fallback keyword extraction without jieba.
        Uses simple bigram extraction from Chinese text.
        """
        # Extract Chinese word sequences (2-4 chars)
        chinese_chars = re.findall(r"[一-鿿]+", text)
        keywords = []
        for segment in chinese_chars:
            # Bigrams
            for i in range(0, len(segment) - 1, 2):
                bigram = segment[i:i+2]
                if len(bigram) == 2:
                    keywords.append(bigram)
            # Extract longer segments (3-4 chars) that might be terms
            if len(segment) >= 3:
                for i in range(0, len(segment) - 2, 3):
                    trigram = segment[i:i+3]
                    if len(trigram) == 3:
                        keywords.append(trigram)
        # Also extract ASCII terms
        ascii_terms = re.findall(r"[A-Za-z][A-Za-z0-9_\-+#.]{1,20}", text)
        keywords.extend(ascii_terms)
        return keywords

    def correct_spelling(self, query: str) -> Tuple[str, bool]:
        """Apply known spelling corrections. Returns (corrected_query, changed)."""
        corrected = query
        changed = False
        for wrong, right in _SPELL_CORRECTIONS.items():
            if wrong in corrected:
                corrected = corrected.replace(wrong, right)
                changed = True
        return corrected, changed
