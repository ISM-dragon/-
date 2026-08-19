"""DCASE-style post-processing for framewise event posteriors.

The chain (ARCHITECTURE-DRAFT stage 4): median filter → dual-threshold
hysteresis (enter 0.5, stay 0.25) → merge gaps ≤ 320 ms → drop events
< 200 ms → cross-model IOU fusion where agreement BOOSTS confidence rather
than arbitrating. The IOU span-merge follows artbyjazi/autoclip's
highlights.py dedupe pattern (MIT).

Pure numpy — fully unit-testable without any model.
"""

from __future__ import annotations

import numpy as np
import scipy.signal

ENTER_THRESHOLD = 0.5
STAY_THRESHOLD = 0.25
MERGE_GAP_SEC = 0.32
MIN_DURATION_SEC = 0.2
FUSION_IOU = 0.4
AGREEMENT_BOOST = 1.15


def median_filter(probs: np.ndarray, fps: float, width_sec: float = 0.15) -> np.ndarray:
    if len(probs) == 0:
        return probs
    width = max(1, int(width_sec * fps))
    if width % 2 == 0:
        width += 1
    return scipy.signal.medfilt(probs, kernel_size=width)


def hysteresis_spans(
    probs: np.ndarray,
    fps: float,
    enter: float = ENTER_THRESHOLD,
    stay: float = STAY_THRESHOLD,
) -> list[tuple[float, float, float]]:
    """(start_sec, end_sec, peak_prob) spans: enter above `enter`, extend
    while above `stay`. Dual thresholds keep one event from shattering into
    fragments every time the posterior dips."""
    spans: list[tuple[float, float, float]] = []
    inside = False
    start = 0
    peak = 0.0
    for i, p in enumerate(probs):
        if not inside and p >= enter:
            inside = True
            start = i
            peak = float(p)
        elif inside:
            if p >= stay:
                peak = max(peak, float(p))
            else:
                spans.append((start / fps, i / fps, peak))
                inside = False
    if inside:
        spans.append((start / fps, len(probs) / fps, peak))
    return spans


def merge_close(
    spans: list[tuple[float, float, float]], gap: float = MERGE_GAP_SEC
) -> list[tuple[float, float, float]]:
    if not spans:
        return []
    merged = [spans[0]]
    for start, end, peak in spans[1:]:
        last_start, last_end, last_peak = merged[-1]
        if start - last_end <= gap:
            merged[-1] = (last_start, max(last_end, end), max(last_peak, peak))
        else:
            merged.append((start, end, peak))
    return merged


def drop_short(
    spans: list[tuple[float, float, float]], min_dur: float = MIN_DURATION_SEC
) -> list[tuple[float, float, float]]:
    return [s for s in spans if s[1] - s[0] >= min_dur]


def postprocess(
    probs: np.ndarray,
    fps: float,
    enter: float = ENTER_THRESHOLD,
    stay: float = STAY_THRESHOLD,
) -> list[tuple[float, float, float]]:
    """The full DCASE chain for one channel's framewise posteriors."""
    filtered = median_filter(probs, fps)
    return drop_short(merge_close(hysteresis_spans(filtered, fps, enter=enter, stay=stay)))


def _iou(a: dict, b: dict) -> float:
    inter = min(a["end"], b["end"]) - max(a["start"], b["start"])
    if inter <= 0:
        return 0.0
    union = max(a["end"], b["end"]) - min(a["start"], b["start"])
    return inter / union if union > 0 else 0.0


def fuse(events: list[dict], iou_threshold: float = FUSION_IOU) -> list[dict]:
    """Merge same-type events from different detectors.

    Overlapping (IOU ≥ threshold) same-type events collapse into one span;
    detection by multiple models is a confidence BOOST (×1.15, capped at
    0.99), not a tiebreak — cross-model agreement is evidence, per the
    architecture's fusion rule."""
    out: list[dict] = []
    for event in sorted(events, key=lambda e: (e["type"], e["start"])):
        merged = False
        for existing in out:
            if existing["type"] != event["type"]:
                continue
            if _iou(existing, event) >= iou_threshold:
                existing["start"] = min(existing["start"], event["start"])
                existing["end"] = max(existing["end"], event["end"])
                sources = set(existing["sources"]) | set(event["sources"])
                base = max(existing["confidence"], event["confidence"])
                if len(sources) > len(existing["sources"]):
                    base = min(0.99, base * AGREEMENT_BOOST)
                existing["confidence"] = round(base, 3)
                existing["sources"] = sorted(sources)
                merged = True
                break
        if not merged:
            out.append(dict(event, sources=sorted(event["sources"])))
    for event in out:
        event["start"] = round(event["start"], 3)
        event["end"] = round(event["end"], 3)
    return sorted(out, key=lambda e: e["start"])
