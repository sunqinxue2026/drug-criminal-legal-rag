"""
Document processing routes.
"""
from __future__ import annotations

import html
import json
import logging
import re
import subprocess
import tempfile
from html.parser import HTMLParser
from io import BytesIO
from pathlib import Path

from flask import Blueprint, jsonify, request

from utils.response import error, success

logger = logging.getLogger(__name__)
document_bp = Blueprint("document", __name__, url_prefix="/api/document")

_BLOCK_TAGS = {
    "article", "aside", "blockquote", "br", "div", "figcaption", "figure", "footer",
    "h1", "h2", "h3", "h4", "h5", "h6", "header", "hr", "li", "main", "nav", "ol",
    "p", "pre", "section", "table", "tbody", "td", "th", "thead", "tr", "ul",
}
_IGNORE_TAGS = {"script", "style", "noscript", "svg", "canvas"}
_HTML_CONTENT_HINTS = (
    "txt_txt", "trs_editor", "article-content", "articlecontent", "article_content", "content", "zoom",
)
_HTML_NOISE_PATTERNS = [
    re.compile(pattern, re.IGNORECASE)
    for pattern in (
        r"^首页\s*>",
        r"^打印本页$",
        r"^字号[:：]",
        r"^责任编辑[:：]",
        r"^登录邮箱系统$",
        r"^English$",
        r"^法院资讯$",
    )
]


def _read_text_with_fallback(path: Path) -> str:
    return _decode_text_with_fallback(path.read_bytes())


def _looks_like_utf16_payload(payload: bytes) -> bool:
    if payload.startswith((b"\xff\xfe", b"\xfe\xff")):
        return True
    sample = payload[:4096]
    if len(sample) < 4:
        return False
    null_count = sample.count(0)
    return null_count >= 8 and null_count / max(len(sample), 1) >= 0.1


def _decode_text_with_fallback(payload: bytes) -> str:
    if payload.startswith((b"\xff\xfe", b"\xfe\xff")):
        try:
            return payload.decode("utf-16").strip()
        except UnicodeDecodeError:
            pass

    if _looks_like_utf16_payload(payload):
        for encoding in ("utf-16le", "utf-16be", "utf-16"):
            try:
                return payload.decode(encoding).strip()
            except UnicodeDecodeError:
                continue

    for encoding in ("utf-8-sig", "utf-8", "gb18030", "gbk"):
        try:
            return payload.decode(encoding).strip()
        except UnicodeDecodeError:
            continue
    return payload.decode("utf-8", errors="ignore").strip()


def _append_text(parts: list[str], value: str) -> None:
    if not value:
        return
    if parts and parts[-1] != "\n":
        parts.append("\n")
    parts.append(value)


def _compact_html_text(value: str) -> str:
    lines: list[str] = []
    for raw_line in value.splitlines():
        normalized = re.sub(r"[ \t\u00a0\u3000]+", " ", raw_line).strip()
        if not normalized:
            if lines and lines[-1]:
                lines.append("")
            continue
        if any(pattern.search(normalized) for pattern in _HTML_NOISE_PATTERNS):
            continue
        lines.append(normalized)
    return "\n".join(line for line in lines).replace("\n\n\n", "\n\n").strip()


class _HtmlVisibleTextParser(HTMLParser):
    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self._ignore_depth = 0
        self._in_title = False
        self._current_capture_depth = 0
        self._current_capture_parts: list[str] = []
        self._best_capture = ""
        self._best_capture_len = 0
        self._body_parts: list[str] = []
        self._title_parts: list[str] = []

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        tag = tag.lower()
        if tag in _IGNORE_TAGS:
            self._ignore_depth += 1
            return
        if tag == "title":
            self._in_title = True
        if self._ignore_depth:
            return

        attr_map = {key.lower(): (value or "") for key, value in attrs}
        hint_text = f"{attr_map.get('id', '')} {attr_map.get('class', '')}".lower()
        if self._current_capture_depth == 0 and any(hint in hint_text for hint in _HTML_CONTENT_HINTS):
            self._current_capture_depth = 1
            self._current_capture_parts = []
        elif self._current_capture_depth > 0:
            self._current_capture_depth += 1

        if tag in _BLOCK_TAGS:
            _append_text(self._body_parts, "")
            if self._current_capture_depth > 0:
                _append_text(self._current_capture_parts, "")

    def handle_startendtag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        self.handle_starttag(tag, attrs)
        self.handle_endtag(tag)

    def handle_endtag(self, tag: str) -> None:
        tag = tag.lower()
        if tag == "title":
            self._in_title = False
        if self._ignore_depth and tag in _IGNORE_TAGS:
            self._ignore_depth -= 1
            return
        if self._ignore_depth:
            return
        if tag in _BLOCK_TAGS:
            _append_text(self._body_parts, "")
            if self._current_capture_depth > 0:
                _append_text(self._current_capture_parts, "")
        if self._current_capture_depth > 0:
            self._current_capture_depth -= 1
            if self._current_capture_depth == 0:
                capture = _compact_html_text("".join(self._current_capture_parts))
                if len(capture) > self._best_capture_len:
                    self._best_capture = capture
                    self._best_capture_len = len(capture)
                self._current_capture_parts = []

    def handle_data(self, data: str) -> None:
        if self._ignore_depth:
            return
        text = data.strip()
        if not text:
            return
        if self._in_title:
            self._title_parts.append(text)
        _append_text(self._body_parts, text)
        if self._current_capture_depth > 0:
            _append_text(self._current_capture_parts, text)

    def extract(self, payload: str) -> tuple[str, str]:
        self.feed(payload)
        title = _compact_html_text(" ".join(self._title_parts))
        content = self._best_capture or _compact_html_text("".join(self._body_parts))
        if title and title not in content:
            content = f"{title}\n\n{content}".strip()
        return title, content


def _extract_text_from_html(payload: bytes) -> str:
    decoded = _decode_text_with_fallback(payload)
    parser = _HtmlVisibleTextParser()
    _, content = parser.extract(decoded)
    return content


def _extract_text_via_word_com(filename: str, payload: bytes) -> tuple[str, str | None]:
    with tempfile.TemporaryDirectory(prefix="legal_doc_") as temp_dir:
        source_path = Path(temp_dir) / filename
        target_path = source_path.with_suffix(".txt")
        source_path.write_bytes(payload)

        ps_source = str(source_path).replace("'", "''")
        ps_target = str(target_path).replace("'", "''")
        script = f"""
$ErrorActionPreference = 'Stop'
$doc = $null
try {{
  $word = New-Object -ComObject Word.Application
}} catch {{
  Write-Error 'Microsoft Word is not installed or COM automation is unavailable.'
  exit 2
}}
$word.Visible = $false
$word.DisplayAlerts = 0
try {{
  $doc = $word.Documents.Open('{ps_source}', $false, $true)
  $doc.SaveAs([string]'{ps_target}', 7)
}} finally {{
  if ($doc -ne $null) {{
    try {{ $doc.Close([ref]0) }} catch {{}}
  }}
  try {{ $word.Quit() }} catch {{}}
}}
"""

        result = subprocess.run(
            ["powershell", "-NoProfile", "-Command", script],
            capture_output=True,
            text=True,
            timeout=60,
        )

        if result.returncode != 0:
            message = (result.stderr or result.stdout or "").strip()
            if result.returncode == 2:
                return "", "当前环境缺少 Microsoft Word，无法解析 .doc 文件，请先安装 Word 或将文件另存为 .docx / pdf / txt。"
            return "", f".doc 文件解析失败: {message or '未知错误'}"

        if not target_path.exists():
            return "", ".doc 文件解析失败: 未生成中间文本文件。"
        return _read_text_with_fallback(target_path), None


def _extract_text_from_bytes(filename: str, payload: bytes) -> tuple[str, str | None]:
    lower_name = (filename or "").lower()
    if lower_name.endswith((".txt", ".md", ".csv")):
        return _decode_text_with_fallback(payload), None
    if lower_name.endswith((".html", ".htm")):
        return _extract_text_from_html(payload), None
    if lower_name.endswith(".json"):
        parsed = json.loads(payload.decode("utf-8-sig", errors="ignore"))
        return json.dumps(parsed, ensure_ascii=False, indent=2), None
    if lower_name.endswith(".doc"):
        return _extract_text_via_word_com(filename, payload)
    if lower_name.endswith(".docx"):
        from docx import Document
        document = Document(BytesIO(payload))
        paragraphs = [paragraph.text.strip() for paragraph in document.paragraphs if paragraph.text.strip()]
        return "\n".join(paragraphs).strip(), None
    if lower_name.endswith(".pdf"):
        from pypdf import PdfReader
        reader = PdfReader(BytesIO(payload))
        pages = [page.extract_text() or "" for page in reader.pages]
        return "\n".join(page.strip() for page in pages if page.strip()).strip(), None
    return "", "当前仅支持 txt、md、csv、html、json、doc、docx、pdf 文件导入。"


@document_bp.route("/test", methods=["GET"])
def document_test():
    return jsonify(success({"message": "Document service is running"}))


@document_bp.route("/extract-text", methods=["POST"])
def extract_document_text():
    try:
        file = request.files.get("file")
        if not file:
            return jsonify(error("缺少文件", 400)), 400

        filename = file.filename or ""
        payload = file.read()
        text, message = _extract_text_from_bytes(filename, payload)
        extension = filename.rsplit(".", 1)[-1].lower() if "." in filename else ""
        supported = bool(text)

        return jsonify(success({
            "filename": filename,
            "extension": extension,
            "text": text,
            "supported": supported,
            "message": message or ("文档解析成功" if supported else "未能从文件中提取文本"),
            "char_count": len(text),
            "status": "parsed" if supported else "linked",
        }))
    except Exception as exc:
        logger.error("Failed to extract document text: %s", exc)
        return jsonify(error(f"文书解析失败: {exc}")), 500
