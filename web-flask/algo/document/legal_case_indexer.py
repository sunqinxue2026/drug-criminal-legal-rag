"""
Helpers for turning Spring Boot legal cases into RAG-ready chunks.
"""
from __future__ import annotations

import logging
import re
from typing import Dict, List

logger = logging.getLogger(__name__)

DEFAULT_CHUNK_SIZE = 500
DEFAULT_CHUNK_OVERLAP = 100


def _clean_text(text: str) -> str:
    normalized = (text or "").replace("\r", "\n")
    normalized = re.sub(r"\n{3,}", "\n\n", normalized)
    normalized = re.sub(r"[ \t]+", " ", normalized)
    return normalized.strip()


def _derive_knowledge_type(chunk: Dict) -> str:
    retrieval_role = (chunk.get("retrievalRole") or "").strip()
    document_type = (chunk.get("documentType") or "").strip()
    document_subtype = (chunk.get("documentSubtype") or "").strip()
    if "指导性案例" in document_type or "指导性案例" in document_subtype:
        return "guiding_case"
    if "案例" in document_type or "案例" in document_subtype:
        return "case_reference"
    return {
        "law_basis": "law_basis",
        "court_reasoning": "case_reasoning",
        "decision_result": "case_result",
        "case_overview": "case_overview",
        "trial_process": "trial_process",
        "issue_focus": "issue_focus",
        "facts": "case_facts",
    }.get(retrieval_role, "body")


def _derive_authority_weight(chunk: Dict) -> float:
    court_level = (chunk.get("courtLevel") or "").strip()
    source_reliability = chunk.get("sourceReliability")
    document_type = (chunk.get("documentType") or "").strip()
    court_name = (chunk.get("courtName") or "").strip()

    if "最高" in court_level:
        base = 1.22
    elif "高级" in court_level:
        base = 1.14
    elif "中级" in court_level:
        base = 1.08
    else:
        base = 1.0

    if source_reliability:
        base += 0.03
    if "指导性案例" in document_type:
        base += 0.08
    elif "案例" in document_type:
        base += 0.04
    if "最高人民法院" in court_name:
        base += 0.05
    return round(base, 2)


def chunk_text(text: str, chunk_size: int = DEFAULT_CHUNK_SIZE, overlap: int = DEFAULT_CHUNK_OVERLAP) -> List[str]:
    cleaned = _clean_text(text)
    if not cleaned:
        return []
    if len(cleaned) <= chunk_size:
        return [cleaned]

    chunks: List[str] = []
    start = 0
    text_length = len(cleaned)

    while start < text_length:
        end = min(text_length, start + chunk_size)
        slice_end = end
        if end < text_length:
            split_candidates = [
                cleaned.rfind("\n\n", start, end),
                cleaned.rfind("\n", start, end),
                cleaned.rfind("。", start, end),
                cleaned.rfind("；", start, end),
                cleaned.rfind("，", start, end),
                cleaned.rfind(" ", start, end),
            ]
            split_at = max([pos for pos in split_candidates if pos > start], default=end)
            if split_at != end:
                slice_end = split_at + (2 if cleaned[split_at:split_at + 2] == "\n\n" else 1)

        chunk = cleaned[start:slice_end].strip()
        if chunk:
            chunks.append(chunk)
        if slice_end >= text_length:
            break
        start = max(slice_end - overlap, start + 1)

    return chunks


def build_index_documents(items: List[Dict], chunk_size: int = DEFAULT_CHUNK_SIZE, overlap: int = DEFAULT_CHUNK_OVERLAP) -> List[Dict]:
    documents: List[Dict] = []
    for item in items:
        item_id = item.get("id")
        title = (item.get("title") or "").strip()
        summary = _clean_text(item.get("summary") or "")
        content = _clean_text(item.get("content") or "")
        if not content:
            continue

        base_metadata = {
            "item_id": item_id,
            "title": title,
            "summary": summary,
            "category_id": item.get("categoryId"),
            "category_name": item.get("categoryName") or "",
            "tags": item.get("tags") or "",
            "source": "case_db",
        }

        for chunk_index, chunk in enumerate(chunk_text(content, chunk_size=chunk_size, overlap=overlap)):
            prefix_parts = [part for part in [title, summary] if part]
            prefixed_content = "\n".join(prefix_parts + [chunk]) if prefix_parts else chunk
            documents.append({
                "content": prefixed_content,
                "metadata": {
                    **base_metadata,
                    "chunk_index": chunk_index,
                    "content": prefixed_content,
                    "chunk_text": chunk,
                    "text_length": len(chunk),
                },
            })

    logger.info("Built %s chunk(s) from %s item(s)", len(documents), len(items))
    return documents


def build_index_documents_from_chunks(chunks: List[Dict]) -> List[Dict]:
    documents: List[Dict] = []
    for chunk in chunks:
        retrieval_text = _clean_text(chunk.get("retrievalText") or "")
        chunk_source = _clean_text(chunk.get("chunkText") or "")
        content = retrieval_text or chunk_source
        if not content:
            continue

        document_type = chunk.get("documentType") or ""
        document_subtype = chunk.get("documentSubtype") or ""
        source = chunk.get("source") or "legal_document_db"
        section_type = chunk.get("sectionType") or ""
        retrieval_role = chunk.get("retrievalRole") or ""
        section_weight = chunk.get("sectionWeight") or 1

        is_case_knowledge = source == "legal_knowledge_db" and ("案例" in document_type or "案例" in document_subtype)
        if is_case_knowledge:
            if section_type in {"", "law"}:
                section_type = "case"
            if retrieval_role in {"", "law_basis"}:
                retrieval_role = "case_reference"
            if float(section_weight or 1) < 1.24:
                section_weight = 1.24

        metadata = {
            "item_id": chunk.get("itemId") or chunk.get("documentId"),
            "document_id": chunk.get("documentId"),
            "chunk_id": chunk.get("chunkId"),
            "chunk_index": chunk.get("chunkIndex", 0),
            "title": (chunk.get("title") or "").strip(),
            "summary": _clean_text(chunk.get("summary") or ""),
            "category_id": chunk.get("categoryId"),
            "category_name": chunk.get("categoryName") or "",
            "tags": chunk.get("tags") or "",
            "document_type": document_type,
            "document_subtype": document_subtype,
            "case_type": chunk.get("caseType") or "",
            "cause": chunk.get("cause") or "",
            "business_domain": chunk.get("businessDomain") or "",
            "procedure_domain": chunk.get("procedureDomain") or "",
            "court_name": chunk.get("courtName") or "",
            "court_level": chunk.get("courtLevel") or "",
            "decision_date": chunk.get("decisionDate") or "",
            "decision_result": chunk.get("decisionResult") or "",
            "keywords": chunk.get("keywords") or "",
            "aliases": chunk.get("aliases") or "",
            "issue_tags": chunk.get("issueTags") or "",
            "result_tags": chunk.get("resultTags") or "",
            "source_reliability": chunk.get("sourceReliability") or 0,
            "source": source,
            "section_type": section_type,
            "section_title": chunk.get("sectionTitle") or "",
            "header_path": chunk.get("headerPath") or "",
            "retrieval_role": retrieval_role,
            "section_weight": section_weight,
            "knowledge_type": _derive_knowledge_type(chunk),
            "authority_weight": _derive_authority_weight(chunk),
            "content": content,
            "chunk_text": chunk_source or content,
            "text_length": len(chunk_source or content),
        }
        documents.append({"content": content, "metadata": metadata})

    logger.info("Built %s index document(s) from %s legal chunk record(s)", len(documents), len(chunks))
    return documents
