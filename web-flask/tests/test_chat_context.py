import unittest

from algo.chat_context import (
    build_attachment_notes,
    build_retrieval_question,
    normalize_case_attachments,
    normalize_document_attachments,
    normalize_history_messages,
    summarize_context_usage,
)


class ChatContextTest(unittest.TestCase):

    def test_normalize_history_messages_keeps_recent_entries(self):
        history = [
            {"role": "user", "content": "第一轮"},
            {"role": "assistant", "content": "第二轮"},
            {"role": "user", "content": "第三轮", "imageUrl": "http://example.com/a.png", "imageName": "a.png"},
        ]

        normalized = normalize_history_messages(history, limit=2)

        self.assertEqual(len(normalized), 2)
        self.assertEqual(normalized[0]["role"], "assistant")
        self.assertEqual(normalized[1]["image_name"], "a.png")

    def test_attachment_notes_include_cases_and_documents(self):
        cases = normalize_case_attachments([
            {"id": 1, "title": "典型案例", "summary": "案例摘要"},
        ])
        documents = normalize_document_attachments([
            {"name": "拘留通知书", "extractedText": "正文内容", "statusLabel": "已提取正文", "charCount": 120},
        ])

        notes = build_attachment_notes(cases, documents)

        self.assertTrue(any("案例1" in note for note in notes))
        self.assertTrue(any("文书1" in note for note in notes))

    def test_build_retrieval_question_merges_context_blocks(self):
        history = normalize_history_messages([{"role": "user", "content": "家属收到拘留通知"}])
        notes = ["文书1：文件名：拘留通知书；内容：已载明被拘留人信息"]

        question = build_retrieval_question("现在最应该先做什么？", history=history, attachment_notes=notes, image_context="图片里是拘留通知")

        self.assertIn("最近会话背景", question)
        self.assertIn("补充材料", question)
        self.assertIn("图片识别结果", question)
        self.assertIn("当前问题", question)

    def test_summarize_context_usage_counts_inputs(self):
        summary = summarize_context_usage(
            history=[{"role": "user", "content": "test"}],
            attached_cases=[{"title": "案例"}],
            attached_documents=[{"name": "文书"}],
            image_context="图片内容",
        )

        self.assertEqual(summary["history_turns_used"], 1)
        self.assertEqual(summary["attached_case_count"], 1)
        self.assertEqual(summary["attached_document_count"], 1)
        self.assertTrue(summary["image_context_used"])


if __name__ == "__main__":
    unittest.main()
