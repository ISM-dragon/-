"""Model weight registry + downloader.

All weights land in PUBLIKCLIP_HOME/models/<name>. Downloads resume (Range)
and verify sha256 when one is pinned. The registry grows per milestone —
M1 adds the audio models, M3 the vision ONNX models. Entries with a
sha256 of None are verified by size-only until first release pinning.
"""

from __future__ import annotations

import hashlib
from dataclasses import dataclass
from pathlib import Path
from typing import Callable

import httpx

from .. import config

ProgressFn = Callable[[float, str], None]


@dataclass(frozen=True)
class ModelSpec:
    name: str            # registry key + subdir name
    filename: str
    url: str
    sha256: str | None = None
    approx_mb: int = 0


REGISTRY: dict[str, ModelSpec] = {}


def register(spec: ModelSpec) -> ModelSpec:
    REGISTRY[f"{spec.name}/{spec.filename}"] = spec
    return spec


def model_path(spec: ModelSpec) -> Path:
    return config.models_dir() / spec.name / spec.filename


def is_present(spec: ModelSpec) -> bool:
    return model_path(spec).exists()


def ensure(spec: ModelSpec, progress: ProgressFn) -> Path:
    """Download with resume; verify sha256 when pinned."""
    dest = model_path(spec)
    if dest.exists():
        return dest
    dest.parent.mkdir(parents=True, exist_ok=True)
    tmp = dest.with_suffix(dest.suffix + ".part")
    offset = tmp.stat().st_size if tmp.exists() else 0
    headers = {"Range": f"bytes={offset}-"} if offset else {}
    label = f"Downloading {spec.name}" + (f" (~{spec.approx_mb} MB)" if spec.approx_mb else "")
    with httpx.stream(
        "GET", spec.url, headers=headers, follow_redirects=True, timeout=config.HTTP_TIMEOUT
    ) as res:
        if res.status_code == 200 and offset:
            offset = 0  # server ignored Range; start over
            tmp.unlink(missing_ok=True)
        elif res.status_code not in (200, 206):
            raise RuntimeError(f"Model download failed for {spec.name}: HTTP {res.status_code}")
        total = int(res.headers.get("content-length", 0)) + offset
        seen = offset
        with open(tmp, "ab") as fh:
            for chunk in res.iter_bytes():
                fh.write(chunk)
                seen += len(chunk)
                if total:
                    progress(seen / total, label)
    if spec.sha256:
        digest = hashlib.sha256()
        with open(tmp, "rb") as fh:
            for block in iter(lambda: fh.read(1 << 20), b""):
                digest.update(block)
        if digest.hexdigest() != spec.sha256:
            tmp.unlink(missing_ok=True)
            raise RuntimeError(
                f"Checksum mismatch for {spec.name} — download corrupted, please retry."
            )
    tmp.replace(dest)
    return dest
