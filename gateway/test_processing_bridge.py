import os
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

os.environ.setdefault("PUBLIC_BASE_URL", "https://gateway.example.test")

from gateway import main


class ProcessingBridgeTest(unittest.TestCase):
    def test_uploaded_source_path_resolves_only_inside_source_root(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            upload_dir = root / "upl_test"
            upload_dir.mkdir()
            source = upload_dir / "source.mp4"
            source.write_bytes(b"video")
            internal_url = "https://gateway.example.test/v1/sources/jobs/upl_test/media/source.mp4"
            with patch.object(main, "SOURCE_ROOT", root), patch.object(main, "PUBLIC_BASE_URL", "https://gateway.example.test"):
                self.assertEqual(main.uploaded_source_path(internal_url), str(source.resolve()))
                self.assertIsNone(main.uploaded_source_path("https://gateway.example.test/v1/sources/jobs/upl_test/media/../main.py"))

    def test_external_source_is_not_treated_as_uploaded_file(self):
        with patch.object(main, "PUBLIC_BASE_URL", "https://gateway.example.test"):
            self.assertIsNone(main.uploaded_source_path("https://cdn.example.test/video.mp4"))


if __name__ == "__main__":
    unittest.main()
