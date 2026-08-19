import asyncio
import tempfile
import unittest
from pathlib import Path

from gateway.worker_queue import PersistentWorkerQueue, WorkerResourceError, validate_media_artifact


class WorkerQueueTests(unittest.IsolatedAsyncioTestCase):
    async def test_queue_deduplicates_and_runs_job(self):
        with tempfile.TemporaryDirectory() as directory:
            queue = PersistentWorkerQueue("test", Path(directory), max_workers=1, min_free_disk_gb=0)
            seen: list[str] = []

            def handler(job_id: str) -> None:
                seen.append(job_id)

            await queue.start(handler)
            self.assertTrue(queue.submit("job-1"))
            self.assertFalse(queue.submit("job-1"))
            await asyncio.wait_for(queue._queue.join(), timeout=2)
            await queue.stop()
            self.assertEqual(seen, ["job-1"])
            self.assertEqual(queue.info().status, "STOPPED")

    async def test_resource_guard_rejects_low_disk_configuration(self):
        with tempfile.TemporaryDirectory() as directory:
            queue = PersistentWorkerQueue("test", Path(directory), min_free_disk_gb=10**9)
            with self.assertRaises(WorkerResourceError):
                queue.check_resources()


class ArtifactValidationTests(unittest.TestCase):
    def test_artifact_validation(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            missing, _ = validate_media_artifact(root / "missing.mp4")
            self.assertFalse(missing)
            empty = root / "empty.mp4"
            empty.write_bytes(b"x")
            valid, reason = validate_media_artifact(empty)
            self.assertFalse(valid)
            self.assertIn("too small", reason or "")
            good = root / "good.mp4"
            good.write_bytes(b"0" * 2048)
            valid, reason = validate_media_artifact(good)
            self.assertTrue(valid)
            self.assertIsNone(reason)


if __name__ == "__main__":
    unittest.main()
