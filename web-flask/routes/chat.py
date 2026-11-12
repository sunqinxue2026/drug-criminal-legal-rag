"""
Chat and semantic search routes backed by the local RAG engine.
"""
from __future__ import annotations

from collections import OrderedDict
import json
import logging
import re
import time
from threading import Lock

import requests
from flask import Blueprint, Response, jsonify, request, stream_with_context

from algo.document.legal_case_indexer import build_index_documents, build_index_documents_from_chunks
from algo.domain_scope import build_curated_official_documents, filter_domain_records
from algo.legal_profile import DEFAULT_PROFILE, build_index_name, filter_chunks_for_profile, normalize_profile
from algo.legal_query_analyzer import analyze_legal_query, build_refusal_answer, rerank_candidates
from algo.rag_engine import RAGEngine
from algo.structured_answer import build_structured_answer, optimize_lawyer_follow_up
from clients.springboot_client import SpringBootClient
from config import get_config
from utils.response import error, success

logger = logging.getLogger(__name__)
chat_bp = Blueprint("chat", __name__, url_prefix="/api")
rag_engine = RAGEngine()
springboot_client = SpringBootClient()
_IMAGE_CONTEXT_CACHE: "OrderedDict[str, str]" = OrderedDict()
_IMAGE_CONTEXT_LOCK = Lock()
_IMAGE_CONTEXT_CACHE_SIZE = max(1, int(get_config().IMAGE_ANALYSIS_CACHE_SIZE))

_GREETING_PATTERNS = (
    re.compile(r"^(你好|您好|hi|hello)$", re.IGNORECASE),
    re.compile(r"^(在吗|有人吗)$"),
    re.compile(r"^(早上好|中午好|下午好|晚上好)$"),
)
_GUIDANCE_PATTERNS = (
    re.compile(r"^(帮帮我|救命|我想咨询|咨询一下|想问一下)$", re.IGNORECASE),
    re.compile(r"^(我不知道怎么问|不知道怎么说|不知道怎么描述|我不会问)$", re.IGNORECASE),
    re.compile(r"^(你来问我|你先问我|你引导我|你一步一步问我)$", re.IGNORECASE),
    re.compile(r"^(能帮我看看吗|帮我看看|帮我分析一下)$", re.IGNORECASE),
)


def _build_demo_documents():
    return [
        {
            "content": "根据刑法及相关司法解释，贩卖、运输、制造毒品的数量、次数、对象以及是否面向未成年人，会直接影响定罪和量刑判断。",
            "metadata": {"title": "涉毒犯罪量刑判断", "source": "demo", "category_name": "涉毒刑事专题"},
        },
        {
            "content": "容留他人吸毒、引诱教唆欺骗他人吸毒等行为，在不同事实组合下可能分别构成独立犯罪，应重点审查行为方式、次数和获利情况。",
            "metadata": {"title": "容留吸毒与引诱教唆吸毒风险", "source": "demo", "category_name": "涉毒刑事专题"},
        },
        {
            "content": "家属或当事人咨询涉毒案件时，应优先核实是否存在毒品实物、聊天记录、转账记录、指认笔录和共同犯罪分工等关键证据。",
            "metadata": {"title": "涉毒案件证据核查重点", "source": "demo", "category_name": "涉毒刑事专题"},
        },
    ]


def _extract_core_question(question: str) -> str:
    text = (question or "").strip()
    if not text:
        return ""
    markers = ["用户问题：", "【用户问题】", "[用户问题]", "User Question:", "[User Question]"]
    for marker in markers:
        index = text.rfind(marker)
        if index >= 0:
            extracted = text[index + len(marker):].strip()
            if extracted:
                return extracted
    return text


def _is_brief_greeting(question: str, image_url: str | None = None) -> bool:
    if image_url:
        return False
    compact = re.sub(r"[\s，。！？、；:：\"'（）()\[\]<>]+", "", _extract_core_question(question))
    if not compact or len(compact) > 8:
        return False
    return any(pattern.fullmatch(compact) for pattern in _GREETING_PATTERNS)


def _build_brief_greeting_answer(profile: str) -> str:
    if normalize_profile(profile) == DEFAULT_PROFILE:
        return "你好，我是 AI 法律助手。你可以直接描述案情、程序阶段，或者上传图片和材料，我会先给你一版简明的涉毒刑事风险分析。"
    return "你好，我是 AI 法律助手。你可以直接描述问题，我会先给你一版简明分析。"


def _is_guidance_request(question: str, image_url: str | None = None) -> bool:
    if image_url:
        return False
    core_question = _extract_core_question(question)
    compact = re.sub(r"[\s，。！？、；:：\"'（）()\[\]<>]+", "", core_question)
    if not compact:
        return True
    if len(compact) > 24:
        return False
    return any(pattern.fullmatch(compact) for pattern in _GUIDANCE_PATTERNS)


def _build_guided_intake_answer(profile: str) -> str:
    if normalize_profile(profile) == DEFAULT_PROFILE:
        return (
            "你好，我是 AI 律师。你不用一开始就把问题讲得很专业，我们可以像律师接待咨询一样一步一步来。\n\n"
            "你先尽量回答下面 4 个问题里的能回答部分：\n"
            "1. 你是本人、家属，还是朋友代问？\n"
            "2. 现在大概处于什么阶段：口头传唤、拘留、取保、起诉，还是法院审理？\n"
            "3. 你目前最担心的是什么：会不会判、能不能取保、家属该怎么做，还是证据问题？\n"
            "4. 手里有没有通知书、聊天记录、转账记录、检测结果、搜查扣押清单之类的材料？\n\n"
            "如果你暂时说不全，也可以只回一句：\n"
            "“我是家属，刚收到拘留通知，想知道现在先做什么。”\n"
            "或者直接把材料发给我，我会继续追问你。"
        )
    return "你好，我是 AI 律师。你可以先告诉我你的身份、当前阶段、最担心的问题，以及手里有没有相关材料。"


def _build_attachment_notes(attached_cases, attached_documents):
    notes = []

    for item in (attached_cases or [])[:6]:
        if not isinstance(item, dict):
            continue
        title = str(item.get("title") or "").strip()
        summary = str(item.get("summary") or "").strip()
        if title and summary:
            notes.append(f"引用案例：{title}；摘要：{summary[:300]}")
        elif title:
            notes.append(f"引用案例：{title}")

    for doc in (attached_documents or [])[:6]:
        if not isinstance(doc, dict):
            continue
        name = str(doc.get("name") or "").strip()
        preview = str(doc.get("preview") or "").strip()
        extracted_text = str(doc.get("extractedText") or doc.get("extracted_text") or "").strip()
        status_label = str(doc.get("statusLabel") or doc.get("status_label") or "").strip()

        if name and extracted_text:
            notes.append(f"补充文书：{name}；状态：{status_label or '已提取正文'}；正文摘录：{extracted_text[:500]}")
        elif name and preview:
            notes.append(f"补充文书：{name}；状态：{status_label or '已上传'}；摘要：{preview[:300]}")
        elif name:
            notes.append(f"补充文书：{name}；状态：{status_label or '已上传'}")

    return notes


def _load_rag_documents(profile: str = DEFAULT_PROFILE):
    curated_documents = build_curated_official_documents()
    local_official_documents = [
        doc for doc in curated_documents
        if filter_chunks_for_profile([dict(doc.get("metadata", {}), content=doc.get("content", ""))], profile)
    ]
    legal_knowledge_chunks = springboot_client.get_all_legal_knowledge_chunks()
    legal_chunks = springboot_client.get_all_legal_document_chunks()
    if legal_knowledge_chunks or legal_chunks:
        merged_chunks = filter_chunks_for_profile(list(legal_knowledge_chunks) + list(legal_chunks), profile)
        documents = build_index_documents_from_chunks(merged_chunks)
        documents.extend(local_official_documents)
        return documents, "focused_legal_knowledge+focused_legal_document"

    if normalize_profile(profile) == DEFAULT_PROFILE and local_official_documents:
        return local_official_documents, "focused_official_corpus"

    items = filter_domain_records(springboot_client.get_all_items())
    if items:
        documents = build_index_documents(items)
        documents.extend(local_official_documents)
        return documents, "focused_item"

    if local_official_documents:
        return local_official_documents, "focused_official_corpus"
    return [], "empty"


def ensure_legal_index(profile: str = DEFAULT_PROFILE):
    profile = normalize_profile(profile)
    index_name = build_index_name(profile)
    rag_engine.index_manager.load(index_name)
    info = rag_engine.index_manager.get_index_info(index_name)
    if info and info.get("count", 0) > 0:
        return index_name

    documents, source_type = _load_rag_documents(profile)
    if documents:
        logger.info("Building legal index %s from %s source with %s chunk(s)", index_name, source_type, len(documents))
        rag_engine.add_documents(documents, index_name)
    else:
        if profile == DEFAULT_PROFILE:
            logger.warning("No drug-crime profile data found for index %s", index_name)
            raise RuntimeError("当前涉毒专题语料尚未准备完成，请先导入规范知识或专题文书。")
        logger.warning("No Spring Boot data found, using demo documents")
        rag_engine.add_documents(_build_demo_documents(), index_name)
    return index_name


def _get_cached_image_context(cache_key: str) -> str | None:
    with _IMAGE_CONTEXT_LOCK:
        cached = _IMAGE_CONTEXT_CACHE.get(cache_key)
        if cached is None:
            return None
        _IMAGE_CONTEXT_CACHE.move_to_end(cache_key)
        return cached


def _store_cached_image_context(cache_key: str, value: str) -> None:
    with _IMAGE_CONTEXT_LOCK:
        _IMAGE_CONTEXT_CACHE[cache_key] = value
        _IMAGE_CONTEXT_CACHE.move_to_end(cache_key)
        while len(_IMAGE_CONTEXT_CACHE) > _IMAGE_CONTEXT_CACHE_SIZE:
            _IMAGE_CONTEXT_CACHE.popitem(last=False)


def build_cached_image_context(image_url, image_name):
    if not image_url:
        return ""

    cache_key = f"{image_url}|{image_name or ''}"
    cached = _get_cached_image_context(cache_key)
    if cached is not None:
        logger.info("Using cached image context for %s", image_name or image_url)
        return cached

    try:
        response = requests.get(image_url, timeout=30)
        response.raise_for_status()
        analysis = rag_engine.llm_client.analyze_image(
            response.content,
            image_name=image_name,
            prompt=(
                "请识别图片中的主要文字和关键信息。"
                "如果是聊天记录、转账截图、通知书、合同或证据材料，请优先提取当事人、时间、金额、行为经过、关键表述和争议焦点。"
            ),
        )
        normalized = (analysis or "").strip()
        if normalized:
            _store_cached_image_context(cache_key, normalized)
        return normalized
    except Exception as exc:
        logger.error("Failed to build cached image context: %s", exc)
        return f"图片识别失败：{exc}"


def _sse_message(event_name: str, payload) -> str:
    return f"event: {event_name}\ndata: {json.dumps(payload, ensure_ascii=False)}\n\n"


def _build_stream_done_payload(answer: str,
                               references,
                               model: str,
                               session_id,
                               image_context: str,
                               query_analysis,
                               response_mode: str,
                               question: str = "",
                               conversation_history=None,
                               context_notes=None):
    optimized_answer = optimize_lawyer_follow_up(
        answer,
        current_query=question,
        conversation_history=conversation_history,
        context_notes=context_notes,
    ) if response_mode in {"structured_consultation", "direct_consultation"} else answer
    return {
        "content": optimized_answer,
        "references": references,
        "model": model,
        "sessionId": session_id,
        "imageAnalysis": image_context,
        "structuredAnswer": build_structured_answer(optimized_answer),
        "queryAnalysis": query_analysis,
        "responseMode": response_mode,
    }


@chat_bp.route("/chat", methods=["POST"])
def chat():
    try:
        started_at = time.perf_counter()
        data = request.get_json() or {}
        question = data.get("question", "")
        session_id = data.get("session_id")
        top_k = data.get("top_k", 5)
        model = data.get("model")
        smart_consulting = data.get("smart_consulting", True)
        image_url = data.get("image_url")
        image_name = data.get("image_name")
        history = data.get("history") if isinstance(data.get("history"), list) else []
        attached_cases = data.get("attached_cases") if isinstance(data.get("attached_cases"), list) else []
        attached_documents = data.get("attached_documents") if isinstance(data.get("attached_documents"), list) else []
        profile = normalize_profile(data.get("profile") or DEFAULT_PROFILE)
        context_notes = _build_attachment_notes(attached_cases, attached_documents)

        if not question:
            return jsonify(error("问题不能为空", 400)), 400

        if _is_brief_greeting(question, image_url):
            greeting_answer = _build_brief_greeting_answer(profile)
            return jsonify(success({
                "answer": greeting_answer,
                "references": [],
                "model": rag_engine.llm_client.resolve_model(model),
                "session_id": session_id,
                "image_analysis": "",
                "structured_answer": build_structured_answer(greeting_answer),
                "query_analysis": {
                    "question": _extract_core_question(question),
                    "response_mode": "brief_greeting",
                },
                "response_mode": "brief_greeting",
            }))

        if _is_guidance_request(question, image_url):
            greeting_answer = _build_guided_intake_answer(profile)
            return jsonify(success({
                "answer": greeting_answer,
                "references": [],
                "model": rag_engine.llm_client.resolve_model(model),
                "session_id": session_id,
                "image_analysis": "",
                "structured_answer": build_structured_answer(greeting_answer),
                "query_analysis": {
                    "question": _extract_core_question(question),
                    "response_mode": "guided_intake",
                },
                "response_mode": "guided_intake",
            }))

        image_started_at = time.perf_counter()
        image_context = build_cached_image_context(image_url, image_name)
        image_elapsed = time.perf_counter() - image_started_at
        enhanced_question = question
        if image_context:
            enhanced_question = f"[Image Analysis]\n{image_context}\n\n[User Question]\n{question}"
            context_notes = [*context_notes, f"图片识别结果：{image_context[:500]}"]

        analysis = analyze_legal_query(enhanced_question)
        if analysis.get("unsafe_request"):
            refusal_answer = build_refusal_answer(analysis)
            return jsonify(success({
                "answer": refusal_answer,
                "references": [],
                "model": rag_engine.llm_client.resolve_model(model),
                "session_id": session_id,
                "image_analysis": image_context,
                "structured_answer": build_structured_answer(refusal_answer),
                "query_analysis": analysis,
                "response_mode": "safety_refusal",
            }))

        answer_started_at = time.perf_counter()
        if smart_consulting:
            index_name = ensure_legal_index(profile)
            result = rag_engine.query(
                enhanced_question,
                index_name=index_name,
                top_k=top_k,
                model=model,
                profile=profile,
                conversation_history=history,
                context_notes=context_notes,
                raw_question=question,
            )
        else:
            result = rag_engine.query_direct(
                enhanced_question,
                model=model,
                profile=profile,
                conversation_history=history,
                context_notes=context_notes,
                raw_question=question,
            )
        answer_elapsed = time.perf_counter() - answer_started_at

        response_mode = result.get("response_mode") or "structured_consultation"
        answer = result["answer"]
        if response_mode == "structured_consultation":
            answer = optimize_lawyer_follow_up(
                answer,
                current_query=question,
                conversation_history=history,
                context_notes=context_notes,
            )
        total_elapsed = time.perf_counter() - started_at
        logger.info(
            "Chat completed in %.2fs (image=%.2fs, answer=%.2fs, smart=%s, response_mode=%s)",
            total_elapsed,
            image_elapsed,
            answer_elapsed,
            bool(smart_consulting),
            response_mode,
        )
        return jsonify(success({
            "answer": answer,
            "references": result["references"],
            "model": result["model"],
            "session_id": session_id,
            "image_analysis": image_context,
            "structured_answer": build_structured_answer(answer),
            "query_analysis": result.get("query_analysis"),
            "response_mode": response_mode,
        }))
    except Exception as exc:
        logger.error("Chat failed: %s", exc)
        return jsonify(error(f"聊天失败: {exc}")), 500


@chat_bp.route("/chat/stream", methods=["POST"])
def chat_stream():
    data = request.get_json() or {}
    question = data.get("question", "")
    session_id = data.get("session_id")
    top_k = data.get("top_k", 5)
    model = data.get("model")
    smart_consulting = data.get("smart_consulting", True)
    image_url = data.get("image_url")
    image_name = data.get("image_name")
    history = data.get("history") if isinstance(data.get("history"), list) else []
    attached_cases = data.get("attached_cases") if isinstance(data.get("attached_cases"), list) else []
    attached_documents = data.get("attached_documents") if isinstance(data.get("attached_documents"), list) else []
    profile = normalize_profile(data.get("profile") or DEFAULT_PROFILE)
    context_notes = _build_attachment_notes(attached_cases, attached_documents)

    if not question:
        return jsonify(error("问题不能为空", 400)), 400

    @stream_with_context
    def generate():
        try:
            stream_context_notes = list(context_notes)
            if _is_brief_greeting(question, image_url):
                greeting_answer = _build_brief_greeting_answer(profile)
                yield _sse_message("done", _build_stream_done_payload(
                    greeting_answer,
                    [],
                    rag_engine.llm_client.resolve_model(model),
                    session_id,
                    "",
                    {
                        "question": _extract_core_question(question),
                        "response_mode": "brief_greeting",
                    },
                    "brief_greeting",
                    question=question,
                    conversation_history=history,
                    context_notes=stream_context_notes,
                ))
                return

            if _is_guidance_request(question, image_url):
                greeting_answer = _build_guided_intake_answer(profile)
                yield _sse_message("done", _build_stream_done_payload(
                    greeting_answer,
                    [],
                    rag_engine.llm_client.resolve_model(model),
                    session_id,
                    "",
                    {
                        "question": _extract_core_question(question),
                        "response_mode": "guided_intake",
                    },
                    "guided_intake",
                    question=question,
                    conversation_history=history,
                    context_notes=stream_context_notes,
                ))
                return

            yield _sse_message("status", {"phase": "image", "message": "正在识别图片或整理材料"})
            image_context = build_cached_image_context(image_url, image_name)
            enhanced_question = question
            if image_context:
                enhanced_question = f"[Image Analysis]\n{image_context}\n\n[User Question]\n{question}"
                stream_context_notes.append(f"图片识别结果：{image_context[:500]}")

            analysis = analyze_legal_query(enhanced_question)
            if analysis.get("unsafe_request"):
                refusal_answer = build_refusal_answer(analysis)
                yield _sse_message("done", _build_stream_done_payload(
                    refusal_answer,
                    [],
                    rag_engine.llm_client.resolve_model(model),
                    session_id,
                    image_context,
                    analysis,
                    "safety_refusal",
                    question=question,
                    conversation_history=history,
                    context_notes=stream_context_notes,
                ))
                return

            yield _sse_message("status", {"phase": "retrieve", "message": "正在检索相关资料"})
            if smart_consulting:
                index_name = ensure_legal_index(profile)
                result = rag_engine.query_stream(
                    enhanced_question,
                    index_name=index_name,
                    top_k=top_k,
                    model=model,
                    profile=profile,
                    conversation_history=history,
                    context_notes=stream_context_notes,
                    raw_question=question,
                )
            else:
                result = rag_engine.query_direct_stream(
                    enhanced_question,
                    model=model,
                    profile=profile,
                    conversation_history=history,
                    context_notes=stream_context_notes,
                    raw_question=question,
                )

            yield _sse_message("meta", {
                "model": result["model"],
                "references": result["references"],
                "queryAnalysis": result.get("query_analysis"),
                "responseMode": result.get("response_mode") or "structured_consultation",
            })
            yield _sse_message("status", {"phase": "answer", "message": "正在生成回答"})

            answer_chunks = []
            for chunk in result["answer_stream"]:
                if not chunk:
                    continue
                answer_chunks.append(chunk)
                yield _sse_message("delta", {"content": chunk})

            answer = "".join(answer_chunks).strip() or "抱歉，我暂时无法回答这个问题，请稍后再试。"
            yield _sse_message("done", _build_stream_done_payload(
                answer,
                result["references"],
                result["model"],
                session_id,
                image_context,
                result.get("query_analysis"),
                result.get("response_mode") or "structured_consultation",
                question=question,
                conversation_history=history,
                context_notes=stream_context_notes,
            ))
        except Exception as exc:
            logger.error("Chat stream failed: %s", exc)
            yield _sse_message("error", {"message": f"聊天失败: {exc}"})

    response = Response(generate(), content_type="text/event-stream; charset=utf-8")
    response.headers["Cache-Control"] = "no-cache"
    response.headers["X-Accel-Buffering"] = "no"
    return response


@chat_bp.route("/search", methods=["POST"])
def search():
    try:
        data = request.get_json() or {}
        query = data.get("query", "")
        top_k = data.get("top_k", 5)
        model = data.get("model")
        profile = normalize_profile(data.get("profile") or DEFAULT_PROFILE)
        if not query:
            return jsonify(error("查询不能为空", 400)), 400

        index_name = ensure_legal_index(profile)
        result = rag_engine.search(query, index_name=index_name, top_k=top_k, profile=profile)
        reranked = rerank_candidates(query, result["references"], result.get("query_analysis") or {}, top_k)

        return jsonify(success({
            "results": reranked,
            "total": len(reranked),
            "model": model or rag_engine.llm_client.model,
            "query_analysis": result.get("query_analysis"),
        }))
    except Exception as exc:
        logger.error("Search failed: %s", exc)
        return jsonify(error(f"搜索失败: {exc}")), 500
