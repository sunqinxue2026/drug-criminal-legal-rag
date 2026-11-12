import sys
import unittest
from pathlib import Path


ROOT_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT_DIR / "web-flask"))

from algo.legal_query_analyzer import build_answer_prompt
from algo.llm.llm_client import LLMClient
from algo.structured_answer import build_structured_answer, optimize_lawyer_follow_up


class StructuredAnswerTest(unittest.TestCase):

    def test_build_structured_answer_supports_initial_conclusion_label(self):
        answer = (
            "初步结论：仅凭“吸食过毒品”这一表述，不能直接等同于构成毒品犯罪。\n"
            "法律依据：是否构成犯罪，要结合具体行为、数量和证据判断。\n"
            "风险提示：如果同时涉及持有、运输或容留他人吸毒，风险会明显上升。\n"
            "行动建议：先核实现有材料，再决定下一步辩护方向。\n"
            "律师追问：公安机关目前是已经对你采取拘留措施，还是还处于口头传唤阶段？是否已经做过尿检？"
        )

        structured = build_structured_answer(answer)

        self.assertEqual(structured["conclusion"], "仅凭“吸食过毒品”这一表述，不能直接等同于构成毒品犯罪。")
        self.assertTrue(structured["basis"].startswith("是否构成犯罪"))
        self.assertTrue(structured["risk"].startswith("如果同时涉及持有"))
        self.assertTrue(structured["advice"].startswith("先核实现有材料"))
        self.assertEqual(structured["followUp"], "公安机关目前是已经对你采取拘留措施，还是还处于口头传唤阶段？")


class LawyerStylePromptTest(unittest.TestCase):

    def test_build_answer_prompt_emphasizes_lawyer_style_and_substantive_sections(self):
        prompt = build_answer_prompt({"answer_style": "layperson_practical"})

        self.assertIn("合格律师沟通素养", prompt)
        self.assertIn("像律师接待咨询一样", prompt)
        self.assertIn("已知事实", prompt)
        self.assertIn("待核实", prompt)
        self.assertIn("必须直接回应用户核心问题", prompt)
        self.assertIn("初步结论", prompt)
        self.assertIn("行动建议", prompt)
        self.assertIn("律师追问", prompt)
        self.assertIn("只能问一个", prompt)


class MultiTurnContextTest(unittest.TestCase):

    def test_context_messages_mark_short_reply_as_answer_to_previous_follow_up(self):
        client = LLMClient(api_key="", allowed_models=["qwen-turbo"])
        history = [
            {
                "role": "assistant",
                "content": (
                    "初步结论：目前更像非法持有毒品。\n"
                    "法律依据：要看数量和证据。\n"
                    "风险提示：仍要警惕往贩卖方向审查。\n"
                    "行动建议：尽快委托律师。\n"
                    "律师追问：被拘留人是否在案发前曾向他人出售过毒品或有明确的贩卖意图？"
                ),
            },
            {
                "role": "user",
                "content": "没有",
            },
        ]

        messages = client._build_context_messages(history, [])
        merged = "\n".join(message["content"] for message in messages)

        self.assertIn("针对上一轮律师追问", merged)
        self.assertIn("用户已经明确回答", merged)
        self.assertIn("不得重复追问同一事实", merged)
        self.assertIn("没有", merged)

    def test_short_current_query_is_treated_as_reply_to_latest_follow_up(self):
        client = LLMClient(api_key="", allowed_models=["qwen-turbo"])
        history = [
            {
                "role": "assistant",
                "content": (
                    "初步结论：当前更像非法持有毒品。\n"
                    "法律依据：要看聊天记录和转账记录。\n"
                    "风险提示：仍需警惕被往贩卖方向审查。\n"
                    "行动建议：尽快委托律师。\n"
                    "律师追问：被拘留人是否在案发前后有与他人联系或交易毒品的行为？"
                ),
            }
        ]

        messages = client._build_query_resolution_messages("没有", history)

        self.assertEqual(len(messages), 1)
        self.assertIn("应视为对上一轮律师追问", messages[0]["content"])
        self.assertIn("不得重复追问同一问题", messages[0]["content"])

    def test_short_current_query_is_rewritten_for_model_as_follow_up_answer(self):
        client = LLMClient(api_key="", allowed_models=["qwen-turbo"])
        history = [
            {
                "role": "assistant",
                "content": (
                    "初步结论：当前更像非法持有毒品。\n"
                    "法律依据：要看聊天记录和转账记录。\n"
                    "风险提示：仍需警惕被往贩卖方向审查。\n"
                    "行动建议：尽快委托律师。\n"
                    "律师追问：被拘留人是否在案发前后有与他人联系或交易毒品的行为？"
                ),
            }
        ]

        rewritten = client._rewrite_query_for_model("没有", history)

        self.assertIn("以下是本案最近已经明确的上下文", rewritten)
        self.assertIn("针对上一轮律师追问", rewritten)
        self.assertIn("我的回答是：没有", rewritten)
        self.assertIn("不要说我没有提供具体信息", rewritten)


class FollowUpOptimizationTest(unittest.TestCase):

    def test_optimize_lawyer_follow_up_replaces_repeated_or_conflicting_question(self):
        answer = (
            "初步结论：目前更像非法持有毒品。\n"
            "法律依据：仍要结合电子数据和客观证据。\n"
            "风险提示：如果转账和聊天内容能相互印证，仍可能往贩卖方向审查。\n"
            "行动建议：及时请律师介入。\n"
            "律师追问：您是否在案件中被采取了强制措施？"
        )
        history = [
            {
                "role": "assistant",
                "content": (
                    "初步结论：根据目前掌握的信息，更像非法持有毒品。\n"
                    "法律依据：需要结合现场查获物、聊天记录、转账记录和主观明知等证据综合判断。\n"
                    "风险提示：如果手机里的转账记录和聊天内容能够相互印证，案件存在向贩卖毒品方向发展的风险。\n"
                    "行动建议：当前阶段请律师介入是及时且必要的。\n"
                    "律师追问：被拘留人是否在案发前后有与他人联系或交易毒品的行为？"
                ),
            }
        ]

        optimized = optimize_lawyer_follow_up(
            answer,
            current_query="没有",
            conversation_history=history,
            context_notes=[
                "家属代问，已收到拘留通知书，案件仍在侦查阶段。",
                "手机里可能有几笔可疑转账，金额从500元到2000元不等。",
            ],
        )

        self.assertIn("律师追问：手机里的可疑转账和聊天记录，能否对应到具体对象、时间和交易内容？", optimized)


if __name__ == "__main__":
    unittest.main()
