"""Face tracking: sparse detections → per-person tracks with interpolated,
median-smoothed boxes for every analysis frame.

Ported from JeremySNR/clip-forge src/main/pipeline/facetracks.ts (MIT).
Identity continuity is what matters — a track that flickers between two
people would blend their mouths and poison the ASD score.
"""

from __future__ import annotations

from dataclasses import dataclass

import numpy as np

from .detect import FaceBox, iou

MATCH_IOU = 0.25
MAX_GAP_SEC = 0.6
MIN_TRACK_SEC = 0.4
MIN_DETECTIONS = 3
SMOOTH_SEC = 0.5


@dataclass
class FaceTrack:
    start: int              # first analysis-frame index
    boxes: list[FaceBox]    # one smoothed box per frame from start


def median_filter(values: list[float], window: int) -> list[float]:
    """Shrinking-window median (no zero-padding artifacts at edges)."""
    half = window // 2
    out = []
    for i in range(len(values)):
        window_slice = sorted(values[max(0, i - half) : min(len(values), i + half + 1)])
        out.append(window_slice[len(window_slice) // 2])
    return out


def _lerp_box(a: FaceBox, b: FaceBox, t: float) -> FaceBox:
    mix = lambda x, y: x + (y - x) * t  # noqa: E731
    return FaceBox(mix(a.x1, b.x1), mix(a.y1, b.y1), mix(a.x2, b.x2), mix(a.y2, b.y2), mix(a.score, b.score))


def _finalize(detections: list[tuple[int, FaceBox]], smooth_window: int) -> FaceTrack:
    start = detections[0][0]
    end = detections[-1][0]
    boxes: list[FaceBox] = []
    d = 0
    for f in range(start, end + 1):
        while d < len(detections) - 1 and detections[d + 1][0] <= f:
            d += 1
        frame_d, box_d = detections[d]
        if frame_d == f or d == len(detections) - 1:
            boxes.append(FaceBox(box_d.x1, box_d.y1, box_d.x2, box_d.y2, box_d.score))
        else:
            next_f, next_box = detections[d + 1]
            t = (f - frame_d) / (next_f - frame_d)
            boxes.append(_lerp_box(box_d, next_box, t))

    cx = median_filter([b.cx for b in boxes], smooth_window)
    cy = median_filter([b.cy for b in boxes], smooth_window)
    w = median_filter([b.x2 - b.x1 for b in boxes], smooth_window)
    h = median_filter([b.y2 - b.y1 for b in boxes], smooth_window)
    smoothed = [
        FaceBox(cx[i] - w[i] / 2, cy[i] - h[i] / 2, cx[i] + w[i] / 2, cy[i] + h[i] / 2, boxes[i].score)
        for i in range(len(boxes))
    ]
    return FaceTrack(start=start, boxes=smoothed)


def build_face_tracks(
    faces_per_frame: list[list[FaceBox] | None],
    scene_cuts: list[int],
    fps: float,
) -> list[FaceTrack]:
    """faces_per_frame[f] is detections for frame f, or None on strided
    (skipped) frames — boxes there are interpolated. Tracks never span cuts."""
    cut_set = set(scene_cuts)
    max_gap = max(1, round(MAX_GAP_SEC * fps))
    min_len = max(2, round(MIN_TRACK_SEC * fps))
    smooth_window = max(3, round(SMOOTH_SEC * fps)) | 1

    done: list[list[tuple[int, FaceBox]]] = []
    active: list[list[tuple[int, FaceBox]]] = []

    def finish(track: list[tuple[int, FaceBox]]) -> None:
        span = track[-1][0] - track[0][0] + 1
        if len(track) >= MIN_DETECTIONS and span >= min_len:
            done.append(track)

    for f, faces in enumerate(faces_per_frame):
        if f in cut_set:
            for track in active:
                finish(track)
            active = []
        still_active = []
        for track in active:
            if f - track[-1][0] > max_gap:
                finish(track)
            else:
                still_active.append(track)
        active = still_active

        if faces is None:
            continue
        used: set[int] = set()
        for box in faces:
            best_i, best_iou = -1, MATCH_IOU
            for ti, track in enumerate(active):
                if ti in used:
                    continue
                v = iou(track[-1][1], box)
                if v > best_iou:
                    best_i, best_iou = ti, v
            if best_i >= 0:
                used.add(best_i)
                active[best_i].append((f, box))
            else:
                active.append([(f, box)])
                used.add(len(active) - 1)

    for track in active:
        finish(track)
    return [_finalize(track, smooth_window) for track in done]


def track_coverage_ratio(tracks: list[FaceTrack], frame_count: int) -> float:
    covered = sum(len(t.boxes) for t in tracks)
    return covered / max(1, frame_count)


def crop_face(frame: np.ndarray, cx: float, cy: float, side: float, crop_size: int = 112, pad: int = 110) -> np.ndarray:
    """Bilinear sample of a square region into a crop_size² grayscale patch,
    out-of-frame pixels filled with the reference pad value (110). Vectorized
    port of clip-forge cropFace (asd.ts)."""
    fh, fw = frame.shape[:2]
    x0 = cx - side / 2
    y0 = cy - side / 2
    step = side / crop_size
    coords = (np.arange(crop_size) + 0.5) * step - 0.5
    sx = x0 + coords
    sy = y0 + coords
    ix = np.floor(sx).astype(int)
    iy = np.floor(sy).astype(int)
    fx = sx - ix
    fy = sy - iy

    padded = np.full((fh + 2, fw + 2), pad, dtype=np.float32)
    padded[1 : fh + 1, 1 : fw + 1] = frame
    # +1 offset into padded; clip so ix+1 lookups stay in the pad ring
    ixc = np.clip(ix + 1, 0, fw + 1)
    iyc = np.clip(iy + 1, 0, fh + 1)
    ixc1 = np.clip(ix + 2, 0, fw + 1)
    iyc1 = np.clip(iy + 2, 0, fh + 1)
    # Anything that floored fully outside gets the pad value via clipping into
    # the pad ring — matches the reference px() out-of-bounds behavior.
    tl = padded[np.ix_(iyc, ixc)]
    tr = padded[np.ix_(iyc, ixc1)]
    bl = padded[np.ix_(iyc1, ixc)]
    br = padded[np.ix_(iyc1, ixc1)]
    fx2 = fx[np.newaxis, :]
    fy2 = fy[:, np.newaxis]
    out = tl * (1 - fx2) * (1 - fy2) + tr * fx2 * (1 - fy2) + bl * (1 - fx2) * fy2 + br * fx2 * fy2
    return out.astype(np.float32)
