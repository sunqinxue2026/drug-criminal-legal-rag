"""
Vector index management routes.
"""
from __future__ import annotations

import logging

from flask import Blueprint, jsonify, request

from algo.document.legal_case_indexer import build_index_documents, build_index_documents_from_chunks
from algo.legal_profile import DEFAULT_PROFILE, build_index_name, filter_chunks_for_profile, normalize_profile
from algo.vector.bge_client import BGEClient
from algo.vector.faiss_manager import FaissManager
from clients.springboot_client import SpringBootClient
from utils.response import error, success

logger = logging.getLogger(__name__)

vector_bp = Blueprint("vector", __name__)
bge_client = BGEClient()
faiss_manager = FaissManager()
springboot_client = SpringBootClient()


def _build_source_key(metadata: dict) -> tuple[str, str] | None:
    source = str(metadata.get("source") or "").strip() or "unknown"
    document_id = metadata.get("document_id")
    item_id = metadata.get("item_id")
    title = str(metadata.get("title") or "").strip()

    source_id = document_id if document_id not in (None, "") else item_id
    if source_id not in (None, ""):
        return source, str(source_id)
    if title:
        return source, title
    return None


def _count_unique_sources(documents: list[dict]) -> int:
    keys = {
        key
        for key in (_build_source_key((document or {}).get("metadata") or {}) for document in (documents or []))
        if key is not None
    }
    return len(keys)


def _load_index_documents(profile: str = DEFAULT_PROFILE):
    legal_knowledge_chunks = springboot_client.get_all_legal_knowledge_chunks()
    legal_chunks = springboot_client.get_all_legal_document_chunks()
    if legal_knowledge_chunks or legal_chunks:
        merged_chunks = filter_chunks_for_profile(list(legal_knowledge_chunks) + list(legal_chunks), profile)
        return build_index_documents_from_chunks(merged_chunks), "legal_knowledge+legal_document"

    if normalize_profile(profile) == DEFAULT_PROFILE:
        return [], "empty"

    items = springboot_client.get_all_items()
    if items:
        return build_index_documents(items), "item"

    return [], "empty"


@vector_bp.route("/api/vector/indexes", methods=["GET"])
def get_indexes():
    try:
        return jsonify(success(faiss_manager.list_indexes()))
    except Exception as exc:
        logger.error("Failed to list indexes: %s", exc)
        return jsonify(error(f"获取索引列表失败: {exc}")), 500


@vector_bp.route("/api/vector/create", methods=["POST"])
def create_index():
    try:
        data = request.json or {}
        name = data.get("name")
        dimension = data.get("dimension", 1024)
        if not name:
            return jsonify(error("索引名称不能为空", 400)), 400

        faiss_manager.create_index(name, dimension)
        return jsonify(success({
            "message": f"索引 {name} 创建成功",
            "name": name,
            "dimension": dimension,
        }))
    except Exception as exc:
        logger.error("Failed to create index: %s", exc)
        return jsonify(error(f"创建索引失败: {exc}")), 500


@vector_bp.route("/api/vector/index", methods=["POST"])
def create_vector_index():
    try:
        data = request.json or {}
        item_id = data.get("item_id")
        text = data.get("text")
        metadata = data.get("metadata", {})
        index_name = data.get("index_name", "legal_cases")

        if not text:
            return jsonify(error("文本内容不能为空", 400)), 400

        vector = bge_client.encode(text)
        metadata["item_id"] = item_id
        metadata["text_length"] = len(text)
        vector_id = faiss_manager.add(index_name, vector, metadata)
        faiss_manager.save(index_name)

        return jsonify(success({
            "item_id": item_id,
            "vector_id": vector_id,
            "index_name": index_name,
            "message": "向量索引创建成功",
        }))
    except Exception as exc:
        logger.error("Failed to create vector index: %s", exc)
        return jsonify(error(f"创建向量索引失败: {exc}")), 500


@vector_bp.route("/api/vector/index/batch", methods=["POST"])
def create_batch_index():
    try:
        data = request.json or {}
        items = data.get("items", [])
        index_name = data.get("index_name", "legal_cases")
        if not items:
            return jsonify(error("items 不能为空", 400)), 400

        texts = [item["text"] for item in items]
        vectors = bge_client.encode_batch(texts)
        metadata_list = []
        for item in items:
            meta = item.get("metadata", {}).copy()
            meta["item_id"] = item.get("item_id")
            meta["text_length"] = len(item["text"])
            metadata_list.append(meta)

        vector_ids = faiss_manager.add_batch(index_name, vectors, metadata_list)
        faiss_manager.save(index_name)
        return jsonify(success({
            "count": len(vector_ids),
            "vector_ids": vector_ids,
            "index_name": index_name,
            "message": f"成功创建 {len(vector_ids)} 条向量索引",
        }))
    except Exception as exc:
        logger.error("Failed to create batch index: %s", exc)
        return jsonify(error(f"批量创建索引失败: {exc}")), 500


@vector_bp.route("/api/vector/search", methods=["POST"])
def search_vectors():
    try:
        data = request.json or {}
        query = data.get("query")
        index_name = data.get("index_name", "legal_cases")
        top_k = data.get("top_k", 5)
        if not query:
            return jsonify(error("查询文本不能为空", 400)), 400

        query_vector = bge_client.encode(query)
        results = faiss_manager.search(index_name, query_vector, top_k)
        results = [result for result in results if not result.get("deleted", False)]

        for result in results:
            content = result.get("chunk_text") or result.get("content") or ""
            result["content_preview"] = content[:220] + "..." if len(content) > 220 else content

        return jsonify(success(results))
    except Exception as exc:
        logger.error("Failed to search vectors: %s", exc)
        return jsonify(error(f"检索失败: {exc}")), 500


@vector_bp.route("/api/vector/rebuild", methods=["POST"])
def rebuild_index():
    try:
        data = request.json or {}
        profile = normalize_profile(data.get("profile") or DEFAULT_PROFILE)
        index_name = data.get("index_name") or build_index_name(profile)
        logger.info("Rebuilding index %s", index_name)
        faiss_manager.clear(index_name)

        documents, source_type = _load_index_documents(profile)
        if not documents:
            return jsonify(error("没有可用于重建索引的数据", 404)), 404

        source_count = _count_unique_sources(documents)
        texts = [doc["content"] for doc in documents]
        metadata_list = [doc["metadata"] for doc in documents]
        vectors = bge_client.encode_batch(texts, batch_size=32)
        vector_ids = faiss_manager.add_batch(index_name, vectors, metadata_list)
        faiss_manager.save(index_name)

        return jsonify(success({
            "index_name": index_name,
            "profile": profile,
            "source_count": source_count,
            "items_count": source_count,
            "source_type": source_type,
            "count": len(vector_ids),
            "message": f"索引重建成功，共处理 {source_count} 条源数据，生成 {len(vector_ids)} 个检索块",
        }))
    except Exception as exc:
        logger.error("Failed to rebuild index: %s", exc)
        return jsonify(error(f"重建索引失败: {exc}")), 500


@vector_bp.route("/api/vector/index/<string:index_name>", methods=["DELETE"])
def delete_index(index_name):
    try:
        faiss_manager.delete_index(index_name)
        return jsonify(success({"message": f"索引 {index_name} 删除成功"}))
    except Exception as exc:
        logger.error("Failed to delete index %s: %s", index_name, exc)
        return jsonify(error(f"删除索引失败: {exc}")), 500


@vector_bp.route("/api/vector/index/<int:item_id>", methods=["DELETE"])
def delete_vector(item_id):
    try:
        index_name = request.args.get("index_name", "legal_cases")
        faiss_manager.delete(index_name, item_id)
        faiss_manager.save(index_name)
        return jsonify(success({"message": f"向量索引已标记删除: {item_id}"}))
    except Exception as exc:
        logger.error("Failed to delete vector %s: %s", item_id, exc)
        return jsonify(error(f"删除向量失败: {exc}")), 500


@vector_bp.route("/api/vector/info/<string:index_name>", methods=["GET"])
def get_index_info(index_name):
    try:
        info = faiss_manager.get_index_info(index_name)
        if not info:
            return jsonify(error(f"索引不存在: {index_name}", 404)), 404
        return jsonify(success(info))
    except Exception as exc:
        logger.error("Failed to get index info for %s: %s", index_name, exc)
        return jsonify(error(f"获取索引信息失败: {exc}")), 500


@vector_bp.route("/api/vector/status", methods=["GET"])
def get_vector_status():
    try:
        return jsonify(success(bge_client.get_status()))
    except Exception as exc:
        logger.error("Failed to get vector status: %s", exc)
        return jsonify(error(f"获取向量状态失败: {exc}")), 500
