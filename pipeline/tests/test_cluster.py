"""Diarization clustering tests — including the exact shape that span-locked
the first implementation into an infinite loop."""

import numpy as np

from publikclip_pipeline.diarize import cluster


def test_build_turns_terminates_on_same_speaker_islands():
    """A short turn sitting across silence gaps from SAME-speaker neighbors
    must not loop forever (the production hang: relabel was a no-op but the
    loop flag fired anyway)."""
    windows = [
        (0.0, 5.0), (5.0, 10.0),      # speaker 0, long
        (20.0, 20.6),                  # speaker 0, tiny island across a gap
        (40.0, 45.0), (45.0, 50.0),    # speaker 0, long
    ]
    labels = np.array([0, 0, 0, 0, 0])
    turns = cluster.build_turns(windows, labels)  # must return, not hang
    assert all(t["speaker"] == 0 for t in turns)


def test_build_turns_absorbs_wrong_speaker_blip():
    windows = [(float(i), float(i + 1)) for i in range(10)]
    labels = np.array([0, 0, 0, 0, 1, 0, 0, 0, 0, 0])  # 1s blip of speaker 1
    turns = cluster.build_turns(windows, labels)
    assert len(turns) == 1
    assert turns[0]["speaker"] == 0


def test_build_turns_keeps_real_turn_taking():
    windows = [(float(i * 2), float(i * 2 + 2)) for i in range(10)]
    labels = np.array([0] * 5 + [1] * 5)
    turns = cluster.build_turns(windows, labels)
    assert [t["speaker"] for t in turns] == [0, 1]


def test_assign_words_largest_intersection():
    segments = [
        {"start": 0.0, "end": 10.0, "words": [
            {"word": "hi", "start": 1.0, "end": 1.4},
            {"word": "yo", "start": 6.0, "end": 6.4},
        ]},
    ]
    turns = [
        {"speaker": 0, "start": 0.0, "end": 5.0},
        {"speaker": 1, "start": 5.0, "end": 10.0},
    ]
    cluster.assign_words(segments, turns)
    words = segments[0]["words"]
    assert words[0]["speaker"] == 0
    assert words[1]["speaker"] == 1


def test_cluster_windows_two_clear_speakers():
    rng = np.random.default_rng(0)
    a = rng.normal(0, 0.05, (40, 192)) + np.eye(192)[0]
    b = rng.normal(0, 0.05, (40, 192)) + np.eye(192)[1]
    emb = np.vstack([a, b])
    emb = emb / np.linalg.norm(emb, axis=1, keepdims=True)
    labels = cluster.cluster_windows(emb)
    assert len(set(labels[:40])) == 1
    assert len(set(labels[40:])) == 1
    assert labels[0] != labels[40]
