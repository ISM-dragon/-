"""Probe + normalize media for frame-accurate downstream math.

Two defects break camera math silently if unhandled (ARCHITECTURE-DRAFT
stage 1): variable frame rate (screen recordings, phone footage) and a
nonzero container start_time offset. VFR sources are re-encoded to CFR once
at ingest; a start_time offset is recorded in the probe and applied when
mapping word/event timestamps to frame indices.

Also extracts the 16 kHz mono analysis wav every M1 model consumes — one
decode, shared by ASR, diarization, and the event ensemble.
"""

from __future__ import annotations

import json
import subprocess
from dataclasses import dataclass
from fractions import Fraction
from pathlib import Path
from typing import Callable

from .. import config
from ..render import ffmpeg_bin

ProgressFn = Callable[[float, str], None]


class FfmpegError(Exception):
    pass


def _run(args: list[str], timeout: float) -> subprocess.CompletedProcess:
    proc = subprocess.run(
        args, capture_output=True, text=True, timeout=timeout
    )
    if proc.returncode != 0:
        tail = (proc.stderr or "")[-4000:]
        raise FfmpegError(f"{args[0]} failed ({proc.returncode}): {tail}")
    return proc


@dataclass
class Probe:
    duration_sec: float
    width: int
    height: int
    fps: float
    vfr: bool
    start_time: float
    video_codec: str
    has_audio: bool

    def to_json(self) -> dict:
        return self.__dict__.copy()


def _parse_rate(rate: str | None) -> float:
    if not rate or rate in ("0/0", "N/A"):
        return 0.0
    try:
        return float(Fraction(rate))
    except (ValueError, ZeroDivisionError):
        return 0.0


def probe(path: Path) -> Probe:
    out = _run(
        [
            ffmpeg_bin.ffprobe(), "-v", "error", "-print_format", "json",
            "-show_format", "-show_streams", str(path),
        ],
        timeout=config.PROBE_TIMEOUT,
    ).stdout
    info = json.loads(out)
    fmt = info.get("format", {})
    streams = info.get("streams", [])
    video = next((s for s in streams if s.get("codec_type") == "video"), None)
    audio = next((s for s in streams if s.get("codec_type") == "audio"), None)
    if video is None:
        raise FfmpegError("No video stream found in this file.")

    avg = _parse_rate(video.get("avg_frame_rate"))
    real = _parse_rate(video.get("r_frame_rate"))
    # r_frame_rate is the container tick rate; a large mismatch with the
    # measured average frame rate is the standard VFR signature.
    vfr = avg > 0 and real > 0 and abs(real - avg) / max(real, avg) > 0.02
    start_time = float(video.get("start_time") or fmt.get("start_time") or 0.0)
    return Probe(
        duration_sec=float(fmt.get("duration") or video.get("duration") or 0.0),
        width=int(video.get("width", 0)),
        height=int(video.get("height", 0)),
        fps=avg or real or 30.0,
        vfr=vfr,
        start_time=start_time,
        video_codec=str(video.get("codec_name", "")),
        has_audio=audio is not None,
    )


def normalize_to_cfr(src: Path, dst: Path, fps: float, progress: ProgressFn) -> None:
    """Re-encode a VFR source to constant frame rate. Expensive; only runs
    when the VFR signature triggers (podcasts from YouTube are CFR)."""
    progress(-1, "Normalizing variable frame rate…")
    target = round(fps) if fps >= 1 else 30
    _run(
        [
            ffmpeg_bin.ffmpeg(), "-y", "-i", str(src),
            "-vf", f"fps={target}",
            "-c:v", "libx264", "-preset", "fast", "-crf", "18",
            "-c:a", "copy",
            "-movflags", "+faststart",
            str(dst),
        ],
        timeout=6 * 3600,  # bounded, but an hour-long re-encode is legitimate
    )


def extract_analysis_audio(src: Path, dst: Path) -> None:
    """16 kHz mono s16 wav — the shared input for every audio model."""
    _run(
        [
            ffmpeg_bin.ffmpeg(), "-y", "-i", str(src),
            "-vn", "-ac", "1", "-ar", str(config.AUDIO_SR),
            "-c:a", "pcm_s16le",
            str(dst),
        ],
        timeout=3600,
    )
