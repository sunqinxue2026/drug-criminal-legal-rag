"""
Profile-specific helpers for vertical legal consultation scenarios.
"""
from __future__ import annotations

from typing import Dict, Iterable, List


DEFAULT_PROFILE = "drug_crime"
ALL_PROFILE = "all"

DRUG_CORE_TERMS = [
    "毒品",
    "涉毒",
    "贩卖毒品",
    "运输毒品",
    "制造毒品",
    "走私毒品",
    "非法持有毒品",
    "容留他人吸毒",
    "引诱他人吸毒",
    "强迫他人吸毒",
    "麻精药品",
    "麻醉药品",
    "精神药品",
    "禁毒",
    "吸毒",
    "海洛因",
    "冰毒",
    "甲基苯丙胺",
    "可卡因",
    "大麻",
    "依托咪酯",
    "笑气",
    "曲马多",
    "毒品犯罪",
]

DRUG_PROCEDURE_TERMS = [
    "刑事拘留",
    "拘留",
    "逮捕",
    "取保候审",
    "监视居住",
    "辩护人",
    "律师",
    "会见",
    "家属",
    "通知家属",
    "认罪认罚",
    "立功",
    "自首",
    "审查起诉",
    "起诉",
    "判刑",
    "量刑",
    "主犯",
    "从犯",
]

DRUG_SUPPORT_TITLES = [
    "中华人民共和国禁毒法",
    "中华人民共和国刑法",
    "中华人民共和国刑事诉讼法",
    "涉麻精药品犯罪典型案例",
]


def normalize_profile(profile: str | None) -> str:
    value = (profile or "").strip().lower()
    return value or DEFAULT_PROFILE


def build_index_name(profile: str | None) -> str:
    normalized = normalize_profile(profile)
    return "legal_cases" if normalized == ALL_PROFILE else f"legal_cases_{normalized}"


def _join_fields(values: Iterable[object]) -> str:
    return "\n".join(str(value or "") for value in values)


def _contains_any(text: str, terms: Iterable[str]) -> bool:
    return any(term and term in text for term in terms)


def is_drug_profile_chunk(chunk: Dict) -> bool:
    title = chunk.get("title") or chunk.get("documentName") or ""
    summary = chunk.get("summary") or ""
    tags = chunk.get("tags") or ""
    keywords = chunk.get("keywords") or ""
    aliases = chunk.get("aliases") or ""
    business_domain = chunk.get("businessDomain") or chunk.get("business_domain") or ""
    procedure_domain = chunk.get("procedureDomain") or chunk.get("procedure_domain") or ""
    document_type = chunk.get("documentType") or chunk.get("document_type") or ""
    document_subtype = chunk.get("documentSubtype") or chunk.get("document_subtype") or ""
    content = chunk.get("retrievalText") or chunk.get("content") or chunk.get("chunkText") or ""
    is_case_material = "案例" in document_type or "案例" in document_subtype

    aggregate = _join_fields([
        title,
        summary,
        tags,
        keywords,
        aliases,
        business_domain,
        procedure_domain,
        document_type,
        document_subtype,
        content[:2400],
    ])
    scope_text = _join_fields([
        title,
        tags,
        keywords,
        aliases,
        business_domain,
        procedure_domain,
        document_type,
        document_subtype,
    ])

    if is_case_material:
        return _contains_any(scope_text, DRUG_CORE_TERMS)

    if _contains_any(aggregate, DRUG_CORE_TERMS):
        return True

    if any(name in title for name in DRUG_SUPPORT_TITLES) and _contains_any(aggregate, DRUG_PROCEDURE_TERMS):
        return True

    return "刑事" in business_domain and _contains_any(aggregate, DRUG_PROCEDURE_TERMS) and _contains_any(aggregate, DRUG_CORE_TERMS)


def filter_chunks_for_profile(chunks: List[Dict], profile: str | None) -> List[Dict]:
    normalized = normalize_profile(profile)
    if normalized == ALL_PROFILE:
        return list(chunks)
    if normalized == DEFAULT_PROFILE:
        return [chunk for chunk in chunks if is_drug_profile_chunk(chunk)]
    return list(chunks)


def profile_scope_statement(profile: str | None) -> str:
    if normalize_profile(profile) == DEFAULT_PROFILE:
        return "当前系统聚焦涉毒刑事法律风险分析、程序权利和家属应对场景。"
    return "当前系统提供通用法律问答服务。"
