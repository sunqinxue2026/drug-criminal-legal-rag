"""
Heuristics for legal query understanding and lightweight reranking.
"""
from __future__ import annotations

import re
from typing import Dict, Iterable, List

from algo.domain_scope import DRUG_RELATED_TERMS, FOCUSED_DOMAIN_NAME, is_drug_related_text


DOMAIN_RULES = [
    {
        "name": "涉毒犯罪认定",
        "terms": [
            "涉毒", "毒品", "贩毒", "贩卖毒品", "运输毒品", "制造毒品", "走私毒品",
            "非法持有毒品", "容留吸毒", "引诱吸毒", "教唆吸毒", "强迫吸毒", "海洛因",
            "冰毒", "甲基苯丙胺", "可卡因", "大麻", "依托咪酯", "笑气", "曲马多",
        ],
        "case_types": ["刑事"],
        "business_domains": ["刑事", "涉毒"],
        "expanded_terms": ["毒品犯罪", "构成要件", "主观明知", "共同犯罪", "司法解释"],
        "preferred_roles": ["law_basis", "case_overview", "court_reasoning", "decision_result"],
    },
    {
        "name": "量刑与从宽处罚",
        "terms": ["量刑", "判多久", "几年", "缓刑", "自首", "立功", "坦白", "从轻", "减轻", "主犯", "从犯"],
        "case_types": ["刑事"],
        "business_domains": ["刑事", "涉毒"],
        "expanded_terms": ["量刑情节", "从宽处罚", "立功认定", "自首坦白"],
        "preferred_roles": ["decision_result", "court_reasoning", "law_basis", "case_overview"],
    },
    {
        "name": "程序与证据",
        "terms": ["立案", "取保", "拘留", "逮捕", "侦查", "起诉", "证据", "聊天记录", "转账记录", "鉴定", "搜查"],
        "case_types": ["刑事"],
        "business_domains": ["刑事", "涉毒"],
        "procedure_domains": ["刑事程序"],
        "expanded_terms": ["证据链条", "程序风险", "电子数据", "毒品实物", "口供印证"],
        "preferred_roles": ["facts", "court_reasoning", "law_basis", "trial_process"],
    },
    {
        "name": "家属与普法应对",
        "terms": ["家属", "律师", "会见", "法律援助", "值班律师", "12309", "风险", "预防", "普法"],
        "case_types": ["刑事"],
        "business_domains": ["刑事", "涉毒", "普法"],
        "procedure_domains": ["家属应对"],
        "expanded_terms": ["程序权利", "家属应对", "法律帮助", "风险提示"],
        "preferred_roles": ["law_basis", "case_overview", "decision_result"],
    },
]

INTENT_RULES = [
    ("case_lookup", ["案例", "判例", "类案", "同类案件", "裁判文书", "判决书", "判决结果", "裁判结果", "审判结果", "量刑结果"]),
    ("next_step", ["怎么办", "如何处理", "下一步", "怎么做"]),
    ("eligibility", ["能不能", "可以吗", "是否合法", "有没有风险"]),
    ("sentencing", ["量刑", "判多久", "缓刑", "从轻", "减轻"]),
    ("procedure", ["立案", "拘留", "逮捕", "取保", "起诉", "再审", "申诉", "侦查"]),
    ("evidence", ["证据", "聊天记录", "转账记录", "鉴定", "搜查", "扣押", "电子数据"]),
]

LAYPERSON_HINTS = ["怎么办", "可以吗", "能不能", "我被", "我想", "我该", "家属", "朋友", "孩子", "要不要"]

UNSAFE_PATTERNS = [
    "怎么制造毒品",
    "如何制造毒品",
    "怎么贩毒",
    "如何贩毒",
    "怎么藏毒",
    "如何藏毒",
    "如何逃避侦查",
    "怎么逃避侦查",
    "如何销毁证据",
    "怎么销毁证据",
]
UNSAFE_REGEX_PATTERNS = [
    re.compile(r"(怎么|如何|怎样).{0,8}(藏|运|卖|买|制).{0,8}(毒品|麻精药品|麻醉药品|精神药品)"),
    re.compile(r"(怎么|如何|怎样).{0,10}(不被发现|逃避侦查|逃避处罚|销毁证据)"),
]
ARTICLE_PATTERN = re.compile(r"第[一二三四五六七八九十百千万零\d]+条")


def _dedupe_keep_order(values: Iterable[str]) -> List[str]:
    seen = set()
    result: List[str] = []
    for value in values:
        normalized = (value or "").strip()
        if not normalized or normalized in seen:
            continue
        seen.add(normalized)
        result.append(normalized)
    return result


def _matched_terms(text: str, terms: Iterable[str]) -> List[str]:
    return [term for term in terms if term and term in text]


def _contains_any(text: str, terms: Iterable[str]) -> bool:
    return any(term and term in text for term in terms)


def _is_unsafe_request(text: str) -> bool:
    if any(pattern in text for pattern in UNSAFE_PATTERNS):
        return True
    return any(pattern.search(text) for pattern in UNSAFE_REGEX_PATTERNS)


def analyze_legal_query(question: str) -> Dict[str, object]:
    text = (question or "").strip()
    lowered = text.lower()

    domains: List[str] = []
    case_types: List[str] = []
    business_domains: List[str] = []
    procedure_domains: List[str] = []
    preferred_roles: List[str] = []
    keywords: List[str] = ARTICLE_PATTERN.findall(text)
    expanded_terms: List[str] = []

    for rule in DOMAIN_RULES:
        matched = _matched_terms(text, rule["terms"])
        if not matched:
            continue
        domains.append(rule["name"])
        case_types.extend(rule.get("case_types", []))
        business_domains.extend(rule.get("business_domains", []))
        procedure_domains.extend(rule.get("procedure_domains", []))
        preferred_roles.extend(rule.get("preferred_roles", []))
        keywords.extend(matched)
        expanded_terms.extend(rule.get("expanded_terms", []))

    if is_drug_related_text(text) and not domains:
        domains.append("涉毒犯罪认定")
        case_types.append("刑事")
        business_domains.extend(["刑事", "涉毒"])
        preferred_roles.extend(["law_basis", "case_overview", "court_reasoning"])
        expanded_terms.extend(["毒品犯罪", "法律风险", "司法解释"])

    intents: List[str] = []
    for intent_name, terms in INTENT_RULES:
        matched = _matched_terms(text, terms)
        if matched:
            intents.append(intent_name)
            keywords.extend(matched)

    if "家属" in text and any(term in text for term in ["拘留", "逮捕", "律师", "会见", "取保"]):
        domains.append("家属与普法应对")
        business_domains.extend(["涉毒程序应对", "普法"])
        procedure_domains.append("家属应对")
        preferred_roles.extend(["law_basis", "trial_process", "case_overview"])
        expanded_terms.extend(["家属应对", "律师会见", "法律援助", "12309"])

    if "聊天记录" in text or "转账记录" in text or "电子数据" in text:
        domains.append("程序与证据")
        business_domains.extend(["涉毒证据规则", "刑事"])
        procedure_domains.append("证据审查")
        preferred_roles.extend(["court_reasoning", "law_basis", "facts"])
        expanded_terms.extend(["电子数据", "转账记录", "聊天记录", "证据合法性"])

    layperson_friendly = _contains_any(text, LAYPERSON_HINTS) or "?" in lowered or "？" in text
    answer_style = "layperson_practical" if layperson_friendly else "legal_analysis"
    unsafe_request = _is_unsafe_request(text)

    normalized_domains = _dedupe_keep_order(domains)
    normalized_case_types = _dedupe_keep_order(case_types)
    normalized_business_domains = _dedupe_keep_order(business_domains)
    normalized_procedure_domains = _dedupe_keep_order(procedure_domains)
    normalized_keywords = _dedupe_keep_order(keywords)
    normalized_expanded_terms = _dedupe_keep_order(expanded_terms)
    normalized_roles = _dedupe_keep_order(preferred_roles or ["case_overview", "court_reasoning", "law_basis", "decision_result"])

    rewrite_parts = [text, f"专题范围：{FOCUSED_DOMAIN_NAME}"]
    if normalized_domains:
        rewrite_parts.append("法律领域：" + "、".join(normalized_domains))
    if normalized_expanded_terms:
        rewrite_parts.append("检索扩展：" + "、".join(normalized_expanded_terms[:8]))

    return {
        "question": text,
        "domains": normalized_domains,
        "case_types": normalized_case_types,
        "business_domains": normalized_business_domains,
        "procedure_domains": normalized_procedure_domains,
        "keywords": normalized_keywords[:12],
        "expanded_terms": normalized_expanded_terms[:12],
        "preferred_roles": normalized_roles,
        "intents": _dedupe_keep_order(intents),
        "layperson_friendly": layperson_friendly,
        "answer_style": answer_style,
        "rewritten_query": "\n".join(_dedupe_keep_order(rewrite_parts)),
        "unsafe_request": unsafe_request,
        "unsafe_reason": "drug_operation_or_evasion" if unsafe_request else "",
        "active_profile": "drug_crime" if is_drug_related_text(text) else "",
    }


def build_answer_prompt(analysis: Dict[str, object]) -> str:
    base = (
        "你是一名谨慎、专业、具备合格律师沟通素养的涉毒刑事法律咨询助手。"
        "请像律师接待咨询一样，先直接回答用户最关心的问题，再说明依据、风险和下一步。"
        "请严格基于已提供的资料和检索结果作答，不要编造法条、案例、事实、证据或结论。"
        "如果事实不足，请明确区分哪些是已知事实、哪些是待核实事实、哪些目前无法判断。"
        "不要承诺无罪、轻判、取保成功或其他确定性结果，不要使用过度绝对的措辞。"
        "坚持普法和风险预警导向，不提供实施违法犯罪、规避侦查或销毁证据的方法。"
        "请使用自然中文段落，不要输出 Markdown 表格、任务清单、emoji 或分隔线。"
        "请按四到五段输出，前四段分别以“初步结论：”“法律依据：”“风险提示：”“行动建议：”开头。"
        "其中“初步结论”部分必须直接回应用户核心问题，不能只写标题、空话或模糊套话。"
        "用户要求最终结论、审判结果、判决结果、是否构成犯罪或大概怎么判时，"
        "必须给出基于现有事实的倾向性判断、可能结果区间和不确定边界，不能用追问代替结论。"
        "“法律依据”部分应说明对应法条、司法解释或裁判规则，但不要杜撰条文编号。"
        "“风险提示”部分应写明定性、证据、程序或量刑方面的主要风险与不确定性。"
        "“行动建议”部分应给出当前阶段合法、可执行的下一步。"
        "“律师追问”是可选段，只有缺少一个会实质改变结论的关键事实时才输出。"
        "如果输出律师追问，必须只问一句，并且只能问一个最关键、最影响法律判断的问题。"
        "这句追问要像律师核实事实一样专业、具体，优先围绕身份、时间、行为经过、证据、程序阶段或当前目标发问，且不得重复已问或已答的内容。"
        "不要使用“还有别的信息吗”“请详细说说”这类空泛追问，也不要把两个问题并成一句。"
    )
    if analysis.get("answer_style") == "layperson_practical":
        prompt = base + "面向普通用户时，请尽量使用通俗中文；必须使用法律术语时，用一句话解释清楚。"
    else:
        prompt = base + "在保持专业性的同时，请把论证写清楚，并明确说明结论成立所依赖的关键事实。"

    if "case_lookup" in (analysis.get("intents") or []):
        prompt += (
            "如果用户是在咨询具体案例、判例、裁判文书或判决结果，请把回答重点放在案情摘要、裁判结果、裁判理由、量刑或处罚结果和参考意义；"
            "不要输出“律师追问”，也不要向用户追问现场查获、称重鉴定、聊天记录等个案事实。"
        )
    return prompt


def build_refusal_answer(_analysis: Dict[str, object]) -> str:
    return (
        "初步结论：这个问题涉及实施违法犯罪、规避侦查或者销毁证据的操作性内容，我不能提供这类帮助。\n"
        "法律依据：这类请求超出合法普法和法律风险提示的边界，继续提供会直接增加现实违法风险。\n"
        "风险提示：如果相关行为已经发生，后续处理不当可能进一步扩大刑事责任、程序风险或证据风险。\n"
        "行动建议：如果你真正关心的是已经发生的案情后果、家属现在能做什么、是否会被追责，"
        "我可以改为从合法合规角度继续帮你分析。\n"
        "律师追问：你现在是已经接到公安机关通知，还是只是想先了解这类行为可能带来的法律后果？"
    )


def rerank_candidates(question: str, candidates: List[Dict], analysis: Dict[str, object], top_k: int) -> List[Dict]:
    preferred_roles = set(analysis.get("preferred_roles") or [])
    search_terms = _dedupe_keep_order(
        [question]
        + list(analysis.get("keywords") or [])
        + list(analysis.get("expanded_terms") or [])
        + list(analysis.get("domains") or [])
        + list(DRUG_RELATED_TERMS[:8])
    )

    reranked: List[Dict] = []
    for candidate in candidates:
        title = str(candidate.get("title") or "")
        summary = str(candidate.get("summary") or "")
        tags = str(candidate.get("tags") or "")
        content = str(candidate.get("content") or "")
        aggregate = "\n".join([
            title,
            summary,
            tags,
            str(candidate.get("keywords") or ""),
            str(candidate.get("aliases") or ""),
            str(candidate.get("business_domain") or ""),
            str(candidate.get("procedure_domain") or ""),
            content[:1200],
        ])

        title_hits = sum(1 for term in search_terms if len(term) >= 2 and term in title)
        body_hits = sum(1 for term in search_terms if len(term) >= 2 and term in aggregate)
        lexical_score = min(1.0, body_hits / max(1, min(len(search_terms), 8)))
        title_score = min(1.0, title_hits / 3.0)
        role_score = 1.0 if candidate.get("retrieval_role") in preferred_roles else 0.2
        vector_score = max(float(candidate.get("score", candidate.get("distance", 0.0)) or 0.0), 0.0)
        lexical_recall_score = min(1.0, max(0.0, float(candidate.get("lexical_recall_score", 0.0) or 0.0)))
        domain_score = 0.15 if is_drug_related_text(aggregate) else -0.15

        hybrid_score = (
            vector_score * 0.55
            + lexical_score * 0.18
            + lexical_recall_score * 0.10
            + title_score * 0.10
            + role_score * 0.12
            + domain_score
        )

        updated = dict(candidate)
        updated["hybrid_score"] = round(hybrid_score, 6)
        updated["lexical_score"] = round(lexical_score, 6)
        updated["title_score"] = round(title_score, 6)
        updated["role_score"] = round(role_score, 6)
        updated["domain_score"] = round(domain_score, 6)
        reranked.append(updated)

    reranked.sort(
        key=lambda item: (item.get("hybrid_score", 0.0), item.get("score", item.get("distance", 0.0))),
        reverse=True,
    )
    return reranked[:top_k]
