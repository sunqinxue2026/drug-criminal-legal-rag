import importlib.util
import json
import shutil
import unittest
from pathlib import Path
from uuid import uuid4


ROOT_DIR = Path(__file__).resolve().parents[1]
TEST_TMP_DIR = ROOT_DIR / "tmp"
TEST_TMP_DIR.mkdir(exist_ok=True)


def load_module(module_name: str, path: Path):
    spec = importlib.util.spec_from_file_location(module_name, path)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    spec.loader.exec_module(module)
    return module


evaluate_drug_rag = load_module("evaluate_drug_rag", ROOT_DIR / "scripts" / "evaluate_drug_rag.py")
report_drug_vertical_corpus = load_module("report_drug_vertical_corpus", ROOT_DIR / "scripts" / "report_drug_vertical_corpus.py")


class EvaluateDrugRagScriptTest(unittest.TestCase):

    def test_has_required_sections_requires_all_non_empty_sections(self):
        structured_answer = {
            "conclusion": "A",
            "basis": "B",
            "risk": "C",
            "advice": "D",
        }
        self.assertTrue(
            evaluate_drug_rag.has_required_sections(
                structured_answer,
                ["conclusion", "basis", "risk", "advice"],
            )
        )
        self.assertFalse(
            evaluate_drug_rag.has_required_sections(
                {**structured_answer, "risk": ""},
                ["conclusion", "basis", "risk", "advice"],
            )
        )

    def test_keyword_hit_rate_uses_keyword_coverage(self):
        text = "拘留通知 家属 律师 程序"
        score = evaluate_drug_rag.keyword_hit_rate(text, ["家属", "律师", "程序", "取保候审"])
        self.assertEqual(score, 0.75)


class ReportDrugVerticalCorpusScriptTest(unittest.TestCase):

    def make_test_dir(self) -> Path:
        path = TEST_TMP_DIR / f"drugtest_{uuid4().hex}"
        path.mkdir(parents=True, exist_ok=False)
        self.addCleanup(lambda: shutil.rmtree(path, ignore_errors=True))
        return path

    def test_flatten_records_supports_articles_and_entries(self):
        payload = [
            {
                "documentName": "文档A",
                "articles": [{"articleNumber": "第一条"}, {"articleNumber": "第二条"}],
            },
            {
                "documentName": "文档B",
                "entries": [{"articleNumber": "案例一"}],
            },
        ]
        records = report_drug_vertical_corpus.flatten_records(payload)
        self.assertEqual(len(records), 3)
        self.assertEqual(records[0]["documentName"], "文档A")
        self.assertEqual(records[-1]["articleNumber"], "案例一")

    def test_build_report_counts_files_and_records(self):
        processed_dir = self.make_test_dir()
        (processed_dir / "laws").mkdir()
        (processed_dir / "guides").mkdir()

        (processed_dir / "laws" / "law.json").write_text(
            json.dumps(
                {
                    "documentName": "禁毒法",
                    "knowledgeType": "法律法规",
                    "documentLevel": "法律",
                    "issuer": "全国人大常委会",
                    "sourceName": "中国政府网",
                    "sourceUrl": "https://www.gov.cn/example",
                    "currentStatus": "现行有效",
                    "articles": [
                        {"articleNumber": "第一条"},
                        {"articleNumber": "第二条"},
                    ],
                },
                ensure_ascii=False,
            ),
            encoding="utf-8",
        )
        (processed_dir / "guides" / "guide.json").write_text(
            json.dumps(
                {
                    "documentName": "家属应对指南",
                    "knowledgeType": "官方解读",
                    "documentLevel": "专题解读",
                    "issuer": "最高检",
                    "sourceName": "最高检官网",
                    "sourceUrl": "https://www.spp.gov.cn/example",
                    "currentStatus": "公开发布",
                    "entries": [{"articleNumber": "导读"}],
                },
                ensure_ascii=False,
            ),
            encoding="utf-8",
        )

        report = report_drug_vertical_corpus.build_report(processed_dir)

        self.assertEqual(report["file_count"], 2)
        self.assertEqual(report["document_count"], 2)
        self.assertEqual(report["record_count"], 3)
        self.assertEqual(report["folder_distribution"][0]["count"], 1)
        self.assertTrue(any(item["name"] == "www.gov.cn" for item in report["source_domain_distribution"]))


if __name__ == "__main__":
    unittest.main()
