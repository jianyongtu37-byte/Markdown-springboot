"""
Domain entity dictionary loader and lookup.
Provides abbreviation expansion, synonym mapping, and entity information.
"""

import json
import os
from typing import Dict, List, Optional


class DomainDict:
    """In-memory domain dictionary with fast lookups."""

    def __init__(self, dict_path: Optional[str] = None):
        self._path = dict_path or os.path.join(
            os.path.dirname(__file__), "..", "..", "data", "domain_dict.json"
        )
        self._abbreviations: Dict[str, str] = {}
        self._synonyms: Dict[str, List[str]] = {}
        self._entities: Dict[str, dict] = {}
        self._reverse_abbrev: Dict[str, str] = {}  # 中文 → 英文缩写
        self._synonym_index: Dict[str, str] = {}   # 同义词 → 规范词
        self.load()

    def load(self):
        """Load dictionary from JSON file."""
        try:
            with open(self._path, "r", encoding="utf-8") as f:
                data = json.load(f)
        except (FileNotFoundError, json.JSONDecodeError):
            print(f"[DomainDict] Warning: could not load {self._path}, using empty dict")
            return

        self._abbreviations = data.get("abbreviations", {})
        self._synonyms = data.get("synonyms", {})
        self._entities = data.get("entities", {})

        # Build reverse lookups
        for abbr, full in self._abbreviations.items():
            self._reverse_abbrev[full] = abbr
        for canonical, synonyms in self._synonyms.items():
            self._synonym_index[canonical] = canonical
            for syn in synonyms:
                self._synonym_index[syn] = canonical

    def reload(self):
        """Hot-reload dictionary from file."""
        self._abbreviations.clear()
        self._synonyms.clear()
        self._entities.clear()
        self._reverse_abbrev.clear()
        self._synonym_index.clear()
        self.load()

    def expand_abbreviation(self, word: str) -> Optional[str]:
        """Expand an abbreviation to its full form. e.g., 'ES' → 'Elasticsearch'."""
        return self._abbreviations.get(word)

    def get_canonical(self, word: str) -> Optional[str]:
        """Map a synonym or abbreviation to its canonical form."""
        # Try abbreviation expansion first
        full = self._abbreviations.get(word)
        if full:
            return full
        # Try synonym lookup
        return self._synonym_index.get(word)

    def get_synonyms(self, word: str) -> List[str]:
        """Get all synonyms for a canonical term."""
        canonical = self.get_canonical(word)
        if canonical and canonical in self._synonyms:
            return self._synonyms[canonical]
        return []

    def get_entity_info(self, name: str) -> Optional[dict]:
        """Get entity metadata."""
        return self._entities.get(name)

    def expand_query_terms(self, query: str) -> str:
        """
        Expand abbreviations and enrich query with synonyms.
        Returns enriched query string with expanded terms appended.
        """
        words = query.split()
        expansions = []
        for word in words:
            # Expand abbreviations
            full = self.expand_abbreviation(word)
            if full:
                expansions.append(full)
            # Add synonyms
            synonyms = self.get_synonyms(word)
            if synonyms:
                expansions.extend(synonyms[:2])  # limit to 2 synonyms
        if expansions:
            return query + " " + " ".join(expansions)
        return query

    def is_empty(self) -> bool:
        """Check if dictionary has any entries loaded."""
        return not bool(self._abbreviations or self._synonyms or self._entities)

    def __len__(self) -> int:
        return len(self._abbreviations) + len(self._synonyms) + len(self._entities)
