"""Small persistent-state worker queue for the single-owner Gateway.

The queue itself is in memory, while job state remains in SQLite. On process
startup, queued jobs are read from SQLite and submitted again. This keeps the
architecture resumable without introducing Redis/Kafka for a personal tool.
"""

from __future__ import annotations

import asyncio
import os
import platform
import shutil
import socket
import time
import uuid
from dataclasses import dataclass
from pathlib import Path
from typing import Callable


@dataclass(frozen=True)
class WorkerInfo:
    worker_id: str
    hostname: str
    platform: str
    cpu_count: int
    max_workers: int
    status: str
    current_jobs: tuple[str, ...]
    last_heartbeat: float
    last_error: str | None

    def as_dict(self) -> dict[str, object]:
        return {
            "worker_id": self.worker_id,
            "hostname": self.hostname,
            "platform": self.platform,
            "cpu_count": self.cpu_count,
            "max_workers": self.max_workers,
            "status": self.status,
            "current_jobs": list(self.current_jobs),
            "last_heartbeat": self.last_heartbeat,
            "last_error": self.last_error,
        }


class WorkerResourceError(RuntimeError):
    """Raised when a worker cannot safely start another long-running job."""


class PersistentWorkerQueue:
    def __init__(self, name: str, root: Path, max_workers: int = 1, min_free_disk_gb: float = 2.0) -> None:
        self.name = name
        self.root = root
        self.max_workers = max(1, max_workers)
        self.min_free_disk_gb = max(0.0, min_free_disk_gb)
        self.worker_id = f"{name}_{uuid.uuid4().hex[:10]}"
        self._queue: asyncio.Queue[str] = asyncio.Queue()
        self._queued: set[str] = set()
        self._current: set[str] = set()
        self._tasks: list[asyncio.Task[None]] = []
        self._handler: Callable[[str], None] | None = None
        self._error_handler: Callable[[str, str], None] | None = None
        self._stopping = False
        self._last_heartbeat = time.time()
        self._last_error: str | None = None

    async def start(self, handler: Callable[[str], None], error_handler: Callable[[str, str], None] | None = None) -> None:
        if self._tasks:
            return
        self._handler = handler
        self._error_handler = error_handler
        self._stopping = False
        self._tasks = [asyncio.create_task(self._worker_loop(index), name=f"{self.name}-{index}") for index in range(self.max_workers)]

    async def stop(self) -> None:
        self._stopping = True
        for task in self._tasks:
            task.cancel()
        if self._tasks:
            await asyncio.gather(*self._tasks, return_exceptions=True)
        self._tasks.clear()
        self._handler = None
        self._error_handler = None

    def submit(self, job_id: str) -> bool:
        if self._stopping or job_id in self._queued or job_id in self._current:
            return False
        self._queued.add(job_id)
        self._queue.put_nowait(job_id)
        self._last_heartbeat = time.time()
        return True

    def info(self) -> WorkerInfo:
        status = "STOPPED" if self._stopping else "RUNNING" if self._tasks else "IDLE"
        return WorkerInfo(self.worker_id, socket.gethostname(), platform.platform(), os.cpu_count() or 1, self.max_workers, status, tuple(sorted(self._current)), self._last_heartbeat, self._last_error)

    def check_resources(self) -> None:
        self.root.mkdir(parents=True, exist_ok=True)
        free_gb = shutil.disk_usage(self.root).free / (1024 ** 3)
        if free_gb < self.min_free_disk_gb:
            raise WorkerResourceError(f"RESOURCE_UNAVAILABLE: only {free_gb:.2f} GB free; need {self.min_free_disk_gb:.2f} GB")

    async def _worker_loop(self, index: int) -> None:
        while not self._stopping:
            job_id = await self._queue.get()
            self._queued.discard(job_id)
            self._current.add(job_id)
            self._last_heartbeat = time.time()
            try:
                self.check_resources()
                if not self._handler:
                    raise RuntimeError("Worker handler is not configured")
                await asyncio.to_thread(self._handler, job_id)
                self._last_error = None
            except asyncio.CancelledError:
                raise
            except Exception as error:  # handler persists user-facing state
                self._last_error = str(error)
                if self._error_handler:
                    try:
                        await asyncio.to_thread(self._error_handler, job_id, str(error))
                    except Exception as callback_error:  # noqa: BLE001
                        self._last_error = f"{error}; error callback failed: {callback_error}"
            finally:
                self._current.discard(job_id)
                self._last_heartbeat = time.time()
                self._queue.task_done()


def validate_media_artifact(path: Path, *, minimum_bytes: int = 1024) -> tuple[bool, str | None]:
    """Reject missing/empty/non-video artifacts before exposing them to clients."""
    try:
        if not path.is_file():
            return False, "ARTIFACT_INVALID: file does not exist"
        if path.stat().st_size < minimum_bytes:
            return False, "ARTIFACT_INVALID: file is empty or too small"
        if path.suffix.lower() not in {".mp4", ".mov", ".webm", ".mkv"}:
            return False, "ARTIFACT_INVALID: unsupported media container"
    except OSError as error:
        return False, f"ARTIFACT_INVALID: {error}"
    return True, None
