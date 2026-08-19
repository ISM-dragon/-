"""UltraFace RFB-320 face detection + shot-change detection.

Ported from JeremySNR/clip-forge src/main/pipeline/detect.ts (MIT) — the
exact preprocessing ((v-127)/128 CHW), score threshold 0.65, IOU-0.5 NMS,
and the sampled mean-abs-diff cut detector. UltraFace model: Linzaer/
Ultra-Light-Fast-Generic-Face-Detector-1MB (MIT), via clip-forge's bundle.

Deliberately NOT MediaPipe (ARCHITECTURE-DRAFT stage 7: sidesteps the 1.0
legacy-solutions breakage entirely).
"""

from __future__ import annotations

from dataclasses import dataclass

import numpy as np

MODEL_W = 320
MODEL_H = 240
DEFAULT_CONFIDENCE = 0.65
NMS_IOU = 0.5
ASD_SCENE_CUT_THRESHOLD = 24.0  # at 25 fps; soft cuts spike, motion doesn't
CUT_MERGE_SEC = 0.3


@dataclass
class FaceBox:
    x1: float
    y1: float
    x2: float
    y2: float
    score: float

    @property
    def cx(self) -> float:
        return (self.x1 + self.x2) / 2

    @property
    def cy(self) -> float:
        return (self.y1 + self.y2) / 2

    @property
    def area(self) -> float:
        return max(0.0, self.x2 - self.x1) * max(0.0, self.y2 - self.y1)


def iou(a: FaceBox, b: FaceBox) -> float:
    ix = min(a.x2, b.x2) - max(a.x1, b.x1)
    iy = min(a.y2, b.y2) - max(a.y1, b.y1)
    if ix <= 0 or iy <= 0:
        return 0.0
    inter = ix * iy
    union = a.area + b.area - inter
    return inter / union if union > 0 else 0.0


def _nms(boxes: list[FaceBox]) -> list[FaceBox]:
    kept: list[FaceBox] = []
    for box in sorted(boxes, key=lambda b: b.score, reverse=True):
        if all(iou(k, box) < NMS_IOU for k in kept):
            kept.append(box)
    return kept


class FaceDetector:
    def __init__(self, model_path: str):
        import onnxruntime as ort

        self.session = ort.InferenceSession(model_path, providers=["CPUExecutionProvider"])

    def detect(self, rgb: np.ndarray, confidence: float = DEFAULT_CONFIDENCE) -> list[FaceBox]:
        """rgb: (MODEL_H, MODEL_W, 3) uint8. Returns normalized (0..1) boxes."""
        x = (rgb.astype(np.float32) - 127.0) / 128.0
        x = x.transpose(2, 0, 1)[np.newaxis]  # CHW
        scores, boxes = self.session.run(None, {"input": x})
        scores, boxes = scores[0], boxes[0]  # (N,2), (N,4)
        mask = scores[:, 1] >= confidence
        out = [
            FaceBox(float(b[0]), float(b[1]), float(b[2]), float(b[3]), float(s))
            for b, s in zip(boxes[mask], scores[mask, 1])
        ]
        return _nms(out)


def frame_difference(a: np.ndarray, b: np.ndarray) -> float:
    """Sampled mean abs diff between two raw frames (every 24th byte)."""
    fa, fb = a.reshape(-1)[::24].astype(np.int16), b.reshape(-1)[::24].astype(np.int16)
    n = min(len(fa), len(fb))
    if n == 0:
        return 0.0
    return float(np.mean(np.abs(fa[:n] - fb[:n])))


def merge_cuts(raw_cuts: list[int], fps: float) -> list[int]:
    """A dissolve registers on several neighbouring frames; keep only the
    last of each cluster (when the new shot has settled)."""
    if not raw_cuts:
        return []
    window = max(1, round(CUT_MERGE_SEC * fps))
    return [
        cut
        for i, cut in enumerate(raw_cuts)
        if i == len(raw_cuts) - 1 or raw_cuts[i + 1] - cut > window
    ]
