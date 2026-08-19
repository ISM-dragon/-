"""Cross-validation + composite tests — the score's audit trail must be
exactly right, it's the product."""

import numpy as np

from publikclip_pipeline.candidates import curve as curve_mod
from publikclip_pipeline.candidates import windows as windows_mod
from publikclip_pipeline.music.brief import mood_prior
from publikclip_pipeline.scoring import rubric


def _t1(**overrides):
    base = {
        "hook": 6, "hook_type": "story_open", "funniness": 8, "punchline_index": 12,
        "shock": 2, "curiosity_gap": 5, "value": 4, "self_contained": True,
        "bait_phrases": [], "summary": "a funny story",
    }
    base.update(overrides)
    return base


def test_funny_without_laughter_is_discounted():
    sub, adj = rubric.cross_validate(_t1(), laughs_near=[], arousal_pct=0.5, heatmap_pct=None)
    assert sub["funniness"] == 8 * rubric.FUNNY_NO_LAUGH
    assert any(a["rule"] == "funny_no_laugh" for a in adj)


def test_funny_with_corroborated_laughter_is_boosted():
    laughs = [{"type": "laugh", "start": 5, "end": 7, "sources": ["jrgillick", "panns"]}]
    sub, adj = rubric.cross_validate(_t1(), laughs_near=laughs, arousal_pct=0.5, heatmap_pct=None)
    assert sub["funniness"] == min(10.0, 8 * rubric.FUNNY_CORROBORATED)
    assert any(a["rule"] == "funny_corroborated" for a in adj)


def test_single_source_laughter_neither_boosts_nor_discounts():
    laughs = [{"type": "laugh", "start": 5, "end": 7, "sources": ["jrgillick"]}]
    sub, adj = rubric.cross_validate(_t1(), laughs_near=laughs, arousal_pct=0.5, heatmap_pct=None)
    assert sub["funniness"] == 8.0
    assert not any(a["rule"].startswith("funny") for a in adj)


def test_shock_without_arousal_discounted_unless_heatmap_high():
    t1 = _t1(shock=7, funniness=2)
    sub, adj = rubric.cross_validate(t1, laughs_near=[], arousal_pct=0.2, heatmap_pct=None)
    assert sub["shock"] == 7 * rubric.SHOCK_NO_AROUSAL

    sub2, adj2 = rubric.cross_validate(t1, laughs_near=[], arousal_pct=0.2, heatmap_pct=0.9)
    assert sub2["shock"] == 7.0  # heatmap elevation rescues it
    assert not any(a["rule"] == "shock_no_arousal" for a in adj2)


def test_bait_penalty_applies_to_all_subscores():
    t1 = _t1(funniness=2, bait_phrases=["smash that like button"])
    sub, adj = rubric.cross_validate(t1, laughs_near=[], arousal_pct=0.5, heatmap_pct=None)
    assert sub["hook"] == 6 * rubric.BAIT_PENALTY
    assert any(a["rule"] == "bait_penalty" for a in adj)


def test_every_adjustment_is_recorded():
    laughs = [{"type": "laugh", "start": 5, "end": 7, "sources": ["jrgillick", "panns"]}]
    t1 = _t1(shock=8, bait_phrases=["subscribe"])
    sub, adj = rubric.cross_validate(t1, laughs_near=laughs, arousal_pct=0.1, heatmap_pct=None)
    rules = {a["rule"] for a in adj}
    assert rules == {"funny_corroborated", "shock_no_arousal", "bait_penalty"}


def test_composite_heatmap_boost_recorded():
    sub = {"hook": 8.0, "funniness": 7.0, "shock": 3.0, "curiosity_gap": 5.0, "value": 4.0}
    scores, adj = rubric.composite(sub, curve_score=0.5, heatmap_pct=0.85, visual=None)
    assert any(a["rule"] == "heatmap_boost" for a in adj)
    assert set(scores) == {"tiktok", "reels", "shorts"}
    assert all(0 <= v <= 100 for v in scores.values())


def test_composite_visual_split_only_when_t2_present():
    sub = {"hook": 8.0, "funniness": 7.0, "shock": 3.0, "curiosity_gap": 5.0, "value": 4.0}
    without, _ = rubric.composite(sub, 0.5, None, None)
    with_visual, _ = rubric.composite(sub, 0.5, None, {"visual_interest": 10})
    assert with_visual["tiktok"] != without["tiktok"]


# --- interest curve + windows ---------------------------------------------


def test_interest_curve_redistributes_missing_channels():
    n = 100
    channels = {
        "heatmap": np.zeros(n),  # absent on most videos
        "dynamics": np.ones(n) * 0.5,
        "events": np.zeros(n),
        "turns": np.ones(n) * 0.2,
        "arousal": np.zeros(n),
        "scenes": np.zeros(n),
        "lexical": np.zeros(n),
    }
    curve, weights = curve_mod.interest_curve(channels)
    assert "heatmap" not in weights  # zero channel dropped
    assert abs(sum(weights.values()) - 1.0) < 1e-6
    assert curve.max() > 0


def test_candidate_windows_snap_to_sentences():
    segments = [
        {"start": float(i * 10), "end": float(i * 10 + 9.5), "text": "x", "words": []}
        for i in range(30)
    ]
    curve = np.zeros(300)
    curve[100:140] = 1.0
    channels = {"dynamics": curve}
    cands = windows_mod.extract(curve, channels, segments, 300.0)
    assert cands
    for c in cands:
        # every start must sit on a sentence start
        assert any(abs(c.start - s["start"]) < 0.01 for s in segments)
        assert windows_mod.MIN_LEN <= c.end - c.start <= windows_mod.MAX_LEN + 0.01


def test_dedupe_prefers_higher_score():
    a = windows_mod.Candidate(start=10, end=40, peak_time=25, curve_score=0.9)
    b = windows_mod.Candidate(start=12, end=42, peak_time=26, curve_score=0.5)
    kept = windows_mod.dedupe([b, a])
    assert kept == [a]


# --- music mood prior ------------------------------------------------------


def test_shouting_disambiguation():
    shout = [{"type": "shout", "start": 1, "end": 2}]
    cheer = shout + [{"type": "cheer", "start": 2, "end": 4}]
    assert "triumphant" in mood_prior(cheer, arousal_pct=0.8)
    assert "tense" in mood_prior(shout, arousal_pct=0.8)


def test_laughter_beats_arousal_for_mood():
    laugh = [{"type": "laugh", "start": 1, "end": 2}]
    assert "comedic" in mood_prior(laugh, arousal_pct=0.7)
    assert "light" in mood_prior(laugh, arousal_pct=0.2)
