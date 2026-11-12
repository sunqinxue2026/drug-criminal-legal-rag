from __future__ import annotations

import html
import json
import re
import shutil
from pathlib import Path
from typing import Any, Dict, Iterable, List


ROOT_DIR = Path(__file__).resolve().parents[1]
DRUG_VERTICAL_DIR = ROOT_DIR / "data" / "legal_sources" / "drug_vertical"
MANIFEST_PATH = DRUG_VERTICAL_DIR / "source_manifest.json"

BLOCK_TAG_RE = re.compile(r"(?i)</(p|div|li|tr|td|h1|h2|h3|h4|h5|section|article|ul|ol|table)>|<br\s*/?>")
TAG_RE = re.compile(r"(?is)<[^>]+>")
SCRIPT_RE = re.compile(r"(?is)<script.*?</script>|<style.*?</style>|<noscript.*?</noscript>")
COMMENT_RE = re.compile(r"(?is)<!--.*?-->")


TARGET_SOURCES: List[Dict[str, Any]] = [
    {
        "id": "defender_choice_reply_judicial_interpretation_2025",
        "localHtml": DRUG_VERTICAL_DIR / "raw" / "guides" / "defender_choice_reply_explainer_2025.html",
        "url": "https://www.spp.gov.cn/xwfbh/wsfbt/202506/t20250626_699332.shtml",
        "documentName": "关于依法保障在押犯罪嫌疑人、被告人选择辩护人权利有关问题的批复",
        "knowledgeType": "司法解释",
        "knowledgeSubtype": "程序权利保障",
        "documentLevel": "司法解释",
        "issuer": "最高人民法院、最高人民检察院",
        "sourceName": "中华人民共和国最高人民检察院官网",
        "businessDomain": "涉毒程序应对",
        "topic": "家属委托辩护、会见与法律援助衔接",
        "effectiveDate": "2025-06-27",
        "currentStatus": "现行有效",
        "rawGroup": "judicial_interpretations",
        "processedGroup": "judicial_interpretations",
        "parser": "single_article_slice",
        "articleNumber": "全文",
        "articleTitle": "批复全文",
        "sliceStartMarker": "《最高人民法院、最高人民检察院关于依法保障在押犯罪嫌疑人、被告人选择辩护人权利有关问题的批复》已于",
        "sliceStopMarkers": [
            "相关链接：",
            "Copyrights",
            "备案序号：",
        ],
        "extraKeywords": [
            "家属委托辩护",
            "会见权",
            "法律援助终止",
            "在押犯罪嫌疑人",
            "被告人",
        ],
    },
    {
        "id": "drug_moneylaundering_dual_investigation_story_2023",
        "localHtml": DRUG_VERTICAL_DIR / "raw" / "guides" / "release_on_top10_drug_cases_story.html",
        "url": "https://www.spp.gov.cn/spp/zdgz/202306/t20230627_618543.shtml",
        "documentName": "一案双查 既打击毒品犯罪又追踪毒资流向",
        "knowledgeType": "官方解读",
        "knowledgeSubtype": "案例解读",
        "documentLevel": "专题解读",
        "issuer": "最高人民检察院",
        "sourceName": "中华人民共和国最高人民检察院官网",
        "businessDomain": "涉毒洗钱与毒资追查",
        "topic": "一案双查、追踪毒资流向与洗钱线索深挖",
        "effectiveDate": "2023-06-27",
        "currentStatus": "公开发布",
        "rawGroup": "guides",
        "processedGroup": "guides",
        "parser": "single_article_slice",
        "articleNumber": "专题1",
        "articleTitle": "一案双查 既打击毒品犯罪又追踪毒资流向",
        "sliceStartMarker": "一案双查 既打击毒品犯罪又追踪毒资流向",
        "sliceStopMarkers": [
            "治罪与治理结合 典型事例为强化禁毒综合治理提供指引",
            "责任编辑：",
            "Copyrights",
        ],
        "extraKeywords": [
            "毒资流向",
            "洗钱",
            "神仙水",
            "一案双查",
            "打财断血",
        ],
    },
    {
        "id": "medical_addictive_cases_notice_2024",
        "localHtml": DRUG_VERTICAL_DIR / "raw" / "guides" / "medical_addictive_cases_qa_2024.html",
        "url": "https://www.spp.gov.cn/spp/xwfbh/wsfbt/202406/t20240621_657334.shtml",
        "documentName": "关于印发惩治涉麻精药品等成瘾性物质滥用犯罪典型案例的通知",
        "knowledgeType": "部门规范",
        "knowledgeSubtype": "工作通知",
        "documentLevel": "规范性文件",
        "issuer": "最高人民检察院",
        "sourceName": "中华人民共和国最高人民检察院官网",
        "businessDomain": "麻精药品与成瘾性物质治理",
        "topic": "麻精药品、依托咪酯等成瘾性物质犯罪办案指引",
        "effectiveDate": "2024-06-18",
        "currentStatus": "公开发布",
        "rawGroup": "guides",
        "processedGroup": "guides",
        "parser": "single_article_slice",
        "articleNumber": "全文",
        "articleTitle": "通知全文",
        "sliceStartMarker": "关于印发惩治涉麻精药品等成瘾性物质滥用犯罪典型案例的通知",
        "sliceStopMarkers": [
            "案例一",
            "责任编辑：",
            "Copyrights",
        ],
        "extraKeywords": [
            "麻精药品",
            "依托咪酯",
            "成瘾性物质",
            "典型案例",
            "通知",
        ],
    },
]


def ensure_dir(path: Path) -> None:
    path.mkdir(parents=True, exist_ok=True)


def save_json(path: Path, payload: Any) -> None:
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")


def normalize_line(line: str) -> str:
    text = line.strip().replace("\xa0", " ").replace("\u3000", " ")
    text = re.sub(r"\s+", " ", text)
    return text.strip()


def html_to_lines(html_text: str) -> List[str]:
    text = COMMENT_RE.sub(" ", html_text)
    text = SCRIPT_RE.sub(" ", text)
    text = BLOCK_TAG_RE.sub("\n", text)
    text = TAG_RE.sub(" ", text)
    text = html.unescape(text)
    text = text.replace("\r", "\n").replace("&nbsp;", " ")
    text = re.sub(r"[ \t\f\v]+", " ", text)
    text = re.sub(r"\n{2,}", "\n", text)
    lines = [normalize_line(line) for line in text.split("\n")]
    return [line for line in lines if line]


def normalize_match_key(text: str) -> str:
    normalized = normalize_line(text)
    normalized = normalized.replace(" ", "")
    return normalized


def dedupe(values: Iterable[str]) -> List[str]:
    seen = set()
    result: List[str] = []
    for value in values:
        text = str(value or "").strip()
        if not text or text in seen:
            continue
        seen.add(text)
        result.append(text)
    return result


def build_summary(content: str) -> str:
    first_line = next((line.strip() for line in content.splitlines() if line.strip()), "")
    if len(first_line) <= 72:
        return first_line
    return first_line[:72].rstrip("，。；、 ") + "..."


def build_keywords(source: Dict[str, Any], article: Dict[str, Any]) -> List[str]:
    return dedupe(
        [
            source.get("knowledgeType"),
            source.get("knowledgeSubtype"),
            source.get("businessDomain"),
            source.get("topic"),
            source.get("documentName"),
            article.get("articleNumber"),
            article.get("articleTitle"),
            *source.get("extraKeywords", []),
        ]
    )


def build_document_record(source: Dict[str, Any], articles: List[Dict[str, Any]]) -> Dict[str, Any]:
    return {
        "documentName": source["documentName"],
        "knowledgeType": source["knowledgeType"],
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
        "articles": articles,
    }


def find_marker_index(lines: List[str], marker: str, occurrence: str = "first") -> int:
    marker_key = normalize_match_key(marker)
    matches = [idx for idx, line in enumerate(lines) if marker_key in normalize_match_key(line)]
    if not matches:
        raise ValueError(f"未找到片段标记：{marker}")
    if occurrence == "last":
        return matches[-1]
    return matches[0]


def extract_single_article_slice(lines: List[str], source: Dict[str, Any]) -> List[str]:
    start_index = find_marker_index(
        lines,
        str(source["sliceStartMarker"]),
        str(source.get("sliceStartOccurrence", "first")),
    )
    stop_markers = [str(item) for item in source.get("sliceStopMarkers", []) if str(item).strip()]
    end_index = len(lines)
    for idx in range(start_index + 1, len(lines)):
        line_key = normalize_match_key(lines[idx])
        if any(normalize_match_key(marker) in line_key for marker in stop_markers):
            end_index = idx
            break
    return lines[start_index:end_index]


def build_single_article_document(source: Dict[str, Any]) -> Dict[str, Any]:
    html_text = Path(source["localHtml"]).read_text(encoding="utf-8")
    lines = html_to_lines(html_text)
    content_lines = extract_single_article_slice(lines, source)
    content = "\n".join(content_lines).strip()
    article = {
        "chapterTitle": source.get("topic", ""),
        "sectionTitle": source.get("topic", ""),
        "articleNumber": source.get("articleNumber", "全文"),
        "articleTitle": source.get("articleTitle", source["documentName"]),
        "content": content,
        "summary": build_summary(content),
        "aliases": source.get("aliases", []),
    }
    article["keywords"] = build_keywords(source, article)
    return build_document_record(source, [article])


def copy_raw_html(source: Dict[str, Any]) -> None:
    raw_dir = DRUG_VERTICAL_DIR / "raw" / str(source["rawGroup"])
    ensure_dir(raw_dir)
    target_html = raw_dir / f"{source['id']}.html"
    target_meta = raw_dir / f"{source['id']}.meta.json"
    source_html = Path(source["localHtml"])
    if source_html.resolve() != target_html.resolve():
        shutil.copyfile(source_html, target_html)
    save_json(target_meta, {key: value for key, value in source.items() if key != "localHtml"})


def write_processed(source: Dict[str, Any], payload: Dict[str, Any]) -> None:
    processed_dir = DRUG_VERTICAL_DIR / "processed" / str(source["processedGroup"])
    ensure_dir(processed_dir)
    save_json(processed_dir / f"{source['id']}.json", payload)


def append_anhui_sixth_case() -> Dict[str, Any]:
    processed_path = DRUG_VERTICAL_DIR / "processed" / "guiding_cases" / "anhui_drug_case_examples_2024.json"
    raw_html_path = DRUG_VERTICAL_DIR / "raw" / "guiding_cases" / "anhui_drug_case_examples_2024.html"
    raw_meta_path = DRUG_VERTICAL_DIR / "raw" / "guiding_cases" / "anhui_drug_case_examples_2024.meta.json"

    payload = json.loads(processed_path.read_text(encoding="utf-8"))
    lines = html_to_lines(raw_html_path.read_text(encoding="utf-8"))
    meta = json.loads(raw_meta_path.read_text(encoding="utf-8"))

    entries = payload.get("entries", [])
    if any(entry.get("articleNumber") == "案例六" for entry in entries):
        meta["stopMarkers"] = ["安徽各地市检察院门户网站"]
        save_json(raw_meta_path, meta)
        save_json(processed_path, payload)
        return {"id": meta["id"], "recordCount": len(entries), "processedPath": str(processed_path)}

    start_index = next(
        idx
        for idx, line in enumerate(lines)
        if normalize_match_key(line) == normalize_match_key("运用大数据思维")
    )
    end_index = next(
        idx
        for idx in range(start_index + 1, len(lines))
        if normalize_match_key(lines[idx]) == normalize_match_key("安徽各地市检察院门户网站")
    )

    article_title = "运用大数据思维督促整治未成年人药品滥用问题"
    content_lines = [article_title, lines[start_index - 1], *lines[start_index + 2 : end_index]]
    content = "\n".join(content_lines).strip()
    entry = {
        "knowledgeType": payload.get("knowledgeType", ""),
        "knowledgeSubtype": payload.get("knowledgeSubtype", ""),
        "documentName": payload.get("documentName", ""),
        "documentLevel": payload.get("documentLevel", ""),
        "issuer": payload.get("issuer", ""),
        "businessDomain": payload.get("businessDomain", ""),
        "topic": payload.get("topic", ""),
        "articleNumber": "案例六",
        "articleTitle": article_title,
        "summary": article_title,
        "keywords": dedupe(
            [
                payload.get("businessDomain"),
                payload.get("topic"),
                "案例六",
                article_title,
                "右美沙芬",
                "未成年人药品滥用",
            ]
        ),
        "aliases": [article_title],
        "content": content,
        "effectiveDate": payload.get("effectiveDate", ""),
        "currentStatus": payload.get("currentStatus", ""),
        "sourceName": payload.get("sourceName", ""),
        "sourceUrl": payload.get("sourceUrl", ""),
        "sourceType": payload.get("sourceType", "official_crawl"),
        "sourceReliability": payload.get("sourceReliability", 1),
    }
    entries.append(entry)
    payload["entries"] = entries

    meta["stopMarkers"] = ["安徽各地市检察院门户网站"]
    save_json(raw_meta_path, meta)
    save_json(processed_path, payload)
    return {"id": meta["id"], "recordCount": len(entries), "processedPath": str(processed_path)}


def update_manifest(sources: List[Dict[str, Any]]) -> None:
    manifest = json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))
    by_id = {item.get("id"): item for item in manifest if isinstance(item, dict)}
    for source in sources:
        serializable = {key: value for key, value in source.items() if key != "localHtml"}
        by_id[source["id"]] = serializable

    updated_manifest: List[Dict[str, Any]] = []
    seen = set()
    for item in manifest:
        if not isinstance(item, dict):
            continue
        item_id = item.get("id")
        if item_id in by_id:
            updated_manifest.append(by_id[item_id])
            seen.add(item_id)
        else:
            updated_manifest.append(item)

    for source in sources:
        if source["id"] in seen:
            continue
        updated_manifest.append(by_id[source["id"]])

    save_json(MANIFEST_PATH, updated_manifest)


def main() -> None:
    results = []
    for source in TARGET_SOURCES:
        copy_raw_html(source)
        payload = build_single_article_document(source)
        write_processed(source, payload)
        results.append(
            {
                "id": source["id"],
                "recordCount": len(payload.get("articles", [])),
                "processedPath": str(
                    DRUG_VERTICAL_DIR / "processed" / str(source["processedGroup"]) / f"{source['id']}.json"
                ),
            }
        )

    results.append(append_anhui_sixth_case())
    update_manifest(TARGET_SOURCES)
    print(json.dumps(results, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
