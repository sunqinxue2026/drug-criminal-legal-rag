import unittest

from algo.structured_answer import build_structured_answer, optimize_lawyer_follow_up


class StructuredAnswerTest(unittest.TestCase):

    def test_build_structured_answer_reads_labeled_paragraphs(self):
        answer = (
            "\u521d\u6b65\u7ed3\u8bba\uff1a\u53ef\u80fd\u6784\u6210\u6d89\u6bd2\u72af\u7f6a\u3002\n"
            "\u6cd5\u5f8b\u4f9d\u636e\uff1a\u4f9d\u636e\u5211\u6cd5\u548c\u53f8\u6cd5\u89e3\u91ca\u3002\n"
            "\u98ce\u9669\u63d0\u793a\uff1a\u5b58\u5728\u5211\u4e8b\u8ffd\u8bc9\u98ce\u9669\u3002\n"
            "\u884c\u52a8\u5efa\u8bae\uff1a\u5c3d\u5feb\u8054\u7cfb\u5f8b\u5e08\u3002"
        )

        structured = build_structured_answer(answer)

        self.assertEqual(structured["conclusion"], "\u53ef\u80fd\u6784\u6210\u6d89\u6bd2\u72af\u7f6a\u3002")
        self.assertEqual(structured["basis"], "\u4f9d\u636e\u5211\u6cd5\u548c\u53f8\u6cd5\u89e3\u91ca\u3002")
        self.assertEqual(structured["risk"], "\u5b58\u5728\u5211\u4e8b\u8ffd\u8bc9\u98ce\u9669\u3002")
        self.assertEqual(structured["advice"], "\u5c3d\u5feb\u8054\u7cfb\u5f8b\u5e08\u3002")

    def test_build_structured_answer_falls_back_to_full_text(self):
        answer = "\u8fd9\u662f\u4e00\u6bb5\u6ca1\u6709\u6807\u9898\u7684\u666e\u901a\u7b54\u590d\u3002"

        structured = build_structured_answer(answer)

        self.assertEqual(structured["conclusion"], answer)
        self.assertEqual(structured["basis"], "")
        self.assertEqual(structured["risk"], "")
        self.assertEqual(structured["advice"], "")

    def test_optimize_removes_follow_up_for_final_outcome_request(self):
        answer = (
            "初步结论：现有信息下更倾向于行政处罚风险，暂不能认定构成刑事犯罪。\n"
            "法律依据：应结合毒品犯罪构成要件、证据链条和检测结果判断。\n"
            "风险提示：如果后续发现交易、持有或再次阳性检测，结论可能变化。\n"
            "行动建议：保留检测材料并配合合法程序。\n"
            "律师追问：手机里的聊天记录和转账记录，能否对应到具体对象和具体内容？"
        )

        optimized = optimize_lawyer_follow_up(answer, current_query="告诉我你的最终审判结果")

        self.assertNotIn("律师追问", optimized)

    def test_optimize_removes_follow_up_for_case_consultation_request(self):
        answer = (
            "初步结论：朱某鸣贩卖甲基苯丙胺数量大，裁判结果应重点关注数量和从宽情节。\n"
            "法律依据：贩卖毒品罪应结合毒品种类、数量和量刑情节判断。\n"
            "风险提示：同类案件只具有参考意义。\n"
            "行动建议：阅读裁判理由和量刑情节。\n"
            "律师追问：现场查获物是否已经完成称重、鉴定并形成扣押清单？"
        )

        optimized = optimize_lawyer_follow_up(answer, current_query="朱某鸣贩卖毒品案")

        self.assertNotIn("律师追问", optimized)

    def test_optimize_removes_repeated_follow_up_when_no_new_candidate(self):
        answer = (
            "初步结论：目前只能作初步判断。\n"
            "法律依据：应结合证据链条判断。\n"
            "风险提示：电子数据仍会影响定性。\n"
            "行动建议：先整理材料。\n"
            "律师追问：手机里的聊天记录和转账记录，能否对应到具体对象和具体内容？"
        )
        history = [{"role": "assistant", "content": answer}]

        optimized = optimize_lawyer_follow_up(
            answer,
            current_query="我已经说明聊天记录和转账情况了",
            conversation_history=history,
        )

        self.assertNotIn("律师追问", optimized)


if __name__ == "__main__":
    unittest.main()
