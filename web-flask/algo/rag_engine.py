"""
Core RAG engine.
"""
import logging
from typing import Any, Dict, List

from algo.legal_lexical_retriever import LegalLexicalRetriever
from algo.legal_profile import filter_chunks_for_profile
from algo.legal_query_analyzer import analyze_legal_query, build_answer_prompt, build_refusal_answer, rerank_candidates
from algo.llm.llm_client import LLMClient
from algo.vector.bge_client import BGEClient
from algo.vector.index_manager import IndexManager
from config import get_config

logger = logging.getLogger(__name__)


class RAGEngine:
    """Retrieval-augmented generation engine."""

    def __init__(self):
        config = get_config()
        self.bge_client = BGEClient()
        self.index_manager = IndexManager()
        self.lexical_retriever = LegalLexicalRetriever()
        self.llm_client = LLMClient(
            api_url=config.LLM_API_URL,
            api_key=config.LLM_API_KEY,
            model=config.LLM_MODEL,
            allowed_models=config.LLM_ALLOWED_MODELS,
        )
        logger.info("RAG engine initialized")

    def query(self, question: str, index_name: str = "default", top_k: int = 5, model: str = None,
              profile: str = "", conversation_history=None, context_notes=None, raw_question: str | None = None) -> Dict[str, Any]:
        try:
            logger.info("RAG query: %s", question[:50])
            retrieval_result = self.search(question, index_name=index_name, top_k=top_k, profile=profile)
            analysis = retrieval_result["query_analysis"]
            if analysis.get("unsafe_request"):
                active_model = self.llm_client.resolve_model(model)
                return {
                    "question": raw_question or question,
                    "answer": build_refusal_answer(analysis),
                    "references": [],
                    "total_references": 0,
                    "query_analysis": analysis,
                    "model": active_model,
                }
            context_docs = retrieval_result["references"]

            active_model = self.llm_client.resolve_model(model)
            answer = self.llm_client.generate_with_context(
                question,
                context_docs,
                system_prompt=build_answer_prompt(analysis),
                model=active_model,
                conversation_history=conversation_history,
                context_notes=context_notes,
            )

            return {
                "question": raw_question or question,
                "answer": answer,
                "references": context_docs,
                "total_references": len(context_docs),
                "query_analysis": analysis,
                "model": active_model,
            }
        except Exception as exc:
            logger.error("RAG query failed: %s", exc)
            raise

    def query_stream(self, question: str, index_name: str = "default", top_k: int = 5, model: str = None,
                     profile: str = "", conversation_history=None, context_notes=None,
                     raw_question: str | None = None) -> Dict[str, Any]:
        logger.info("RAG streaming query: %s", question[:50])
        retrieval_result = self.search(question, index_name=index_name, top_k=top_k, profile=profile)
        analysis = retrieval_result["query_analysis"]
        active_model = self.llm_client.resolve_model(model)
        if analysis.get("unsafe_request"):
            return {
                "question": raw_question or question,
                "answer_stream": iter(()),
                "references": [],
                "total_references": 0,
                "query_analysis": analysis,
                "model": active_model,
                "response_mode": "safety_refusal",
            }

        context_docs = retrieval_result["references"]
        return {
            "question": raw_question or question,
            "answer_stream": self.llm_client.generate_with_context_stream(
                question,
                context_docs,
                system_prompt=build_answer_prompt(analysis),
                model=active_model,
                conversation_history=conversation_history,
                context_notes=context_notes,
            ),
            "references": context_docs,
            "total_references": len(context_docs),
            "query_analysis": analysis,
            "model": active_model,
            "response_mode": "structured_consultation",
        }

    def search(self, question: str, index_name: str = "default", top_k: int = 5, profile: str = "") -> Dict[str, Any]:
        analysis = analyze_legal_query(question)
        analysis["active_profile"] = profile or analysis.get("active_profile") or ""
        if analysis.get("unsafe_request"):
            return {
                "question": question,
                "references": [],
                "total_references": 0,
                "query_analysis": analysis,
            }
        retrieval_queries = self._build_retrieval_queries(question, analysis)
        candidates = self._retrieve_candidates(
            question,
            analysis,
            index_name,
            retrieval_queries,
            top_k=max(top_k * 6, 18),
            profile=analysis.get("active_profile") or "",
        )
        reranked = rerank_candidates(question, candidates, analysis, max(top_k * 4, 18))
        reranked = self._dedupe_by_title(reranked, top_k)
        context_docs = [
            self._to_context_doc(candidate, rank)
            for rank, candidate in enumerate(reranked, start=1)
        ]
        return {
            "question": question,
            "references": context_docs,
            "total_references": len(context_docs),
            "query_analysis": analysis,
        }

    def query_direct(self, question: str, model: str = None, profile: str = "",
                     conversation_history=None, context_notes=None, raw_question: str | None = None) -> Dict[str, Any]:
        try:
            logger.info("Direct LLM query: %s", question[:50])
            analysis = analyze_legal_query(question)
            analysis["active_profile"] = profile or analysis.get("active_profile") or ""
            if analysis.get("unsafe_request"):
                active_model = self.llm_client.resolve_model(model)
                return {
                    "question": raw_question or question,
                    "answer": build_refusal_answer(analysis),
                    "references": [],
                    "total_references": 0,
                    "query_analysis": analysis,
                    "model": active_model,
                }
            active_model = self.llm_client.resolve_model(model)
            answer = self.llm_client.generate_direct(
                question,
                system_prompt=build_answer_prompt(analysis),
                model=active_model,
                conversation_history=conversation_history,
                context_notes=context_notes,
            )

            return {
                "question": raw_question or question,
                "answer": answer,
                "references": [],
                "total_references": 0,
                "query_analysis": analysis,
                "model": active_model,
            }
        except Exception as exc:
            logger.error("Direct LLM query failed: %s", exc)
            raise

    def query_direct_stream(self, question: str, model: str = None, profile: str = "",
                            conversation_history=None, context_notes=None, raw_question: str | None = None) -> Dict[str, Any]:
        logger.info("Direct streaming LLM query: %s", question[:50])
        analysis = analyze_legal_query(question)
        analysis["active_profile"] = profile or analysis.get("active_profile") or ""
        active_model = self.llm_client.resolve_model(model)
        if analysis.get("unsafe_request"):
            return {
                "question": raw_question or question,
                "answer_stream": iter(()),
                "references": [],
                "total_references": 0,
                "query_analysis": analysis,
                "model": active_model,
                "response_mode": "safety_refusal",
            }

        return {
            "question": raw_question or question,
            "answer_stream": self.llm_client.generate_direct_stream(
                question,
                system_prompt=build_answer_prompt(analysis),
                model=active_model,
                conversation_history=conversation_history,
                context_notes=context_notes,
            ),
            "references": [],
            "total_references": 0,
            "query_analysis": analysis,
            "model": active_model,
            "response_mode": "direct_consultation",
        }

    def add_documents(self, documents: List[Dict[str, Any]], index_name: str = "default"):
        try:
            logger.info("Adding %s document(s) to index %s", len(documents), index_name)
            texts = [doc.get("content", "") for doc in documents]
            metadata = [doc.get("metadata", {}) for doc in documents]
            vectors = self.bge_client.encode(texts)
            self.index_manager.add_vectors(index_name, vectors, metadata)
        except Exception as exc:
            logger.error("Failed to add documents: %s", exc)
            raise

    def _build_retrieval_queries(self, question: str, analysis: Dict[str, Any]) -> List[str]:
        queries = [question]
        rewritten = (analysis.get("rewritten_query") or "").strip()
        if rewritten and rewritten != question:
            queries.append(rewritten)

        expansion_terms = list(analysis.get("expanded_terms") or [])
        if expansion_terms:
            queries.append(" ".join(expansion_terms[:8]))

        deduped: List[str] = []
        seen = set()
        for query in queries:
            normalized = (query or "").strip()
            if not normalized or normalized in seen:
                continue
            seen.add(normalized)
            deduped.append(normalized)
        return deduped

    def _retrieve_candidates(self, question: str, analysis: Dict[str, Any], index_name: str,
                             queries: List[str], top_k: int, profile: str = "") -> List[Dict[str, Any]]:
        merged: Dict[str, Dict[str, Any]] = {}

        for query in queries:
            query_vector = self.bge_client.encode(query)
            scores, indices, metadata = self.index_manager.search(index_name, query_vector, top_k)

            for score, idx, meta in zip(scores, indices, metadata):
                key = str(meta.get("chunk_id") or f"{meta.get('item_id')}:{idx}")
                current = merged.get(key)
                if current is None or float(score) > float(current.get("score", 0.0)):
                    merged[key] = {
                        **meta,
                        "score": float(score),
                        "distance": float(score),
                        "index": idx,
                        "matched_queries": [query],
                    }
                else:
                    current.setdefault("matched_queries", []).append(query)

        lexical_candidates = self.lexical_retriever.search(question, analysis=analysis, top_k=top_k, profile=profile)
        for candidate in lexical_candidates:
            key = str(candidate.get("chunk_id") or f"{candidate.get('item_id')}:lexical")
            current = merged.get(key)
            if current is None:
                merged[key] = candidate
            else:
                current["lexical_recall_score"] = max(
                    float(current.get("lexical_recall_score", 0.0) or 0.0),
                    float(candidate.get("lexical_recall_score", 0.0) or 0.0),
                )

        for candidate in merged.values():
            matched_queries = list(dict.fromkeys(candidate.get("matched_queries") or []))
            candidate["matched_queries"] = matched_queries
            candidate["matched_query_count"] = len(matched_queries)

        return filter_chunks_for_profile(list(merged.values()), profile)

    def _dedupe_by_title(self, candidates: List[Dict[str, Any]], top_k: int) -> List[Dict[str, Any]]:
        unique: List[Dict[str, Any]] = []
        seen = set()
        for candidate in candidates:
            source_key = (
                str(candidate.get("title") or "").strip()
                or str(candidate.get("document_id") or "").strip()
                or str(candidate.get("item_id") or "").strip()
                or str(candidate.get("chunk_id") or "").strip()
            )
            if not source_key or source_key in seen:
                continue
            seen.add(source_key)
            unique.append(candidate)
            if len(unique) >= top_k:
                break
        return unique

    def _to_context_doc(self, meta: Dict[str, Any], rank: int) -> Dict[str, Any]:
        return {
            "rank": rank,
            "content": meta.get("content", ""),
            "title": meta.get("title", ""),
            "distance": float(meta.get("distance", meta.get("score", 0.0)) or 0.0),
            "hybrid_score": float(meta.get("hybrid_score", 0.0) or 0.0),
            "category": meta.get("category_name", ""),
            "tags": meta.get("tags", ""),
            "summary": meta.get("summary", ""),
            "source": meta.get("source", "case_db"),
            "source_name": meta.get("source_name", ""),
            "source_url": meta.get("source_url", ""),
            "issuer": meta.get("issuer", ""),
            "effective_date": meta.get("effective_date", ""),
            "current_status": meta.get("current_status", ""),
            "item_id": meta.get("item_id"),
            "document_id": meta.get("document_id"),
            "chunk_id": meta.get("chunk_id"),
            "chunk_index": meta.get("chunk_index"),
            "section_type": meta.get("section_type", ""),
            "section_title": meta.get("section_title", ""),
            "retrieval_role": meta.get("retrieval_role", ""),
            "knowledge_type": meta.get("knowledge_type", ""),
            "case_type": meta.get("case_type", ""),
            "cause": meta.get("cause", ""),
            "business_domain": meta.get("business_domain", ""),
            "procedure_domain": meta.get("procedure_domain", ""),
            "court_name": meta.get("court_name", ""),
            "court_level": meta.get("court_level", ""),
        }
