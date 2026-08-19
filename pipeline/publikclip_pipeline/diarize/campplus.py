"""CAM++ speaker embeddings — the ungated local diarization path.

Model definition vendored from modelscope/3D-Speaker (Apache-2.0), weights
from huggingface.co/funasr/campplus (Apache-2.0, verified 2026-08-10, no
gating). This is what lets publikclip ship diarization inside a downloadable
app with no HuggingFace account — pyannote's gated weights couldn't.

Features: kaldi-style 80-dim fbank at 16 kHz with cepstral mean subtraction,
the exact frontend 3D-Speaker/FunASR use with this checkpoint.
"""

from __future__ import annotations

import numpy as np
import torch

from ..vendor.campplus.dtdnn import CAMPPlus

EMBED_DIM = 192
WINDOW_SEC = 1.5
HOP_SEC = 0.75
MIN_WINDOW_SEC = 0.5


def load_model(checkpoint_path: str, device: torch.device) -> CAMPPlus:
    model = CAMPPlus(feat_dim=80, embedding_size=EMBED_DIM)
    state = torch.load(checkpoint_path, map_location=device, weights_only=False)
    model.load_state_dict(state, strict=True)
    model.to(device)
    model.eval()
    return model


def _fbank(wav: torch.Tensor) -> torch.Tensor:
    import torchaudio.compliance.kaldi as kaldi

    feat = kaldi.fbank(
        wav.unsqueeze(0),
        num_mel_bins=80,
        frame_length=25,
        frame_shift=10,
        sample_frequency=16000,
        dither=0.0,
    )
    return feat - feat.mean(dim=0, keepdim=True)  # CMN


def speech_windows(
    segments: list[dict], duration: float
) -> list[tuple[float, float]]:
    """Slide WINDOW_SEC/HOP_SEC windows over ASR speech segments."""
    windows: list[tuple[float, float]] = []
    for seg in segments:
        start, end = float(seg["start"]), min(float(seg["end"]), duration)
        if end - start < MIN_WINDOW_SEC:
            continue
        t = start
        while t < end:
            w_end = min(t + WINDOW_SEC, end)
            if w_end - t >= MIN_WINDOW_SEC:
                windows.append((round(t, 3), round(w_end, 3)))
            if w_end >= end:
                break
            t += HOP_SEC
    return windows


def embed_windows(
    model: CAMPPlus,
    y16k: np.ndarray,
    windows: list[tuple[float, float]],
    device: torch.device,
    batch_size: int = 64,
    progress=None,
) -> np.ndarray:
    """(n_windows, 192) length-normalized embeddings."""
    sr = 16000
    out = np.zeros((len(windows), EMBED_DIM), dtype=np.float32)
    with torch.inference_mode():
        batch_feats: list[torch.Tensor] = []
        batch_idx: list[int] = []
        max_frames = 0

        def _flush() -> None:
            nonlocal batch_feats, batch_idx, max_frames
            if not batch_feats:
                return
            padded = torch.zeros(len(batch_feats), max_frames, 80)
            for j, feat in enumerate(batch_feats):
                padded[j, : feat.shape[0]] = feat
            emb = model(padded.to(device)).cpu().numpy()
            for j, idx in enumerate(batch_idx):
                out[idx] = emb[j]
            batch_feats, batch_idx, max_frames = [], [], 0

        for i, (start, end) in enumerate(windows):
            chunk = y16k[int(start * sr) : int(end * sr)]
            if len(chunk) < int(MIN_WINDOW_SEC * sr):
                continue
            feat = _fbank(torch.from_numpy(chunk.astype(np.float32)))
            batch_feats.append(feat)
            batch_idx.append(i)
            max_frames = max(max_frames, feat.shape[0])
            if len(batch_feats) >= batch_size:
                _flush()
                if progress:
                    progress(i / max(1, len(windows)))
        _flush()
    norms = np.linalg.norm(out, axis=1, keepdims=True)
    norms[norms == 0] = 1.0
    return out / norms
