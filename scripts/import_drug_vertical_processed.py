from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import subprocess
import sys
from dataclasses import dataclass
from decimal import Decimal
from pathlib import Path
from typing import Any, Dict, Iterable, List

import pymysql
from pymysql.cursors import DictCursor


ROOT_DIR = Path(__file__).resolve().parents[1]
APP_CONFIG = ROOT_DIR / "web-springboot" / "src" / "main" / "resources" / "application.yml"
DEFAULT_PROCESSED_DIR = ROOT_DIR / "data" / "legal_sources" / "drug_vertical" / "processed"
INDEX_REBUILD_SCRIPT = ROOT_DIR / "scripts" / "rebuild_drug_vertical_index.py"

CHUNK_SIZE = 420
CHUNK_OVERLAP = 40
SUMMARY_LIMIT = 200

DEFAULT_CATEGORY = "家属应对与普法指引"
CATEGORY_RULES: Dict[str, List[str]] = {
    "涉毒犯罪认定": [
        "走私", "贩卖", "运输", "制造", "非法持有", "包庇", "窝藏", "转移", "隐瞒",
        "引诱", "教唆", "欺骗", "容留", "非法种植", "毒品原植物", "制毒物品", "共同犯罪",
    ],
    "量刑与从宽处罚": [
        "数量大", "数量较大", "情节严重", "情节较重", "从重处罚", "从轻处罚", "减轻处罚",
        "再犯", "死刑", "无期徒刑", "十五年有期徒刑", "七年以上有期徒刑", "量刑",
    ],
    "程序权利与证据规则": [
        "特情", "证据", "侦查", "鉴定", "取证", "扣押", "枪支", "弹药", "爆炸物", "审查",
    ],
    "家属应对与普法指引": [
        "禁毒宣传", "宣传教育", "举报", "强制隔离戒毒", "社区戒毒", "社区康复", "戒毒",
    ],
    "麻精药品与新型涉毒风险": [
        "麻醉药品", "精神药品", "麻精药品", "依托咪酯", "右美沙芬", "芬太尼", "曲马多",
        "哌替啶", "美沙酮", "三唑仑", "阿普唑仑", "药店", "医院", "诊所",
    ],
}


@dataclass
class DbConfig:
    host: str
    port: int
    database: str
    username: str
    password: str


@dataclass
class TextSlice:
    start: int
    end: int
    text: str


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="导入涉毒刑事专题 processed JSON 到 legal_knowledge，并可重建索引")
    parser.add_argument("--processed-dir", default=str(DEFAULT_PROCESSED_DIR), help="processed 目录")
    parser.add_argument("--files", nargs="*", default=[], help="可选，指定要导入的 JSON 文件")
    parser.add_argument("--rebuild-index", action="store_true", help="导入完成后重建专题索引")
    return parser.parse_args()


def load_db_config() -> DbConfig:
    text = APP_CONFIG.read_text(encoding="utf-8")

    def extract_value(key: str) -> str | None:
        matched = re.search(rf"^\s*{re.escape(key)}:\s*(.+?)\s*$", text, re.MULTILINE)
        if not matched:
            return None
        raw = matched.group(1).strip()
        placeholder = re.fullmatch(r"\$\{([^:}]+):([^}]*)\}", raw)
        if not placeholder:
            return raw
        env_name, default_value = placeholder.groups()
        return os.environ.get(env_name, default_value)

    jdbc = extract_value("url")
    username = extract_value("username")
    password = extract_value("password")
    if not jdbc or not username or password is None:
        raise RuntimeError(f"无法从 {APP_CONFIG} 解析数据库配置")

    jdbc_match = re.search(r"jdbc:mysql://(?P<host>[^:/\s]+)(?::(?P<port>\d+))?/(?P<db>[^\?\s]+)", jdbc)
    if not jdbc_match:
        raise RuntimeError(f"无法从 {APP_CONFIG} 解析数据库配置")
    return DbConfig(
        host=jdbc_match.group("host"),
        port=int(jdbc_match.group("port") or 3306),
        database=jdbc_match.group("db"),
        username=username,
        password=password,
    )


def connect(config: DbConfig):
    return pymysql.connect(
        host=config.host,
        port=config.port,
        user=config.username,
        password=config.password,
        database=config.database,
        charset="utf8mb4",
        cursorclass=DictCursor,
        autocommit=False,
    )


def iter_source_files(processed_dir: Path, explicit_files: List[str]) -> List[Path]:
    if explicit_files:
        return [Path(path).resolve() for path in explicit_files]
    files = []
    for path in processed_dir.rglob("*.json"):
        name = path.name.lower()
        if (
            name.startswith("import_batch_")
            or name.startswith("temp_batch_")
            or name.endswith("_test.json")
            or name.endswith(".summary.json")
            or name == "corpus_report.json"
        ):
            continue
        files.append(path)
    return sorted(files)


def load_category_map(cur) -> Dict[str, int]:
    cur.execute("SELECT id, name FROM category WHERE deleted = 0")
    return {row["name"]: row["id"] for row in cur.fetchall()}


def load_documents(path: Path) -> List[Dict[str, Any]]:
    payload = json.loads(path.read_text(encoding="utf-8"))
    if isinstance(payload, list):
        return [item for item in payload if isinstance(item, dict)]
    if isinstance(payload, dict):
        return [payload]
    return []


def flatten_document(document: Dict[str, Any]) -> List[Dict[str, Any]]:
    base = {key: value for key, value in document.items() if key not in {"articles", "entries"}}
    if isinstance(document.get("articles"), list):
        return [{**base, **article} for article in document["articles"] if isinstance(article, dict)]
    if isinstance(document.get("entries"), list):
        return [{**base, **entry} for entry in document["entries"] if isinstance(entry, dict)]
    return [base]


def normalize_text(value: Any) -> str:
    if value is None:
        return ""
    text = str(value).replace("\r", "\n").replace("\u3000", " ").replace("\xa0", " ").strip()
    text = re.sub(r"[ \t\f\v]+", " ", text)
    text = re.sub(r"\n{3,}", "\n\n", text)
    return text.strip()


def normalize_title(value: str) -> str:
    return re.sub(r"\s+", "", normalize_text(value)).lower()


def split_tags(value: Any) -> List[str]:
    if isinstance(value, list):
        items = value
    else:
        text = normalize_text(value)
        if not text:
            return []
        items = re.split(r"[，,、/\n;；]+", text)
    result: List[str] = []
    seen = set()
    for item in items:
        text = normalize_text(item)
        if text and text not in seen:
            seen.add(text)
            result.append(text)
    return result


def add_if_text(values: List[str], value: Any) -> None:
    text = normalize_text(value)
    if text:
        values.append(text)


def join_distinct(values: Iterable[str]) -> str:
    result: List[str] = []
    seen = set()
    for value in values:
        text = normalize_text(value)
        if text and text not in seen:
            seen.add(text)
            result.append(text)
    return ",".join(result)


def build_title(document_name: str, article_number: str, article_title: str) -> str:
    parts: List[str] = []
    add_if_text(parts, document_name)
    add_if_text(parts, article_number)
    add_if_text(parts, article_title)
    return " ".join(parts).strip()


def build_summary(article_title: str, content: str) -> str:
    if normalize_text(article_title):
        return article_title[:SUMMARY_LIMIT]
    first_line = next((line.strip() for line in normalize_text(content).splitlines() if line.strip()), "")
    return first_line[:SUMMARY_LIMIT]


def sha256_hex(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def parse_date(value: Any):
    text = normalize_text(value)
    if not text:
        return None
    try:
        return text[:10]
    except Exception:
        return None


def infer_category_name(record: Dict[str, Any]) -> str:
    text = " ".join(
        normalize_text(record.get(key))
        for key in (
            "documentName", "knowledgeType", "documentLevel", "topic", "articleNumber",
            "articleTitle", "summary", "content", "keywords", "aliases",
        )
    )
    scores: Dict[str, int] = {name: 0 for name in CATEGORY_RULES}
    for category, keywords in CATEGORY_RULES.items():
        for keyword in keywords:
            if keyword in text:
                scores[category] += 1

    if "麻醉药品" in text or "精神药品" in text:
        scores["麻精药品与新型涉毒风险"] += 2
    if any(token in text for token in ("数量大", "数量较大", "情节严重", "情节较重", "从重处罚")):
        scores["量刑与从宽处罚"] += 2
    if any(token in text for token in ("走私", "贩卖", "运输", "制造", "非法持有", "容留")):
        scores["涉毒犯罪认定"] += 2
    if any(token in text for token in ("侦查", "证据", "鉴定", "特情")):
        scores["程序权利与证据规则"] += 2
    if any(token in text for token in ("举报", "戒毒", "宣传教育")):
        scores["家属应对与普法指引"] += 2

    category, score = max(scores.items(), key=lambda item: item[1])
    return category if score > 0 else DEFAULT_CATEGORY


def detect_knowledge_subtype(document: Dict[str, Any], article_number: str) -> str:
    return normalize_text(document.get("knowledgeSubtype")) or normalize_text(document.get("documentLevel")) or article_number


def section_type_for(knowledge_type: str) -> str:
    if "司法解释" in knowledge_type:
        return "judicial_interpretation"
    if "案例" in knowledge_type:
        return "case"
    return "law"


def retrieval_role_for(knowledge_type: str) -> str:
    if "司法解释" in knowledge_type:
        return "judicial_rule"
    if "案例" in knowledge_type:
        return "case_reference"
    return "law_basis"


def section_weight_for(knowledge_type: str) -> Decimal:
    if "司法解释" in knowledge_type:
        return Decimal("1.55")
    if "法律" in knowledge_type:
        return Decimal("1.48")
    if "案例" in knowledge_type:
        return Decimal("1.30")
    return Decimal("1.15")


def build_retrieval_text(knowledge: Dict[str, Any], content: str) -> str:
    parts: List[str] = []
    labels = [
        ("标题", knowledge["title"]),
        ("知识类型", knowledge["knowledge_type"]),
        ("规范名称", knowledge["document_name"]),
        ("条号", knowledge["article_number"]),
        ("条文标题", knowledge["article_title"]),
        ("业务领域", knowledge["business_domain"]),
        ("主题", knowledge["topic"]),
        ("发布机关", knowledge["issuer"]),
        ("摘要", knowledge["summary"]),
        ("关键词", knowledge["keywords"]),
    ]
    for label, value in labels:
        text = normalize_text(value)
        if text:
            parts.append(f"{label}：{text}")
    parts.append(content)
    return "\n".join(parts)


def build_chunk_retrieval_text(knowledge: Dict[str, Any], chunk_text: str) -> str:
    parts: List[str] = []
    for label, value in (
        ("标题", knowledge["title"]),
        ("知识类型", knowledge["knowledge_type"]),
        ("规范名称", knowledge["document_name"]),
        ("条号", knowledge["article_number"]),
        ("业务领域", knowledge["business_domain"]),
        ("主题", knowledge["topic"]),
        ("关键词", knowledge["keywords"]),
    ):
        text = normalize_text(value)
        if text:
            parts.append(f"{label}：{text}")
    parts.append(chunk_text)
    return "\n".join(parts)


def find_split_point(text: str, start: int, end: int) -> int:
    for marker in ("\n\n", "\n", "。", "；", "！"):
        split = text.rfind(marker, start, end)
        if split > start:
            return split + len(marker)
    return end


def slice_text(text: str) -> List[TextSlice]:
    normalized = normalize_text(text)
    if not normalized:
        return []
    if len(normalized) <= CHUNK_SIZE:
        return [TextSlice(0, len(normalized), normalized)]

    slices: List[TextSlice] = []
    start = 0
    while start < len(normalized):
        end = min(len(normalized), start + CHUNK_SIZE)
        if end < len(normalized):
            split = find_split_point(normalized, start, end)
            if split > start + 80:
                end = split
        chunk = normalized[start:end].strip()
        if chunk:
            slices.append(TextSlice(start, end, chunk))
        if end >= len(normalized):
            break
        start = max(end - CHUNK_OVERLAP, start + 1)
    return slices


def build_chunks(knowledge_id: int, knowledge: Dict[str, Any]) -> List[Dict[str, Any]]:
    chunks: List[Dict[str, Any]] = []
    slices = slice_text(knowledge["display_text"])
    for index, slice_item in enumerate(slices):
        metadata = {
            "knowledgeType": knowledge["knowledge_type"],
            "documentName": knowledge["document_name"],
            "articleNumber": knowledge["article_number"],
            "businessDomain": knowledge["business_domain"],
        }
        chunks.append(
            {
                "knowledge_id": knowledge_id,
                "chunk_strategy": "article_semantic",
                "chunk_version": "v1",
                "section_type": section_type_for(knowledge["knowledge_type"]),
                "section_title": knowledge["article_number"] or knowledge["article_title"] or "条文",
                "header_path": knowledge["document_name"] + (f" > {knowledge['article_number']}" if knowledge["article_number"] else ""),
                "retrieval_role": retrieval_role_for(knowledge["knowledge_type"]),
                "section_weight": section_weight_for(knowledge["knowledge_type"]),
                "section_order": 0,
                "chunk_index": index,
                "char_start_offset": slice_item.start,
                "char_end_offset": slice_item.end,
                "chunk_hash": sha256_hex(f"{knowledge['title']}|{slice_item.text}"),
                "chunk_text": slice_item.text,
                "retrieval_text": build_chunk_retrieval_text(knowledge, slice_item.text),
                "char_count": len(slice_item.text),
                "token_count": None,
                "metadata_json": json.dumps(metadata, ensure_ascii=False),
            }
        )
    return chunks


def build_knowledge_row(record: Dict[str, Any], source_file_name: str, category_map: Dict[str, int]) -> Dict[str, Any] | None:
    document_name = normalize_text(record.get("documentName") or record.get("title") or record.get("name"))
    article_number = normalize_text(record.get("articleNumber") or record.get("articleNo") or record.get("caseNumber"))
    article_title = normalize_text(record.get("articleTitle") or record.get("clauseTitle"))
    content = normalize_text(record.get("content") or record.get("text") or record.get("displayText"))
    if not content or not document_name:
        return None

    category_name = infer_category_name(record)
    category_id = category_map.get(category_name)
    if category_id is None:
        raise RuntimeError(f"缺少专题分类: {category_name}")

    knowledge_type = normalize_text(record.get("knowledgeType")) or "法律法规"
    topic = normalize_text(record.get("topic")) or category_name

    keywords: List[str] = []
    for value in (
        category_name,
        topic,
        document_name,
        article_number,
        article_title,
    ):
        add_if_text(keywords, value)
    keywords.extend(split_tags(record.get("keywords")))

    aliases = split_tags(record.get("aliases"))
    title = build_title(document_name, article_number, article_title)
    summary = normalize_text(record.get("summary")) or build_summary(article_title, content)

    knowledge = {
        "source_type": normalize_text(record.get("sourceType")) or "official_crawl",
        "source_name": normalize_text(record.get("sourceName")) or source_file_name,
        "source_url": normalize_text(record.get("sourceUrl")),
        "source_reliability": int(record.get("sourceReliability") or 1),
        "source_file_name": source_file_name,
        "content_sha256": sha256_hex("\n".join([title, article_number, content])),
        "title": title,
        "title_normalized": normalize_title(title),
        "knowledge_type": knowledge_type,
        "knowledge_subtype": detect_knowledge_subtype(record, article_number),
        "document_name": document_name,
        "document_level": normalize_text(record.get("documentLevel")) or knowledge_type,
        "issuer": normalize_text(record.get("issuer")),
        "category_id": category_id,
        "business_domain": category_name,
        "topic": topic,
        "chapter_title": normalize_text(record.get("chapterTitle")),
        "section_title": normalize_text(record.get("sectionTitle")),
        "article_number": article_number,
        "article_title": article_title,
        "keywords": join_distinct(keywords),
        "aliases": join_distinct(aliases),
        "summary": summary,
        "display_text": content,
        "retrieval_text": "",
        "effective_date": parse_date(record.get("effectiveDate")),
        "expiry_date": parse_date(record.get("expiryDate")),
        "current_status": normalize_text(record.get("currentStatus")) or "现行有效",
        "parser_version": "drug-vertical-import-v1",
        "parse_status": 1,
        "extract_confidence": Decimal("0.96"),
        "status": 1,
        "deleted": 0,
    }
    knowledge["retrieval_text"] = build_retrieval_text(knowledge, content)
    return knowledge


def find_duplicate(cur, knowledge: Dict[str, Any]) -> Dict[str, Any] | None:
    cur.execute(
        """
        SELECT id, title, article_number
        FROM legal_knowledge
        WHERE deleted = 0
          AND (
                content_sha256 = %s
                OR (
                    title_normalized = %s
                    AND article_number = %s
                )
              )
        ORDER BY id DESC
        LIMIT 1
        """,
        (knowledge["content_sha256"], knowledge["title_normalized"], knowledge["article_number"]),
    )
    return cur.fetchone()


def insert_knowledge(cur, knowledge: Dict[str, Any]) -> int:
    cur.execute(
        """
        INSERT INTO legal_knowledge (
            source_type, source_name, source_url, source_reliability, source_file_name, content_sha256,
            title, title_normalized, knowledge_type, knowledge_subtype, document_name, document_level, issuer,
            category_id, business_domain, topic, chapter_title, section_title, article_number, article_title,
            keywords, aliases, summary, display_text, retrieval_text,
            effective_date, expiry_date, current_status, parser_version, parse_status, extract_confidence,
            status, create_time, update_time, deleted
        ) VALUES (
            %s, %s, %s, %s, %s, %s,
            %s, %s, %s, %s, %s, %s, %s,
            %s, %s, %s, %s, %s, %s, %s,
            %s, %s, %s, %s, %s,
            %s, %s, %s, %s, %s, %s,
            %s, NOW(), NOW(), %s
        )
        """,
        (
            knowledge["source_type"], knowledge["source_name"], knowledge["source_url"], knowledge["source_reliability"],
            knowledge["source_file_name"], knowledge["content_sha256"], knowledge["title"], knowledge["title_normalized"],
            knowledge["knowledge_type"], knowledge["knowledge_subtype"], knowledge["document_name"], knowledge["document_level"],
            knowledge["issuer"], knowledge["category_id"], knowledge["business_domain"], knowledge["topic"],
            knowledge["chapter_title"], knowledge["section_title"], knowledge["article_number"], knowledge["article_title"],
            knowledge["keywords"], knowledge["aliases"], knowledge["summary"], knowledge["display_text"],
            knowledge["retrieval_text"], knowledge["effective_date"], knowledge["expiry_date"], knowledge["current_status"],
            knowledge["parser_version"], knowledge["parse_status"], knowledge["extract_confidence"], knowledge["status"],
            knowledge["deleted"],
        ),
    )
    return int(cur.lastrowid)


def insert_chunks(cur, chunks: List[Dict[str, Any]]) -> None:
    for chunk in chunks:
        cur.execute(
            """
            INSERT INTO legal_knowledge_chunk (
                knowledge_id, chunk_strategy, chunk_version, section_type, section_title, header_path,
                retrieval_role, section_weight, section_order, chunk_index, char_start_offset, char_end_offset,
                chunk_hash, chunk_text, retrieval_text, char_count, token_count, metadata_json, create_time, deleted
            ) VALUES (
                %s, %s, %s, %s, %s, %s,
                %s, %s, %s, %s, %s, %s,
                %s, %s, %s, %s, %s, %s, NOW(), 0
            )
            """,
            (
                chunk["knowledge_id"], chunk["chunk_strategy"], chunk["chunk_version"], chunk["section_type"],
                chunk["section_title"], chunk["header_path"], chunk["retrieval_role"], chunk["section_weight"],
                chunk["section_order"], chunk["chunk_index"], chunk["char_start_offset"], chunk["char_end_offset"],
                chunk["chunk_hash"], chunk["chunk_text"], chunk["retrieval_text"], chunk["char_count"],
                chunk["token_count"], chunk["metadata_json"],
            ),
        )


def rebuild_index() -> None:
    completed = subprocess.run(
        [sys.executable, str(INDEX_REBUILD_SCRIPT)],
        cwd=str(ROOT_DIR),
        capture_output=True,
        text=True,
        encoding="utf-8",
        timeout=300,
        check=False,
    )
    if completed.returncode != 0:
        raise RuntimeError((completed.stderr or completed.stdout or "索引重建失败").strip())
    print(completed.stdout.strip())


def query_totals(cur) -> Dict[str, int]:
    totals: Dict[str, int] = {}
    for table in ("legal_knowledge", "legal_knowledge_chunk"):
        cur.execute(f"SELECT COUNT(*) AS total FROM {table} WHERE deleted = 0")
        totals[table] = int(cur.fetchone()["total"])
    return totals


def main() -> None:
    args = parse_args()
    processed_dir = Path(args.processed_dir).resolve()
    files = iter_source_files(processed_dir, args.files)
    if not files:
        raise RuntimeError(f"未找到可导入的 processed JSON: {processed_dir}")

    config = load_db_config()
    summary = {
        "files": len(files),
        "imported": 0,
        "skipped": 0,
        "failed": 0,
        "knowledge_ids": [],
    }

    conn = connect(config)
    try:
        with conn.cursor() as cur:
            category_map = load_category_map(cur)
            for path in files:
                documents = load_documents(path)
                for document in documents:
                    for record in flatten_document(document):
                        knowledge = build_knowledge_row(record, path.name, category_map)
                        if knowledge is None:
                            continue
                        duplicate = find_duplicate(cur, knowledge)
                        if duplicate is not None:
                            summary["skipped"] += 1
                            continue
                        knowledge_id = insert_knowledge(cur, knowledge)
                        chunks = build_chunks(knowledge_id, knowledge)
                        insert_chunks(cur, chunks)
                        summary["imported"] += 1
                        summary["knowledge_ids"].append(knowledge_id)
            totals = query_totals(cur)
            conn.commit()
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()

    summary.update(totals)
    print(json.dumps(summary, ensure_ascii=False, indent=2))

    if args.rebuild_index and summary["imported"] > 0:
        rebuild_index()


if __name__ == "__main__":
    main()
