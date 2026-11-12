"""
FAISS-based vector index manager with numpy fallback.
"""
import logging
import os
import pickle
from datetime import datetime

import numpy as np

from config import get_config

try:
    import faiss
except ImportError:  # pragma: no cover - local fallback path
    faiss = None

logger = logging.getLogger(__name__)


class _NumpyIndex:
    def __init__(self, dimension):
        self.d = dimension
        self.vectors = np.empty((0, dimension), dtype="float32")

    @property
    def ntotal(self):
        return int(len(self.vectors))

    def add(self, vectors):
        array = np.array(vectors, dtype="float32")
        if array.ndim == 1:
            array = array.reshape(1, -1)
        if array.shape[1] != self.d:
            raise ValueError(f"vector dimension mismatch: expected {self.d}, got {array.shape[1]}")
        if self.vectors.size == 0:
            self.vectors = array.copy()
        else:
            self.vectors = np.vstack([self.vectors, array])

    def search(self, query, limit):
        query_array = np.array(query, dtype="float32")
        if query_array.ndim == 1:
            query_array = query_array.reshape(1, -1)
        if self.ntotal == 0:
            return (
                np.empty((1, 0), dtype="float32"),
                np.empty((1, 0), dtype="int64"),
            )

        scores = np.matmul(self.vectors, query_array[0])
        top_indices = np.argsort(-scores)[:limit]
        top_scores = scores[top_indices]
        return top_scores.reshape(1, -1), top_indices.reshape(1, -1)


def _to_normalized_float32(vectors):
    array = np.array(vectors, dtype="float32")
    if array.ndim == 1:
        array = array.reshape(1, -1)
    if faiss is not None:
        faiss.normalize_L2(array)
    else:
        norms = np.linalg.norm(array, axis=1, keepdims=True)
        norms[norms == 0] = 1.0
        array = array / norms
    return array


def _build_source_key(metadata):
    if not isinstance(metadata, dict):
        return None

    source = str(metadata.get("source") or "").strip() or "unknown"
    document_id = metadata.get("document_id")
    item_id = metadata.get("item_id")
    title = str(metadata.get("title") or "").strip()

    source_id = document_id if document_id not in (None, "") else item_id
    if source_id not in (None, ""):
        return (source, str(source_id))
    if title:
        return (source, title)
    return None


def _count_unique_sources(metadata_list):
    keys = {
        key
        for key in (_build_source_key(metadata) for metadata in (metadata_list or []))
        if key is not None
    }
    return len(keys)


class FaissManager:
    """Manage local FAISS indexes and their metadata."""

    def __init__(self, index_dir=None):
        config = get_config()
        self.index_dir = index_dir or getattr(config, "INDEX_DIR", "./data/indexes")
        self.indexes = {}
        self.metadata = {}

        os.makedirs(self.index_dir, exist_ok=True)
        logger.info("Initialized FAISS manager with index dir: %s", self.index_dir)
        self._load_all_indexes()

    def _load_all_indexes(self):
        try:
            for filename in os.listdir(self.index_dir):
                if filename.endswith(".index"):
                    index_name = filename[:-6]
                    self.load(index_name)
        except Exception as exc:
            logger.error("Failed to load indexes from disk: %s", exc)

    def create_index(self, name, dimension=1024):
        try:
            index = faiss.IndexFlatIP(dimension) if faiss is not None else _NumpyIndex(dimension)
            self.indexes[name] = index
            self.metadata[name] = []
            self.save(name)
            logger.info("Created index %s with dimension %s", name, dimension)
            return index
        except Exception as exc:
            logger.error("Failed to create index %s: %s", name, exc)
            raise

    def add(self, index_name, vector, metadata=None):
        try:
            normalized = _to_normalized_float32(vector)
            if index_name not in self.indexes:
                self.create_index(index_name, normalized.shape[1])

            index = self.indexes[index_name]
            index.add(normalized)
            self.metadata[index_name].append(metadata or {})
            vector_id = index.ntotal - 1
            logger.debug("Added vector %s to index %s", vector_id, index_name)
            return vector_id
        except Exception as exc:
            logger.error("Failed to add vector to %s: %s", index_name, exc)
            raise

    def add_batch(self, index_name, vectors, metadata_list=None):
        try:
            normalized = _to_normalized_float32(vectors)
            if index_name not in self.indexes:
                self.create_index(index_name, normalized.shape[1])

            index = self.indexes[index_name]
            start_id = index.ntotal
            index.add(normalized)

            if metadata_list:
                self.metadata[index_name].extend(metadata_list)
            else:
                self.metadata[index_name].extend([{}] * len(normalized))

            vector_ids = list(range(start_id, index.ntotal))
            logger.info("Added %s vectors to index %s", len(vector_ids), index_name)
            return vector_ids
        except Exception as exc:
            logger.error("Failed to batch add vectors to %s: %s", index_name, exc)
            raise

    def search(self, index_name, query_vector, k=5):
        try:
            if index_name not in self.indexes:
                logger.warning("Index not found: %s", index_name)
                return []

            index = self.indexes[index_name]
            if index.ntotal == 0:
                return []

            normalized_query = _to_normalized_float32(query_vector)
            limit = min(k, index.ntotal)
            scores, indices = index.search(normalized_query, limit)

            results = []
            for i, idx in enumerate(indices[0]):
                if idx < 0 or idx >= len(self.metadata[index_name]):
                    continue
                result = dict(self.metadata[index_name][idx])
                result["score"] = float(scores[0][i])
                result["index"] = int(idx)
                results.append(result)

            logger.info("Search completed on %s with %s result(s)", index_name, len(results))
            return results
        except Exception as exc:
            logger.error("Search failed on %s: %s", index_name, exc)
            raise

    def delete(self, index_name, vector_id):
        try:
            if index_name in self.metadata and 0 <= vector_id < len(self.metadata[index_name]):
                updated = dict(self.metadata[index_name][vector_id])
                updated["deleted"] = True
                self.metadata[index_name][vector_id] = updated
                logger.info("Marked vector %s as deleted in %s", vector_id, index_name)
        except Exception as exc:
            logger.error("Failed to mark vector deleted in %s: %s", index_name, exc)

    def clear(self, index_name):
        try:
            if index_name in self.indexes:
                dimension = self.indexes[index_name].d
                self.indexes[index_name] = faiss.IndexFlatIP(dimension) if faiss is not None else _NumpyIndex(dimension)
                self.metadata[index_name] = []
                logger.info("Cleared index %s", index_name)
        except Exception as exc:
            logger.error("Failed to clear index %s: %s", index_name, exc)

    def save(self, index_name):
        try:
            if index_name not in self.indexes:
                return

            index_path = os.path.join(self.index_dir, f"{index_name}.index")
            metadata_path = os.path.join(self.index_dir, f"{index_name}.meta")

            if faiss is not None:
                faiss.write_index(self.indexes[index_name], index_path)
            else:
                with open(index_path, "wb") as file_obj:
                    pickle.dump({
                        "dimension": self.indexes[index_name].d,
                        "vectors": self.indexes[index_name].vectors,
                    }, file_obj)
            with open(metadata_path, "wb") as file_obj:
                pickle.dump(self.metadata[index_name], file_obj)
            logger.info("Saved index %s", index_name)
        except Exception as exc:
            logger.error("Failed to save index %s: %s", index_name, exc)

    def load(self, index_name):
        try:
            index_path = os.path.join(self.index_dir, f"{index_name}.index")
            metadata_path = os.path.join(self.index_dir, f"{index_name}.meta")

            if not os.path.exists(index_path):
                return

            if faiss is not None:
                self.indexes[index_name] = faiss.read_index(index_path)
            else:
                with open(index_path, "rb") as file_obj:
                    payload = pickle.load(file_obj)
                index = _NumpyIndex(int(payload.get("dimension", 0) or 0))
                vectors = np.array(payload.get("vectors") or [], dtype="float32")
                if vectors.size:
                    index.add(vectors)
                self.indexes[index_name] = index
            if os.path.exists(metadata_path):
                with open(metadata_path, "rb") as file_obj:
                    self.metadata[index_name] = pickle.load(file_obj)
            else:
                self.metadata[index_name] = []
            logger.info("Loaded index %s", index_name)
        except Exception as exc:
            logger.error("Failed to load index %s: %s", index_name, exc)

    def list_indexes(self):
        result = []
        now = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        for name, index in self.indexes.items():
            metadata_list = self.metadata.get(name, [])
            result.append(
                {
                    "name": name,
                    "count": index.ntotal,
                    "sourceCount": _count_unique_sources(metadata_list),
                    "metadataCount": len(metadata_list),
                    "dimension": index.d,
                    "createTime": now,
                    "updateTime": now,
                }
            )
        return result

    def get_index_info(self, index_name):
        if index_name not in self.indexes:
            return None

        index = self.indexes[index_name]
        return {
            "name": index_name,
            "count": index.ntotal,
            "sourceCount": _count_unique_sources(self.metadata.get(index_name, [])),
            "dimension": index.d,
            "metadata_count": len(self.metadata.get(index_name, [])),
        }

    def delete_index(self, index_name):
        try:
            self.indexes.pop(index_name, None)
            self.metadata.pop(index_name, None)

            index_path = os.path.join(self.index_dir, f"{index_name}.index")
            metadata_path = os.path.join(self.index_dir, f"{index_name}.meta")

            if os.path.exists(index_path):
                os.remove(index_path)
            if os.path.exists(metadata_path):
                os.remove(metadata_path)
            logger.info("Deleted index %s", index_name)
        except Exception as exc:
            logger.error("Failed to delete index %s: %s", index_name, exc)
            raise
