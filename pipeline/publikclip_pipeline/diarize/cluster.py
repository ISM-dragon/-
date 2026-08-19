"""Speaker clustering: cosine affinity → spectral clustering with eigengap
auto-K → temporal turn building → word assignment.

The word-assignment tie-breaking (largest total intersection with a speaker
turn, midpoint-nearest fallback) follows whisperX diarize.py's algorithm
(BSD-2-Clause) reimplemented over plain numpy interval arrays.
"""

from __future__ import annotations

import numpy as np

MAX_SPEAKERS = 8
SUBSAMPLE_LIMIT = 3000


def _spectral_labels(affinity: np.ndarray, k: int) -> np.ndarray:
    from scipy.linalg import eigh
    from sklearn.cluster import KMeans

    # Normalized Laplacian embedding, then k-means — standard recipe.
    deg = affinity.sum(axis=1)
    deg[deg == 0] = 1e-9
    d_inv_sqrt = 1.0 / np.sqrt(deg)
    lap = np.eye(len(affinity)) - (affinity * d_inv_sqrt[None, :]) * d_inv_sqrt[:, None]
    vals, vecs = eigh(lap)
    embedding = vecs[:, :k]
    norms = np.linalg.norm(embedding, axis=1, keepdims=True)
    norms[norms == 0] = 1.0
    embedding = embedding / norms
    return KMeans(n_clusters=k, n_init=10, random_state=0).fit_predict(embedding)


def _eigengap_k(affinity: np.ndarray, max_k: int = MAX_SPEAKERS) -> int:
    from scipy.linalg import eigh

    deg = affinity.sum(axis=1)
    deg[deg == 0] = 1e-9
    d_inv_sqrt = 1.0 / np.sqrt(deg)
    lap = np.eye(len(affinity)) - (affinity * d_inv_sqrt[None, :]) * d_inv_sqrt[:, None]
    vals = eigh(lap, eigvals_only=True, subset_by_index=[0, min(max_k, len(lap) - 1)])
    gaps = np.diff(vals[: max_k + 1])
    if len(gaps) == 0:
        return 1
    # gaps[i] sits between eigenvalues i and i+1; the largest gap after the
    # first K near-zero eigenvalues means K clusters — so K = argmax + 1.
    # (A dominant gaps[0] correctly means one cluster.)
    k = int(np.argmax(gaps)) + 1
    return max(1, min(k, max_k))


def _refine_affinity(affinity: np.ndarray, p: float = 0.7) -> np.ndarray:
    """Row-percentile pruning + symmetrization (the standard spectral-
    clustering refinement, per pyannote/3D-Speaker practice).

    Voices from the same recording share channel/room characteristics, so
    raw cosine similarity sits uniformly high and the eigengap washes out —
    on a real two-host podcast the unrefined matrix reads as ONE speaker.
    Zeroing everything below each row's p-quantile keeps only genuinely
    strong neighbor links, which restores the block structure."""
    thresholds = np.quantile(affinity, p, axis=1, keepdims=True)
    pruned = np.where(affinity >= thresholds, affinity, 0.0)
    return np.maximum(pruned, pruned.T)  # symmetrize


def cluster_windows(embeddings: np.ndarray, num_speakers: int | None = None) -> np.ndarray:
    """Label each embedding window with a speaker index."""
    n = len(embeddings)
    if n == 0:
        return np.zeros(0, dtype=int)
    if n == 1:
        return np.zeros(1, dtype=int)

    idx = np.arange(n)
    if n > SUBSAMPLE_LIMIT:
        idx = np.linspace(0, n - 1, SUBSAMPLE_LIMIT).astype(int)
    sub = embeddings[idx]
    affinity = _refine_affinity(np.clip(sub @ sub.T, 0.0, None))

    k = num_speakers or _eigengap_k(affinity)
    if k == 1:
        return np.zeros(n, dtype=int)
    sub_labels = _spectral_labels(affinity, k)

    # Centroid assign for every window (covers the subsampled case).
    centroids = np.stack([sub[sub_labels == c].mean(axis=0) for c in range(k)])
    norms = np.linalg.norm(centroids, axis=1, keepdims=True)
    norms[norms == 0] = 1.0
    centroids = centroids / norms
    return np.argmax(embeddings @ centroids.T, axis=1)


def build_turns(
    windows: list[tuple[float, float]],
    labels: np.ndarray,
    min_turn_sec: float = 1.0,
) -> list[dict]:
    """Overlapping-window votes → contiguous speaker turns.

    Median-smooths labels over a 3-window neighborhood, merges adjacent
    same-speaker windows, then absorbs sub-min_turn_sec islands into their
    longer neighbor (a 0.4 s 'turn' is a clustering wobble, not a reply)."""
    if not windows:
        return []
    smoothed = labels.copy()
    for i in range(1, len(labels) - 1):
        trio = labels[i - 1 : i + 2]
        vals, counts = np.unique(trio, return_counts=True)
        smoothed[i] = vals[np.argmax(counts)]

    def _merge(items: list[dict], gap: float = 0.35) -> list[dict]:
        merged: list[dict] = []
        for turn in items:
            if (
                merged
                and merged[-1]["speaker"] == turn["speaker"]
                and turn["start"] <= merged[-1]["end"] + gap
            ):
                merged[-1]["end"] = max(merged[-1]["end"], turn["end"])
            else:
                merged.append(dict(turn))
        return merged

    turns = _merge(
        [
            {"speaker": int(label), "start": start, "end": end}
            for (start, end), label in zip(windows, smoothed)
        ]
    )

    # Absorb tiny islands into their longer neighbor. Bounded passes, and
    # `changed` only fires on a REAL relabel — a same-speaker island that
    # sits across a silence gap is legitimate and simply stays (the
    # unbounded while-loop version span-locked on exactly that case).
    for _ in range(3):
        changed = False
        for i, turn in enumerate(turns):
            if turn["end"] - turn["start"] >= min_turn_sec:
                continue
            neighbors = [turns[j] for j in (i - 1, i + 1) if 0 <= j < len(turns)]
            if not neighbors:
                continue
            host = max(neighbors, key=lambda t: t["end"] - t["start"])
            if turn["speaker"] != host["speaker"]:
                turn["speaker"] = host["speaker"]
                changed = True
        turns = _merge(turns)
        if not changed:
            break

    for turn in turns:
        turn["start"] = round(turn["start"], 3)
        turn["end"] = round(turn["end"], 3)
    return turns


def assign_words(segments: list[dict], turns: list[dict]) -> None:
    """Mutates segments: adds 'speaker' to each word and each segment.

    Largest-intersection rule per word; midpoint-nearest turn as fallback
    (whisperX assign_word_speakers algorithm)."""
    if not turns:
        return
    starts = np.array([t["start"] for t in turns])
    ends = np.array([t["end"] for t in turns])
    speakers = [t["speaker"] for t in turns]

    def _word_speaker(w_start: float, w_end: float) -> int:
        inter = np.minimum(ends, w_end) - np.maximum(starts, w_start)
        best = int(np.argmax(inter))
        if inter[best] > 0:
            return speakers[best]
        mid = (w_start + w_end) / 2
        centers = (starts + ends) / 2
        return speakers[int(np.argmin(np.abs(centers - mid)))]

    for seg in segments:
        votes: dict[int, float] = {}
        for word in seg.get("words", []):
            spk = _word_speaker(word["start"], word["end"])
            word["speaker"] = spk
            votes[spk] = votes.get(spk, 0.0) + (word["end"] - word["start"])
        if votes:
            seg["speaker"] = max(votes.items(), key=lambda kv: kv[1])[0]
