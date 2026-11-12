from __future__ import annotations

import importlib
import os
import sys
import tempfile
import unittest
from pathlib import Path


ROOT_DIR = Path(__file__).resolve().parents[1]
if str(ROOT_DIR) not in sys.path:
    sys.path.insert(0, str(ROOT_DIR))


class FileServiceTestCase(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        os.environ["FILE_STORAGE_ROOT"] = self.temp_dir.name
        os.environ["INTERNAL_SERVICE_TOKEN"] = "test-token"
        os.environ["FILE_ALLOWED_ORIGINS"] = "http://localhost:5174"
        os.environ["FILE_DEBUG"] = "false"

        import app as file_app_module

        self.module = importlib.reload(file_app_module)
        self.client = self.module.app.test_client()

    def tearDown(self):
        self.temp_dir.cleanup()
        for key in ["FILE_STORAGE_ROOT", "INTERNAL_SERVICE_TOKEN", "FILE_ALLOWED_ORIGINS", "FILE_DEBUG"]:
            os.environ.pop(key, None)

    def test_upload_requires_internal_token(self):
        response = self.client.post(
            "/file/upload",
            data={"bucket": "chat-images"},
            content_type="multipart/form-data",
        )
        self.assertEqual(response.status_code, 401)

    def test_upload_and_download_round_trip(self):
        response = self.client.post(
            "/file/upload",
            data={
                "bucket": "chat-images",
                "file": (self._bytes_io(b"image-bytes"), "example.png"),
            },
            content_type="multipart/form-data",
            headers={"X-Internal-Service-Token": "test-token"},
        )
        self.assertEqual(response.status_code, 200)
        payload = response.get_json()
        self.assertEqual(payload["data"]["bucket"], "chat-images")
        self.assertTrue(payload["data"]["objectKey"].endswith(".png"))

        file_url = payload["data"]["url"]
        path = file_url.split("http://localhost", 1)[-1]
        download_response = self.client.get(path or "/")
        try:
            self.assertEqual(download_response.status_code, 200)
            self.assertEqual(download_response.data, b"image-bytes")
        finally:
            download_response.close()

    def test_invalid_bucket_is_rejected(self):
        response = self.client.post(
            "/file/upload",
            data={
                "bucket": "../escape",
                "file": (self._bytes_io(b"hello"), "note.txt"),
            },
            content_type="multipart/form-data",
            headers={"X-Internal-Service-Token": "test-token"},
        )
        self.assertEqual(response.status_code, 400)

    @staticmethod
    def _bytes_io(payload: bytes):
        from io import BytesIO

        return BytesIO(payload)


if __name__ == "__main__":
    unittest.main()
