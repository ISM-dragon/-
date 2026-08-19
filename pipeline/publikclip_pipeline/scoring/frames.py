"""Scene-anchored frame sampling for T2 visual scoring.

5–12 frames per clip: one at each scene change inside the window (visual
variety carries information), padded with uniform samples when the window
has fewer cuts than the floor. 512 px JPEG at quality 6 — low-detail on
purpose; T2 rates visual interest, it doesn't read license plates.
"""

from __future__ import annotations

import subprocess
from pathlib import Path

from ..render import ffmpeg_bin

MIN_FRAMES = 5
MAX_FRAMES = 12
FRAME_WIDTH = 512


def sample_times(start: float, end: float, scene_times: list[float]) -> list[float]:
    inside = [t for t in scene_times if start + 0.5 <= t <= end - 0.5]
    times = [start + 0.3] + inside[: MAX_FRAMES - 2] + [max(start + 0.5, end - 0.5)]
    if len(times) < MIN_FRAMES:
        step = (end - start) / (MIN_FRAMES + 1)
        times = [start + step * (i + 1) for i in range(MIN_FRAMES)]
    times = sorted(set(round(t, 2) for t in times))
    return times[:MAX_FRAMES]


def extract_frames(media_path: str, times: list[float], tmp_dir: Path) -> list[bytes]:
    tmp_dir.mkdir(parents=True, exist_ok=True)
    frames: list[bytes] = []
    for i, t in enumerate(times):
        out = tmp_dir / f"frame_{i:02d}.jpg"
        proc = subprocess.run(
            [
                ffmpeg_bin.ffmpeg(), "-y", "-ss", f"{t:.2f}", "-i", media_path,
                "-frames:v", "1", "-vf", f"scale={FRAME_WIDTH}:-2",
                "-q:v", "6", str(out),
            ],
            capture_output=True, timeout=120,
        )
        if proc.returncode == 0 and out.exists():
            frames.append(out.read_bytes())
            out.unlink(missing_ok=True)
    return frames
