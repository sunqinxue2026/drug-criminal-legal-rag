"""
Index manager built on top of the real FAISS implementation.
"""
import logging
from typing import Any, Dict, Iterable, List, Tuple

import numpy as np

from .faiss_manager import FaissManager

logger = logging.getLogger(__name__)


class IndexManager(FaissManager):
    """Compatibility wrapper used by the RAG engine."""

    def __init__(self, index_dir="./data/indexes"):
        super().__init__(index_dir)
        logger.info("Index manager initialized with directory: %s", index_dir)

    def add_vectors(self, index_name: str, vectors, metadata: Iterable[Dict[str, Any]] = None):
        vector_array = np.array(vectors, dtype="float32")
        metadata_list = list(metadata or [])

        if vector_array.ndim == 1:
            vector_array = vector_array.reshape(1, -1)

        if metadata_list and len(metadata_list) != len(vector_array):
            raise ValueError("metadata count must match vector count")

        vector_ids = self.add_batch(
            index_name=index_name,
            vectors=vector_array,
            metadata_list=metadata_list or None,
        )
        self.save(index_name)
        logger.info("Added %s vectors to index %s", len(vector_ids), index_name)
        return vector_ids

    def search(
        self, index_name: str, query_vector, top_k: int = 5
    ) -> Tuple[List[float], List[int], List[Dict[str, Any]]]:
        results = super().search(index_name=index_name, query_vector=query_vector, k=top_k)

        scores: List[float] = []
        indices: List[int] = []
        metadata: List[Dict[str, Any]] = []

        for item in results:
            if item.get("deleted", False):
                continue
            scores.append(float(item.get("score", 0.0)))
            indices.append(int(item.get("index", -1)))
            metadata.append(item)

        logger.info("Search completed for %s with %s usable result(s)", index_name, len(metadata))
        return scores, indices, metadata
