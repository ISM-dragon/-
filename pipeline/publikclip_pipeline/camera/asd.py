"""LR-ASD audio-visual active-speaker detection over ONNX.

Ported from JeremySNR/clip-forge src/main/pipeline/asd.ts (MIT); model from
Junhua-Liao/LR-ASD (MIT, IJCV 2025), ONNX graphs pre-exported by clip-forge
with a parity-proving export script. The model watches each face's mouth
region AND listens to the soundtrack — someone chewing while another person
talks cannot fool it, which is precisely where mouth-motion heuristics die.

Preprocessing geometry is undocumented outside clip-forge's source and wrong
values degrade silently (RESEARCH gap list), so every constant here mirrors
asd.ts exactly: 25 fps, detect stride 2, crop 0.7×max(w,h) shifted down
0.2×, pad 110, 112² grayscale from a 640-wide decode, 13-dim MFCC at 4
audio frames per video frame (python_speech_features — the SAME library the
LR-ASD reference uses, so no MFCC parity risk), multi-duration backend
ensemble over 1–6 s windows, ±2-frame mean smoothing. Logit > 0 = speaking.
"""

from __future__ import annotations

import subprocess
from dataclasses import dataclass

import numpy as np

from ..render import ffmpeg_bin
from .detect import (
    ASD_SCENE_CUT_THRESHOLD,
    MODEL_H,
    MODEL_W,
    FaceDetector,
    frame_difference,
    merge_cuts,
)
from .tracks import FaceTrack, build_face_tracks, crop_face, track_coverage_ratio

ASD_FPS = 25
DETECT_STRIDE = 2
CROP_SIZE = 112
CROP_SIDE_FACTOR = 0.7
CROP_DOWN_SHIFT = 0.2
BACKEND_WINDOWS_SEC = [1, 2, 3, 4, 5, 6]
CROP_PASS_WIDTH = 640
MIN_TRACK_COVERAGE = 0.25
MFCC_COEFFS = 13


@dataclass
class ScoredTrack:
    start: int
    centres: list[float]   # horizontal face centre 0..1 per frame
    centres_y: list[float]
    areas: list[float]
    scores: list[float]    # ASD logits, > 0 = speaking


@dataclass
class AsdAnalysis:
    tracks: list[ScoredTrack]
    frame_count: int
    scene_cuts: list[int]
    fps: int


def _stream_frames(video_path: str, start: float, duration: float, vf: str, pix_fmt: str, frame_bytes: int):
    """Yield raw frames from an ffmpeg decode."""
    proc = subprocess.Popen(
        [
            ffmpeg_bin.ffmpeg(), "-v", "error",
            "-ss", f"{start:.3f}", "-t", f"{duration:.3f}",
            "-i", video_path,
            "-vf", vf, "-f", "rawvideo", "-pix_fmt", pix_fmt, "-",
        ],
        stdout=subprocess.PIPE,
    )
    try:
        while True:
            buf = proc.stdout.read(frame_bytes)
            if len(buf) < frame_bytes:
                break
            yield np.frombuffer(buf, dtype=np.uint8)
    finally:
        proc.stdout.close()
        proc.wait()


def detection_pass(
    video_path: str, start: float, duration: float, detector: FaceDetector
) -> tuple[list, list[int], int]:
    """Pass 1: small RGB frames → strided face detections + scene cuts."""
    faces_per_frame: list = []
    raw_cuts: list[int] = []
    prev: np.ndarray | None = None
    for f, flat in enumerate(
        _stream_frames(
            video_path, start, duration,
            f"fps={ASD_FPS},scale={MODEL_W}:{MODEL_H}", "rgb24", MODEL_W * MODEL_H * 3,
        )
    ):
        frame = flat.reshape(MODEL_H, MODEL_W, 3)
        if prev is not None and frame_difference(prev, frame) > ASD_SCENE_CUT_THRESHOLD:
            raw_cuts.append(f)
        if f % DETECT_STRIDE == 0:
            faces_per_frame.append(detector.detect(frame))
        else:
            faces_per_frame.append(None)
        prev = frame
    return faces_per_frame, merge_cuts(raw_cuts, ASD_FPS), len(faces_per_frame)


def crop_pass(
    video_path: str, start: float, duration: float, tracks: list[FaceTrack], src_w: int, src_h: int
) -> list[list[np.ndarray]]:
    """Pass 2: grayscale frames → per-track mouth crops."""
    crop_w = min(CROP_PASS_WIDTH, src_w or CROP_PASS_WIDTH)
    crop_h = max(2, 2 * round((crop_w * (src_h or crop_w)) / max(1, src_w or crop_w) / 2))
    crops: list[list[np.ndarray]] = [[] for _ in tracks]
    for f, flat in enumerate(
        _stream_frames(
            video_path, start, duration,
            f"fps={ASD_FPS},scale={crop_w}:{crop_h}", "gray", crop_w * crop_h,
        )
    ):
        frame = flat.reshape(crop_h, crop_w)
        for t, track in enumerate(tracks):
            i = f - track.start
            if i < 0 or i >= len(track.boxes):
                continue
            box = track.boxes[i]
            w = (box.x2 - box.x1) * crop_w
            h = (box.y2 - box.y1) * crop_h
            size = max(w, h)
            cx = box.cx * crop_w
            cy = box.cy * crop_h + CROP_DOWN_SHIFT * size
            crops[t].append(crop_face(frame, cx, cy, max(4.0, size * CROP_SIDE_FACTOR), CROP_SIZE))
    return crops


def extract_mfcc(video_path: str, start: float, duration: float) -> np.ndarray | None:
    """13-dim MFCC of the clip audio — python_speech_features, the exact
    library the LR-ASD reference implementation uses."""
    from python_speech_features import mfcc as psf_mfcc

    proc = subprocess.run(
        [
            ffmpeg_bin.ffmpeg(), "-v", "error",
            "-ss", f"{start:.3f}", "-t", f"{duration:.3f}",
            "-i", video_path, "-vn", "-ac", "1", "-ar", "16000", "-f", "s16le", "-",
        ],
        capture_output=True, timeout=600,
    )
    pcm = np.frombuffer(proc.stdout, dtype=np.int16)
    if len(pcm) < 1600:  # under 100 ms
        return None
    return psf_mfcc(pcm, 16000, numcep=MFCC_COEFFS, winlen=0.025, winstep=0.010).astype(np.float32)


def _mfcc_slice(mfcc: np.ndarray, start_frame: int, frames: int) -> np.ndarray:
    """4 audio rows per video frame, repeating the last row past the end."""
    need = frames * 4
    idx = np.minimum(len(mfcc) - 1, start_frame * 4 + np.arange(need))
    return mfcc[idx]


def _smooth_scores(scores: np.ndarray) -> list[float]:
    """Mean over ±2 frames, as in the reference visualization."""
    out = []
    for i in range(len(scores)):
        lo, hi = max(0, i - 2), min(len(scores), i + 3)
        out.append(float(np.mean(scores[lo:hi])))
    return out


class AsdModel:
    def __init__(self, frontend_path: str, backend_path: str):
        import onnxruntime as ort

        opts = ort.SessionOptions()
        opts.log_severity_level = 3
        self.frontend = ort.InferenceSession(frontend_path, opts, providers=["CPUExecutionProvider"])
        self.backend = ort.InferenceSession(backend_path, opts, providers=["CPUExecutionProvider"])

    def score_track(self, crops: list[np.ndarray], mfcc: np.ndarray | None, track_start: int) -> list[float]:
        frames = len(crops)
        video = np.stack(crops).astype(np.float32)[np.newaxis]  # [1, frames, 112, 112]
        audio = (
            _mfcc_slice(mfcc, track_start, frames)[np.newaxis]
            if mfcc is not None
            else np.zeros((1, frames * 4, MFCC_COEFFS), dtype=np.float32)
        )
        embed_a, embed_v = self.frontend.run(
            ["embedA", "embedV"], {"audio": audio.astype(np.float32), "video": video}
        )

        if mfcc is None:
            _, scores_v = self.backend.run(
                ["scoresAV", "scoresV"], {"embedA": embed_a, "embedV": embed_v}
            )
            return _smooth_scores(np.asarray(scores_v))

        total = np.zeros(frames, dtype=np.float64)
        passes = 0
        for seconds in BACKEND_WINDOWS_SEC:
            window = seconds * ASD_FPS
            for start in range(0, frames, window):
                length = min(window, frames - start)
                scores_av, _ = self.backend.run(
                    ["scoresAV", "scoresV"],
                    {
                        "embedA": embed_a[:, start : start + length],
                        "embedV": embed_v[:, start : start + length],
                    },
                )
                total[start : start + length] += np.asarray(scores_av).reshape(-1)[:length]
            passes += 1
            if window >= frames:
                break
        return _smooth_scores(total / passes)


def analyze_clip(
    video_path: str,
    start: float,
    end: float,
    detector: FaceDetector,
    model: AsdModel,
    src_w: int,
    src_h: int,
) -> AsdAnalysis:
    duration = max(0.1, end - start)
    faces_per_frame, scene_cuts, frame_count = detection_pass(video_path, start, duration, detector)
    if frame_count == 0:
        return AsdAnalysis([], 0, [], ASD_FPS)
    tracks = build_face_tracks(faces_per_frame, scene_cuts, ASD_FPS)
    if not tracks or track_coverage_ratio(tracks, frame_count) < MIN_TRACK_COVERAGE:
        return AsdAnalysis([], frame_count, scene_cuts, ASD_FPS)

    crops = crop_pass(video_path, start, duration, tracks, src_w, src_h)
    mfcc = extract_mfcc(video_path, start, duration)
    scored: list[ScoredTrack] = []
    for t, track in enumerate(tracks):
        frames = min(len(track.boxes), len(crops[t]))
        if frames < 2:
            continue
        scores = model.score_track(crops[t][:frames], mfcc, track.start)
        scored.append(
            ScoredTrack(
                start=track.start,
                centres=[b.cx for b in track.boxes[:frames]],
                centres_y=[b.cy for b in track.boxes[:frames]],
                areas=[b.area for b in track.boxes[:frames]],
                scores=scores,
            )
        )
    return AsdAnalysis(scored, frame_count, scene_cuts, ASD_FPS)
