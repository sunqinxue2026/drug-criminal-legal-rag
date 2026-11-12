import unittest
from unittest.mock import Mock, patch

from utils.image_fetch import ImageFetchError, fetch_public_image, validate_public_image_url


class ImageFetchSecurityTest(unittest.TestCase):

    def test_validate_public_image_url_rejects_localhost(self):
        with self.assertRaises(ImageFetchError):
            validate_public_image_url("http://127.0.0.1:5000/test.png")

        with self.assertRaises(ImageFetchError):
            validate_public_image_url("http://localhost/test.png")

    @patch("utils.image_fetch.socket.getaddrinfo")
    def test_validate_public_image_url_rejects_private_dns_result(self, mock_getaddrinfo):
        mock_getaddrinfo.return_value = [
            (2, 1, 6, "", ("192.168.1.20", 0)),
        ]

        with self.assertRaises(ImageFetchError):
            validate_public_image_url("https://example.test/image.png")

    @patch("utils.image_fetch.socket.getaddrinfo")
    @patch("utils.image_fetch.requests.get")
    def test_fetch_public_image_enforces_image_content_type(self, mock_get, mock_getaddrinfo):
        mock_getaddrinfo.return_value = [
            (2, 1, 6, "", ("8.8.8.8", 0)),
        ]

        response = Mock()
        response.headers = {"Content-Type": "text/html"}
        response.iter_content.return_value = [b"not-image"]
        response.raise_for_status.return_value = None
        mock_get.return_value = response

        with self.assertRaises(ImageFetchError):
            fetch_public_image("https://public.example/test")

    @patch("utils.image_fetch.socket.getaddrinfo")
    @patch("utils.image_fetch.requests.get")
    def test_fetch_public_image_returns_bytes_for_public_image(self, mock_get, mock_getaddrinfo):
        mock_getaddrinfo.return_value = [
            (2, 1, 6, "", ("8.8.8.8", 0)),
        ]

        response = Mock()
        response.headers = {"Content-Type": "image/png"}
        response.iter_content.return_value = [b"abc", b"def"]
        response.raise_for_status.return_value = None
        mock_get.return_value = response

        result = fetch_public_image("https://public.example/test.png")

        self.assertEqual(result.content, b"abcdef")
        self.assertEqual(result.content_type, "image/png")


if __name__ == "__main__":
    unittest.main()
