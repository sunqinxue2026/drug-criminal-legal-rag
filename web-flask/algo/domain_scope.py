"""
Domain scoping helpers for the drug-related criminal law scenario.
"""
from __future__ import annotations

import json
import re
from pathlib import Path
from typing import Dict, Iterable, List, Sequence


FOCUSED_DOMAIN_NAME = "涉毒刑事法律风险分析与普法"
FOCUSED_CATEGORY_NAME = "涉毒刑事专题"
DRUG_RELATED_TERMS = [
    "涉毒",
    "毒品",
    "贩毒",
    "吸毒",
    "戒毒",
    "贩卖毒品",
    "运输毒品",
    "制造毒品",
    "走私毒品",
    "非法持有毒品",
    "容留吸毒",
    "引诱吸毒",
    "教唆吸毒",
    "强迫吸毒",
    "麻醉药品",
    "精神药品",
    "麻精药品",
    "依托咪酯",
    "甲基苯丙胺",
    "冰毒",
    "海洛因",
    "可卡因",
    "鸦片",
    "K粉",
    "笑气",
    "曲马多",
    "毒资",
    "洗钱",
    "禁毒法",
    "毒品犯罪",
]

FOCUSED_SCOPE_TERMS = [
    "涉毒程序应对",
    "涉毒证据规则",
    "麻精药品与新型涉毒风险",
]
FOCUSED_SOURCE_FILES = [
    ("laws", "anti_drug_law_zygov.json"),
    ("guiding_cases", "medical_drug_typical_cases_2025.json"),
]


def _compact_text(value: object) -> str:
    text = str(value or "")
    text = text.replace("\r", "\n")
    text = re.sub(r"\n{3,}", "\n\n", text)
    text = re.sub(r"[ \t]+", " ", text)
    return text.strip()


def _join_lines(parts: Iterable[object]) -> str:
    return "\n".join(part for part in (_compact_text(value) for value in parts) if part)


def is_drug_related_text(text: object) -> bool:
    normalized = _compact_text(text)
    if not normalized:
        return False
    return any(term in normalized for term in DRUG_RELATED_TERMS)


def _record_text(record: Dict) -> str:
    return _join_lines([
        record.get("title"),
        record.get("documentName"),
        record.get("documentType"),
        record.get("documentSubtype"),
        record.get("businessDomain"),
        record.get("procedureDomain"),
        record.get("cause"),
        record.get("topic"),
        record.get("keywords"),
        record.get("aliases"),
        record.get("summary"),
        record.get("retrievalText"),
        record.get("chunkText"),
        record.get("content"),
    ])


def is_drug_related_record(record: Dict) -> bool:
    if not isinstance(record, dict):
        return False
    text = _record_text(record)
    return is_drug_related_text(text) or any(term in text for term in FOCUSED_SCOPE_TERMS)


def filter_domain_records(records: Sequence[Dict]) -> List[Dict]:
    return [record for record in records if is_drug_related_record(record)]


def _processed_root() -> Path:
    return Path(__file__).resolve().parents[2] / "data" / "legal_sources" / "drug_vertical" / "processed"


def _entry_tags(payload: Dict, entry: Dict) -> str:
    values: List[str] = []
    for raw in (
        entry.get("keywords"),
        entry.get("aliases"),
        payload.get("topic"),
        payload.get("businessDomain"),
        entry.get("businessDomain"),
    ):
        if isinstance(raw, list):
            values.extend(str(item) for item in raw if item)
        elif raw:
            values.append(str(raw))

    deduped: List[str] = []
    seen = set()
    for value in values:
        normalized = _compact_text(value)
        if not normalized or normalized in seen:
            continue
        seen.add(normalized)
        deduped.append(normalized)
    return ",".join(deduped)


def _build_article_documents(payload: Dict, source_key: str) -> List[Dict]:
    documents: List[Dict] = []
    for index, article in enumerate(payload.get("articles") or [], start=1):
        content = _join_lines([
            payload.get("documentName"),
            payload.get("businessDomain"),
            payload.get("topic"),
            _join_lines([article.get("articleNumber"), article.get("articleTitle")]),
            article.get("keywords"),
            article.get("aliases"),
            article.get("content"),
        ])
        if not is_drug_related_text(content) and not any(term in content for term in FOCUSED_SCOPE_TERMS):
            continue

        chunk_id = f"official-{source_key}-article-{index}"
        title = _join_lines([payload.get("documentName"), article.get("articleNumber")]) or payload.get("documentName")
        tags = _entry_tags(payload, article)
        metadata = {
            "item_id": chunk_id,
            "document_id": source_key,
            "chunk_id": chunk_id,
            "chunk_index": index - 1,
            "title": title,
            "summary": _compact_text(article.get("summary") or article.get("content")),
            "category_name": FOCUSED_CATEGORY_NAME,
            "tags": tags,
            "document_type": payload.get("knowledgeType") or "司法解释",
            "document_subtype": payload.get("documentLevel") or "",
            "case_type": "刑事",
            "cause": payload.get("topic") or "",
            "business_domain": payload.get("businessDomain") or "刑事",
            "procedure_domain": "",
            "court_name": payload.get("issuer") or "",
            "court_level": "最高人民法院" if "最高人民法院" in str(payload.get("issuer") or "") else "",
            "decision_date": payload.get("effectiveDate") or "",
            "decision_result": "",
            "keywords": tags,
            "aliases": "",
            "issue_tags": tags,
            "result_tags": "",
            "source_reliability": payload.get("sourceReliability") or 1,
            "source": "official_topic_corpus",
            "section_type": "law",
            "section_title": article.get("articleNumber") or "",
            "retrieval_role": "law_basis",
            "section_weight": 1.35,
            "knowledge_type": "law_basis",
            "authority_weight": 1.32,
            "content": content,
            "chunk_text": _compact_text(article.get("content")),
            "text_length": len(content),
            "drug_related": True,
        }
        documents.append({"content": content, "metadata": metadata})
    return documents


def _build_case_documents(payload: Dict, source_key: str) -> List[Dict]:
    documents: List[Dict] = []
    for index, entry in enumerate(payload.get("entries") or [], start=1):
        if not is_drug_related_record(entry):
            continue

        content = _join_lines([entry.get("articleTitle"), entry.get("summary"), entry.get("content")])
        if not content:
            continue

        title = _compact_text(entry.get("articleTitle") or entry.get("documentName") or payload.get("documentName"))
        tags = _entry_tags(payload, entry)
        chunk_id = f"official-{source_key}-entry-{index}"
        metadata = {
            "item_id": chunk_id,
            "document_id": source_key,
            "chunk_id": chunk_id,
            "chunk_index": index - 1,
            "title": title,
            "summary": _compact_text(entry.get("summary")),
            "category_name": FOCUSED_CATEGORY_NAME,
            "tags": tags,
            "document_type": entry.get("knowledgeType") or payload.get("knowledgeType") or "典型案例",
            "document_subtype": entry.get("knowledgeSubtype") or payload.get("knowledgeSubtype") or "",
            "case_type": "刑事",
            "cause": entry.get("topic") or payload.get("topic") or "",
            "business_domain": entry.get("businessDomain") or payload.get("businessDomain") or "刑事",
            "procedure_domain": "",
            "court_name": entry.get("issuer") or payload.get("issuer") or "",
            "court_level": "最高人民法院" if "最高人民法院" in str(entry.get("issuer") or payload.get("issuer") or "") else "",
            "decision_date": entry.get("effectiveDate") or payload.get("effectiveDate") or "",
            "decision_result": "",
            "keywords": tags,
            "aliases": ",".join(entry.get("aliases") or []),
            "issue_tags": tags,
            "result_tags": "",
            "source_reliability": entry.get("sourceReliability") or payload.get("sourceReliability") or 1,
            "source": "official_topic_corpus",
            "section_type": "case",
            "section_title": entry.get("articleNumber") or "",
            "retrieval_role": "case_reference",
            "section_weight": 1.26,
            "knowledge_type": "case_reference",
            "authority_weight": 1.28,
            "content": content,
            "chunk_text": _compact_text(entry.get("content") or entry.get("summary")),
            "text_length": len(content),
            "drug_related": True,
        }
        documents.append({"content": content, "metadata": metadata})
    return documents


def build_curated_official_documents() -> List[Dict]:
    documents: List[Dict] = []
    seen_contents = set()

    for group, filename in FOCUSED_SOURCE_FILES:
        file_path = _processed_root() / group / filename
        if not file_path.exists():
            continue

        payload = json.loads(file_path.read_text(encoding="utf-8"))
        source_key = f"{group}-{file_path.stem}"
        file_documents = _build_article_documents(payload, source_key) if payload.get("articles") else _build_case_documents(payload, source_key)

        for document in file_documents:
            content = document.get("content", "")
            if content in seen_contents:
                continue
            seen_contents.add(content)
            documents.append(document)

    return documents
