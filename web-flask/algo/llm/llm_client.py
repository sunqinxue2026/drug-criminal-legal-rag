"""
LLM client with Qwen support and model validation.
"""
from __future__ import annotations

import base64
import json
import logging
import mimetypes
import re

import requests
from requests.adapters import HTTPAdapter
from urllib3.util.retry import Retry

from algo.structured_answer import build_structured_answer
from config import get_config

logger = logging.getLogger(__name__)

STRUCTURED_RESPONSE_SYSTEM_PROMPT = (
    "请按四到五段自然中文输出，前四段分别以“初步结论：”“法律依据：”“风险提示：”“行动建议：”开头。"
    "前四段都必须有实质内容，不要只写标题。"
    "第一段必须直接回答用户核心问题；用户要求最终结论、审判结果、判决结果、是否构成犯罪或大概怎么判时，"
    "必须先给出基于现有事实的倾向性判断、可能结果区间和不确定边界，不能只说需要更多信息。"
    "“律师追问：”是可选段，只有缺少一个会实质改变结论的关键事实时才输出。"
    "如果输出律师追问，必须只有一句话，只能问一个最关键问题，不能重复此前已问过或已回答的事实。"
    "不要输出 Markdown 标记。"
)

DEFAULT_LEGAL_SYSTEM_PROMPT = (
    "你是一名谨慎、专业、具备合格律师沟通素养的法律咨询助手。"
    "请严格依据用户提供的事实、上下文材料和检索资料作答，不要编造法条、案例、证据或结论。"
    "请像律师接待咨询一样，区分已知事实、待核实事实、法律评价、风险边界和下一步建议。"
    "追问不是必选项。用户要求最终结论、审判结果、判决结果、是否构成犯罪或大概怎么判时，"
    "必须先给出当前证据下的倾向性判断和可能结果区间，再说明哪些事实会改变判断。"
    "只有确实缺少一个会实质改变结论的关键事实时，才在结尾补一条专业追问。"
    "如果用户已经明确回答上一轮律师追问，本轮必须把该回答视为已知事实，不得重复询问同一事实，也不要仅仅换一种说法重复追问。"
    "如果用户只回复“有”“没有”“是”“不是”“不清楚”等简短词语，默认这是在回答上一轮律师追问。"
    "如果还需要追问，新的律师追问必须在此基础上继续推进到下一个最关键、最影响法律判断的事实点。"
    "不要作出过度确定的承诺，不要省略风险提示。"
    "面向普通用户时，请用通俗中文解释法律术语。"
    "请使用自然中文段落，不要输出 Markdown、表格、任务清单、分隔线或 emoji。"
)

SHORT_REPLY_PATTERN = re.compile(
    r"^(有|没有|无|不是|是|对|不对|不清楚|不知道|还没有|未|没有过|没卖过|没做过|不是这样|没有这个情况)[\s，。,。！？!?；;]*$"
)


class LLMClient:
    """LLM client with configurable Qwen models."""

    def __init__(self, api_url=None, api_key=None, model="qwen-turbo", allowed_models=None):
        config = get_config()
        self.api_url = api_url or config.LLM_API_URL
        self.compatible_api_url = config.LLM_COMPATIBLE_API_URL
        self.api_key = api_key if api_key is not None else config.LLM_API_KEY
        self.allowed_models = allowed_models or list(config.LLM_ALLOWED_MODELS)
        self.request_timeout = max(1, int(config.LLM_REQUEST_TIMEOUT_SECONDS))
        self.max_output_tokens = max(256, int(config.LLM_MAX_TOKENS))
        self.fail_fast = bool(config.LLM_FAIL_FAST)
        self.vision_api_url = config.VISION_API_URL
        self.vision_model = config.VISION_MODEL
        self.session = self._build_session(
            max_retries=max(0, int(config.LLM_MAX_RETRIES)),
            backoff_seconds=max(0, int(config.LLM_RETRY_BACKOFF_SECONDS)),
        )
        self.model = self.resolve_model(model)
        self.use_qwen = "qwen" in self.model.lower()
        logger.info("LLM client initialized with model=%s fail_fast=%s", self.model, self.fail_fast)

    def resolve_model(self, model_name=None):
        requested = (model_name or self.model or "").strip().lower()
        if requested in self.allowed_models:
            return requested
        return self.allowed_models[0]

    def get_model_info(self):
        return {
            "current_model": self.model,
            "allowed_models": self.allowed_models,
            "has_api_key": bool(self.api_key),
            "provider": "qwen" if self.use_qwen else "openai-compatible",
            "mock_mode": not bool(self.api_key) and not self.fail_fast,
            "fail_fast": self.fail_fast,
        }

    def set_model(self, model_name):
        self.model = self.resolve_model(model_name)
        self.use_qwen = "qwen" in self.model.lower()
        logger.info("Switched LLM model to: %s", self.model)
        return self.model

    def analyze_image(self, image_bytes, image_name=None, prompt=None, model=None):
        if not self.api_key:
            if self.fail_fast:
                raise RuntimeError("LLM_API_KEY is not configured for image analysis")
            return "当前未配置可用的视觉模型 API Key，暂时无法识别图片内容。"

        mime_type = mimetypes.guess_type(image_name or "")[0] or "image/jpeg"
        image_base64 = base64.b64encode(image_bytes).decode("utf-8")
        image_url = f"data:{mime_type};base64,{image_base64}"
        active_model = model or self.vision_model

        headers = {
            "Authorization": f"Bearer {self.api_key}",
            "Content-Type": "application/json",
        }
        payload = {
            "model": active_model,
            "messages": [
                {
                    "role": "user",
                    "content": [
                        {
                            "type": "text",
                            "text": prompt or (
                                "请识别图片中的主要文字内容，并总结其中与法律咨询相关的关键信息。"
                                "如果是聊天记录、转账截图、合同或证据材料，请优先提取当事人、时间、金额、承诺内容和争议焦点。"
                            ),
                        },
                        {
                            "type": "image_url",
                            "image_url": {"url": image_url},
                        },
                    ],
                }
            ],
        }

        result = self._post_json(self.vision_api_url, headers, payload, timeout=120)
        return result["choices"][0]["message"]["content"]

    def chat(self, messages, temperature=0.7, max_tokens=None, model=None):
        active_model = self.resolve_model(model or self.model)
        active_max_tokens = max(256, int(max_tokens or self.max_output_tokens))
        logger.info(
            "LLM request with %s message(s), model=%s, max_tokens=%s",
            len(messages),
            active_model,
            active_max_tokens,
        )

        if not self.api_key:
            error = RuntimeError("LLM_API_KEY is not configured")
            if self.fail_fast:
                raise error
            logger.warning("%s, returning mock response", error)
            return self._mock_response(messages, active_model)

        try:
            if "qwen" in active_model:
                return self._call_qwen_api(messages, temperature, active_max_tokens, active_model)
            return self._call_openai_api(messages, temperature, active_max_tokens, active_model)
        except Exception as exc:
            logger.error("LLM chat failed: %s", exc)
            if self.fail_fast:
                raise
            return self._mock_response(messages, active_model)

    def stream_chat(self, messages, temperature=0.7, max_tokens=None, model=None):
        active_model = self.resolve_model(model or self.model)
        active_max_tokens = max(256, int(max_tokens or self.max_output_tokens))
        logger.info(
            "Streaming LLM request with %s message(s), model=%s, max_tokens=%s",
            len(messages),
            active_model,
            active_max_tokens,
        )

        if not self.api_key:
            error = RuntimeError("LLM_API_KEY is not configured")
            if self.fail_fast:
                raise error
            logger.warning("%s, returning mock stream", error)
            yield from self._chunk_text(self._mock_response(messages, active_model))
            return

        try:
            yield from self._stream_openai_compatible_api(messages, temperature, active_max_tokens, active_model)
        except Exception as exc:
            logger.error("LLM stream failed: %s", exc)
            if self.fail_fast:
                raise
            yield from self._chunk_text(self._mock_response(messages, active_model))

    def _build_session(self, max_retries: int, backoff_seconds: int) -> requests.Session:
        session = requests.Session()
        retry = Retry(
            total=max_retries,
            connect=max_retries,
            read=max_retries,
            status=max_retries,
            backoff_factor=backoff_seconds,
            status_forcelist=(429, 500, 502, 503, 504),
            allowed_methods=frozenset({"POST"}),
            raise_on_status=False,
        )
        adapter = HTTPAdapter(max_retries=retry)
        session.mount("https://", adapter)
        session.mount("http://", adapter)
        return session

    def _post_json(self, url, headers, payload, timeout=None):
        response = self.session.post(url, headers=headers, json=payload, timeout=timeout or self.request_timeout)
        if response.status_code == 200:
            return response.json()
        raise RuntimeError(f"LLM API failed: {response.status_code} - {response.text}")

    def _call_qwen_api(self, messages, temperature, max_tokens, model):
        headers = {
            "Authorization": f"Bearer {self.api_key}",
            "Content-Type": "application/json",
        }
        payload = {
            "model": model,
            "input": {"messages": messages},
            "parameters": {
                "temperature": temperature,
                "max_tokens": max_tokens,
                "top_p": 0.8,
                "result_format": "message",
            },
        }

        result = self._post_json(self.api_url, headers, payload)
        if result.get("output") and result["output"].get("choices"):
            return result["output"]["choices"][0]["message"]["content"]
        raise RuntimeError(f"Unexpected Qwen response: {result}")

    def _call_openai_api(self, messages, temperature, max_tokens, model):
        headers = {
            "Authorization": f"Bearer {self.api_key}",
            "Content-Type": "application/json",
        }
        payload = {
            "model": model,
            "messages": messages,
            "temperature": temperature,
            "max_tokens": max_tokens,
        }
        result = self._post_json(self.api_url, headers, payload)
        if result.get("choices"):
            return result["choices"][0]["message"]["content"]
        raise RuntimeError(f"Unexpected OpenAI-compatible response: {result}")

    def _mock_response(self, messages, model):
        user_message = ""
        for msg in reversed(messages):
            if msg.get("role") == "user":
                user_message = msg.get("content", "")
                break

        preview = f"{user_message[:100]}..." if len(user_message) > 100 else user_message
        return (
            "初步结论：当前处于演示模式，因为尚未配置可用的 LLM_API_KEY，我暂时不能基于真实模型给出正式分析；"
            f"但我已收到你的问题“{preview}”。\n"
            f"法律依据：当前模型配置显示为 {self.resolve_model(model)}，需要接入可用模型后，系统才能结合知识库生成完整法律回答。\n"
            "风险提示：在演示模式下，任何结论都不适合作为个案判断依据，以免误导你对案件阶段、证据和风险的理解。\n"
            "行动建议：请先完成模型配置，或稍后在模型服务恢复后重新提问；如果事情紧急，建议尽快联系执业律师处理。\n"
            "律师追问：你现在最急着确认的是案件所处阶段、证据情况，还是家属下一步该先做什么？"
        )

    def _chunk_text(self, text: str, chunk_size: int = 48):
        normalized = text or ""
        for index in range(0, len(normalized), chunk_size):
            yield normalized[index:index + chunk_size]

    def _compact_text(self, value, limit=None):
        text = re.sub(r"\s+", " ", str(value or "")).strip()
        if limit and len(text) > limit:
            return text[:limit].rstrip() + "..."
        return text

    def _is_short_direct_reply(self, content: str) -> bool:
        text = self._compact_text(content, limit=80)
        return bool(text) and (len(text) <= 18 or bool(SHORT_REPLY_PATTERN.fullmatch(text)))

    def _extract_follow_up_exchange(self, conversation_history=None):
        last_follow_up = ""
        latest_exchange = {"follow_up": "", "user_reply": ""}

        for item in conversation_history or []:
            if not isinstance(item, dict):
                continue

            role = str(item.get("role") or "user").strip().lower()
            content = self._compact_text(item.get("content"), limit=600)
            if not content:
                continue

            if role == "assistant":
                follow_up = self._compact_text(build_structured_answer(content).get("followUp"), limit=180)
                if follow_up:
                    last_follow_up = follow_up
            else:
                if last_follow_up:
                    latest_exchange = {
                        "follow_up": last_follow_up,
                        "user_reply": self._compact_text(content, limit=180),
                    }

        return latest_exchange

    def _extract_latest_follow_up(self, conversation_history=None) -> str:
        latest_follow_up = ""
        for item in conversation_history or []:
            if not isinstance(item, dict):
                continue
            role = str(item.get("role") or "user").strip().lower()
            if role != "assistant":
                continue
            content = self._compact_text(item.get("content"), limit=600)
            if not content:
                continue
            follow_up = self._compact_text(build_structured_answer(content).get("followUp"), limit=180)
            if follow_up:
                latest_follow_up = follow_up
        return latest_follow_up

    def _build_history_summary_lines(self, conversation_history=None):
        lines = []
        last_follow_up = ""

        for item in list(conversation_history or [])[-6:]:
            if not isinstance(item, dict):
                continue

            role = str(item.get("role") or "user").strip().lower()
            content = self._compact_text(item.get("content"), limit=800)
            if not content:
                continue

            if role == "assistant":
                structured = build_structured_answer(content)
                conclusion = self._compact_text(structured.get("conclusion"), limit=120)
                follow_up = self._compact_text(structured.get("followUp"), limit=120)
                parts = []
                if conclusion:
                    parts.append(f"结论：{conclusion}")
                if follow_up:
                    parts.append(f"律师追问：{follow_up}")
                    last_follow_up = follow_up
                if not parts:
                    parts.append(self._compact_text(content, limit=160))
                lines.append("助手：" + "；".join(parts))
                continue

            if last_follow_up and self._is_short_direct_reply(content):
                lines.append(f"用户：针对上一轮律师追问“{last_follow_up}”的回答是：{self._compact_text(content, limit=80)}")
            else:
                lines.append(f"用户：{self._compact_text(content, limit=180)}")

        return lines

    def _build_context_messages(self, conversation_history=None, context_notes=None):
        messages = []

        if conversation_history:
            history_lines = self._build_history_summary_lines(conversation_history)
            latest_exchange = self._extract_follow_up_exchange(conversation_history)

            if history_lines:
                messages.append({
                    "role": "system",
                    "content": "以下是最近对话上下文，请结合上下文保持回答连续性：\n\n" + "\n".join(history_lines),
                })
            if latest_exchange.get("follow_up") and latest_exchange.get("user_reply"):
                messages.append({
                    "role": "system",
                    "content": (
                        "多轮对话推进要求：上一轮律师追问是“"
                        + latest_exchange["follow_up"]
                        + "”；用户已经明确回答“"
                        + latest_exchange["user_reply"]
                        + "”。"
                        + "本轮必须吸收这条回答，不得重复追问同一事实；如果现有事实足以判断，应直接给结论，"
                        + "只有仍有会改变结论的关键缺口时才追问一个新问题。"
                    ),
                })

        if context_notes:
            note_lines = [str(note).strip()[:280] for note in context_notes[:6] if str(note).strip()]
            if note_lines:
                messages.append({
                    "role": "system",
                    "content": "以下是用户补充材料摘要，请结合这些材料作答：\n\n" + "\n".join(note_lines),
                })

        return messages

    def _build_query_resolution_messages(self, query, conversation_history=None):
        query_text = self._compact_text(query, limit=120)
        latest_follow_up = self._extract_latest_follow_up(conversation_history)
        if not query_text or not latest_follow_up or not self._is_short_direct_reply(query_text):
            return []

        return [{
            "role": "system",
            "content": (
                "当前用户这一句“"
                + query_text
                + "”应视为对上一轮律师追问“"
                + latest_follow_up
                + "”的直接回答。"
                + "请把这条回答作为已知事实吸收进分析，不得重复追问同一问题。"
                + "如果现有事实足以判断，应直接给结论；只有仍有会改变结论的关键缺口时才追问一个新问题。"
            ),
        }]

    def _rewrite_query_for_model(self, query, conversation_history=None):
        query_text = self._compact_text(query, limit=160)
        latest_follow_up = self._extract_latest_follow_up(conversation_history)
        if not query_text or not latest_follow_up or not self._is_short_direct_reply(query_text):
            return query

        history_digest = "\n".join(self._build_history_summary_lines(conversation_history)[-4:])
        return (
            ("以下是本案最近已经明确的上下文，请继续在此基础上分析：\n" + history_digest + "\n\n") if history_digest else ""
        ) + (
            f"针对上一轮律师追问“{latest_follow_up}”，我的回答是：{query_text}。"
            "请继续基于此前已经明确的案情、程序阶段和材料分析，不要把这句简短回答当成一个全新的独立问题，"
            "也不要说我没有提供具体信息。请吸收这条回答后给出当前结论；"
            "只有仍有会改变结论的关键缺口时，才提出一个新的、不会重复的律师追问。"
        )

    def generate_with_context(self, query, context_docs, system_prompt=None, model=None,
                              conversation_history=None, context_notes=None):
        effective_query = self._rewrite_query_for_model(query, conversation_history)
        messages = [
            {
                "role": "system",
                "content": STRUCTURED_RESPONSE_SYSTEM_PROMPT,
            },
            {
                "role": "system",
                "content": system_prompt or DEFAULT_LEGAL_SYSTEM_PROMPT,
            },
        ]
        messages.extend(self._build_context_messages(conversation_history, context_notes))
        messages.extend(self._build_query_resolution_messages(query, conversation_history))

        if context_docs:
            context_text = "\n\n".join(
                f"参考资料{index + 1}：标题：{doc.get('title', '未命名')}；内容：{doc.get('content', '')[:320]}"
                for index, doc in enumerate(context_docs[:3])
            )
            messages.append({
                "role": "system",
                "content": f"以下是检索到的相关法律资料，请优先基于这些资料作答：\n\n{context_text}",
            })

        messages.append({"role": "user", "content": effective_query})
        return self.chat(messages, model=model)

    def generate_with_context_stream(self, query, context_docs, system_prompt=None, model=None,
                                     conversation_history=None, context_notes=None):
        effective_query = self._rewrite_query_for_model(query, conversation_history)
        messages = [
            {
                "role": "system",
                "content": STRUCTURED_RESPONSE_SYSTEM_PROMPT,
            },
            {
                "role": "system",
                "content": system_prompt or DEFAULT_LEGAL_SYSTEM_PROMPT,
            },
        ]
        messages.extend(self._build_context_messages(conversation_history, context_notes))
        messages.extend(self._build_query_resolution_messages(query, conversation_history))

        if context_docs:
            context_text = "\n\n".join(
                f"参考资料{index + 1}：标题：{doc.get('title', '未命名')}；内容：{doc.get('content', '')[:320]}"
                for index, doc in enumerate(context_docs[:3])
            )
            messages.append({
                "role": "system",
                "content": f"以下是检索到的相关法律资料，请优先基于这些资料作答：\n\n{context_text}",
            })

        messages.append({"role": "user", "content": effective_query})
        yield from self.stream_chat(messages, model=model)

    def generate_direct(self, query, system_prompt=None, model=None,
                        conversation_history=None, context_notes=None):
        effective_query = self._rewrite_query_for_model(query, conversation_history)
        messages = [
            {
                "role": "system",
                "content": system_prompt or DEFAULT_LEGAL_SYSTEM_PROMPT,
            },
            {
                "role": "system",
                "content": STRUCTURED_RESPONSE_SYSTEM_PROMPT,
            },
        ]
        messages.extend(self._build_context_messages(conversation_history, context_notes))
        messages.extend(self._build_query_resolution_messages(query, conversation_history))
        messages.append({
            "role": "user",
            "content": effective_query,
        })
        return self.chat(messages, model=model)

    def generate_direct_stream(self, query, system_prompt=None, model=None,
                               conversation_history=None, context_notes=None):
        effective_query = self._rewrite_query_for_model(query, conversation_history)
        messages = [
            {
                "role": "system",
                "content": system_prompt or DEFAULT_LEGAL_SYSTEM_PROMPT,
            },
            {
                "role": "system",
                "content": STRUCTURED_RESPONSE_SYSTEM_PROMPT,
            },
        ]
        messages.extend(self._build_context_messages(conversation_history, context_notes))
        messages.extend(self._build_query_resolution_messages(query, conversation_history))
        messages.append({
            "role": "user",
            "content": effective_query,
        })
        yield from self.stream_chat(messages, model=model)

    def _stream_openai_compatible_api(self, messages, temperature, max_tokens, model):
        headers = {
            "Authorization": f"Bearer {self.api_key}",
            "Content-Type": "application/json",
        }
        payload = {
            "model": model,
            "messages": messages,
            "temperature": temperature,
            "max_tokens": max_tokens,
            "stream": True,
        }
        with self.session.post(
            self.compatible_api_url,
            headers=headers,
            json=payload,
            timeout=self.request_timeout,
            stream=True,
        ) as response:
            if response.status_code != 200:
                raise RuntimeError(f"LLM stream API failed: {response.status_code} - {response.text}")

            for raw_line in response.iter_lines(decode_unicode=True):
                if not raw_line:
                    continue
                line = raw_line.strip()
                if not line.startswith("data:"):
                    continue
                data = line[5:].strip()
                if data == "[DONE]":
                    break
                payload = json.loads(data)
                choices = payload.get("choices") or []
                if not choices:
                    continue
                delta = choices[0].get("delta") or {}
                content = delta.get("content")
                if isinstance(content, str) and content:
                    yield content
