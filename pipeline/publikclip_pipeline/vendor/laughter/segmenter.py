# Vendored/ported from jrgillick/laughter-detection (MIT) — the exact
# inference recipe from segment_laughter.py + laugh_segmenter.py +
# utils/audio_utils.featurize_melspec + SwitchBoardLaughterInferenceDataset.
# See VENDORED-LICENSES.md. Upstream: https://github.com/jrgillick/laughter-detection
#
# Faithfulness note: featurize_melspec calls amplitude_to_db on a POWER mel
# spectrogram — technically power_to_db territory, but the checkpoint was
# trained with exactly this scaling, so we reproduce it bit-for-bit rather
# than "fix" it.

from __future__ import annotations

import librosa
import numpy as np
import scipy.signal
import torch

SAMPLE_RATE = 8000
HOP_LENGTH = 186
WINDOW_FRAMES = 44


def featurize_melspec(y: np.ndarray, sr: int) -> np.ndarray:
    S = librosa.feature.melspectrogram(y=y, sr=sr, hop_length=HOP_LENGTH).T
    return librosa.amplitude_to_db(S, ref=np.max)


def predict_probs(
    model: torch.nn.Module,
    y8k: np.ndarray,
    device: torch.device,
    batch_size: int = 256,
    progress=None,
) -> np.ndarray:
    """One probability per feature frame via 44-frame sliding windows.

    Windows are materialized PER BATCH from the stride-tricks view — an
    hour-plus of audio yields ~300k windows, and contiguizing them all up
    front is a ~7 GB allocation that swaps a 24 GB machine (learned on the
    first real 2 h run)."""
    features = featurize_melspec(y8k, SAMPLE_RATE)
    n = len(features) - WINDOW_FRAMES
    if n <= 0:
        return np.zeros(0, dtype=np.float32)
    view = np.lib.stride_tricks.sliding_window_view(features, WINDOW_FRAMES, axis=0)
    probs: list[np.ndarray] = []
    with torch.inference_mode():
        for i in range(0, n, batch_size):
            batch = np.ascontiguousarray(
                view[i : min(i + batch_size, n)].transpose(0, 2, 1)[:, np.newaxis, :, :],
                dtype=np.float32,
            )
            preds = model(torch.from_numpy(batch).to(device)).cpu().numpy().squeeze(-1)
            probs.append(np.atleast_1d(preds))
            if progress and (i // batch_size) % 20 == 0:
                progress(i / n)
    return np.concatenate(probs)


def lowpass(sig: np.ndarray, filter_order: int = 2, cutoff: float = 0.01) -> np.ndarray:
    if len(sig) < 3 * (filter_order + 1):
        return sig
    B, A = scipy.signal.butter(filter_order, cutoff, output="ba")
    return scipy.signal.filtfilt(B, A, sig)


def get_laughter_instances(
    probs: np.ndarray, threshold: float = 0.5, min_length: float = 0.2, fps: float = 100.0
) -> list[tuple[float, float]]:
    instances: list[list[int]] = []
    current: list[int] = []
    for i, p in enumerate(probs):
        if p > threshold:
            current.append(i)
        elif current:
            instances.append(current)
            current = []
    if current:
        instances.append(current)
    spans = [(frames[0] / fps, frames[-1] / fps) for frames in instances]
    return [(s, e) for s, e in spans if e - s > min_length]


def segment(
    model: torch.nn.Module,
    y8k: np.ndarray,
    duration_sec: float,
    device: torch.device,
    threshold: float = 0.5,
    min_length: float = 0.2,
    progress=None,
) -> list[dict]:
    """Full upstream pipeline: probs → lowpass → threshold → spans.

    fps is measured (len(probs)/duration) exactly as segment_laughter.py does,
    rather than assumed — the mel hop gives ~43 fps, not the nominal 100.
    """
    probs = predict_probs(model, y8k, device, progress=progress)
    if len(probs) == 0 or duration_sec <= 0:
        return []
    fps = len(probs) / duration_sec
    smoothed = lowpass(probs)
    spans = get_laughter_instances(smoothed, threshold=threshold, min_length=min_length, fps=fps)
    out = []
    for start, end in spans:
        i0, i1 = int(start * fps), max(int(start * fps) + 1, int(end * fps))
        conf = float(np.mean(probs[i0:i1]))
        out.append({"start": round(start, 3), "end": round(end, 3), "confidence": round(conf, 3)})
    return out
