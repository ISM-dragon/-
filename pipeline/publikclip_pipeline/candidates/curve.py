"""The free interest curve — the cost gate for everything downstream.

Weighted sum of local signals on a 1 s grid, weights anchored to the SHAP
importances reported in arXiv 2512.21402 (see RESEARCH.md): audio energy
dynamics and engagement (heatmap) dominate, event density and speaker-turn
rate follow, arousal and lexical power-words trail.

License note: the research draft named the NRC-VAD lexicon for the lexical
channel, but NRC lexicons are research-only (commercial use requires a
paid license) — incompatible with AGPL redistribution. Swapped for a small
built-in power-word list; recorded as a deliberate substitution.
"""

from __future__ import annotations

import numpy as np

# SHAP-anchored channel weights (normalized below).
WEIGHTS = {
    "heatmap": 0.30,       # real human engagement, when present
    "dynamics": 0.25,      # energy deviation from local baseline
    "events": 0.18,        # laughter/gasp/applause density
    "turns": 0.10,         # conversational back-and-forth rate
    "arousal": 0.09,
    "scenes": 0.05,        # visual change rate
    "lexical": 0.03,
}

# Compact power-word list (built-in, license-clean). Lexical is the weakest
# channel by design — it only nudges.
POWER_WORDS = {
    "insane", "crazy", "unbelievable", "never", "secret", "nobody", "worst",
    "best", "free", "money", "dead", "died", "killed", "scared", "terrified",
    "shocked", "shocking", "literally", "actually", "truth", "lie", "lied",
    "caught", "exposed", "banned", "illegal", "million", "billion", "broke",
    "rich", "hate", "hated", "love", "loved", "wild", "weird", "crime",
    "arrested", "fight", "fought", "cried", "screamed", "blew", "exploded",
}

EVENT_WEIGHTS = {"laugh": 1.0, "gasp": 0.9, "scream": 0.8, "cheer": 0.7, "applause": 0.6, "shout": 0.5}


def _norm(x: np.ndarray) -> np.ndarray:
    if len(x) == 0:
        return x
    top = np.percentile(x, 98)
    if top <= 0:
        return np.zeros_like(x)
    return np.clip(x / top, 0.0, 1.0)


def heatmap_channel(heatmap: list[dict] | None, n: int) -> np.ndarray:
    out = np.zeros(n)
    if not heatmap:
        return out
    for seg in heatmap:
        a, b = int(seg["start_time"]), int(np.ceil(seg["end_time"]))
        out[max(0, a) : min(n, b)] = np.maximum(out[max(0, a) : min(n, b)], seg["value"])
    return out


def dynamics_channel(dynamics: list[float], grid_sec: float, n: int) -> np.ndarray:
    arr = np.asarray(dynamics, dtype=float)
    if len(arr) == 0:
        return np.zeros(n)
    per_sec = max(1, int(round(1.0 / grid_sec)))
    trimmed = arr[: (len(arr) // per_sec) * per_sec]
    coarse = trimmed.reshape(-1, per_sec).mean(axis=1)
    out = np.zeros(n)
    out[: min(n, len(coarse))] = coarse[:n]
    return _norm(out)


def events_channel(timeline: list[dict], n: int) -> np.ndarray:
    """Weighted event presence, with a ±2 s halo — the moment AROUND a laugh
    is what clips well, not just the laugh itself."""
    out = np.zeros(n)
    for event in timeline:
        w = EVENT_WEIGHTS.get(event["type"])
        if w is None:
            continue
        w *= float(event.get("confidence", 1.0))
        a = max(0, int(event["start"]) - 2)
        b = min(n, int(np.ceil(event["end"])) + 2)
        out[a:b] = np.maximum(out[a:b], w)
    return out


def turns_channel(turns: list[dict], n: int, window: int = 15) -> np.ndarray:
    """Speaker-change rate in a sliding window."""
    changes = np.zeros(n)
    for prev, cur in zip(turns, turns[1:]):
        if prev["speaker"] != cur["speaker"]:
            t = int(cur["start"])
            if 0 <= t < n:
                changes[t] += 1.0
    kernel = np.ones(window)
    density = np.convolve(changes, kernel, mode="same")
    return _norm(density)


def arousal_channel(arousal: list[float], grid_sec: float, n: int) -> np.ndarray:
    arr = np.asarray(arousal, dtype=float)
    if len(arr) == 0:
        return np.zeros(n)
    per_sec = max(1, int(round(1.0 / grid_sec)))
    if per_sec > 1:
        trimmed = arr[: (len(arr) // per_sec) * per_sec]
        coarse = trimmed.reshape(-1, per_sec).mean(axis=1)
    else:
        # grid coarser than 1 s → repeat
        coarse = np.repeat(arr, int(round(grid_sec)))
    out = np.zeros(n)
    out[: min(n, len(coarse))] = coarse[:n]
    return _norm(out)


def scenes_channel(scene_times: list[float], n: int, window: int = 20) -> np.ndarray:
    marks = np.zeros(n)
    for t in scene_times:
        if 0 <= int(t) < n:
            marks[int(t)] += 1.0
    density = np.convolve(marks, np.ones(window), mode="same")
    return _norm(density)


def lexical_channel(segments: list[dict], n: int) -> np.ndarray:
    out = np.zeros(n)
    for seg in segments:
        for word in seg.get("words", []):
            token = word["word"].lower().strip(".,!?\"'")
            if token in POWER_WORDS:
                t = int(word["start"])
                if 0 <= t < n:
                    out[max(0, t - 1) : min(n, t + 2)] += 0.5
    return _norm(out)


def interest_curve(channels: dict[str, np.ndarray]) -> tuple[np.ndarray, dict[str, float]]:
    """Weighted sum. Missing channels (all-zero heatmap on most videos) get
    their weight redistributed proportionally so the curve never silently
    deflates — and the redistribution is reported for provenance."""
    n = max(len(c) for c in channels.values())
    active = {k: v for k, v in channels.items() if len(v) and float(np.max(v)) > 0}
    total_w = sum(WEIGHTS[k] for k in active)
    if total_w == 0:
        return np.zeros(n), {}
    effective = {k: WEIGHTS[k] / total_w for k in active}
    curve = np.zeros(n)
    for name, weight in effective.items():
        ch = channels[name]
        padded = np.zeros(n)
        padded[: len(ch)] = ch
        curve += weight * padded
    return curve, {k: round(v, 4) for k, v in effective.items()}
