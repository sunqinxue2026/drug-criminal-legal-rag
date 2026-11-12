from __future__ import annotations

import json
import os
import re
import sys
from pathlib import Path

import pymysql
from pymysql.cursors import DictCursor


ROOT_DIR = Path(__file__).resolve().parents[1]
WEB_FLASK_DIR = ROOT_DIR / "web-flask"
APP_CONFIG = ROOT_DIR / "web-springboot" / "src" / "main" / "resources" / "application.yml"

sys.path.insert(0, str(WEB_FLASK_DIR))

from algo.document.legal_case_indexer import build_index_documents_from_chunks  # noqa: E402
from algo.legal_profile import DEFAULT_PROFILE, build_index_name, filter_chunks_for_profile  # noqa: E402
from algo.vector.bge_client import BGEClient  # noqa: E402
from algo.vector.faiss_manager import FaissManager  # noqa: E402


def load_db_config():
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
    return {
        "host": jdbc_match.group("host"),
        "port": int(jdbc_match.group("port") or 3306),
        "database": jdbc_match.group("db"),
        "username": username,
        "password": password,
    }


def connect():
    config = load_db_config()
    return pymysql.connect(
        host=config["host"],
        port=config["port"],
        user=config["username"],
        password=config["password"],
        database=config["database"],
        charset="utf8mb4",
        cursorclass=DictCursor,
    )


def load_knowledge_chunks(cur):
    cur.execute(
        """
        SELECT
            chunk.id AS chunkId,
            knowledge.id AS documentId,
            knowledge.id AS itemId,
            chunk.chunk_index AS chunkIndex,
            knowledge.title AS title,
            COALESCE(knowledge.summary, knowledge.article_title, '') AS summary,
            knowledge.category_id AS categoryId,
            COALESCE(category.name, '') AS categoryName,
            COALESCE(knowledge.keywords, '') AS tags,
            knowledge.knowledge_type AS documentType,
            COALESCE(knowledge.article_number, knowledge.knowledge_subtype, '') AS documentSubtype,
            '规范' AS caseType,
            COALESCE(knowledge.topic, knowledge.article_title, '') AS cause,
            COALESCE(knowledge.business_domain, '') AS businessDomain,
            '' AS procedureDomain,
            COALESCE(knowledge.issuer, '') AS courtName,
            '' AS courtLevel,
            knowledge.effective_date AS decisionDate,
            COALESCE(knowledge.current_status, '') AS decisionResult,
            COALESCE(knowledge.keywords, '') AS keywords,
            COALESCE(knowledge.aliases, '') AS aliases,
            '' AS issueTags,
            '' AS resultTags,
            COALESCE(knowledge.source_reliability, 0) AS sourceReliability,
            'legal_knowledge_db' AS source,
            COALESCE(chunk.section_type, '') AS sectionType,
            COALESCE(chunk.section_title, '') AS sectionTitle,
            COALESCE(chunk.header_path, '') AS headerPath,
            COALESCE(chunk.retrieval_role, '') AS retrievalRole,
            COALESCE(chunk.section_weight, 1) AS sectionWeight,
            COALESCE(chunk.retrieval_text, '') AS retrievalText,
            COALESCE(chunk.chunk_text, '') AS chunkText
        FROM legal_knowledge_chunk chunk
        INNER JOIN legal_knowledge knowledge ON knowledge.id = chunk.knowledge_id
        LEFT JOIN category ON category.id = knowledge.category_id
        ORDER BY knowledge.id, chunk.section_order, chunk.chunk_index
        """
    )
    return list(cur.fetchall())


def load_document_chunks(cur):
    cur.execute(
        """
        SELECT
            chunk.id AS chunkId,
            document.id AS documentId,
            document.id AS itemId,
            chunk.chunk_index AS chunkIndex,
            document.title AS title,
            COALESCE(document.summary, document.result_summary, document.facts_summary, '') AS summary,
            document.category_id AS categoryId,
            COALESCE(category.name, '') AS categoryName,
            COALESCE(document.keywords, '') AS tags,
            COALESCE(document.document_type, '') AS documentType,
            COALESCE(document.document_subtype, '') AS documentSubtype,
            COALESCE(document.case_type, '') AS caseType,
            COALESCE(document.cause_normalized, document.cause_raw, '') AS cause,
            COALESCE(document.business_domain, '') AS businessDomain,
            COALESCE(document.procedure_domain, '') AS procedureDomain,
            COALESCE(document.court_name, '') AS courtName,
            COALESCE(document.court_level, '') AS courtLevel,
            document.decision_date AS decisionDate,
            COALESCE(document.decision_result, '') AS decisionResult,
            COALESCE(document.keywords, '') AS keywords,
            COALESCE(document.aliases, '') AS aliases,
            COALESCE(document.issue_tags, '') AS issueTags,
            COALESCE(document.result_tags, '') AS resultTags,
            COALESCE(document.source_reliability, 0) AS sourceReliability,
            'legal_document_db' AS source,
            COALESCE(chunk.section_type, '') AS sectionType,
            COALESCE(chunk.section_title, '') AS sectionTitle,
            COALESCE(chunk.header_path, '') AS headerPath,
            COALESCE(chunk.retrieval_role, '') AS retrievalRole,
            COALESCE(chunk.section_weight, 1) AS sectionWeight,
            COALESCE(chunk.retrieval_text, '') AS retrievalText,
            COALESCE(chunk.chunk_text, '') AS chunkText
        FROM legal_document_chunk chunk
        INNER JOIN legal_document document ON document.id = chunk.document_id
        LEFT JOIN category ON category.id = document.category_id
        ORDER BY document.id, chunk.section_order, chunk.chunk_index
        """
    )
    return list(cur.fetchall())


def main():
    with connect() as conn:
        with conn.cursor() as cur:
            chunks = load_knowledge_chunks(cur) + load_document_chunks(cur)

    filtered_chunks = filter_chunks_for_profile(chunks, DEFAULT_PROFILE)
    documents = build_index_documents_from_chunks(filtered_chunks)
    if not documents:
        raise RuntimeError("没有可用于重建的专题分块")

    bge_client = BGEClient()
    faiss_manager = FaissManager(index_dir=str(WEB_FLASK_DIR / "data" / "indexes"))
    index_name = build_index_name(DEFAULT_PROFILE)
    if index_name in faiss_manager.indexes:
        faiss_manager.clear(index_name)

    texts = [document["content"] for document in documents]
    metadata_list = [document["metadata"] for document in documents]
    vectors = bge_client.encode_batch(texts, batch_size=32)
    vector_ids = faiss_manager.add_batch(index_name, vectors, metadata_list)
    faiss_manager.save(index_name)

    print(json.dumps({
        "profile": DEFAULT_PROFILE,
        "index_name": index_name,
        "source_chunks": len(filtered_chunks),
        "index_documents": len(documents),
        "vectors": len(vector_ids),
        "dimension": bge_client.get_dimension(),
        "mode": bge_client.get_status().get("mode"),
    }, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
