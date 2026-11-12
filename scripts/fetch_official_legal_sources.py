from __future__ import annotations

import argparse
import html
import json
import re
import subprocess
import tempfile
from pathlib import Path
from typing import Any, Dict, Iterable, List, Tuple

import requests
import urllib3


urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)


ROOT_DIR = Path(__file__).resolve().parents[1]
DEFAULT_MANIFEST = ROOT_DIR / "data" / "legal_sources" / "source_manifest.json"
DEFAULT_BASE_DIR = ROOT_DIR / "data" / "legal_sources"
DEFAULT_HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
        "(KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36"
    )
}

BLOCK_TAG_RE = re.compile(r"(?i)</(p|div|li|tr|td|h1|h2|h3|h4|h5|section|article|ul|ol|table)>|<br\s*/?>")
TAG_RE = re.compile(r"(?is)<[^>]+>")
SCRIPT_RE = re.compile(r"(?is)<script.*?</script>|<style.*?</style>|<noscript.*?</noscript>")
COMMENT_RE = re.compile(r"(?is)<!--.*?-->")

ORDINAL = "一二三四五六七八九十百千万零两〇0-9"
ARTICLE_RE = re.compile(rf"^(第[{ORDINAL}]+条)\s*(.*)$")
POINT_RE = re.compile(r"^([0-9]{1,3})[.．、]\s*(.*)$")
PART_RE = re.compile(rf"^(第[{ORDINAL}]+编)\s*(.*)$")
CHAPTER_RE = re.compile(rf"^(第[{ORDINAL}]+章)\s*(.*)$")
SECTION_RE = re.compile(rf"^(第[{ORDINAL}]+节)\s*(.*)$")
TOPIC_SECTION_RE = re.compile(rf"^([{ORDINAL}]+、)\s*(.*)$")
SUBTOPIC_SECTION_RE = re.compile(rf"^(（[{ORDINAL}]+）)\s*(.*)$")
DATE_LINE_RE = re.compile(r"^(?:日期|时间)[:：]\s*(.+)$")

CASE_BLOCK_RE = re.compile(
    r"^(指导性案例\d+号|参考案例\d+号|典型案例\d+号|典型案例[一二三四五六七八九十0-9]+|典型事例[一二三四五六七八九十0-9]+|案例[一二三四五六七八九十0-9]+|检例第\d+号|（检例第\d+号）)$"
)
CASE_NUMBER_RE = re.compile(
    r"^(指导性案例\d+号|参考案例\d+号|典型案例\d+号|典型案例[一二三四五六七八九十0-9]+|典型事例[一二三四五六七八九十0-9]+|案例[一二三四五六七八九十0-9]+|检例第\d+号|（检例第\d+号）)(?:[:：]\s*(.*))?$"
)
CASE_SECTION_NAMES = (
    "关键词",
    "要旨",
    "裁判要点",
    "基本案情",
    "基本情况",
    "诉讼及履职过程",
    "检察机关履职情况",
    "检察机关履职过程",
    "调查和督促履职",
    "处理结果",
    "裁判结果",
    "裁判理由",
    "制发检察建议",
    "指导意义",
    "典型意义",
    "相关规定",
    "相关法条",
    "相关链接",
)

NOISE_EXACT = {
    "首页",
    "English",
    "登录邮箱系统",
    "[ 中文版 ] [ English ]",
    "关注：",
    "字号：",
    "小",
    "大",
    "打印本页",
}

NOISE_CONTAINS = [
    "所在位置：",
    "责任编辑：",
    "中国共产党新闻网",
    "中国政府网",
    "中国人大网",
    "中国政协网",
    "中国法院网",
    "全国人民法庭信息网",
    "人民法院诉讼资产网",
    "中华人民共和国最高人民法院 版权所有",
    "京公网安备",
    "京ICP备",
    "地址：北京市东城区东交民巷27号",
    "邮编：100745",
    "总机：67550114",
    "举报电话：67556131",
]

STOP_MARKERS = [
    "责任编辑：",
    "相关链接：",
    "上一篇：",
    "下一篇：",
    "【 打印此页 】",
    "【 关闭此页 】",
    "版权所有：",
    "版权所有",
    "技术支持：",
    "-- 相关单位 --",
    "-- 友情链接 --",
    "中国共产党新闻网",
    "中国政府网",
    "中国人大网",
    "中国政协网",
    "中国法院网",
    "全国人民法庭信息网",
    "人民法院诉讼资产网",
    "中华人民共和国最高人民法院 版权所有",
    "京公网安备",
    "京ICP备",
    "地址：北京市东城区东交民巷27号",
]

ARTICLE_NOISE_PATTERNS = [
    re.compile(r"\s*关联法条(?:\s+.*)?$"),
    re.compile(r"\s*沿革信息(?:\s+.*)?$"),
    re.compile(r"\s*国家法律\d+篇(?:\s+.*)?$"),
    re.compile(r"\s*地方法规\d+篇(?:\s+.*)?$"),
    re.compile(r"\s*案例\d+篇(?:\s+.*)?$"),
    re.compile(r"\s*裁判\d+篇(?:\s+.*)?$"),
    re.compile(r"\s*期刊\d+篇(?:\s+.*)?$"),
]


def load_manifest(path: Path) -> List[Dict[str, Any]]:
    return json.loads(path.read_text(encoding="utf-8"))


def ensure_dir(path: Path) -> None:
    path.mkdir(parents=True, exist_ok=True)


def build_headers(source: Dict[str, Any]) -> Dict[str, str]:
    headers = dict(DEFAULT_HEADERS)
    extra_headers = source.get("headers") or {}
    for key, value in extra_headers.items():
        if isinstance(key, str) and isinstance(value, str) and value.strip():
            headers[key] = value.strip()
    return headers


def decode_html_bytes(raw_bytes: bytes, source: Dict[str, Any], apparent_encoding: str = "") -> str:
    candidates: List[str] = []

    def add_candidate(value: Any) -> None:
        text = (value or "").strip() if isinstance(value, str) else ""
        if text and text.lower() not in {item.lower() for item in candidates}:
            candidates.append(text)

    add_candidate(source.get("encoding"))
    head_text = raw_bytes[:4096].decode("ascii", errors="ignore")
    meta_charset = re.search(r"charset=['\"]?\s*([A-Za-z0-9._-]+)", head_text, re.IGNORECASE)
    if meta_charset:
        add_candidate(meta_charset.group(1))
    add_candidate(apparent_encoding)
    for fallback in ("utf-8", "gb18030", "gbk"):
        add_candidate(fallback)

    for encoding in candidates:
        try:
            return raw_bytes.decode(encoding)
        except UnicodeDecodeError:
            continue
    return raw_bytes.decode("utf-8", errors="ignore")


def ps_quote(value: str) -> str:
    return "'" + value.replace("'", "''") + "'"


def fetch_html_via_powershell(source: Dict[str, Any]) -> str:
    with tempfile.TemporaryDirectory(prefix="legal_fetch_") as temp_dir:
        output_path = Path(temp_dir) / f"{source.get('id', 'source')}.html"
        lines = [
            "$ErrorActionPreference = 'Stop'",
            "$ProgressPreference = 'SilentlyContinue'",
            "$headers = @{}",
        ]
        for key, value in build_headers(source).items():
            lines.append(f"$headers[{ps_quote(key)}] = {ps_quote(value)}")
        lines.extend(
            [
                f"Invoke-WebRequest -Uri {ps_quote(source['url'])} -Headers $headers -OutFile {ps_quote(str(output_path))}",
                f"Get-Content -LiteralPath {ps_quote(str(output_path))} -Encoding Byte | Out-Null",
            ]
        )
        completed = subprocess.run(
            ["powershell", "-NoLogo", "-NoProfile", "-Command", "\n".join(lines)],
            capture_output=True,
            text=True,
            encoding="utf-8",
            timeout=90,
            check=False,
        )
        if completed.returncode != 0:
            stderr = (completed.stderr or completed.stdout or "").strip()
            raise RuntimeError(f"PowerShell 抓取失败: {stderr or source['url']}")
        raw_bytes = output_path.read_bytes()
    return decode_html_bytes(raw_bytes, source)


def fetch_html(source: Dict[str, Any]) -> str:
    fetch_mode = str(source.get("fetchMode", "auto")).strip().lower()
    if fetch_mode == "powershell":
        return fetch_html_via_powershell(source)

    try:
        response = requests.get(
            source["url"],
            headers=build_headers(source),
            timeout=30,
            verify=bool(source.get("verifySsl", True)),
        )
        response.raise_for_status()
        return decode_html_bytes(response.content, source, response.apparent_encoding or response.encoding or "")
    except Exception:
        if fetch_mode == "requests":
            raise
        return fetch_html_via_powershell(source)


def html_to_lines(html_text: str) -> List[str]:
    text = COMMENT_RE.sub(" ", html_text)
    text = SCRIPT_RE.sub(" ", text)
    text = BLOCK_TAG_RE.sub("\n", text)
    text = TAG_RE.sub(" ", text)
    text = html.unescape(text)
    text = text.replace("\r", "\n").replace("\u3000", " ").replace("\xa0", " ").replace("&nbsp;", " ")
    text = re.sub(r"[ \t\f\v]+", " ", text)
    text = re.sub(r"\n{2,}", "\n", text)
    lines = [normalize_line(line) for line in text.split("\n")]
    return [line for line in lines if line]


def normalize_line(line: str) -> str:
    normalized = line.strip().replace(" ", " ")
    normalized = re.sub(r"\s+", " ", normalized)
    return normalized.strip()


def normalize_match_key(value: str) -> str:
    return re.sub(r"\s+", "", normalize_line(value))


def is_noise_line(line: str) -> bool:
    if not line:
        return True
    if line in NOISE_EXACT:
        return True
    if len(line) <= 1 and not ARTICLE_RE.match(line):
        return True
    return any(marker in line for marker in NOISE_CONTAINS)


def should_stop(line: str) -> bool:
    return any(marker in line for marker in STOP_MARKERS)


def should_stop_for_source(line: str, source: Dict[str, Any]) -> bool:
    if is_source_identity_line(line, source):
        return False
    markers = list(STOP_MARKERS) + list(source.get("stopMarkers") or [])
    return any(marker in line for marker in markers if marker)


def is_noise_line_for_source(line: str, source: Dict[str, Any]) -> bool:
    if is_source_identity_line(line, source):
        return False
    if is_noise_line(line):
        return True
    ignore_exact = {str(item).strip() for item in (source.get("ignoreExact") or []) if str(item).strip()}
    if line in ignore_exact:
        return True
    ignore_contains = [str(item).strip() for item in (source.get("ignoreContains") or []) if str(item).strip()]
    return any(marker in line for marker in ignore_contains)


def is_source_identity_line(line: str, source: Dict[str, Any]) -> bool:
    normalized = normalize_line(line)
    if not normalized:
        return False

    document_name = normalize_line(str(source.get("documentName", "")))
    if document_name and (normalized == document_name or normalized in document_name or document_name in normalized):
        return True

    issuer = normalize_line(str(source.get("issuer", ""))).replace("、", " ")
    normalized_compact = normalized.replace("、", " ")
    if issuer and issuer in normalized_compact:
        return True

    return False


def trim_relevant_lines(lines: List[str], source: Dict[str, Any]) -> List[str]:
    document_name = source["documentName"]
    article_index = next((index for index, line in enumerate(lines) if ARTICLE_RE.match(line) or POINT_RE.match(line)), None)
    if article_index is None:
        raise ValueError(f"{document_name} 未识别到任何条文")

    title_indexes = [index for index, line in enumerate(lines) if document_name in line]
    start_index = max(0, article_index - int(source.get("preambleLines", 18)))
    if title_indexes:
        title_index = max(index for index in title_indexes if index <= article_index)
        start_index = min(start_index, title_index)

    trimmed: List[str] = []
    for line in lines[start_index:]:
        if should_stop_for_source(line, source):
            break
        if is_noise_line_for_source(line, source):
            continue
        trimmed.append(line)
    return trimmed


def get_custom_section_header(line: str, source: Dict[str, Any] | None = None) -> Tuple[str, str] | None:
    if not source:
        return None
    headings = [
        normalize_line(str(item))
        for item in (source.get("sectionHeadings") or [])
        if normalize_line(str(item))
    ]
    if not headings:
        return None

    line_key = normalize_match_key(line)
    for index, heading in enumerate(headings, start=1):
        if line_key == normalize_match_key(heading):
            return f"{source.get('sectionNumberPrefix', '专题')}{index}", heading
    return None


def is_section_header(line: str, source: Dict[str, Any] | None = None) -> bool:
    normalized = (line or "").strip()
    return bool(
        TOPIC_SECTION_RE.match(normalized)
        or SUBTOPIC_SECTION_RE.match(normalized)
        or get_custom_section_header(normalized, source)
    )


def split_section_header(line: str, source: Dict[str, Any] | None = None) -> Tuple[str, str] | None:
    normalized = (line or "").strip()
    custom_header = get_custom_section_header(normalized, source)
    if custom_header:
        return custom_header
    matched = SUBTOPIC_SECTION_RE.match(normalized)
    if matched:
        number, title = matched.groups()
        return number, title.strip() or number
    matched = TOPIC_SECTION_RE.match(normalized)
    if matched:
        number, title = matched.groups()
        return number, title.strip() or number
    return None


def trim_sectioned_article_lines(lines: List[str], source: Dict[str, Any]) -> List[str]:
    document_name = source["documentName"]
    first_section_index = next((idx for idx, line in enumerate(lines) if is_section_header(line, source)), None)
    title_indexes = [idx for idx, line in enumerate(lines) if line == document_name or document_name in line]
    if title_indexes:
        eligible = title_indexes[-int(source.get("titleOccurrenceFromEnd", 1)) :]
        start_index = eligible[0] if eligible else title_indexes[-1]
        if first_section_index is not None and start_index > first_section_index:
            eligible_before_section = [idx for idx in title_indexes if idx <= first_section_index]
            if eligible_before_section:
                start_index = eligible_before_section[-1]
    else:
        start_index = max(0, (first_section_index or 0) - int(source.get("preambleLines", 6)))

    section_start_marker = normalize_match_key(str(source.get("sectionStartMarker", "")))
    if section_start_marker:
        marker_index = next(
            (
                idx
                for idx in range(start_index, len(lines))
                if normalize_match_key(lines[idx]).startswith(section_start_marker)
            ),
            None,
        )
        if marker_index is not None:
            start_index = marker_index

    trimmed: List[str] = []
    for line in lines[start_index:]:
        if should_stop_for_source(line, source):
            break
        if is_noise_line_for_source(line, source):
            continue
        trimmed.append(line)
    return trimmed


def clean_article_content(content: str) -> str:
    cleaned = content.strip()
    for pattern in ARTICLE_NOISE_PATTERNS:
        cleaned = pattern.sub("", cleaned).strip()
    cleaned = re.sub(r"\s+\n", "\n", cleaned)
    cleaned = re.sub(r"\n{3,}", "\n\n", cleaned)
    return cleaned.strip()


def filter_articles(source: Dict[str, Any], articles: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    include_numbers = {
        str(item).strip()
        for item in (source.get("includeArticleNumbers") or [])
        if str(item).strip()
    }
    exclude_numbers = {
        str(item).strip()
        for item in (source.get("excludeArticleNumbers") or [])
        if str(item).strip()
    }

    filtered = articles
    if include_numbers:
        filtered = [article for article in filtered if article.get("articleNumber", "").strip() in include_numbers]
    if exclude_numbers:
        filtered = [article for article in filtered if article.get("articleNumber", "").strip() not in exclude_numbers]
    return filtered


def parse_sectioned_article(lines: List[str], source: Dict[str, Any]) -> List[Dict[str, Any]]:
    document_name = source["documentName"]
    relevant_lines = trim_sectioned_article_lines(lines, source)
    articles: List[Dict[str, Any]] = []
    intro_lines: List[str] = []
    current: Dict[str, Any] | None = None
    publish_date = ""

    def flush_current() -> None:
        nonlocal current
        if current is None:
            return
        content = "\n".join(part for part in current.pop("contentLines", []) if part).strip()
        content = clean_article_content(content)
        if not content:
            current = None
            return
        current["content"] = content
        current["summary"] = build_summary(content)
        current["keywords"] = build_keywords(source, current)
        current["aliases"] = source.get("articleAliases", {}).get(current["articleNumber"], [])
        if publish_date:
            current["publishDate"] = publish_date
        articles.append(current)
        current = None

    for line in relevant_lines:
        if line == document_name:
            continue
        date_match = DATE_LINE_RE.match(line)
        if date_match:
            publish_date = date_match.group(1).strip()
            continue

        header = split_section_header(line, source)
        if header:
            flush_current()
            article_number, article_title = header
            current = {
                "chapterTitle": source.get("topic", ""),
                "sectionTitle": line,
                "articleNumber": article_number,
                "articleTitle": article_title,
                "contentLines": [],
            }
            continue

        if current is None:
            intro_lines.append(line)
        else:
            current["contentLines"].append(line)

    flush_current()

    intro_content = clean_article_content("\n".join(intro_lines).strip())
    if intro_content and bool(source.get("includeIntro", True)):
        intro_number = source.get("introArticleNumber", "导读")
        intro_article = {
            "chapterTitle": source.get("topic", ""),
            "sectionTitle": source.get("introArticleTitle", "基本说明"),
            "articleNumber": intro_number,
            "articleTitle": source.get("introArticleTitle", "基本说明"),
            "content": intro_content,
            "summary": build_summary(intro_content),
            "keywords": build_keywords(
                source,
                {
                    "chapterTitle": source.get("topic", ""),
                    "sectionTitle": source.get("introArticleTitle", "基本说明"),
                    "articleNumber": intro_number,
                    "articleTitle": source.get("introArticleTitle", "基本说明"),
                },
            ),
            "aliases": source.get("articleAliases", {}).get(intro_number, []),
        }
        if publish_date:
            intro_article["publishDate"] = publish_date
        articles.insert(0, intro_article)

    if not articles:
        raise ValueError(f"{document_name} 未识别到可导入的说明分节")
    return articles


def parse_articles(lines: List[str], source: Dict[str, Any]) -> List[Dict[str, Any]]:
    document_name = source["documentName"]
    part_title = ""
    chapter_title = ""
    section_title = ""
    current: Dict[str, Any] | None = None
    articles: List[Dict[str, Any]] = []

    def flush_current() -> None:
        nonlocal current
        if current is None:
            return
        content = "\n".join(part for part in current.pop("contentLines", []) if part).strip()
        content = clean_article_content(content)
        if not content:
            current = None
            return
        current["content"] = content
        current["summary"] = build_summary(content)
        current["keywords"] = build_keywords(source, current)
        current["aliases"] = source.get("articleAliases", {}).get(current["articleNumber"], [])
        articles.append(current)
        current = None

    for line in lines:
        if line == document_name:
            continue

        if PART_RE.match(line):
            part_title = line
            chapter_title = ""
            section_title = ""
            continue
        if CHAPTER_RE.match(line):
            chapter_title = line
            section_title = ""
            continue
        if SECTION_RE.match(line):
            section_title = line
            continue

        article_match = ARTICLE_RE.match(line)
        point_match = POINT_RE.match(line)
        if article_match or point_match:
            flush_current()
            if article_match:
                article_number, tail = article_match.groups()
            else:
                point_number, tail = point_match.groups()
                article_number = f"第{point_number}项"
            current = {
                "partTitle": part_title,
                "chapterTitle": chapter_title,
                "sectionTitle": section_title,
                "articleNumber": article_number,
                "articleTitle": "",
                "contentLines": [tail.strip(" ：")] if tail.strip(" ：") else [],
            }
            continue

        if current is not None:
            current["contentLines"].append(line)

    flush_current()
    if not articles:
        raise ValueError(f"{document_name} 条文切分失败")
    return articles


def parse_guiding_case_batch(lines: List[str], source: Dict[str, Any]) -> List[Dict[str, Any]]:
    first_case_index = next((idx for idx, line in enumerate(lines) if CASE_BLOCK_RE.match(line)), None)
    if first_case_index is None:
        return parse_guiding_case_batch_title_first(lines, source)

    records: List[Dict[str, Any]] = []
    current_number = ""
    current_title = ""
    current_lines: List[str] = []

    def flush_current() -> None:
        nonlocal current_number, current_title, current_lines
        if not current_number:
            return
        record = build_case_entry_record(source, current_number, current_title, current_lines)
        if record:
            records.append(record)
        current_number = ""
        current_title = ""
        current_lines = []

    for line in lines[first_case_index:]:
        if should_stop_for_source(line, source):
            break
        if CASE_BLOCK_RE.match(line):
            flush_current()
            current_number = line.strip()
            continue
        if not current_number:
            continue
        if not current_title:
            section_name, _ = split_case_section_line(line)
            if section_name:
                current_lines.append(line)
                continue
            if line.startswith("【") and line.endswith("】"):
                current_lines.append(line)
                continue
            if line and not line.startswith("（"):
                current_title = line.strip()
                continue
        current_lines.append(line)

    flush_current()
    if not records:
        raise ValueError(f"{source['documentName']} 案例切分失败")
    return records


def normalize_case_number_text(text: str) -> str:
    normalized = text.strip()
    if normalized.startswith("（") and normalized.endswith("）"):
        normalized = normalized[1:-1].strip()
    return normalized


def is_title_first_guiding_case_start(lines: List[str], index: int) -> bool:
    title = lines[index].strip()
    if not title or title.startswith("（") or title.startswith("【"):
        return False
    if not title.endswith("案"):
        return False
    if index + 1 >= len(lines):
        return False
    return bool(re.match(r"^（检例第\d+号）$", lines[index + 1].strip()))


def parse_guiding_case_batch_title_first(lines: List[str], source: Dict[str, Any]) -> List[Dict[str, Any]]:
    case_starts: List[Tuple[int, str, str]] = []
    for index in range(len(lines) - 1):
        if is_title_first_guiding_case_start(lines, index):
            case_starts.append(
                (
                    index,
                    lines[index].strip(),
                    normalize_case_number_text(lines[index + 1]),
                )
            )

    if not case_starts:
        raise ValueError(f"{source['documentName']} 未识别到案例编号")

    records: List[Dict[str, Any]] = []
    for start_index, case_title, case_number in case_starts:
        next_starts = [item[0] for item in case_starts if item[0] > start_index]
        end_index = next_starts[0] if next_starts else len(lines)
        case_lines: List[str] = []
        for line in lines[start_index + 2 : end_index]:
            if should_stop_for_source(line, source):
                break
            if is_noise_line(line):
                continue
            case_lines.append(line)
        record = build_case_entry_record(source, case_number, case_title, case_lines)
        if record:
            records.append(record)

    if not records:
        raise ValueError(f"{source['documentName']} 案例切分失败")
    return records


def parse_guiding_case_single(lines: List[str], source: Dict[str, Any]) -> List[Dict[str, Any]]:
    first_case_index = next((idx for idx, line in enumerate(lines) if CASE_NUMBER_RE.match(line)), None)
    if first_case_index is None:
        raise ValueError(f"{source['documentName']} 未识别到案例编号")

    trimmed: List[str] = []
    for line in lines[first_case_index:]:
        if should_stop_for_source(line, source):
            break
        if is_noise_line(line):
            continue
        trimmed.append(line)

    number = ""
    title = ""
    body_start = 0
    for index, line in enumerate(trimmed):
        matched = CASE_NUMBER_RE.match(line)
        if matched:
            number = matched.group(1)
            inline_title = (matched.group(2) or "").strip()
            if inline_title:
                title = inline_title
            body_start = index + 1
            continue
        section_name, _ = split_case_section_line(line)
        if section_name:
            body_start = index
            break
        if not title and not line.startswith("（") and "发布时间" not in line and "来源：" not in line:
            title = line.strip()

    if not number:
        raise ValueError(f"{source['documentName']} 未识别到案例编号")
    if not title:
        title = source.get("fallbackTitle", source["documentName"])

    record = build_case_entry_record(source, number, title, trimmed[body_start:])
    if not record:
        raise ValueError(f"{source['documentName']} 案例正文解析失败")
    return [record]


def split_case_section_line(line: str) -> Tuple[str | None, str]:
    normalized = line.strip()
    for section_name in CASE_SECTION_NAMES:
        if normalized == section_name:
            return section_name, ""
        for separator in (" ", "：", ":"):
            prefix = f"{section_name}{separator}"
            if normalized.startswith(prefix):
                return section_name, normalized[len(prefix):].strip()
    return None, normalized


def extract_case_sections(lines: List[str]) -> Dict[str, str]:
    sections: Dict[str, List[str]] = {}
    current_key = "前言"
    sections[current_key] = []

    for line in lines:
        normalized = line.strip()
        if not normalized:
            continue
        section_name, section_content = split_case_section_line(normalized)
        if section_name:
            current_key = section_name
            sections.setdefault(current_key, [])
            if section_content:
                sections[current_key].append(section_content)
            continue
        sections.setdefault(current_key, []).append(normalized)

    return {key: "\n".join(value).strip() for key, value in sections.items() if any(value)}


def build_case_entry_record(source: Dict[str, Any], case_number: str, case_title: str, case_lines: List[str]) -> Dict[str, Any]:
    sections = extract_case_sections(case_lines)
    raw_keywords = sections.get("关键词", "")
    content_parts: List[str] = []
    if case_title:
        content_parts.append(case_title)
    preamble = sections.get("前言", "")
    if preamble:
        content_parts.append(preamble)
    for section_name in CASE_SECTION_NAMES:
        text = sections.get(section_name, "")
        if text:
            content_parts.append(section_name)
            content_parts.append(text)

    content = "\n".join(part for part in content_parts if part).strip()
    if not content:
        return {}

    summary = sections.get("要旨") or sections.get("裁判要点") or sections.get("基本案情") or build_summary(content)
    if len(summary) > 220:
        summary = summary[:220].rstrip("，。；、 ") + "..."

    keywords: List[str] = []
    add_if_text(keywords, source.get("businessDomain"))
    add_if_text(keywords, source.get("topic"))
    add_if_text(keywords, case_number)
    add_if_text(keywords, case_title)
    for token in re.split(r"[、，,/\s]+", raw_keywords):
        add_if_text(keywords, token)

    return {
        "knowledgeType": source["knowledgeType"],
        "knowledgeSubtype": first_non_blank(source.get("knowledgeSubtype", ""), source.get("documentLevel", "")),
        "documentName": source["documentName"],
        "documentLevel": source.get("documentLevel", ""),
        "issuer": source.get("issuer", ""),
        "businessDomain": first_non_blank(source.get("businessDomain", ""), infer_case_domain(raw_keywords)),
        "topic": source.get("topic", ""),
        "articleNumber": case_number,
        "articleTitle": case_title,
        "summary": summary,
        "keywords": dedupe(keywords),
        "aliases": dedupe([case_title]),
        "content": content,
        "effectiveDate": source.get("effectiveDate", ""),
        "currentStatus": source.get("currentStatus", "公开发布"),
        "sourceName": source.get("sourceName", ""),
        "sourceUrl": source["url"],
        "sourceType": "official_crawl",
        "sourceReliability": 1,
    }


def infer_case_domain(raw_keywords: str) -> str:
    if "刑事" in raw_keywords:
        return "刑事"
    if "民事" in raw_keywords:
        return "民事"
    if "行政" in raw_keywords:
        return "行政"
    if "执行" in raw_keywords:
        return "执行"
    if "劳动" in raw_keywords:
        return "劳动"
    return ""


def build_keywords(source: Dict[str, Any], article: Dict[str, Any]) -> List[str]:
    values: List[str] = []
    for value in [
        source.get("knowledgeType"),
        source.get("knowledgeSubtype"),
        source.get("businessDomain"),
        source.get("topic"),
        source.get("documentName"),
        article.get("partTitle"),
        article.get("chapterTitle"),
        article.get("sectionTitle"),
        article.get("articleNumber"),
        article.get("articleTitle"),
    ]:
        add_if_text(values, value)
    for value in source.get("extraKeywords") or []:
        add_if_text(values, value)
    return dedupe(values)


def build_summary(content: str) -> str:
    first_line = next((line.strip() for line in content.splitlines() if line.strip()), "")
    if len(first_line) <= 72:
        return first_line
    return first_line[:72].rstrip("，。；、. ") + "..."


def add_if_text(values: List[str], value: Any) -> None:
    text = (value or "").strip() if isinstance(value, str) else ""
    if text:
        values.append(text)


def first_non_blank(*values: Any) -> str:
    for value in values:
        text = (value or "").strip() if isinstance(value, str) else ""
        if text:
            return text
    return ""


def dedupe(values: Iterable[str]) -> List[str]:
    seen = set()
    result: List[str] = []
    for value in values:
        text = (value or "").strip()
        if not text or text in seen:
            continue
        seen.add(text)
        result.append(text)
    return result


def build_document_record(source: Dict[str, Any], articles: List[Dict[str, Any]]) -> Dict[str, Any]:
    return {
        "documentName": source["documentName"],
        "knowledgeType": source["knowledgeType"],
        "documentLevel": source.get("documentLevel", ""),
        "issuer": source.get("issuer", ""),
        "businessDomain": source.get("businessDomain", ""),
        "topic": source.get("topic", ""),
        "effectiveDate": source.get("effectiveDate", ""),
        "currentStatus": source.get("currentStatus", "现行有效"),
        "sourceName": source.get("sourceName", ""),
        "sourceUrl": source["url"],
        "sourceType": "official_crawl",
        "sourceReliability": 1,
        "articles": articles,
    }


def build_nested_document_record(source: Dict[str, Any], entries: List[Dict[str, Any]]) -> Dict[str, Any]:
    return {
        "documentName": source["documentName"],
        "knowledgeType": source["knowledgeType"],
        "knowledgeSubtype": first_non_blank(source.get("knowledgeSubtype", ""), source.get("documentLevel", "")),
        "documentLevel": source.get("documentLevel", ""),
        "issuer": source.get("issuer", ""),
        "businessDomain": source.get("businessDomain", ""),
        "topic": source.get("topic", ""),
        "effectiveDate": source.get("effectiveDate", ""),
        "currentStatus": source.get("currentStatus", "公开发布"),
        "sourceName": source.get("sourceName", ""),
        "sourceUrl": source["url"],
        "sourceType": "official_crawl",
        "sourceReliability": 1,
        "entries": entries,
    }


def save_json(path: Path, payload: Any) -> None:
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")


def process_source(source: Dict[str, Any], base_dir: Path) -> Dict[str, Any]:
    raw_dir = base_dir / "raw" / source.get("rawGroup", "misc")
    processed_dir = base_dir / "processed" / source.get("processedGroup", "misc")
    ensure_dir(raw_dir)
    ensure_dir(processed_dir)

    html_text = fetch_html(source)
    raw_html_path = raw_dir / f"{source['id']}.html"
    raw_meta_path = raw_dir / f"{source['id']}.meta.json"
    raw_html_path.write_text(html_text, encoding="utf-8")
    save_json(raw_meta_path, source)

    lines = html_to_lines(html_text)
    parser_name = source.get("parser", "article_units")
    if parser_name == "guiding_case_batch":
        entries = parse_guiding_case_batch(lines, source)
        document = build_nested_document_record(source, entries)
        record_count = len(entries)
    elif parser_name == "guiding_case_single":
        entries = parse_guiding_case_single(lines, source)
        document = build_nested_document_record(source, entries)
        record_count = len(entries)
    elif parser_name == "sectioned_article":
        articles = parse_sectioned_article(lines, source)
        articles = filter_articles(source, articles)
        document = build_document_record(source, articles)
        record_count = len(articles)
    else:
        relevant_lines = trim_relevant_lines(lines, source)
        articles = parse_articles(relevant_lines, source)
        articles = filter_articles(source, articles)
        document = build_document_record(source, articles)
        record_count = len(articles)

    processed_path = processed_dir / f"{source['id']}.json"
    save_json(processed_path, document)
    return {
        "id": source["id"],
        "documentName": source["documentName"],
        "processedPath": str(processed_path),
        "recordCount": record_count,
    }


def import_batch(import_path: Path, api_base: str, rebuild_index: bool) -> Dict[str, Any]:
    with import_path.open("rb") as handle:
        response = requests.post(
            f"{api_base.rstrip('/')}/legal-knowledge/import/file",
            files={"file": (import_path.name, handle, "application/json")},
            data={"rebuildIndex": str(rebuild_index).lower()},
            timeout=180,
        )
    response.raise_for_status()
    return response.json()


def main() -> None:
    parser = argparse.ArgumentParser(description="抓取官方法律法规、司法解释和公开案例语料")
    parser.add_argument("--manifest", default=str(DEFAULT_MANIFEST), help="语料清单 JSON")
    parser.add_argument("--base-dir", default=str(DEFAULT_BASE_DIR), help="原始/处理目录根路径")
    parser.add_argument(
        "--import-api",
        default="",
        help="可选，导入到 Spring Boot 的 API 根地址，例如 http://127.0.0.1:8080/api",
    )
    parser.add_argument("--rebuild-index", action="store_true", help="导入后是否重建检索索引")
    args = parser.parse_args()

    manifest_path = Path(args.manifest)
    base_dir = Path(args.base_dir)
    ensure_dir(base_dir)
    ensure_dir(base_dir / "raw")
    ensure_dir(base_dir / "processed")

    manifest = load_manifest(manifest_path)
    batch_records = []
    summary = []
    for source in manifest:
        result = process_source(source, base_dir)
        batch_records.append(json.loads(Path(result["processedPath"]).read_text(encoding="utf-8")))
        summary.append(result)
        print(f"[ok] {result['documentName']} -> {result['recordCount']} 条记录")

    batch_path = base_dir / "processed" / "import_batch_official_sources.json"
    save_json(batch_path, batch_records)
    print(f"[batch] {batch_path}")

    if args.import_api:
        response = import_batch(batch_path, args.import_api, args.rebuild_index)
        print(json.dumps(response, ensure_ascii=False, indent=2))

    summary_path = base_dir / "processed" / "import_batch_official_sources.summary.json"
    save_json(summary_path, summary)


if __name__ == "__main__":
    main()
