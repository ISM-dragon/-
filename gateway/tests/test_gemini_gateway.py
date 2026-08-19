from __future__ import annotations

import asyncio
import json
import os
import stat
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from gateway import main
from gateway.processing_service import classify_gemini_error, pipeline_environment


class GeminiGatewayTests(unittest.TestCase):
    def test_child_environment_inherits_canonical_key_without_legacy_key(self):
        with patch.dict(os.environ, {"PUBLIKCLIP_GEMINI_API_KEY": "old-local-key"}, clear=False):
            env = pipeline_environment(Path("/tmp/ism-processing"), Path("/tmp/ism-pipeline"), "server-key")
        self.assertEqual(env["GEMINI_API_KEY"], "server-key")
        self.assertNotIn("PUBLIKCLIP_GEMINI_API_KEY", env)
        self.assertEqual(env["PUBLIKCLIP_DISABLE_LOCAL_SECRETS"], "1")

    def test_child_environment_does_not_expose_empty_or_old_key(self):
        with patch.dict(os.environ, {"PUBLIKCLIP_GEMINI_API_KEY": "old-local-key", "GEMINI_API_KEY": "old-server-key"}, clear=False):
            env = pipeline_environment(Path("/tmp/ism-processing"), Path("/tmp/ism-pipeline"), "")
        self.assertNotIn("GEMINI_API_KEY", env)
        self.assertNotIn("PUBLIKCLIP_GEMINI_API_KEY", env)

    def test_missing_key_diagnostic_is_safe(self):
        with patch.object(main, "GEMINI_API_KEY", ""):
            result = main._gemini_diagnostic_sync()
        self.assertEqual(result["status"], "not_configured")
        self.assertEqual(result["code"], "GEMINI_NOT_CONFIGURED")
        self.assertNotIn("server-secret-value", str(result))

    def test_capabilities_never_return_secret(self):
        with patch.object(main, "GEMINI_API_KEY", "server-secret-value"):
            result = asyncio.run(main.processing_capabilities())
        self.assertTrue(result["gemini"])
        self.assertNotIn("server-secret-value", str(result))
        self.assertNotIn("api_key", str(result).lower())

    def test_worker_child_process_inherits_key_without_logging_it(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            marker = root / "child-key.txt"
            fake = root / "fake-pipeline"
            fake.write_text("#!/usr/bin/env python3\nimport json, os, pathlib\npathlib.Path(os.environ['ISM_TEST_MARKER']).write_text(os.environ.get('GEMINI_API_KEY', ''))\nprint(json.dumps({'event':'job','job_id':'fake_pipeline_job'}), flush=True)\nprint(json.dumps({'event':'result','ok':True,'job_id':'fake_pipeline_job'}), flush=True)\n")
            fake.chmod(fake.stat().st_mode | stat.S_IXUSR)
            db_path = root / "gateway.db"
            processing_root = root / "processing"
            with patch.object(main, "DB_PATH", db_path), patch.object(main, "PROCESSING_ROOT", processing_root), patch.object(main, "PIPELINE_DIR", Path("/home/ubuntu/github_clone/repo/pipeline")), patch.object(main, "PIPELINE_BIN", str(fake)), patch.object(main, "GEMINI_API_KEY", "server-secret-value"), patch.dict(os.environ, {"ISM_TEST_MARKER": str(marker)}, clear=False):
                main.init_db()
                with main.closing(main.db()) as connection:
                    connection.execute("INSERT INTO processing_jobs (id, source, llm, captions, status, created_at, updated_at) VALUES (?, ?, ?, ?, 'queued', ?, ?)", ("proc_test", "https://example.com/video", "gemini", "classic", main.now_iso(), main.now_iso()))
                    connection.commit()
                main.run_processing_job("proc_test", "https://example.com/video", "gemini", "classic")
                with main.closing(main.db()) as connection:
                    row = connection.execute("SELECT status, error_code FROM processing_jobs WHERE id=?", ("proc_test",)).fetchone()
            self.assertEqual(row["status"], "done")
            self.assertIsNone(row["error_code"])
            self.assertEqual(marker.read_text(), "server-secret-value")

    def test_error_classification_is_stable(self):
        class FakeError(Exception):
            code = "GEMINI_AUTH_FAILED"

        self.assertEqual(classify_gemini_error(FakeError("secret must not be exposed")), ("auth_failed", "GEMINI_AUTH_FAILED"))


if __name__ == "__main__":
    unittest.main()
