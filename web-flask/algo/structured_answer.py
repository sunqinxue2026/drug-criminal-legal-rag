"""
Helpers for converting free-text legal answers into stable sectioned payloads.
"""
from __future__ import annotations

from difflib import SequenceMatcher
import re
from typing import Dict, Iterable, Tuple


SECTION_LABELS = {
    "conclusion": ["初步结论", "结论"],
    "basis": ["法律依据", "法条依据"],
    "risk": ["风险提示", "注意事项"],
    "advice": ["行动建议", "维权建议", "下一步建议"],
    "followUp": ["律师追问", "补充追问", "进一步追问"],
}

BOUNDARY_LABELS = [label for labels in SECTION_LABELS.values() for label in labels]
MAX_FOLLOW_UP_ROUNDS = 2
FINAL_OUTCOME_TERMS = (
    "最终结果",
    "最终审判结果",
    "审判结果",
    "判决结果",
    "裁判结果",
    "处理结果",
    "结案结果",
    "最终结论",
    "最后结论",
    "最终的结论",
    "最终得出的结论",
    "告诉我最终",
    "直接告诉我结论",
    "会怎么判",
    "大概怎么判",
    "最后怎么判",
    "能判多久",
    "判多久",
    "是否构成犯罪",
    "构成犯罪吗",
    "不构成犯罪",
    "定罪结果",
)
FOLLOW_UP_HELP_TERMS = ("还需要问", "还要问", "需要补充", "需要提供", "还缺什么", "问完")
CASE_CONSULTATION_TERMS = (
    "案例",
    "判例",
    "类案",
    "同类案件",
    "裁判文书",
    "判决书",
    "裁定书",
    "判决结果",
    "裁判结果",
    "审判结果",
    "量刑结果",
)


def build_structured_answer(answer: str) -> Dict[str, str]:
    text = _normalize_boundaries(answer)
    sections = {
        "conclusion": "",
        "basis": "",
        "risk": "",
        "advice": "",
        "followUp": "",
    }
    if not text:
        return sections

    current_section = "conclusion"
    explicit_section_found = False
    for raw_line in text.replace("\r\n", "\n").split("\n"):
        line = _sanitize_line(raw_line)
        if not line:
            continue

        matched_section, content = _match_section(line)
        if matched_section:
            explicit_section_found = True
            current_section = matched_section
            if content:
                sections[current_section] = _append_line(sections[current_section], content)
            continue
        sections[current_section] = _append_line(sections[current_section], line)

    if not explicit_section_found:
        sections["conclusion"] = text.strip()
    elif not sections["conclusion"].strip():
        sections["conclusion"] = _sanitize_line(answer)

    normalized = {key: value.strip() for key, value in sections.items()}
    normalized["followUp"] = _normalize_follow_up(normalized.get("followUp", ""))
    return normalized


def optimize_lawyer_follow_up(answer: str,
                              current_query: str = "",
                              conversation_history: Iterable[Dict] | None = None,
                              context_notes: Iterable[str] | None = None) -> str:
    structured = build_structured_answer(answer)
    current_follow_up = structured.get("followUp", "").strip()
    if not current_follow_up:
        return answer

    optimized = _select_follow_up(
        current_follow_up,
        current_query=current_query,
        conversation_history=conversation_history,
        context_notes=context_notes,
    )
    if optimized == current_follow_up:
        return answer

    structured["followUp"] = optimized or ""
    return _compose_structured_answer(structured)


def _normalize_boundaries(answer: str) -> str:
    text = (answer or "").replace("\r\n", "\n").strip()
    if not text:
        return ""
    pattern = r"(?<!\n)(" + "|".join(re.escape(label) for label in BOUNDARY_LABELS) + r")(?:：|:)"
    return re.sub(pattern, lambda match: "\n" + match.group(1) + "：", text)


def _sanitize_line(value: str) -> str:
    result = (value or "").strip()
    previous = None
    while result and result != previous:
        previous = result
        result = re.sub(r"^```[\w-]*\s*", "", result)
        result = re.sub(r"^>+\s*", "", result)
        result = re.sub(r"^#{1,6}\s*", "", result)
        result = re.sub(r"^(?:[\(\[]?\d+[\)\.、])\s*", "", result)
        result = result.strip()
    return result


def _match_section(line: str) -> Tuple[str, str]:
    for section, labels in SECTION_LABELS.items():
        for label in labels:
            for separator in ("：", ":"):
                prefix = f"{label}{separator}"
                if line.startswith(prefix):
                    return section, line[len(prefix):].strip()
    return "", ""


def _append_line(current: str, incoming: str) -> str:
    current = (current or "").strip()
    incoming = (incoming or "").strip()
    if not current:
        return incoming
    if not incoming:
        return current
    return f"{current}\n{incoming}"


def _normalize_follow_up(value: str) -> str:
    text = re.sub(r"\s+", " ", _sanitize_line(value)).strip()
    if not text:
        return ""

    cut_points = [index for index in (
        text.find("？"),
        text.find("?"),
        text.find("。"),
        text.find("！"),
        text.find("!"),
        text.find("；"),
        text.find(";"),
    ) if index >= 0]

    if not cut_points:
        return text
    return text[:min(cut_points) + 1].strip()


def _compose_structured_answer(sections: Dict[str, str]) -> str:
    ordered_sections = [
        ("初步结论", sections.get("conclusion", "")),
        ("法律依据", sections.get("basis", "")),
        ("风险提示", sections.get("risk", "")),
        ("行动建议", sections.get("advice", "")),
        ("律师追问", sections.get("followUp", "")),
    ]
    return "\n".join(
        f"{label}：{value.strip()}"
        for label, value in ordered_sections
        if str(value or "").strip()
    ).strip()


def _select_follow_up(current_follow_up: str,
                      current_query: str = "",
                      conversation_history: Iterable[Dict] | None = None,
                      context_notes: Iterable[str] | None = None) -> str:
    if _is_final_outcome_request(current_query):
        return ""
    if _is_case_consultation_request(current_query):
        return ""

    normalized_current = _normalize_for_compare(current_follow_up)
    recent_follow_ups = [
        _normalize_follow_up(build_structured_answer(str(item.get("content") or "")).get("followUp", ""))
        for item in conversation_history or []
        if isinstance(item, dict) and str(item.get("role") or "").strip().lower() == "assistant"
    ]
    normalized_recent = {_normalize_for_compare(value) for value in recent_follow_ups if value}
    if len([value for value in recent_follow_ups if value]) >= MAX_FOLLOW_UP_ROUNDS and not _is_follow_up_help_request(current_query):
        return ""

    combined_text = " ".join(
        filter(
            None,
            [
                str(current_query or ""),
                " ".join(
                    str(item.get("content") or "")
                    for item in conversation_history or []
                    if isinstance(item, dict)
                ),
                " ".join(str(note or "") for note in context_notes or []),
            ],
        )
    )
    compact_context = re.sub(r"\s+", "", combined_text)
    current_is_short_reply = _is_short_direct_reply(current_query)

    if not (
        _is_duplicate_or_stalled_follow_up(current_follow_up, normalized_current, recent_follow_ups, current_is_short_reply)
        or _follow_up_conflicts_with_known_facts(current_follow_up, compact_context)
    ):
        return current_follow_up

    for candidate in _build_follow_up_candidates(compact_context):
        normalized_candidate = _normalize_for_compare(candidate)
        if not normalized_candidate or normalized_candidate in normalized_recent:
            continue
        if normalized_candidate == normalized_current:
            continue
        if _is_similar_follow_up(candidate, recent_follow_ups):
            continue
        return candidate

    return ""


def _build_follow_up_candidates(compact_context: str) -> Tuple[str, ...]:
    candidates = []

    if "转账" in compact_context or "聊天记录" in compact_context:
        candidates.append("手机里的可疑转账和聊天记录，能否对应到具体对象、时间和交易内容？")
    if "尿检" in compact_context or "毛发" in compact_context or "吸毒史" in compact_context:
        candidates.append("本次到案后是否已经做过尿检或毛发检测，结果是什么？")
    if "电子秤" in compact_context or "分装袋" in compact_context or "出租屋" in compact_context:
        candidates.append("电子秤、分装袋和疑似毒品实物，平时由谁实际保管、谁能接触到？")
    if "查获" in compact_context or "冰毒" in compact_context or "毒品" in compact_context:
        candidates.append("现场查获物是否已经完成称重、鉴定并形成扣押清单？")
    if "出租屋" in compact_context:
        candidates.append("案发时的出租屋，是否还有其他人共同居住或能够接触这些物品？")

    candidates.append("手机里的聊天记录和转账记录，能否对应到具体对象和具体内容？")
    return tuple(dict.fromkeys(candidates))


def _follow_up_conflicts_with_known_facts(follow_up: str, compact_context: str) -> bool:
    if not follow_up:
        return False
    if re.search(r"(强制措施|被拘留|被逮捕|诉讼阶段)", follow_up) and re.search(r"(拘留通知|被拘留|侦查阶段|带走)", compact_context):
        return True
    if re.search(r"(本人还是家属|你的身份|您是本人)", follow_up) and "家属" in compact_context:
        return True
    if re.search(r"(是否出售过毒品|贩卖意图|联系或交易毒品|实际交易行为)", follow_up) and "没有" in compact_context:
        return True
    return False


def _is_duplicate_or_stalled_follow_up(current_follow_up: str,
                                       normalized_current: str,
                                       recent_follow_ups: Iterable[str],
                                       current_is_short_reply: bool) -> bool:
    if not normalized_current:
        return False
    if _is_similar_follow_up(current_follow_up, recent_follow_ups):
        return True
    if current_is_short_reply:
        return True
    return False


def _is_short_direct_reply(value: str) -> bool:
    text = re.sub(r"\s+", "", str(value or "")).strip()
    if not text:
        return False
    return len(text) <= 18 or bool(
        re.fullmatch(r"(有|没有|无|不是|是|对|不对|不清楚|不知道|还没有|未|没有过|没卖过|没做过|不是这样|没有这个情况)[，。,。！？!?；;]*", text)
    )


def _normalize_for_compare(value: str) -> str:
    return re.sub(r"[\s，。,。！？!?；;：:、\"'“”‘’（）()\[\]<>《》]+", "", str(value or "")).strip()


def _is_final_outcome_request(value: str) -> bool:
    text = re.sub(r"\s+", "", str(value or "")).strip()
    if not text:
        return False
    if any(term in text for term in FINAL_OUTCOME_TERMS):
        return True
    return bool(re.search(r"(最终|最后).{0,8}(结果|结论|怎么判|判多久)", text))


def _is_follow_up_help_request(value: str) -> bool:
    text = re.sub(r"\s+", "", str(value or "")).strip()
    return any(term in text for term in FOLLOW_UP_HELP_TERMS)


def _is_case_consultation_request(value: str) -> bool:
    text = re.sub(r"\s+", "", str(value or "")).strip()
    if not text:
        return False
    if any(term in text for term in CASE_CONSULTATION_TERMS):
        return True
    if re.search(r"(某|被告人|上诉人|原审被告人).{0,18}(贩卖毒品|运输毒品|制造毒品|走私毒品|非法持有毒品|容留他人吸毒).{0,6}案", text):
        return True
    if re.fullmatch(r"[\u4e00-\u9fa5]{1,8}(贩卖毒品|运输毒品|制造毒品|走私毒品|非法持有毒品|容留他人吸毒)案", text):
        return True
    return False


def _is_similar_follow_up(value: str, recent_follow_ups: Iterable[str]) -> bool:
    normalized_value = _normalize_for_compare(value)
    if not normalized_value:
        return False

    for recent in recent_follow_ups or []:
        normalized_recent = _normalize_for_compare(recent)
        if not normalized_recent:
            continue
        if normalized_value == normalized_recent:
            return True
        if normalized_value in normalized_recent or normalized_recent in normalized_value:
            return True
        if SequenceMatcher(None, normalized_value, normalized_recent).ratio() >= 0.72:
            return True
        if _same_fact_cluster(normalized_value, normalized_recent):
            return True
    return False


def _same_fact_cluster(first: str, second: str) -> bool:
    clusters = (
        ("转账", "聊天记录", "对象", "交易", "内容"),
        ("尿检", "毛发", "检测", "吸毒史"),
        ("电子秤", "分装袋", "保管", "接触"),
        ("查获", "称重", "鉴定", "扣押"),
        ("出租屋", "共同居住", "接触", "物品"),
    )
    for cluster in clusters:
        first_hits = sum(1 for term in cluster if term in first)
        second_hits = sum(1 for term in cluster if term in second)
        if first_hits >= 2 and second_hits >= 2:
            return True
    return False
