import unittest

from gateway.main import validate_public_source
from fastapi import HTTPException


class GatewaySafetyTests(unittest.TestCase):
    def test_public_https_source_is_allowed(self):
        self.assertEqual(validate_public_source("https://www.youtube.com/watch?v=demo"), "https://www.youtube.com/watch?v=demo")

    def test_localhost_is_rejected(self):
        with self.assertRaises(HTTPException):
            validate_public_source("http://127.0.0.1:8787/internal")

    def test_private_ip_is_rejected(self):
        with self.assertRaises(HTTPException):
            validate_public_source("https://10.0.0.4/video")

    def test_non_http_source_is_rejected(self):
        with self.assertRaises(HTTPException):
            validate_public_source("file:///tmp/video.mp4")


if __name__ == "__main__":
    unittest.main()
