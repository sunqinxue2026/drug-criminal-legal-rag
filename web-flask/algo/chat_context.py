"""
Helpers for multi-turn legal consultation context building.
"""
from __future__ import annotations

import re
from typing import Dict, Iterable, List


LEGAL_DISCLAIMER = (
    "本系统回答仅用于涉毒刑事普法与风险提示，不构成正式法律意见或律师代理意见；"
    "如已被采取强制措施、即将讯问或准备签署文书，请尽快联系执业律师。"
)

PRIVACY_NOTICE = (
    "上传图片、聊天记录和文书前，请尽量隐去身份证号、住址、电话、银行卡号等敏感信息。"
)


def compact_text(value: object, limit: int | None = None) -> str:
    text = str(value or "").replace("\r", "\n")
    text = re.sub(r"\s+", " ", text).strip()
    if limit and len(text) > limit:
        return f"{text[:limit].rstrip()}..."
    return text


def normalize_history_messages(history: Iterable[Dict] | None, limit: int = 6) -> List[Dict[str, str]]:
    normalized: List[Dict[str, str]] = []
    for message in list(history or [])[-limit:]:
        if not isinstance(message, dict):
            continue
        role = "assistant" if str(message.get("role") or "").strip().lower() == "assistant" else "user"
        content = compact_text(message.get("content"), limit=500)
        image_url = compact_text(message.get("imageUrl") or message.get("image_url"))
        image_name = compact_text(message.get("imageName") or message.get("image_name"), limit=120)
        if not content and not image_url:
            continue
        normalized.append(
            {
                "role": role,
                "content": content,
                "image_url": image_url,
                "image_name": image_name,
            }
        )
    return normalized


def normalize_case_attachments(items: Iterable[Dict] | None) -> List[Dict[str, str]]:
    normalized: List[Dict[str, str]] = []
    for item in items or []:
        if not isinstance(item, dict):
            continue
        title = compact_text(item.get("title"), limit=180)
        summary = compact_text(item.get("summary"), limit=220)
        if not title and not summary:
            continue
        normalized.append(
            {
                "id": str(item.get("id") or "").strip(),
                "title": title,
                "summary": summary,
            }
        )
    return normalized


def normalize_document_attachments(items: Iterable[Dict] | None) -> List[Dict[str, str]]:
    normalized: List[Dict[str, str]] = []
    for item in items or []:
        if not isinstance(item, dict):
            continue
        name = compact_text(item.get("name"), limit=160)
        extracted_text = compact_text(item.get("extractedText") or item.get("extracted_text"), limit=320)
        preview = compact_text(item.get("preview"), limit=200)
        status_label = compact_text(item.get("statusLabel") or item.get("status_label"), limit=40)
        if not name and not extracted_text and not preview:
            continue
        normalized.append(
            {
                "name": name,
                "url": compact_text(item.get("url"), limit=260),
                "extracted_text": extracted_text,
                "preview": preview,
                "char_count": str(item.get("charCount") or item.get("char_count") or "").strip(),
                "status_label": status_label,
            }
        )
    return normalized


def build_history_digest(history: Iterable[Dict] | None) -> str:
    lines: List[str] = []
    for index, message in enumerate(history or [], start=1):
        role = "用户" if message.get("role") == "user" else "助手"
        content = compact_text(message.get("content"), limit=180)
        parts = [f"第{index}条{role}消息：{content or '见附件/图片'}"]
        if message.get("image_name"):
            parts.append(f"关联图片：{message['image_name']}")
        lines.append("；".join(part for part in parts if part))
    return "\n".join(lines)


def build_attachment_notes(attached_cases: Iterable[Dict] | None, attached_documents: Iterable[Dict] | None) -> List[str]:
    notes: List[str] = []
    cases = list(attached_cases or [])
    documents = list(attached_documents or [])

    if cases:
        notes.append("用户附带了补充案例材料：")
        notes.extend(
            f"案例{index}：标题：{item.get('title') or '未命名案例'}；摘要：{item.get('summary') or '暂无摘要'}"
            for index, item in enumerate(cases, start=1)
        )

    if documents:
        notes.append("用户附带了补充文书材料：")
        for index, item in enumerate(documents, start=1):
            body = item.get("extracted_text") or item.get("preview") or item.get("url") or "暂无正文"
            char_count = item.get("char_count")
            extra = f"；正文长度：{char_count}字" if char_count else ""
            notes.append(
                f"文书{index}：文件名：{item.get('name') or '未命名文书'}；状态：{item.get('status_label') or '已上传'}{extra}；内容：{body}"
            )
    return notes


def build_retrieval_question(question: str,
                             history: Iterable[Dict] | None = None,
                             attachment_notes: Iterable[str] | None = None,
                             image_context: str = "") -> str:
    blocks: List[str] = []
    history_digest = build_history_digest(history)
    if history_digest:
        blocks.append(f"【最近会话背景】\n{history_digest}")
    attachment_text = "\n".join(compact_text(note, limit=260) for note in attachment_notes or [] if compact_text(note))
    if attachment_text:
        blocks.append(f"【补充材料】\n{attachment_text}")
    image_text = compact_text(image_context, limit=320)
    if image_text:
        blocks.append(f"【图片识别结果】\n{image_text}")
    blocks.append(f"【当前问题】\n{compact_text(question, limit=1000)}")
    return "\n\n".join(block for block in blocks if block)


def summarize_context_usage(history: Iterable[Dict] | None,
                            attached_cases: Iterable[Dict] | None,
                            attached_documents: Iterable[Dict] | None,
                            image_context: str = "") -> Dict[str, object]:
    return {
        "history_turns_used": len(list(history or [])),
        "attached_case_count": len(list(attached_cases or [])),
        "attached_document_count": len(list(attached_documents or [])),
        "image_context_used": bool(compact_text(image_context)),
    }
