"""
Lightweight BM25-style lexical retriever for legal chunks.
"""
from __future__ import annotations

import logging
import math
import re
import threading
import time
from collections import Counter
from typing import Any, Dict, Iterable, List

from algo.document.legal_case_indexer import build_index_documents, build_index_documents_from_chunks
from algo.domain_scope import build_curated_official_documents, filter_domain_records
from algo.legal_profile import filter_chunks_for_profile, normalize_profile
from clients.springboot_client import SpringBootClient

logger = logging.getLogger(__name__)

CHINESE_SEGMENT_RE = re.compile(r"[\u4e00-\u9fff]{2,}")
ASCII_TOKEN_RE = re.compile(r"[A-Za-z0-9_]+")


def _dedupe_keep_order(values: Iterable[str]) -> List[str]:
    seen = set()
    result: List[str] = []
    for value in values:
        text = (value or "").strip()
        if not text or text in seen:
            continue
        seen.add(text)
        result.append(text)
    return result


def _tokenize_text(text: str) -> List[str]:
    tokens: List[str] = []
    if not text:
        return tokens

    for segment in CHINESE_SEGMENT_RE.findall(text):
        if 2 <= len(segment) <= 8:
            tokens.append(segment)
        if len(segment) >= 2:
            tokens.extend(segment[index:index + 2] for index in range(len(segment) - 1))
        if len(segment) >= 3:
            tokens.extend(segment[index:index + 3] for index in range(len(segment) - 2))

    for token in ASCII_TOKEN_RE.findall(text.lower()):
        tokens.append(token)

    return _dedupe_keep_order(tokens)


def _build_query_tokens(question: str, analysis: Dict[str, Any] | None) -> List[str]:
    values: List[str] = [question]
    if analysis:
        values.extend(analysis.get("keywords") or [])
        values.extend(analysis.get("expanded_terms") or [])
        values.extend(analysis.get("domains") or [])
        values.extend(analysis.get("business_domains") or [])
        values.extend(analysis.get("procedure_domains") or [])

    tokens: List[str] = []
    for value in values:
        tokens.append((value or "").strip())
        tokens.extend(_tokenize_text(value or ""))
    return _dedupe_keep_order(tokens)


class LegalLexicalRetriever:
    def __init__(self, cache_ttl_seconds: int = 300):
        self.cache_ttl_seconds = cache_ttl_seconds
        self.springboot_client = SpringBootClient()
        self._lock = threading.Lock()
        self._cache: Dict[str, Dict[str, Any]] = {}

    def search(self, question: str, analysis: Dict[str, Any] | None = None, top_k: int = 20,
               profile: str = "") -> List[Dict[str, Any]]:
        query_tokens = _build_query_tokens(question, analysis)
        if not query_tokens:
            return []

        normalized_profile = normalize_profile(profile)
        index = self._get_index(normalized_profile)
        scores: List[Dict[str, Any]] = []
        max_score = 0.0

        for entry in index["documents"]:
            score = self._bm25_score(query_tokens, entry["tf"], entry["doc_len"], index["avg_doc_len"], index["doc_freq"], index["doc_count"])
            if score <= 0:
                continue
            max_score = max(max_score, score)
            metadata = dict(entry["metadata"])
            metadata["lexical_recall_score"] = score
            metadata["matched_queries"] = [question]
            metadata["matched_query_count"] = 1
            metadata.setdefault("score", 0.0)
            metadata.setdefault("distance", 0.0)
            scores.append(metadata)

        if max_score > 0:
            for item in scores:
                item["lexical_recall_score"] = round(float(item["lexical_recall_score"]) / max_score, 6)

        scores.sort(
            key=lambda item: (
                float(item.get("lexical_recall_score", 0.0) or 0.0),
                float(item.get("authority_weight", 1.0) or 1.0),
                float(item.get("section_weight", 1.0) or 1.0),
            ),
            reverse=True,
        )
        return scores[:top_k]

    def invalidate(self) -> None:
        with self._lock:
            self._cache = {}

    def _get_index(self, profile: str) -> Dict[str, Any]:
        now = time.time()
        with self._lock:
            cache = self._cache.get(profile)
            if cache and now - cache.get("loaded_at", 0) < self.cache_ttl_seconds:
                return cache

            documents = self._load_documents(profile)
            doc_freq: Counter[str] = Counter()
            indexed_docs = []
            total_len = 0

            for doc in documents:
                metadata = doc.get("metadata", {})
                search_text = "\n".join(
                    part for part in [
                        metadata.get("title", ""),
                        metadata.get("summary", ""),
                        metadata.get("tags", ""),
                        metadata.get("document_type", ""),
                        metadata.get("document_subtype", ""),
                        metadata.get("cause", ""),
                        metadata.get("business_domain", ""),
                        metadata.get("procedure_domain", ""),
                        metadata.get("keywords", ""),
                        metadata.get("aliases", ""),
                        metadata.get("issue_tags", ""),
                        metadata.get("result_tags", ""),
                        metadata.get("section_title", ""),
                        doc.get("content", ""),
                    ] if part
                )
                tokens = _tokenize_text(search_text)
                if not tokens:
                    continue
                term_freq = Counter(tokens)
                doc_len = sum(term_freq.values())
                total_len += doc_len
                doc_freq.update(term_freq.keys())
                indexed_docs.append({
                    "metadata": metadata,
                    "tf": term_freq,
                    "doc_len": doc_len,
                })

            cache = {
                "documents": indexed_docs,
                "doc_freq": doc_freq,
                "doc_count": len(indexed_docs),
                "avg_doc_len": (total_len / len(indexed_docs)) if indexed_docs else 1.0,
                "loaded_at": now,
            }
            self._cache[profile] = cache
            logger.info("Built lexical index for %s profile with %s document(s)", profile, len(indexed_docs))
            return cache

    def _load_documents(self, profile: str) -> List[Dict[str, Any]]:
        curated_documents = build_curated_official_documents()
        legal_knowledge_chunks = filter_domain_records(self.springboot_client.get_all_legal_knowledge_chunks())
        legal_chunks = filter_domain_records(self.springboot_client.get_all_legal_document_chunks())
        if legal_knowledge_chunks or legal_chunks:
            merged_chunks = filter_chunks_for_profile(list(legal_knowledge_chunks) + list(legal_chunks), profile)
            documents = build_index_documents_from_chunks(merged_chunks)
            documents.extend(self._filter_documents_for_profile(curated_documents, profile))
            return documents

        items = filter_domain_records(self.springboot_client.get_all_items())
        if items:
            documents = build_index_documents(items)
            documents.extend(self._filter_documents_for_profile(curated_documents, profile))
            return documents
        return self._filter_documents_for_profile(curated_documents, profile)

    @staticmethod
    def _filter_documents_for_profile(documents: List[Dict[str, Any]], profile: str) -> List[Dict[str, Any]]:
        filtered: List[Dict[str, Any]] = []
        for doc in documents:
            metadata = dict(doc.get("metadata", {}))
            metadata["content"] = doc.get("content", "")
            if filter_chunks_for_profile([metadata], profile):
                filtered.append(doc)
        return filtered

    @staticmethod
    def _bm25_score(query_tokens: List[str], term_freq: Counter[str], doc_len: int,
                    avg_doc_len: float, doc_freq: Counter[str], doc_count: int) -> float:
        if not doc_count or not avg_doc_len:
            return 0.0

        k1 = 1.5
        b = 0.75
        score = 0.0
        for token in query_tokens:
            frequency = term_freq.get(token, 0)
            if frequency <= 0:
                continue
            document_frequency = doc_freq.get(token, 0)
            idf = math.log(1 + (doc_count - document_frequency + 0.5) / (document_frequency + 0.5))
            numerator = frequency * (k1 + 1)
            denominator = frequency + k1 * (1 - b + b * doc_len / avg_doc_len)
            score += idf * numerator / denominator
        return score
