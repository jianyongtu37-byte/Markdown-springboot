"""
Strategy Router — maps intent to rewrite strategies.

Determines which L3 strategies to execute for each intent type,
with execution order and parallelism hints.
"""

from enum import Enum
from typing import List, Dict

from core.rewrite.intent_classifier import QueryIntent


class StrategyType(Enum):
    COREFERENCE = "coreference"
    KEYWORD_EXPANSION = "keyword_expansion"
    HYDE = "hyde"
    DECOMPOSITION = "decomposition"
    ABSTRACTION = "abstraction"
    MULTI_PERSPECTIVE = "multi_perspective"
    NONE = "none"


# Intent → Strategy mapping with priority order
# Strategies within a group can be run in parallel (asyncio.gather)
# Groups run sequentially to allow earlier outputs to inform later ones
INTENT_STRATEGY_MAP: Dict[QueryIntent, List[List[StrategyType]]] = {
    QueryIntent.FACTUAL: [
        # Group 1: parallel — enrich query for better retrieval
        [StrategyType.HYDE, StrategyType.KEYWORD_EXPANSION],
    ],
    QueryIntent.PROCEDURAL: [
        # Group 1: break into sub-steps, then enrich each
        [StrategyType.DECOMPOSITION],
        # Group 2: parallel enrichment of decomposed queries
        [StrategyType.HYDE, StrategyType.KEYWORD_EXPANSION],
    ],
    QueryIntent.COMPARATIVE: [
        # Group 1: decompose comparison into individual queries
        [StrategyType.DECOMPOSITION, StrategyType.MULTI_PERSPECTIVE],
        # Group 2: expand keywords for each variant
        [StrategyType.KEYWORD_EXPANSION],
    ],
    QueryIntent.CORRECTIVE: [
        # Group 1: resolve coreferences, then HyDE for the corrected query
        [StrategyType.COREFERENCE],
        [StrategyType.HYDE],
    ],
    QueryIntent.METADATA: [
        # Simple: just keyword expansion for metadata fields
        [StrategyType.KEYWORD_EXPANSION],
    ],
    QueryIntent.CHITCHAT: [
        # No rewrite needed
        [],
    ],
}

# Strategies that are safe to run without LLM
NON_LLM_STRATEGIES = {
    StrategyType.KEYWORD_EXPANSION,
}

# Strategies that require LLM
LLM_STRATEGIES = {
    StrategyType.COREFERENCE,
    StrategyType.HYDE,
    StrategyType.DECOMPOSITION,
    StrategyType.ABSTRACTION,
    StrategyType.MULTI_PERSPECTIVE,
}

# Cost estimates (relative, 1 = cheapest LLM call)
STRATEGY_COST: Dict[StrategyType, float] = {
    StrategyType.COREFERENCE: 1.0,
    StrategyType.KEYWORD_EXPANSION: 0.0,   # no LLM
    StrategyType.HYDE: 1.2,                 # slightly longer generation
    StrategyType.DECOMPOSITION: 1.0,
    StrategyType.ABSTRACTION: 1.0,
    StrategyType.MULTI_PERSPECTIVE: 1.5,    # generates multiple variants
    StrategyType.NONE: 0.0,
}


class StrategyRouter:
    """Routes intent to a prioritized list of strategy groups."""

    def __init__(self, config=None):
        self.config = config
        # Feature flags from config
        self._enabled = {
            StrategyType.HYDE: getattr(
                config, "REWRITE_HYDE_ENABLED", True
            ) if config else True,
            StrategyType.DECOMPOSITION: getattr(
                config, "REWRITE_DECOMPOSITION_ENABLED", True
            ) if config else True,
            StrategyType.MULTI_PERSPECTIVE: getattr(
                config, "REWRITE_MULTI_PERSPECTIVE_ENABLED", True
            ) if config else True,
        }

    def route(self, intent: QueryIntent) -> List[List[StrategyType]]:
        """
        Get strategy groups for an intent.

        Returns list of strategy groups. Each group is a list
        that can be executed in parallel. Groups run sequentially.
        """
        groups = INTENT_STRATEGY_MAP.get(intent, [])
        if not groups:
            return []

        # Filter based on feature flags
        filtered = []
        for group in groups:
            enabled_group = [
                s for s in group
                if self._is_enabled(s)
            ]
            if enabled_group:
                filtered.append(enabled_group)

        return filtered

    def _is_enabled(self, strategy: StrategyType) -> bool:
        if strategy in self._enabled:
            return self._enabled[strategy]
        return True  # enabled by default if not in feature flags

    @staticmethod
    def get_parallel_groups(ordered_list: List[StrategyType]) -> List[List[StrategyType]]:
        """
        Group a flat list of strategies into parallel-executable groups.
        Strategies that depend on LLM are isolated to their own groups
        (they can still run in parallel with other LLM strategies).
        """
        return [[s] for s in ordered_list]

    @staticmethod
    def estimate_cost(intent: QueryIntent) -> float:
        """Estimate the LLM cost multiplier for a given intent."""
        groups = INTENT_STRATEGY_MAP.get(intent, [])
        total = 0.0
        for group in groups:
            total += sum(STRATEGY_COST.get(s, 0) for s in group)
        return total
