from __future__ import annotations

import argparse
import json
import re
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import Any

import pymysql
from pymysql.cursors import DictCursor


ROOT_DIR = Path(__file__).resolve().parents[1]
APP_CONFIG = ROOT_DIR / "web-springboot" / "src" / "main" / "resources" / "application.yml"
BACKUP_ROOT = ROOT_DIR / "data" / "legal_sources" / "drug_vertical" / "backups"

DEFAULT_CATEGORIES = [
    {
        "name": "涉毒犯罪认定",
        "description": "聚焦毒品犯罪构成要件、罪名区分、共同犯罪和主观明知认定。",
        "sort": 1,
        "keywords": ["毒品犯罪", "走私", "贩卖", "运输", "制造", "非法持有", "容留他人吸毒", "共同犯罪", "主观明知", "吸毒"],
    },
    {
        "name": "量刑与从宽处罚",
        "description": "聚焦毒品数量、主从犯区分、自首立功、未成年人和从宽从严规则。",
        "sort": 2,
        "keywords": ["量刑", "从轻", "从重", "减轻", "缓刑", "自首", "立功", "从犯", "主犯", "累犯", "毒品数量", "认罪认罚"],
    },
    {
        "name": "程序权利与证据规则",
        "description": "聚焦拘留逮捕、律师会见、取保候审、搜查扣押和电子证据审查。",
        "sort": 3,
        "keywords": ["刑事拘留", "逮捕", "取保候审", "会见", "辩护", "审查起诉", "侦查", "证据", "电子数据", "搜查", "扣押", "鉴定"],
    },
    {
        "name": "家属应对与普法指引",
        "description": "聚焦家属沟通、律师委托、戒治衔接、风险提示和禁毒普法。",
        "sort": 4,
        "keywords": ["家属", "律师", "委托", "通知", "普法", "风险提示", "宣传教育", "禁毒宣传", "社区戒毒", "社区康复", "强制隔离戒毒", "帮扶"],
    },
    {
        "name": "麻精药品与新型涉毒风险",
        "description": "聚焦麻醉药品、精神药品、依托咪酯、右美沙芬、笑气等新型风险。",
        "sort": 5,
        "keywords": ["麻精药品", "麻醉药品", "精神药品", "依托咪酯", "右美沙芬", "笑气", "曲马多", "医疗机构", "药店", "新型毒品"],
    },
]

DEFAULT_ITEMS = [
    {
        "title": "代购“上头电子烟”是否会被认定为毒品犯罪",
        "summary": "围绕依托咪酯电子烟、主观明知和代购牟利等情节，说明何时可能触发走私、贩卖、运输毒品类风险。",
        "content": "当事人以“帮朋友带货”“代买电子烟”为名接触依托咪酯等被列管物质时，司法机关通常会结合聊天记录、转账、购买频次、售价差额和收货对象来判断其是否明知系毒品或国家管制精神药品。若存在反复代购、加价牟利、拆分转卖、联系上下家等情节，风险会明显上升，可能触发走私、贩卖、运输毒品相关认定。",
        "category": "涉毒犯罪认定",
        "tags": "涉毒犯罪认定,依托咪酯,主观明知,代购风险",
    },
    {
        "title": "涉毒案件中家属第一时间应该做什么",
        "summary": "梳理家属在接到拘留或传唤消息后更稳妥的应对顺序，避免因慌乱造成额外法律风险。",
        "content": "家属在接到涉毒刑事风险消息后，通常应先核实承办机关和案件阶段，再尽快保存通知书、转账记录、聊天记录和病历处方等材料，并及时联系律师了解会见、取保候审和证据固定路径。不要代为串供、删聊天记录或转移物品，也不要轻信“花钱捞人”式承诺。系统更适合提供程序普法和风险提醒，而不是替代正式法律意见。",
        "category": "家属应对与普法指引",
        "tags": "家属应对,律师委托,取保候审,程序普法",
    },
    {
        "title": "涉毒案件量刑会重点看哪些因素",
        "summary": "从毒品种类数量、主从犯地位、自首立功、未成年人参与和再犯等角度说明量刑判断逻辑。",
        "content": "涉毒刑事案件的量刑通常会围绕毒品种类与数量、行为方式、是否牟利、是否向未成年人出售、是否多次实施、是否系累犯毒犯，以及是否存在自首、立功、从犯、坦白、认罪认罚等情节综合判断。对于麻精药品类案件，还会结合是否违规套购、医疗机构身份、处方来源和实际流向来判断社会危害性。",
        "category": "量刑与从宽处罚",
        "tags": "量刑与从宽处罚,毒品数量,自首立功,认罪认罚",
    },
    {
        "title": "聊天记录和转账记录在涉毒案件里有多重要",
        "summary": "说明电子数据、指认笔录、扣押清单和言词证据在涉毒案件中的常见证明作用与争议点。",
        "content": "在涉毒案件中，聊天记录、转账流水、快递信息、定位轨迹、同案人供述和扣押清单常被组合使用，用于证明主观明知、交易链条和资金往来。辩护与风险分析时应重点审查电子数据提取过程、取证时间链、截图完整性、鉴定意见和供述之间是否相互印证，避免单一证据被过度放大。",
        "category": "程序权利与证据规则",
        "tags": "程序权利与证据规则,电子证据,聊天记录,转账记录",
    },
    {
        "title": "药店和医疗机构违规出售麻精药品的刑事风险",
        "summary": "围绕麻醉药品、精神药品、医疗机构管理漏洞和非法流向问题，说明何时可能升级为刑事风险。",
        "content": "药店从业者、诊所经营者或医院工作人员若利用处方审核、库存管理、系统权限等漏洞，违规出售、截留、套购麻醉药品和精神药品，并流入吸贩毒环节，通常会被重点审查其身份职责、处方真实性、购销差额、流向对象和牟利情况。达到一定程度时，不再只是行业合规问题，而可能转化为贩卖毒品等刑事风险。",
        "category": "麻精药品与新型涉毒风险",
        "tags": "麻精药品与新型涉毒风险,麻醉药品,精神药品,医疗机构",
    },
]

TOPICAL_TERMS = [
    "禁毒", "毒品", "涉毒", "麻精药品", "麻醉药品", "精神药品", "依托咪酯", "右美沙芬", "笑气",
]


@dataclass
class DbConfig:
    host: str
    port: int
    database: str
    username: str
    password: str


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="清理数据库中的泛法律旧语料，收敛到涉毒刑事专题")
    parser.add_argument("--dry-run", action="store_true", help="只输出计划，不写入数据库")
    parser.add_argument("--backup-dir", default="", help="可选，自定义备份目录")
    return parser.parse_args()


def load_db_config() -> DbConfig:
    text = APP_CONFIG.read_text(encoding="utf-8")
    jdbc_match = re.search(r"jdbc:mysql://(?P<host>[^:/\s]+)(?::(?P<port>\d+))?/(?P<db>[^\?\s]+)", text)
    username_match = re.search(r"^\s*username:\s*(.+?)\s*$", text, re.MULTILINE)
    password_match = re.search(r"^\s*password:\s*(.+?)\s*$", text, re.MULTILINE)
    if not jdbc_match or not username_match or not password_match:
        raise RuntimeError(f"无法从 {APP_CONFIG} 解析数据库配置")
    return DbConfig(
        host=jdbc_match.group("host"),
        port=int(jdbc_match.group("port") or 3306),
        database=jdbc_match.group("db"),
        username=username_match.group(1).strip(),
        password=password_match.group(1).strip(),
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


def backup_tables(conn, backup_dir: Path) -> None:
    backup_dir.mkdir(parents=True, exist_ok=True)
    tables = [
        "category",
        "item",
        "favorite",
        "item_like",
        "comment",
        "legal_knowledge",
        "legal_knowledge_chunk",
        "legal_document",
        "legal_document_chunk",
        "legal_document_chunk_index",
        "legal_document_party",
        "legal_document_reference",
        "legal_document_ext",
    ]
    with conn.cursor() as cur:
        for table in tables:
            cur.execute(f"SELECT * FROM {table}")
            rows = cur.fetchall()
            (backup_dir / f"{table}.json").write_text(
                json.dumps(rows, ensure_ascii=False, indent=2, default=str),
                encoding="utf-8",
            )


def normalize_text(*parts: Any) -> str:
    values = []
    for part in parts:
        if part is None:
            continue
        text = str(part).strip()
        if text:
            values.append(text.lower())
    return " ".join(values)


def contains_topical_term(text: str) -> bool:
    return any(term.lower() in text for term in TOPICAL_TERMS)


def classify_category(text: str) -> str:
    best_name = "家属应对与普法指引"
    best_score = 0
    for category in DEFAULT_CATEGORIES:
        score = 0
        for token in [category["name"], category["description"], *category["keywords"]]:
            token_text = str(token).strip().lower()
            if len(token_text) >= 2 and token_text in text:
                score += 1
        if category["name"].lower() in text:
            score += 2
        if score > best_score:
            best_score = score
            best_name = category["name"]
    return best_name


def ensure_categories(cur) -> dict[str, int]:
    cur.execute("DELETE FROM category")
    category_ids: dict[str, int] = {}
    for category in DEFAULT_CATEGORIES:
        cur.execute(
            """
            INSERT INTO category (name, description, parent_id, sort, create_time, update_time, deleted)
            VALUES (%s, %s, 0, %s, NOW(), NOW(), 0)
            """,
            (category["name"], category["description"], category["sort"]),
        )
        category_ids[category["name"]] = cur.lastrowid
    return category_ids


def keep_knowledge_rows(cur) -> list[dict[str, Any]]:
    cur.execute(
        """
        SELECT id, title, document_name, source_name, business_domain, topic, article_title, keywords, summary, display_text, retrieval_text
        FROM legal_knowledge
        """
    )
    rows = cur.fetchall()
    kept = []
    for row in rows:
        joined = normalize_text(
            row.get("title"),
            row.get("document_name"),
            row.get("source_name"),
            row.get("article_title"),
        )
        if contains_topical_term(joined):
            kept.append(row)
    return kept


def keep_document_rows(cur) -> list[dict[str, Any]]:
    cur.execute(
        """
        SELECT id, title, case_number, cause_normalized, case_type, business_domain, procedure_domain, keywords, summary, clean_text
        FROM legal_document
        """
    )
    rows = cur.fetchall()
    kept = []
    for row in rows:
        joined = normalize_text(
            row.get("title"),
            row.get("case_number"),
            row.get("cause_normalized"),
            row.get("case_type"),
            row.get("procedure_domain"),
        )
        if contains_topical_term(joined):
            kept.append(row)
    return kept


def cleanup_knowledge(cur, category_ids: dict[str, int]) -> int:
    kept_rows = keep_knowledge_rows(cur)
    kept_ids = [row["id"] for row in kept_rows]

    if kept_ids:
        placeholders = ",".join(["%s"] * len(kept_ids))
        cur.execute(f"DELETE FROM legal_knowledge_chunk WHERE knowledge_id NOT IN ({placeholders})", kept_ids)
        cur.execute(f"DELETE FROM legal_knowledge WHERE id NOT IN ({placeholders})", kept_ids)
    else:
        cur.execute("DELETE FROM legal_knowledge_chunk")
        cur.execute("DELETE FROM legal_knowledge")

    for row in kept_rows:
        joined = normalize_text(
            row.get("title"),
            row.get("document_name"),
            row.get("source_name"),
            row.get("business_domain"),
            row.get("topic"),
            row.get("article_title"),
            row.get("keywords"),
            row.get("summary"),
            row.get("display_text"),
        )
        category_name = classify_category(joined)
        cur.execute(
            """
            UPDATE legal_knowledge
            SET category_id=%s, business_domain=%s, topic=%s, status=1, deleted=0,
                current_status=COALESCE(current_status, '现行有效')
            WHERE id=%s
            """,
            (
                category_ids[category_name],
                category_name,
                row.get("topic") or category_name,
                row["id"],
            ),
        )
    return len(kept_rows)


def cleanup_documents(cur, category_ids: dict[str, int]) -> int:
    kept_rows = keep_document_rows(cur)
    kept_ids = [row["id"] for row in kept_rows]

    if kept_ids:
        placeholders = ",".join(["%s"] * len(kept_ids))
        cur.execute(
            f"""
            DELETE idx FROM legal_document_chunk_index idx
            INNER JOIN legal_document_chunk chunk ON idx.chunk_id = chunk.id
            WHERE chunk.document_id NOT IN ({placeholders})
            """,
            kept_ids,
        )
        cur.execute(f"DELETE FROM legal_document_chunk WHERE document_id NOT IN ({placeholders})", kept_ids)
        cur.execute(f"DELETE FROM legal_document_party WHERE document_id NOT IN ({placeholders})", kept_ids)
        cur.execute(f"DELETE FROM legal_document_reference WHERE document_id NOT IN ({placeholders})", kept_ids)
        cur.execute(f"DELETE FROM legal_document_ext WHERE document_id NOT IN ({placeholders})", kept_ids)
        cur.execute(f"DELETE FROM legal_document WHERE id NOT IN ({placeholders})", kept_ids)
    else:
        cur.execute("DELETE idx FROM legal_document_chunk_index idx INNER JOIN legal_document_chunk chunk ON idx.chunk_id = chunk.id")
        cur.execute("DELETE FROM legal_document_chunk")
        cur.execute("DELETE FROM legal_document_party")
        cur.execute("DELETE FROM legal_document_reference")
        cur.execute("DELETE FROM legal_document_ext")
        cur.execute("DELETE FROM legal_document")

    for row in kept_rows:
        joined = normalize_text(
            row.get("title"),
            row.get("case_number"),
            row.get("cause_normalized"),
            row.get("case_type"),
            row.get("business_domain"),
            row.get("procedure_domain"),
            row.get("keywords"),
            row.get("summary"),
            row.get("clean_text"),
        )
        category_name = classify_category(joined)
        cur.execute(
            """
            UPDATE legal_document
            SET category_id=%s, case_type='刑事', business_domain=%s, status=1, deleted=0
            WHERE id=%s
            """,
            (category_ids[category_name], category_name, row["id"]),
        )
    return len(kept_rows)


def rebuild_items(cur, category_ids: dict[str, int]) -> int:
    cur.execute("DELETE FROM favorite")
    cur.execute("DELETE FROM item_like")
    cur.execute("DELETE FROM comment")
    cur.execute("DELETE FROM item")

    for item in DEFAULT_ITEMS:
        cur.execute(
            """
            INSERT INTO item (
                title, content, summary, category_id, tags,
                view_count, like_count, favorite_count, status,
                create_time, update_time, deleted
            )
            VALUES (%s, %s, %s, %s, %s, 0, 0, 0, 1, NOW(), NOW(), 0)
            """,
            (
                item["title"],
                item["content"],
                item["summary"],
                category_ids[item["category"]],
                item["tags"],
            ),
        )
    return len(DEFAULT_ITEMS)


def main() -> None:
    args = parse_args()
    db_config = load_db_config()
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    backup_dir = Path(args.backup_dir) if args.backup_dir else BACKUP_ROOT / f"db_cleanup_{timestamp}"

    print(f"[config] host={db_config.host}:{db_config.port} db={db_config.database}")
    print(f"[backup] {backup_dir}")

    conn = connect(db_config)
    try:
        backup_tables(conn, backup_dir)
        print("[backup] completed")

        if args.dry_run:
            conn.rollback()
            print("[dry-run] backup completed, database not modified")
            return

        with conn.cursor() as cur:
            category_ids = ensure_categories(cur)
            knowledge_count = cleanup_knowledge(cur, category_ids)
            document_count = cleanup_documents(cur, category_ids)
            item_count = rebuild_items(cur, category_ids)
            summary = {
                "categories": len(category_ids),
                "items": item_count,
                "legal_knowledge": knowledge_count,
                "legal_documents": document_count,
                "backup_dir": str(backup_dir),
            }
            (backup_dir / "cleanup_summary.json").write_text(
                json.dumps(summary, ensure_ascii=False, indent=2),
                encoding="utf-8",
            )
            conn.commit()
            print(json.dumps(summary, ensure_ascii=False, indent=2))
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()


if __name__ == "__main__":
    main()
