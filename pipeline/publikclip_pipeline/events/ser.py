"""Speech-emotion arousal channel (speechbrain wav2vec2-IEMOCAP, Apache-2.0,
ungated HF weights).

Purpose in the architecture: arousal corroborates or discounts LLM shock
scores (M2: shock ×0.6 with no arousal support) and disambiguates shouting
as triumphant-vs-distressed for the music brief. It is a *supporting* signal,
so it runs sparsely — 5 s windows, 2.5 s hop, speech regions only — and the
stage falls back to a DSP arousal proxy (energy dynamics) if the model can't
load, recording which path produced the curve.

IEMOCAP's 4 classes map to arousal: angry/happy = high, neutral = mid,
sad = low. Categorical→dimensional is a blunt instrument; the curve is
consumed as a percentile signal, never as absolute emotion.
"""

from __future__ import annotations

import numpy as np

SER_REPO = "speechbrain/emotion-recognition-wav2vec2-IEMOCAP"
WINDOW_SEC = 5.0
HOP_SEC = 2.5
GRID_SEC = 0.5  # arousal curve granularity

# class order in the model's label encoder: ang, hap, neu, sad
AROUSAL_WEIGHTS = {"ang": 1.0, "hap": 0.85, "neu": 0.4, "sad": 0.15}


def _windows(segments: list[dict], duration: float) -> list[tuple[float, float]]:
    spans: list[tuple[float, float]] = []
    for seg in segments:
        start, end = float(seg["start"]), min(float(seg["end"]), duration)
        t = start
        while t < end:
            w_end = min(t + WINDOW_SEC, end)
            if w_end - t >= 1.0:
                spans.append((t, w_end))
            if w_end >= end:
                break
            t += HOP_SEC
    return spans


def arousal_curve_ser(
    y16k: np.ndarray,
    segments: list[dict],
    cache_dir: str,
    progress=None,
) -> np.ndarray | None:
    """Arousal on a GRID_SEC grid via SER, or None if the model won't load."""
    try:
        import torch
        from speechbrain.inference.interfaces import foreign_class

        classifier = foreign_class(
            source=SER_REPO,
            pymodule_file="custom_interface.py",
            classname="CustomEncoderWav2vec2Classifier",
            savedir=cache_dir,
            run_opts={"device": "cpu"},
        )
    except Exception:  # noqa: BLE001 — any load failure → DSP fallback
        return None

    sr = 16000
    duration = len(y16k) / sr
    n_bins = int(np.ceil(duration / GRID_SEC))
    acc = np.zeros(n_bins)
    weight = np.zeros(n_bins)
    spans = _windows(segments, duration)
    with torch.inference_mode():
        for i, (start, end) in enumerate(spans):
            chunk = torch.from_numpy(
                y16k[int(start * sr) : int(end * sr)].astype(np.float32)
            ).unsqueeze(0)
            try:
                out_prob, _, _, lab = classifier.classify_batch(chunk)
                probs = out_prob.exp().squeeze(0).cpu().numpy()
                labels = ["ang", "hap", "neu", "sad"]
                arousal = float(sum(p * AROUSAL_WEIGHTS[l] for p, l in zip(probs, labels)))
            except Exception:  # noqa: BLE001 — one bad window shouldn't kill the curve
                continue
            b0, b1 = int(start / GRID_SEC), max(int(start / GRID_SEC) + 1, int(end / GRID_SEC))
            acc[b0:b1] += arousal
            weight[b0:b1] += 1.0
            if progress and i % 20 == 0:
                progress(i / max(1, len(spans)))
    curve = np.divide(acc, weight, out=np.full(n_bins, np.nan), where=weight > 0)
    # Fill non-speech bins by interpolation so consumers get a dense curve.
    if np.all(np.isnan(curve)):
        return None
    idx = np.arange(n_bins)
    good = ~np.isnan(curve)
    return np.interp(idx, idx[good], curve[good])


def arousal_curve_dsp(dynamics: list[float], dynamics_grid_sec: float) -> np.ndarray:
    """Fallback proxy: energy dynamics resampled onto the arousal grid and
    squashed to 0..1. Honest but blunt — the checkpoint records when this
    path was used so scoring can weight it down."""
    arr = np.asarray(dynamics, dtype=float)
    if len(arr) == 0:
        return np.zeros(0)
    factor = max(1, int(round(GRID_SEC / dynamics_grid_sec)))
    trimmed = arr[: (len(arr) // factor) * factor]
    coarse = trimmed.reshape(-1, factor).mean(axis=1) if len(trimmed) else arr
    top = np.percentile(coarse, 95) or 1.0
    return np.clip(coarse / top, 0.0, 1.0)
