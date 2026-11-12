from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any, Dict, List

try:
    import requests
except ModuleNotFoundError:
    requests = None


ROOT_DIR = Path(__file__).resolve().parents[1]
DEFAULT_DATASET = ROOT_DIR / "data" / "evaluation" / "drug_rag_eval_set.json"
DEFAULT_OUTPUT = ROOT_DIR / "logs" / "drug_rag_eval_report.json"


def require_requests():
    if requests is None:
        raise RuntimeError(
            "运行评测脚本前需要安装 requests。"
            "可执行 `pip install -r web-flask/requirements.txt` 补齐依赖。"
        )
    return requests


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="评测涉毒刑事 RAG 问答链路")
    parser.add_argument("--endpoint", default="http://localhost:5000/api/chat", help="Flask /api/chat 地址")
    parser.add_argument("--dataset", default=str(DEFAULT_DATASET), help="评测集 JSON")
    parser.add_argument("--output", default=str(DEFAULT_OUTPUT), help="评测报告输出路径")
    parser.add_argument("--timeout", type=int, default=120, help="单题超时时间（秒）")
    return parser.parse_args()


def load_dataset(path: Path) -> List[Dict[str, Any]]:
    payload = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(payload, list):
        raise ValueError("评测集必须是 JSON 数组")
    return [item for item in payload if isinstance(item, dict)]


def has_required_sections(structured_answer: Dict[str, Any], required_sections: List[str]) -> bool:
    for section in required_sections:
        if not str(structured_answer.get(section) or "").strip():
            return False
    return True


def keyword_hit_rate(text: str, keywords: List[str]) -> float:
    if not keywords:
        return 1.0
    hits = sum(1 for keyword in keywords if keyword and keyword in text)
    return hits / len(keywords)


def evaluate_item(item: Dict[str, Any], endpoint: str, timeout: int) -> Dict[str, Any]:
    response = require_requests().post(
        endpoint,
        json={
            "question": item["question"],
            "profile": "drug_crime",
            "smart_consulting": True,
            "top_k": 5,
        },
        timeout=timeout,
    )
    response.raise_for_status()
    payload = response.json().get("data", {})
    answer = str(payload.get("answer") or "")
    structured_answer = payload.get("structured_answer") or {}
    references = payload.get("references") or []
    combined_reference_text = "\n".join(str(ref.get("content") or "") for ref in references if isinstance(ref, dict))
    combined_text = f"{answer}\n{combined_reference_text}"
    keyword_score = keyword_hit_rate(combined_text, item.get("expected_keywords") or [])
    section_ok = has_required_sections(structured_answer, item.get("required_sections") or [])
    min_references = int(item.get("min_references") or 0)
    citation_ok = len(references) >= min_references
    passed = section_ok and citation_ok and keyword_score >= 0.5
    return {
        "id": item.get("id"),
        "question": item.get("question"),
        "answer_preview": answer[:180],
        "reference_count": len(references),
        "section_ok": section_ok,
        "citation_ok": citation_ok,
        "keyword_hit_rate": round(keyword_score, 4),
        "passed": passed,
    }


def main() -> None:
    args = parse_args()
    dataset = load_dataset(Path(args.dataset))
    results: List[Dict[str, Any]] = []
    for item in dataset:
        results.append(evaluate_item(item, args.endpoint, args.timeout))

    total = len(results)
    passed = sum(1 for item in results if item["passed"])
    section_ok = sum(1 for item in results if item["section_ok"])
    citation_ok = sum(1 for item in results if item["citation_ok"])
    avg_keyword_hit_rate = round(sum(item["keyword_hit_rate"] for item in results) / total, 4) if total else 0.0

    report = {
        "dataset": str(Path(args.dataset).resolve()),
        "endpoint": args.endpoint,
        "total": total,
        "passed": passed,
        "pass_rate": round(passed / total, 4) if total else 0.0,
        "section_completeness_rate": round(section_ok / total, 4) if total else 0.0,
        "citation_presence_rate": round(citation_ok / total, 4) if total else 0.0,
        "avg_keyword_hit_rate": avg_keyword_hit_rate,
        "results": results,
    }

    output_path = Path(args.output)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
