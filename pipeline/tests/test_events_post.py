"""DCASE post-processing chain + fusion tests — pure numpy, no models."""

import numpy as np

from publikclip_pipeline.events import post
from publikclip_pipeline.events.dsp import long_pauses


def test_hysteresis_enters_high_stays_low():
    fps = 100.0
    probs = np.zeros(500)
    probs[100:130] = 0.8   # strong onset
    probs[130:180] = 0.3   # dips below enter (0.5) but above stay (0.25)
    probs[180:] = 0.0
    spans = post.hysteresis_spans(probs, fps)
    assert len(spans) == 1
    start, end, peak = spans[0]
    assert abs(start - 1.0) < 0.02
    assert abs(end - 1.8) < 0.02  # survived the dip — one event, not two
    assert peak == 0.8


def test_hysteresis_no_entry_below_enter_threshold():
    probs = np.full(200, 0.4)  # above stay, never above enter
    assert post.hysteresis_spans(probs, 100.0) == []


def test_merge_close_spans():
    spans = [(1.0, 2.0, 0.9), (2.2, 3.0, 0.7), (5.0, 6.0, 0.8)]
    merged = post.merge_close(spans, gap=0.32)
    assert len(merged) == 2
    assert merged[0] == (1.0, 3.0, 0.9)


def test_drop_short():
    spans = [(1.0, 1.1, 0.9), (2.0, 2.5, 0.6)]
    assert post.drop_short(spans) == [(2.0, 2.5, 0.6)]


def test_fuse_agreement_boosts_confidence():
    events = [
        {"type": "laugh", "start": 10.0, "end": 12.0, "confidence": 0.8, "sources": ["jrgillick"]},
        {"type": "laugh", "start": 10.2, "end": 12.1, "confidence": 0.7, "sources": ["panns"]},
    ]
    fused = post.fuse(events)
    assert len(fused) == 1
    assert fused[0]["sources"] == ["jrgillick", "panns"]
    assert fused[0]["confidence"] == round(min(0.99, 0.8 * post.AGREEMENT_BOOST), 3)
    assert fused[0]["start"] == 10.0 and fused[0]["end"] == 12.1


def test_fuse_different_types_do_not_merge():
    events = [
        {"type": "laugh", "start": 10.0, "end": 12.0, "confidence": 0.8, "sources": ["jrgillick"]},
        {"type": "gasp", "start": 10.1, "end": 11.9, "confidence": 0.6, "sources": ["panns"]},
    ]
    assert len(post.fuse(events)) == 2


def test_fuse_disjoint_same_type_stay_separate():
    events = [
        {"type": "laugh", "start": 10.0, "end": 11.0, "confidence": 0.8, "sources": ["jrgillick"]},
        {"type": "laugh", "start": 50.0, "end": 51.0, "confidence": 0.9, "sources": ["panns"]},
    ]
    fused = post.fuse(events)
    assert len(fused) == 2
    assert all(len(e["sources"]) == 1 for e in fused)


def test_long_pauses_span_segments():
    segments = [
        {"words": [{"word": "hi", "start": 0.0, "end": 0.4}]},
        {"words": [{"word": "so", "start": 2.5, "end": 2.8}, {"word": "yeah", "start": 2.9, "end": 3.1}]},
    ]
    pauses = long_pauses(segments, min_gap=1.2)
    assert len(pauses) == 1
    assert pauses[0]["start"] == 0.4 and pauses[0]["end"] == 2.5


def test_postprocess_full_chain():
    fps = 100.0
    probs = np.zeros(1000)
    probs[100:140] = 0.9    # event A
    probs[150:190] = 0.85   # close to A → merged (gap 0.1s < 0.32)
    probs[500:510] = 0.95   # 0.1s — too short, dropped
    spans = post.postprocess(probs, fps)
    assert len(spans) == 1
    start, end, _ = spans[0]
    assert start < 1.2 and end > 1.8
