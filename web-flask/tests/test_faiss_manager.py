import unittest

from algo.vector.faiss_manager import _count_unique_sources


class FaissManagerStatsTest(unittest.TestCase):

    def test_count_unique_sources_deduplicates_chunks_from_same_record(self):
        metadata_list = [
            {"source": "legal_document_db", "document_id": 101, "chunk_index": 0},
            {"source": "legal_document_db", "document_id": 101, "chunk_index": 1},
            {"source": "legal_knowledge_db", "document_id": 202, "chunk_index": 0},
            {"source": "case_db", "item_id": 303, "chunk_index": 0},
            {"source": "case_db", "item_id": 303, "chunk_index": 1},
        ]

        self.assertEqual(_count_unique_sources(metadata_list), 3)

    def test_count_unique_sources_falls_back_to_title_when_ids_missing(self):
        metadata_list = [
            {"source": "legal_document_db", "title": "示例文书", "chunk_index": 0},
            {"source": "legal_document_db", "title": "示例文书", "chunk_index": 1},
            {"source": "legal_document_db", "title": "另一份文书", "chunk_index": 0},
        ]

        self.assertEqual(_count_unique_sources(metadata_list), 2)


if __name__ == "__main__":
    unittest.main()
