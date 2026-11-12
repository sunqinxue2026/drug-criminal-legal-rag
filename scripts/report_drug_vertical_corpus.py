from __future__ import annotations

import argparse
import json
from collections import Counter
from pathlib import Path
from typing import Any, Dict, Iterable, List
from urllib.parse import urlparse


ROOT_DIR = Path(__file__).resolve().parents[1]
DEFAULT_PROCESSED_DIR = ROOT_DIR / "data" / "legal_sources" / "drug_vertical" / "processed"
DEFAULT_JSON_OUTPUT = ROOT_DIR / "data" / "legal_sources" / "drug_vertical" / "processed" / "corpus_report.json"
DEFAULT_MARKDOWN_OUTPUT = ROOT_DIR / "docs" / "DRUG_VERTICAL_CORPUS_REPORT.md"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="统计涉毒刑事垂直语料库覆盖情况")
    parser.add_argument("--processed-dir", default=str(DEFAULT_PROCESSED_DIR), help="processed JSON 目录")
    parser.add_argument("--json-output", default=str(DEFAULT_JSON_OUTPUT), help="统计 JSON 输出路径")
    parser.add_argument("--markdown-output", default=str(DEFAULT_MARKDOWN_OUTPUT), help="统计 Markdown 输出路径")
    return parser.parse_args()


def iter_json_files(processed_dir: Path) -> List[Path]:
    excluded_prefixes = ("temp_batch_", "import_batch_")
    excluded_suffixes = ("_test.json", ".summary.json")
    excluded_names = {"corpus_report.json"}
    return sorted(
        path for path in processed_dir.rglob("*.json")
        if not path.name.startswith(excluded_prefixes)
        and not path.name.endswith(excluded_suffixes)
        and path.name not in excluded_names
    )


def load_payload(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def normalize_text(value: Any) -> str:
    if value is None:
        return ""
    return str(value).strip()


def flatten_records(payload: Any) -> List[Dict[str, Any]]:
    if isinstance(payload, list):
        records: List[Dict[str, Any]] = []
        for item in payload:
            records.extend(flatten_records(item))
        return records

    if not isinstance(payload, dict):
        return []

    base = {key: value for key, value in payload.items() if key not in {"articles", "entries"}}
    nested = payload.get("articles")
    if isinstance(nested, list):
        return [{**base, **item} for item in nested if isinstance(item, dict)]

    nested = payload.get("entries")
    if isinstance(nested, list):
        return [{**base, **item} for item in nested if isinstance(item, dict)]

    return [payload]


def source_domain(record: Dict[str, Any]) -> str:
    url = normalize_text(record.get("sourceUrl"))
    if not url:
        return "unknown"
    host = urlparse(url).netloc.lower()
    return host or "unknown"


def relative_parent_label(path: Path, root: Path) -> str:
    relative = path.relative_to(root)
    parent = relative.parent.as_posix()
    return parent if parent != "." else "root"


def top_items(counter: Counter[str], limit: int = 10) -> List[Dict[str, Any]]:
    return [
        {"name": name, "count": count}
        for name, count in counter.most_common(limit)
    ]


def build_report(processed_dir: Path) -> Dict[str, Any]:
    files = iter_json_files(processed_dir)

    folder_counter: Counter[str] = Counter()
    knowledge_type_counter: Counter[str] = Counter()
    document_level_counter: Counter[str] = Counter()
    issuer_counter: Counter[str] = Counter()
    source_name_counter: Counter[str] = Counter()
    source_domain_counter: Counter[str] = Counter()
    status_counter: Counter[str] = Counter()
    file_summaries: List[Dict[str, Any]] = []

    total_documents = 0
    total_records = 0

    for file_path in files:
        payload = load_payload(file_path)
        documents = payload if isinstance(payload, list) else [payload]
        documents = [item for item in documents if isinstance(item, dict)]
        records = flatten_records(payload)

        total_documents += len(documents)
        total_records += len(records)
        folder_counter[relative_parent_label(file_path, processed_dir)] += 1

        for record in records:
            knowledge_type_counter[normalize_text(record.get("knowledgeType")) or "unknown"] += 1
            document_level_counter[normalize_text(record.get("documentLevel")) or "unknown"] += 1
            issuer_counter[normalize_text(record.get("issuer")) or "unknown"] += 1
            source_name_counter[normalize_text(record.get("sourceName")) or "unknown"] += 1
            source_domain_counter[source_domain(record)] += 1
            status_counter[normalize_text(record.get("currentStatus")) or "unknown"] += 1

        file_summaries.append(
            {
                "file": str(file_path.relative_to(processed_dir).as_posix()),
                "document_count": len(documents),
                "record_count": len(records),
                "folder": relative_parent_label(file_path, processed_dir),
                "sample_document": normalize_text((documents[0] if documents else {}).get("documentName")) if documents else "",
            }
        )

    return {
        "processed_dir": str(processed_dir),
        "file_count": len(files),
        "document_count": total_documents,
        "record_count": total_records,
        "folder_distribution": top_items(folder_counter, limit=20),
        "knowledge_type_distribution": top_items(knowledge_type_counter, limit=20),
        "document_level_distribution": top_items(document_level_counter, limit=20),
        "issuer_distribution": top_items(issuer_counter, limit=20),
        "source_name_distribution": top_items(source_name_counter, limit=20),
        "source_domain_distribution": top_items(source_domain_counter, limit=20),
        "status_distribution": top_items(status_counter, limit=20),
        "files": file_summaries,
    }


def render_markdown(report: Dict[str, Any]) -> str:
    def render_table(items: Iterable[Dict[str, Any]], title: str) -> str:
        rows = [f"## {title}", "", "| 类别 | 数量 |", "| --- | ---: |"]
        for item in items:
            rows.append(f"| {item['name']} | {item['count']} |")
        rows.append("")
        return "\n".join(rows)

    summary = [
        "# 涉毒刑事垂直语料覆盖报告",
        "",
        "## 总体规模",
        "",
        f"- processed JSON 文件数：{report['file_count']}",
        f"- 原始文档数：{report['document_count']}",
        f"- 可检索知识记录数：{report['record_count']}",
        "",
        "## 论文可直接引用的表述",
        "",
        (
            "本系统围绕涉毒刑事法律咨询场景，构建了包含法律法规、司法解释、指导性案例、典型案例与官方解读在内的"
            f"垂直语料库。当前已整理 {report['file_count']} 份 processed JSON 来源文件，"
            f"累计形成 {report['record_count']} 条可切片检索的知识记录，覆盖法条依据、程序权利、家属应对、"
            "新型毒品风险与典型案例等核心问答场景。"
        ),
        "",
    ]

    tables = [
        render_table(report["folder_distribution"], "来源目录分布"),
        render_table(report["knowledge_type_distribution"], "知识类型分布"),
        render_table(report["document_level_distribution"], "文档层级分布"),
        render_table(report["issuer_distribution"], "发布主体分布"),
        render_table(report["source_domain_distribution"], "来源域名分布"),
        render_table(report["status_distribution"], "效力状态分布"),
    ]
    return "\n".join(summary + tables)


def write_outputs(report: Dict[str, Any], json_output: Path, markdown_output: Path) -> None:
    json_output.parent.mkdir(parents=True, exist_ok=True)
    markdown_output.parent.mkdir(parents=True, exist_ok=True)
    json_output.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    markdown_output.write_text(render_markdown(report), encoding="utf-8")


def main() -> None:
    args = parse_args()
    processed_dir = Path(args.processed_dir).resolve()
    report = build_report(processed_dir)
    write_outputs(report, Path(args.json_output), Path(args.markdown_output))
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
