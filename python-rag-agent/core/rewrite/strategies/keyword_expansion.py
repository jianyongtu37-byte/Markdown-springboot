"""
Keyword Expansion Strategy — domain-aware query term enrichment.

Uses the domain dictionary and entity linking to expand query keywords
without modifying the original query's meaning.

This is the LLM-free strategy — safe to run in parallel with LLM strategies
and can be executed at L2 or as an L3 enhancement.
"""

from typing import Dict, List, Optional
from dataclasses import dataclass, field

from core.rewrite.strategies.coreference import StrategyOutput
from core.rewrite.strategy_router import StrategyType
from core.rewrite.domain_dict import DomainDict


class KeywordExpansionStrategy:
    """Domain-aware keyword expansion. No LLM required."""

    def __init__(self, domain_dict: Optional[DomainDict] = None):
        self.domain_dict = domain_dict or DomainDict()

    async def execute(self, query: str, history: List[Dict]) -> "StrategyOutput":
        """
        Expand query with domain synonyms and related terms.
        The original query is preserved; expansions are appended.
        """
        expanded_terms = []

        # Extract candidate terms from query
        import re
        # Chinese terms (2+ chars)
        chinese_terms = re.findall(r"[一-鿿]{2,}", query)
        # ASCII terms
        ascii_terms = re.findall(r"[A-Za-z][A-Za-z0-9_\-+#.]{1,20}", query)
        candidates = chinese_terms + ascii_terms

        seen = set()
        for term in candidates:
            # Look up abbreviation expansion
            full = self.domain_dict.expand_abbreviation(term)
            if full and full not in seen:
                seen.add(full)
                expanded_terms.append(full)

            # Look up canonical form
            canonical = self.domain_dict.get_canonical(term)
            if canonical and canonical not in seen:
                seen.add(canonical)
                expanded_terms.append(canonical)

            # Get related synonyms
            for syn in self.domain_dict.get_synonyms(term):
                if syn not in seen:
                    seen.add(syn)
                    expanded_terms.append(syn)

            # Get entity related terms
            entity_info = self.domain_dict.get_entity_info(term)
            if entity_info:
                for related in entity_info.get("related", []):
                    if related not in seen:
                        seen.add(related)
                        expanded_terms.append(related)

        # Build enriched search query
        if expanded_terms:
            search_query = query + " " + " ".join(expanded_terms[:5])
        else:
            search_query = query

        return StrategyOutput(
            strategy=StrategyType.KEYWORD_EXPANSION,
            rewritten_query=query,  # original preserved
            search_query=search_query,
            metadata={
                "expansion_terms": expanded_terms[:5],
                "term_count": len(expanded_terms),
            },
        )
