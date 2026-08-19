"""Keep-range / remap math — the A/V-sync-critical core of per-clip editing."""

from publikclip_pipeline.edits import timeline as tl


def _words(spans):
    return [{"word": w, "start": a, "end": b} for w, a, b in spans]


def test_dead_space_basic_cut():
    words = _words([("hey", 10.0, 10.4), ("so", 13.0, 13.3)])  # 2.6s gap
    cuts = tl.detect_dead_space(words, [], 10.0, 14.0)
    active = [c for c in cuts if not c["kept"]]
    # the mid gap AND the trailing dead air both get cut
    assert len(active) == 2
    import pytest

    assert active[0]["start"] == pytest.approx(10.4 + tl.BREATH_PAD)
    assert active[0]["end"] == pytest.approx(13.0 - tl.BREATH_PAD)
    assert active[1]["start"] == pytest.approx(13.3 + tl.BREATH_PAD)


def test_pause_near_laughter_is_protected():
    words = _words([("joke.", 10.0, 10.4), ("anyway", 13.0, 13.3)])
    events = [{"type": "laugh", "start": 10.6, "end": 11.4}]
    cuts = tl.detect_dead_space(words, events, 10.0, 14.0)
    assert all(c["kept"] for c in cuts)
    assert "comedic" in cuts[0]["reason"]


def test_natural_sentence_pause_kept():
    words = _words([("done.", 10.0, 10.4), ("next", 11.3, 11.6)])  # 0.9s after '.'
    cuts = tl.detect_dead_space(words, [], 10.0, 12.0)
    assert cuts and cuts[0]["kept"]
    assert "natural" in cuts[0]["reason"]


def test_short_gaps_ignored():
    words = _words([("a", 10.0, 10.3), ("b", 10.6, 10.9)])  # 0.3s < MIN_CUT_GAP
    assert tl.detect_dead_space(words, [], 10.0, 11.0) == []


def test_keep_ranges_and_disable():
    cuts = [
        {"start": 12.0, "end": 14.0, "kept": False},
        {"start": 20.0, "end": 21.0, "kept": False},
    ]
    ranges = tl.keep_ranges(10.0, 30.0, cuts)
    assert ranges == [(10.0, 12.0), (14.0, 20.0), (21.0, 30.0)]
    # user re-enables the first cut region
    ranges2 = tl.keep_ranges(10.0, 30.0, cuts, disabled=[0])
    assert ranges2 == [(10.0, 20.0), (21.0, 30.0)]


def test_remap_words_drop_and_shift():
    remap = tl.TimeRemap([(10.0, 12.0), (14.0, 20.0)])
    words = _words([("in1", 10.5, 11.0), ("gone", 12.5, 13.5), ("in2", 15.0, 15.5)])
    out = remap.remap_words(words)
    assert [w["word"] for w in out] == ["in1", "in2"]
    assert out[0]["start"] == 0.5
    assert out[1]["start"] == 3.0  # 2.0 kept + (15-14)


def test_remap_output_duration():
    remap = tl.TimeRemap([(10.0, 12.0), (14.0, 20.0)])
    assert remap.output_duration == 8.0
    assert remap.to_output(11.0) == 1.0
    assert remap.to_output(13.0) is None
    assert remap.to_output_clamped(13.0) == 2.0  # snaps to next keep start


def test_remap_trajectory_length_and_source_pick():
    # 25fps, clip 10..20 → frames[i] = i (identity marker)
    frames = [[float(i), 0, 100, 100] for i in range(250)]
    remap = tl.TimeRemap([(10.0, 12.0), (14.0, 16.0)])
    out = remap.remap_trajectory(frames, 25.0, clip_start=10.0)
    assert len(out) == 100  # 4s output at 25fps
    assert out[0][0] == 0.0
    # first output frame after the cut (t_out=2.0) maps to source t=14 → idx 100
    assert out[50][0] == 100.0
