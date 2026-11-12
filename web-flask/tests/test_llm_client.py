import unittest

from algo.llm.llm_client import LLMClient


class LLMClientTest(unittest.TestCase):

    def test_chat_returns_mock_response_when_api_key_missing_and_fail_fast_disabled(self):
        client = LLMClient(api_key=None, model="qwen-turbo", allowed_models=["qwen-turbo"])
        client.fail_fast = False

        response = client.chat([{"role": "user", "content": "请分析拘留通知"}])

        self.assertIn("演示模式", response)
        self.assertIn("请分析拘留通知", response)

    def test_chat_raises_when_api_key_missing_and_fail_fast_enabled(self):
        client = LLMClient(api_key=None, model="qwen-turbo", allowed_models=["qwen-turbo"])
        client.fail_fast = True

        with self.assertRaises(RuntimeError):
            client.chat([{"role": "user", "content": "test"}])


if __name__ == "__main__":
    unittest.main()
