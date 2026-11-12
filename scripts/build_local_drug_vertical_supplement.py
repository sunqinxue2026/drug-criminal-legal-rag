from __future__ import annotations

import argparse
import html
import json
import re
import shutil
from pathlib import Path
from typing import Any, Dict, Iterable, List


ROOT_DIR = Path(__file__).resolve().parents[1]
DRUG_VERTICAL_DIR = ROOT_DIR / "data" / "legal_sources" / "drug_vertical"
MANIFEST_PATH = DRUG_VERTICAL_DIR / "source_manifest.json"
LOCAL_CRAWL_DIR = ROOT_DIR / "data" / "public_site_crawls" / "spc_judicial_interpretations"

BLOCK_TAG_RE = re.compile(r"(?i)</(p|div|li|tr|td|h1|h2|h3|h4|h5|section|article|ul|ol|table)>|<br\s*/?>")
TAG_RE = re.compile(r"(?is)<[^>]+>")
SCRIPT_RE = re.compile(r"(?is)<script.*?</script>|<style.*?</style>|<noscript.*?</noscript>")
COMMENT_RE = re.compile(r"(?is)<!--.*?-->")
ORDINAL = "一二三四五六七八九十百千万零两〇0-9"
ARTICLE_RE = re.compile(rf"^(第[{ORDINAL}]+条)\s*(.*)$")
PART_RE = re.compile(rf"^(第[{ORDINAL}]+编)\s*(.*)$")
CHAPTER_RE = re.compile(rf"^(第[{ORDINAL}]+章)\s*(.*)$")
SECTION_RE = re.compile(rf"^(第[{ORDINAL}]+节)\s*(.*)$")

NOISE_EXACT = {
    "首页",
    "English",
    "登录邮箱系统",
    "关注：",
    "字号：",
    "小",
    "大",
    "打印本页",
}


SUPPLEMENT_SOURCES: List[Dict[str, Any]] = [
    {
        "id": "money_laundering_judicial_interpretation_2024",
        "localHtml": LOCAL_CRAWL_DIR / "raw_html" / "440681.html",
        "url": "https://www.court.gov.cn/fabu/xiangqing/440681.html",
        "documentName": "最高人民法院、最高人民检察院关于办理洗钱刑事案件适用法律若干问题的解释",
        "knowledgeType": "司法解释",
        "knowledgeSubtype": "司法解释",
        "documentLevel": "司法解释",
        "issuer": "最高人民法院、最高人民检察院",
        "sourceName": "最高人民法院新闻局",
        "businessDomain": "涉毒洗钱与毒资追缴",
        "topic": "洗钱罪与毒资流转认定",
        "effectiveDate": "2024-08-20",
        "currentStatus": "现行有效",
        "rawGroup": "judicial_interpretations",
        "processedGroup": "judicial_interpretations",
        "articleAliases": {
            "第一条": ["本人实施上游犯罪能否构成洗钱", "自洗钱入罪"],
            "第二条": ["他洗钱认定", "明知他人上游犯罪所得仍代转资金"],
            "第三条": ["如何认定知道或者应当知道", "明知推定"],
            "第四条": ["洗钱情节严重标准", "五百万元以上"],
            "第五条": ["虚拟资产洗钱", "现金密集场所混同", "赌博洗钱"],
            "第六条": ["洗钱与掩饰隐瞒犯罪所得竞合"],
            "第七条": ["上游犯罪未裁判能否认定洗钱"],
            "第九条": ["洗钱罪罚金标准"],
            "第十条": ["认罪悔罪积极追缴从宽"],
            "第十二条": ["毒品犯罪属于上游犯罪", "毒资洗钱上游范围"],
            "第十三条": ["司法解释施行日期"],
        },
        "extraKeywords": ["毒资", "洗钱", "上游犯罪", "虚拟资产", "资金链"],
        "stopMarkers": [
            "责任编辑：",
            "友情链接开始",
            "友情链接结束",
            "地址 版权开始",
            "法院相关链接结束",
            "京公网安备",
            "京ICP备",
        ],
    },
    {
        "id": "death_review_rights_2019",
        "localHtml": LOCAL_CRAWL_DIR / "raw_html" / "175632.html",
        "url": "https://www.court.gov.cn/fabu/xiangqing/175632.html",
        "documentName": "最高人民法院关于死刑复核及执行程序中保障当事人合法权益的若干规定",
        "knowledgeType": "司法解释",
        "knowledgeSubtype": "程序权利保障",
        "documentLevel": "司法解释",
        "issuer": "最高人民法院",
        "sourceName": "最高人民法院网",
        "businessDomain": "涉毒重案程序权利",
        "topic": "死刑复核、执行会见与辩护保障",
        "effectiveDate": "2019-09-01",
        "currentStatus": "现行有效",
        "rawGroup": "judicial_interpretations",
        "processedGroup": "judicial_interpretations",
        "articleAliases": {
            "第一条": ["死刑复核阶段委托辩护律师", "近亲属代为委托辩护"],
            "第二条": ["死刑复核辩护意见提交期限"],
            "第四条": ["复核裁定后提交新证据怎么办", "暂停执行"],
            "第五条": ["死刑复核裁判文书送达律师"],
            "第六条": ["执行死刑前申请会见近亲属", "留下遗言"],
            "第七条": ["近亲属申请会见", "拒绝会见的处理"],
            "第八条": ["会见亲友"],
            "第九条": ["会见未成年子女", "视频通话"],
            "第十一条": ["违反会见规定的处理"],
            "第十三条": ["规定施行日期"],
        },
        "extraKeywords": ["死刑复核", "会见", "辩护律师", "近亲属", "执行程序"],
        "stopMarkers": [
            "责任编辑：",
            "友情链接开始",
            "友情链接结束",
            "地址 版权开始",
            "法院相关链接结束",
            "京公网安备",
            "京ICP备",
        ],
    },
]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="基于本地抓取 HTML 补充涉毒垂直语料")
    parser.add_argument(
        "--manifest",
        default=str(MANIFEST_PATH),
        help="涉毒垂直语料 source_manifest.json 路径",
    )
    parser.add_argument(
        "--base-dir",
        default=str(DRUG_VERTICAL_DIR),
        help="涉毒垂直语料根目录",
    )
    return parser.parse_args()


def ensure_dir(path: Path) -> None:
    path.mkdir(parents=True, exist_ok=True)


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


def is_noise_line(line: str) -> bool:
    if not line:
        return True
    if line in NOISE_EXACT:
        return True
    if len(line) == 1 and not ARTICLE_RE.match(line):
        return True
    return False


def should_stop(line: str, source: Dict[str, Any]) -> bool:
    markers = [
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
        *source.get("stopMarkers", []),
    ]
    return any(marker and marker in line for marker in markers)


def trim_relevant_lines(lines: List[str], source: Dict[str, Any]) -> List[str]:
    document_name = source["documentName"]
    article_index = next((idx for idx, line in enumerate(lines) if ARTICLE_RE.match(line)), None)
    if article_index is None:
        raise ValueError(f"{document_name} 未识别到条文起点")

    title_indexes = [idx for idx, line in enumerate(lines) if document_name in line]
    start_index = max(0, article_index - 18)
    if title_indexes:
        prior_titles = [idx for idx in title_indexes if idx <= article_index]
        if prior_titles:
            start_index = min(start_index, prior_titles[-1])

    trimmed: List[str] = []
    for line in lines[start_index:]:
        if should_stop(line, source):
            break
        if is_noise_line(line):
            continue
        trimmed.append(line)
    return trimmed


def clean_article_content(content: str) -> str:
    cleaned = content.strip()
    cleaned = re.sub(r"\s+\n", "\n", cleaned)
    cleaned = re.sub(r"\n{3,}", "\n\n", cleaned)
    return cleaned.strip()


def build_summary(content: str) -> str:
    first_line = next((line.strip() for line in content.splitlines() if line.strip()), "")
    if len(first_line) <= 72:
        return first_line
    return first_line[:72].rstrip("，。；、. ") + "..."


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


def build_keywords(source: Dict[str, Any], article: Dict[str, Any]) -> List[str]:
    base_values = [
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
        *(source.get("extraKeywords") or []),
    ]
    return dedupe(base_values)


def parse_articles(lines: List[str], source: Dict[str, Any]) -> List[Dict[str, Any]]:
    part_title = ""
    chapter_title = ""
    section_title = ""
    current: Dict[str, Any] | None = None
    articles: List[Dict[str, Any]] = []

    def flush_current() -> None:
        nonlocal current
        if current is None:
            return
        content = "\n".join(current.pop("contentLines", [])).strip()
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
        if line == source["documentName"]:
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

        matched = ARTICLE_RE.match(line)
        if matched:
            flush_current()
            article_number, tail = matched.groups()
            current = {
                "partTitle": part_title,
                "chapterTitle": chapter_title,
                "sectionTitle": section_title,
                "articleNumber": article_number,
                "articleTitle": "",
                "contentLines": [tail.strip()] if tail.strip() else [],
            }
            continue

        if current is not None:
            current["contentLines"].append(line)

    flush_current()
    if not articles:
        raise ValueError(f"{source['documentName']} 条文切分失败")
    return articles


def build_document_record(source: Dict[str, Any], articles: List[Dict[str, Any]]) -> Dict[str, Any]:
    return {
        "documentName": source["documentName"],
        "knowledgeType": source["knowledgeType"],
        "documentLevel": source["documentLevel"],
        "issuer": source["issuer"],
        "businessDomain": source["businessDomain"],
        "topic": source["topic"],
        "effectiveDate": source["effectiveDate"],
        "currentStatus": source["currentStatus"],
        "sourceName": source["sourceName"],
        "sourceUrl": source["url"],
        "sourceType": "official_crawl",
        "sourceReliability": 1,
        "articles": articles,
    }


def save_json(path: Path, payload: Any) -> None:
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")


def process_source(source: Dict[str, Any], base_dir: Path) -> Dict[str, Any]:
    raw_dir = base_dir / "raw" / source["rawGroup"]
    processed_dir = base_dir / "processed" / source["processedGroup"]
    ensure_dir(raw_dir)
    ensure_dir(processed_dir)

    local_html_path = Path(source["localHtml"])
    html_text = local_html_path.read_text(encoding="utf-8")
    lines = html_to_lines(html_text)
    relevant_lines = trim_relevant_lines(lines, source)
    articles = parse_articles(relevant_lines, source)
    document = build_document_record(source, articles)

    raw_html_output = raw_dir / f"{source['id']}.html"
    raw_meta_output = raw_dir / f"{source['id']}.meta.json"
    processed_output = processed_dir / f"{source['id']}.json"

    shutil.copyfile(local_html_path, raw_html_output)
    meta_payload = {key: value for key, value in source.items() if key != "localHtml"}
    save_json(raw_meta_output, meta_payload)
    save_json(processed_output, document)

    return {
        "id": source["id"],
        "documentName": source["documentName"],
        "recordCount": len(articles),
        "processedPath": str(processed_output),
    }


def merge_manifest(manifest_path: Path, sources: List[Dict[str, Any]]) -> None:
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    by_id = {item.get("id"): item for item in manifest if isinstance(item, dict)}

    for source in sources:
        payload = {key: value for key, value in source.items() if key != "localHtml"}
        if source["id"] in by_id:
            by_id[source["id"]].update(payload)
        else:
            manifest.append(payload)

    save_json(manifest_path, manifest)


def main() -> None:
    args = parse_args()
    base_dir = Path(args.base_dir).resolve()
    manifest_path = Path(args.manifest).resolve()

    ensure_dir(base_dir)
    results = [process_source(source, base_dir) for source in SUPPLEMENT_SOURCES]
    merge_manifest(manifest_path, SUPPLEMENT_SOURCES)

    print(json.dumps(results, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
