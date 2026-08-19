"""Free DSP signal curves: RMS energy + spectral flux at a 100 ms grid, plus
long-pause detection from transcript word gaps.

These feed the interest curve (M2), punch-in triggers (M3), and prosodic
caption emphasis gets its per-word RMS from the same frame grid (M4). F0 is
deliberately NOT computed over the full video — pitch extraction is slow and
only selected clips need it (computed on-demand in M4).
"""

from __future__ import annotations

import librosa
import numpy as np

GRID_SEC = 0.1  # 100 ms analysis grid shared by all curve consumers
LONG_PAUSE_SEC = 1.2


def energy_curves(y16k: np.ndarray, sr: int = 16000) -> dict:
    """RMS + spectral flux resampled onto the 100 ms grid, plus normalized
    'dynamics' (how much the local energy deviates from its neighborhood —
    flat delivery scores 0, punchy delivery spikes)."""
    hop = int(sr * GRID_SEC)
    frame = hop * 2
    rms = librosa.feature.rms(y=y16k, frame_length=frame, hop_length=hop)[0]
    stft = np.abs(librosa.stft(y=y16k, n_fft=frame, hop_length=hop))
    flux = np.sqrt(np.mean(np.diff(stft, axis=1, prepend=stft[:, :1]) ** 2, axis=0))
    flux = flux[: len(rms)]

    # Local dynamics: |rms - 5s moving average|, normalized by global std.
    win = max(1, int(5.0 / GRID_SEC))
    kernel = np.ones(win) / win
    local_mean = np.convolve(rms, kernel, mode="same")
    std = float(np.std(rms)) or 1.0
    dynamics = np.abs(rms - local_mean) / std

    return {
        "grid_sec": GRID_SEC,
        "rms": np.round(rms.astype(float), 5).tolist(),
        "flux": np.round(flux.astype(float), 5).tolist(),
        "dynamics": np.round(dynamics.astype(float), 4).tolist(),
    }


def long_pauses(segments: list[dict], min_gap: float = LONG_PAUSE_SEC) -> list[dict]:
    """Gaps between consecutive words (across segments too). A long pause is
    a beat — comedic timing, a speaker collecting themselves — and both the
    interest curve and candidate boundary snapping use them."""
    words: list[dict] = []
    for seg in segments:
        words.extend(seg.get("words", []))
    words.sort(key=lambda w: w["start"])
    pauses = []
    for prev, cur in zip(words, words[1:]):
        gap = cur["start"] - prev["end"]
        if gap >= min_gap:
            pauses.append(
                {
                    "type": "pause",
                    "start": round(prev["end"], 3),
                    "end": round(cur["start"], 3),
                    "confidence": 1.0,
                    "sources": ["transcript"],
                }
            )
    return pauses
