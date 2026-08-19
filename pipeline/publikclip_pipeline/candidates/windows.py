"""Candidate window extraction: interest-curve maxima → 15–75 s windows →
sentence-boundary snapping → IOU dedupe → ~35 candidates.

Boundary snapping follows clip-forge's sentences.ts snap() arithmetic (MIT):
expand/contract each edge to the nearest sentence boundary within a snap
radius, preferring boundaries that follow a pause. The IOU span dedupe is
autoclip's highlights.py pattern (MIT).
"""

from __future__ import annotations

from dataclasses import dataclass, field

import numpy as np

MIN_LEN = 15.0
MAX_LEN = 75.0
TARGET_LEN = 42.0
SNAP_RADIUS = 6.0
DEDUPE_IOU = 0.55
MAX_CANDIDATES = 35


@dataclass
class Candidate:
    start: float
    end: float
    peak_time: float
    curve_score: float
    channel_scores: dict[str, float] = field(default_factory=dict)

    def to_json(self) -> dict:
        return {
            "start": round(self.start, 3),
            "end": round(self.end, 3),
            "peak_time": round(self.peak_time, 3),
            "curve_score": round(self.curve_score, 4),
            "channel_scores": self.channel_scores,
        }


def sentence_boundaries(segments: list[dict]) -> tuple[np.ndarray, np.ndarray]:
    """(starts, ends) of ASR sentences — the only legal cut points."""
    starts = np.array([float(s["start"]) for s in segments])
    ends = np.array([float(s["end"]) for s in segments])
    return starts, ends


def _snap(t: float, boundaries: np.ndarray, radius: float = SNAP_RADIUS) -> float | None:
    if len(boundaries) == 0:
        return None
    idx = int(np.argmin(np.abs(boundaries - t)))
    if abs(boundaries[idx] - t) <= radius:
        return float(boundaries[idx])
    return None


def local_maxima(curve: np.ndarray, min_distance_sec: int = 20) -> list[int]:
    """Peak indices, greedily suppressing neighbors within min_distance."""
    order = np.argsort(curve)[::-1]
    picked: list[int] = []
    for idx in order:
        if curve[idx] <= 0:
            break
        if all(abs(idx - p) >= min_distance_sec for p in picked):
            picked.append(int(idx))
        if len(picked) >= MAX_CANDIDATES * 3:  # generous pool pre-dedupe
            break
    return sorted(picked)


def window_around(
    peak: int,
    curve: np.ndarray,
    seg_starts: np.ndarray,
    seg_ends: np.ndarray,
    duration: float,
) -> tuple[float, float] | None:
    """Grow a window around the peak until curve mass drops off, then snap
    both edges to sentence boundaries."""
    half = TARGET_LEN / 2
    raw_start = max(0.0, peak - half)
    raw_end = min(duration, peak + half)

    start = _snap(raw_start, seg_starts)
    end = _snap(raw_end, seg_ends)
    if start is None:
        start = raw_start
    if end is None:
        end = raw_end
    # A clip must start where a sentence starts; drifting an end is tolerable,
    # a mid-word opening is not.
    if start >= end:
        return None
    length = end - start
    if length < MIN_LEN:
        # try extending the end to the next sentence end
        later = seg_ends[seg_ends > start + MIN_LEN]
        if len(later) == 0:
            return None
        end = float(later[0])
        length = end - start
    if length > MAX_LEN:
        earlier = seg_ends[(seg_ends > start + MIN_LEN) & (seg_ends <= start + MAX_LEN)]
        if len(earlier) == 0:
            return None
        end = float(earlier[-1])
    return (round(start, 3), round(end, 3))


def _iou(a: tuple[float, float], b: tuple[float, float]) -> float:
    inter = min(a[1], b[1]) - max(a[0], b[0])
    if inter <= 0:
        return 0.0
    union = max(a[1], b[1]) - min(a[0], b[0])
    return inter / union


def dedupe(candidates: list[Candidate], iou: float = DEDUPE_IOU) -> list[Candidate]:
    """Keep the higher-scored of any overlapping pair (autoclip pattern)."""
    kept: list[Candidate] = []
    for cand in sorted(candidates, key=lambda c: c.curve_score, reverse=True):
        if all(_iou((cand.start, cand.end), (k.start, k.end)) < iou for k in kept):
            kept.append(cand)
        if len(kept) >= MAX_CANDIDATES:
            break
    return sorted(kept, key=lambda c: c.start)


def extract(
    curve: np.ndarray,
    channels: dict[str, np.ndarray],
    segments: list[dict],
    duration: float,
) -> list[Candidate]:
    seg_starts, seg_ends = sentence_boundaries(segments)
    peaks = local_maxima(curve)
    out: list[Candidate] = []
    for peak in peaks:
        window = window_around(peak, curve, seg_starts, seg_ends, duration)
        if window is None:
            continue
        start, end = window
        a, b = int(start), max(int(start) + 1, int(np.ceil(end)))
        per_channel = {
            name: round(float(np.mean(ch[a : min(b, len(ch))])), 4)
            for name, ch in channels.items()
            if len(ch) > a
        }
        out.append(
            Candidate(
                start=start,
                end=end,
                peak_time=float(peak),
                curve_score=float(np.mean(curve[a : min(b, len(curve))])),
                channel_scores=per_channel,
            )
        )
    return dedupe(out)
