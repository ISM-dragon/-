"""Instagram sync store tests — matching, snapshot ladder, recompute
fidelity, and the auto-fit (SYNC-DESIGN.md decisions #10–#13).

The recompute tests are the load-bearing ones: fit_constants only works if
recompute_platform_scores replays the live scoring math EXACTLY."""

import json
import sqlite3
import time
from datetime import datetime, timedelta, timezone

import pytest

from publikclip_pipeline import config
from publikclip_pipeline.insights import calibration, instagram
from publikclip_pipeline.scoring import constants as constants_mod
from publikclip_pipeline.scoring import rubric


@pytest.fixture(autouse=True)
def isolated_home(tmp_path, monkeypatch):
    monkeypatch.setenv("PUBLIKCLIP_HOME", str(tmp_path / "home"))
    yield


def _t1(**overrides):
    base = {
        "hook": 6, "hook_type": "story_open", "funniness": 8, "punchline_index": 12,
        "shock": 2, "curiosity_gap": 5, "value": 4, "self_contained": True,
        "bait_phrases": [], "summary": "a funny story about a surprise visit",
    }
    base.update(overrides)
    return base


def make_entry(t1, *, laughs=None, arousal_pct=0.2, heatmap_pct=None,
               curve_score=0.4, visual=None, constants=None):
    """Assemble a scored-clip entry exactly the way ScoreStage does."""
    sub, adjustments = rubric.cross_validate(
        t1, laughs_near=laughs or [], arousal_pct=arousal_pct,
        heatmap_pct=heatmap_pct, constants=constants,
    )
    platform, comp_adj = rubric.composite(
        sub, curve_score, heatmap_pct, visual, constants=constants
    )
    return {
        "start": 10.0, "end": 41.5, "curve_score": curve_score,
        "t1_raw": t1, "subscores": {k: round(v, 2) for k, v in sub.items()},
        "adjustments": adjustments + comp_adj,
        "arousal_pct": arousal_pct, "heatmap_pct": heatmap_pct,
        "summary": t1.get("summary", ""), "t2": visual,
        "platform_scores": platform, "score": max(platform.values()),
        "best_platform": max(platform, key=platform.get),
    }


# --- recompute fidelity ------------------------------------------------------


@pytest.mark.parametrize("case", [
    # (t1 overrides, laughs, arousal, heatmap, visual)
    ({}, [], 0.2, None, None),                                     # funny_no_laugh fires
    ({}, [{"sources": ["panns", "jrgillick"]}], 0.2, None, None),  # corroborated
    ({}, [{"sources": ["panns"]}], 0.2, None, None),               # single-source, no rule
    ({"shock": 7}, [], 0.1, None, None),                           # shock_no_arousal fires
    ({"funniness": 1, "shock": 1}, [], 0.9, 0.9, None),            # heatmap boost
    ({"bait_phrases": ["like and subscribe", "comment below"]}, [], 0.2, None, None),
    ({}, [], 0.5, 0.85, {"visual_interest": 8, "faces_visible": True,
                          "expressive_peak": True, "on_screen_text": False, "notes": ""}),
])
def test_recompute_reproduces_live_scoring(case):
    overrides, laughs, arousal, heatmap, visual = case
    entry = make_entry(_t1(**overrides), laughs=laughs, arousal_pct=arousal,
                       heatmap_pct=heatmap, visual=visual)
    recomputed = rubric.recompute_platform_scores(entry, constants_mod.DEFAULTS)
    assert recomputed == entry["platform_scores"]


def test_recompute_with_different_constants_moves_the_score():
    entry = make_entry(_t1())  # funny_no_laugh fired at 0.55
    softer = dict(constants_mod.DEFAULTS, funny_no_laugh=0.8)
    assert rubric.recompute_platform_scores(entry, softer)["reels"] > entry["platform_scores"]["reels"]


def test_recompute_without_t1_raw_returns_none():
    assert rubric.recompute_platform_scores({"subscores": {"hook": 5}}, constants_mod.DEFAULTS) is None


# --- snapshot ladder (decision #12) ------------------------------------------


@pytest.mark.parametrize("age_h,ages,due", [
    (3.0, [], False),            # before the first rung
    (7.0, [], True),             # 6h rung passed, nothing captured
    (30.0, [25.0], False),       # 24h rung covered
    (50.0, [25.0], True),        # 48h rung passed, best capture is 25h
    (50.0, [49.0], False),       # 48h rung covered
    (800.0, [730.0], False),     # 30d rung covered — media goes quiet
    (800.0, [500.0], True),      # 30d rung passed, not covered
    (None, [], True),            # unknown posting time: take one snapshot
    (None, [None], False),
])
def test_insights_due_ladder(age_h, ages, due):
    assert calibration.insights_due(age_h, ages) is due


# --- matching (decision #10) --------------------------------------------------


def _media_row(posted_offset_h=2.0, rendered_at=None, **overrides):
    rendered_at = rendered_at or time.time() - 86400
    row = {
        "media_id": "M1",
        "caption": "a funny story about a surprise visit #reels",
        "posted_at": rendered_at + posted_offset_h * 3600,
        "duration_s": 31.4,
    }
    row.update(overrides)
    return row, rendered_at


def _clip(rendered_at, **overrides):
    clip = {
        "job_id": "j1", "clip_index": 0, "rendered_at": rendered_at,
        "duration": 31.5, "summary": "a funny story about a surprise visit",
    }
    clip.update(overrides)
    return clip


def test_match_score_strong_on_duration_timing_caption():
    media, rendered_at = _media_row()
    assert calibration.match_score(media, _clip(rendered_at)) > 0.8


def test_match_score_zero_when_posted_before_render():
    media, rendered_at = _media_row(posted_offset_h=-5.0)
    assert calibration.match_score(media, _clip(rendered_at)) == 0.0


def test_match_score_zero_outside_window():
    media, rendered_at = _media_row(posted_offset_h=15 * 24.0)
    assert calibration.match_score(media, _clip(rendered_at)) == 0.0


def test_match_score_survives_missing_duration():
    """Copyright-flagged media (no media_url ⇒ no probe) must still match on
    timing + caption — weights renormalize, they don't penalize."""
    media, rendered_at = _media_row(duration_s=None)
    score = calibration.match_score(media, _clip(rendered_at))
    assert score >= calibration.SUGGEST_THRESHOLD


def test_match_score_duration_mismatch_drags_it_down():
    media, rendered_at = _media_row(duration_s=8.0, caption="x")
    assert calibration.match_score(media, _clip(rendered_at)) < calibration.SUGGEST_THRESHOLD


# --- store: jobs on disk, suggestions, link/reject/unlink ---------------------


def _write_job(job_id="j1", n_clips=1, entries=None):
    job_dir = config.jobs_dir() / job_id
    (job_dir / "clips").mkdir(parents=True)
    entries = entries or [make_entry(_t1()) for _ in range(n_clips)]
    outputs = [
        {"clip": i, "path": str(job_dir / "clips" / f"clip_{i:02d}.mp4"),
         "duration": 31.5, "score": e["score"], "best_platform": e["best_platform"]}
        for i, e in enumerate(entries)
    ]
    (job_dir / "render.json").write_text(json.dumps({"data": {"outputs": outputs}}))
    (job_dir / "score.json").write_text(
        json.dumps({"data": {"clips": entries, "scoring_config_version": 1}})
    )
    return entries


def _iso(epoch):
    return datetime.fromtimestamp(epoch, tz=timezone.utc).isoformat()


def test_suggestions_reject_link_unlink_roundtrip():
    config.ensure_home()
    entries = _write_job()
    rendered_at = (config.jobs_dir() / "j1" / "render.json").stat().st_mtime
    calibration.upsert_media(
        {"id": "M1", "caption": "a funny story about a surprise visit",
         "media_type": "VIDEO", "permalink": "https://instagram.com/reel/x",
         "timestamp": _iso(rendered_at + 3600), "media_url": None},
        None, 31.4,
    )

    suggested = calibration.suggestions()
    assert len(suggested) == 1
    s = suggested[0]
    assert (s["media_id"], s["job_id"], s["clip_index"]) == ("M1", "j1", 0)

    calibration.reject_match("M1", "j1", 0)
    assert calibration.suggestions() == []

    calibration.link_clip("j1", 0, "M1", entries[0], link_source="manual", config_version=1)
    rows = calibration.tracked()
    assert rows[0]["ig_media_id"] == "M1"
    assert rows[0]["link_source"] == "manual"
    assert rows[0]["reels_score"] == entries[0]["platform_scores"]["reels"]
    assert json.loads(rows[0]["provenance_json"])["t1_raw"] == entries[0]["t1_raw"]

    assert calibration.unlink("M1") is True
    assert calibration.tracked() == []


def test_overview_works_disconnected_and_marks_copyright():
    config.ensure_home()
    _write_job()
    calibration.upsert_media(
        {"id": "M9", "caption": "song heavy reel", "media_type": "VIDEO",
         "timestamp": _iso(time.time() - 3600), "media_url": None,
         "permalink": "https://instagram.com/reel/y"},
        None, None,
    )
    view = calibration.overview()
    assert view["connected"] is False
    assert view["clip_library"][0]["linked"] is False
    unlinked = [m for m in view["unlinked"] if m["media_id"] == "M9"]
    assert unlinked and unlinked[0]["copyright_flagged"] is True
    assert view["calibration"]["active"]["version"] == 1


def test_schema_migration_from_old_published_clips():
    """A db created by the pre-sync schema opens cleanly and gains the new
    columns; its rows surface with sane defaults."""
    config.ensure_home()
    conn = sqlite3.connect(config.db_path())
    conn.executescript(
        "CREATE TABLE published_clips ("
        " id INTEGER PRIMARY KEY AUTOINCREMENT, job_id TEXT NOT NULL,"
        " clip_index INTEGER NOT NULL, ig_media_id TEXT UNIQUE,"
        " linked_at REAL NOT NULL, score REAL NOT NULL, platform TEXT,"
        " provenance_json TEXT NOT NULL, metrics_json TEXT, metrics_fetched_at REAL);"
    )
    conn.execute(
        "INSERT INTO published_clips (job_id, clip_index, ig_media_id, linked_at,"
        " score, platform, provenance_json) VALUES ('old', 0, 'OLD1', 1.0, 60.5,"
        " 'reels', '{\"subscores\": {\"hook\": 7.0}}')"
    )
    conn.commit()
    conn.close()

    rows = calibration.tracked()
    assert rows[0]["ig_media_id"] == "OLD1"
    assert rows[0]["reels_score"] is None  # migrated column, honest default
    view = calibration.overview()
    linked = [r for r in view["linked"] if r["media_id"] == "OLD1"]
    assert linked and linked[0]["reels_score"] == 60.5  # falls back to stored score


# --- snapshots + qualifying outcomes ------------------------------------------


def _link_with_snapshot(media_id, entry, *, posted_hours_ago, views, job_id="j1", clip_index=0):
    calibration.upsert_media(
        {"id": media_id, "caption": "c", "media_type": "VIDEO",
         "timestamp": _iso(time.time() - posted_hours_ago * 3600),
         "media_url": "https://cdn/x.mp4", "permalink": "https://instagram.com/reel/z"},
        None, 31.4,
    )
    calibration.link_clip(job_id, clip_index, media_id, entry,
                          link_source="manual", config_version=1)
    calibration.store_metrics(media_id, {"views": views, "reach": views // 2})


def test_qualifying_outcomes_pick_snapshot_nearest_48h():
    config.ensure_home()
    entry = make_entry(_t1())
    _link_with_snapshot("M1", entry, posted_hours_ago=72, views=1000)
    outcomes = calibration.qualifying_outcomes()
    assert len(outcomes) == 1
    assert outcomes[0]["views"] == 1000.0
    assert outcomes[0]["snapshot_age_h"] == pytest.approx(72, abs=1)
    assert outcomes[0]["can_recompute"] is True

    # too-young snapshots don't qualify
    _link_with_snapshot("M2", entry, posted_hours_ago=10, views=50, clip_index=1)
    assert len(calibration.qualifying_outcomes()) == 1


# --- the auto-fit (decision #13) ----------------------------------------------


def _fit_dataset(target_constants, n=24):
    """Synthetic outcomes whose views EXACTLY rank by the score recomputed
    under `target_constants` — the fit should walk to those constants."""
    entries = []
    for i in range(n):
        kind = i % 4
        if kind == 0:    # funny, no laughter — exercises funny_no_laugh
            t1 = _t1(funniness=8, hook=3 + i % 5)
            entry = make_entry(t1, laughs=[])
        elif kind == 1:  # corroborated laughter
            t1 = _t1(funniness=7, hook=2 + i % 5)
            entry = make_entry(t1, laughs=[{"sources": ["a", "b"]}])
        elif kind == 2:  # shocking, flat arousal
            t1 = _t1(funniness=1, shock=8, hook=4 + i % 5)
            entry = make_entry(t1, arousal_pct=0.1)
        else:            # plain value clip
            t1 = _t1(funniness=1, shock=1, value=6, hook=1 + i % 5)
            entry = make_entry(t1)
        entries.append(entry)
    scored = [
        (e, rubric.recompute_platform_scores(e, target_constants)["reels"])
        for e in entries
    ]
    return scored


def test_fit_below_threshold_is_a_noop():
    config.ensure_home()
    entry = make_entry(_t1())
    _link_with_snapshot("M1", entry, posted_hours_ago=72, views=1000)
    result = calibration.fit_constants()
    assert result["applied"] is False
    assert constants_mod.active()["version"] == 1


def test_fit_recovers_target_constants_and_versions_loudly():
    config.ensure_home()
    target = {"funny_no_laugh": 0.8, "funny_corroborated": 1.3,
              "shock_no_arousal": 0.48, "heatmap_boost": 1.0}
    for i, (entry, target_score) in enumerate(_fit_dataset(target)):
        _link_with_snapshot(f"M{i}", entry, posted_hours_ago=60,
                            views=int(target_score * 1000) + i, clip_index=i)

    result = calibration.fit_constants()
    assert result["applied"] is True, result
    assert result["version"] == 2
    for key, (lo, hi) in constants_mod.CLAMPS.items():
        assert lo <= result["constants"][key] <= hi
    # The discriminative constant walked toward the target. (Exact recovery
    # is not identifiable: a rank-only objective ties every constant that
    # preserves the target ordering, so we assert direction, not value.)
    assert result["constants"]["funny_no_laugh"] > constants_mod.DEFAULTS["funny_no_laugh"]

    active = constants_mod.active()
    assert active["version"] == 2
    assert active["fitted_from_n"] == 24
    history = constants_mod.history()
    assert [v["version"] for v in history] == [1, 2]
    assert history[0]["constants"] == constants_mod.DEFAULTS

    # immediately refitting is throttled until FIT_MIN_NEW new outcomes land
    again = calibration.fit_constants()
    assert again["applied"] is False


def test_fit_discards_refit_that_cannot_beat_active_constants():
    """Views that already rank perfectly under the defaults: any refit ties
    at best on held-out folds and must be discarded."""
    config.ensure_home()
    for i, (entry, default_score) in enumerate(_fit_dataset(constants_mod.DEFAULTS)):
        _link_with_snapshot(f"M{i}", entry, posted_hours_ago=60,
                            views=int(default_score * 1000) + i, clip_index=i)
    result = calibration.fit_constants()
    assert result["applied"] is False
    assert constants_mod.active()["version"] == 1


# --- the sync orchestrator (decision #12) --------------------------------------


def test_sync_full_pass_with_faked_api(monkeypatch, tmp_path):
    """One sync: new media lands with a thumbnail + duration, the linked
    Reel's insights ladder fires and appends a snapshot, meta records the
    run, and the fit no-ops below threshold — all without a network."""
    config.ensure_home()
    entries = _write_job()
    calibration.upsert_media(
        {"id": "M1", "caption": "posted earlier", "media_type": "VIDEO",
         "timestamp": _iso(time.time() - 50 * 3600), "media_url": "https://cdn/m1.mp4",
         "permalink": "https://instagram.com/reel/m1"},
        None, 31.4,
    )
    calibration.link_clip("j1", 0, "M1", entries[0], link_source="manual", config_version=1)

    fake_thumb = tmp_path / "m2.jpg"
    fake_thumb.write_bytes(b"jpg")
    monkeypatch.setattr(instagram, "load_connection", lambda: {"username": "tester", "user_id": "42", "access_token": "t", "token_obtained_at": time.time()})
    monkeypatch.setattr(instagram, "refresh_if_needed", lambda c: c)
    monkeypatch.setattr(
        instagram, "recent_media",
        lambda conn, stop_at=None: [
            {"id": "M2", "caption": "fresh reel", "media_type": "VIDEO",
             "timestamp": _iso(time.time() - 7 * 3600), "media_url": "https://cdn/m2.mp4",
             "thumbnail_url": "https://cdn/m2.jpg",
             "permalink": "https://instagram.com/reel/m2"},
        ],
    )
    monkeypatch.setattr(instagram, "cache_thumbnail", lambda conn, m: str(fake_thumb))
    monkeypatch.setattr(instagram, "probe_duration", lambda url: 29.9)
    # M1 has no cached thumbnail, so the backfill leg fires media_node — it
    # must be faked too or the test would hit the real Graph API.
    monkeypatch.setattr(
        instagram, "media_node",
        lambda conn, mid, fields=instagram.MEDIA_FIELDS: {
            "id": mid, "media_type": "VIDEO", "thumbnail_url": "https://cdn/back.jpg",
            "timestamp": _iso(time.time() - 50 * 3600),
            "permalink": f"https://instagram.com/reel/{mid}",
        },
    )
    pulled: list[str] = []
    monkeypatch.setattr(
        instagram, "media_insights",
        lambda conn, mid: pulled.append(mid) or {"views": 4321, "reach": 2000, "reels_skip_rate": 31.0},
    )

    summary = calibration.sync()
    assert summary["ok"] is True, summary
    assert summary["new_media"] == 1
    assert summary["thumbs_cached"] == 2  # M2 fresh + M1 backfilled via media_node
    assert summary["snapshots_pulled"] == 1
    assert pulled == ["M1"]  # linked + 48h rung passed; M2 is unlinked

    snaps = calibration.snapshots_for("M1")
    assert len(snaps) == 1 and snaps[0]["metrics"]["views"] == 4321
    assert snaps[0]["age_hours"] == pytest.approx(50, abs=1)
    assert summary["fit"]["applied"] is False  # 1 outcome, threshold 20

    view = calibration.overview()
    assert view["last_synced_at"] is not None
    m2 = [m for m in view["unlinked"] if m["media_id"] == "M2"]
    assert m2 and m2[0]["thumb"] == str(fake_thumb) and m2[0]["duration_s"] == 29.9

    # second sync immediately after: ladder satisfied, nothing pulled
    summary2 = calibration.sync()
    assert summary2["snapshots_pulled"] == 0


def test_sync_tombstones_deleted_media(monkeypatch):
    config.ensure_home()
    entries = _write_job()
    calibration.upsert_media(
        {"id": "MDEAD", "caption": "gone", "media_type": "VIDEO",
         "timestamp": _iso(time.time() - 50 * 3600), "media_url": None,
         "permalink": "https://instagram.com/reel/dead"},
        None, None,
    )
    calibration.link_clip("j1", 0, "MDEAD", entries[0], link_source="manual", config_version=1)
    monkeypatch.setattr(instagram, "load_connection", lambda: {"username": "t", "user_id": "42", "access_token": "t", "token_obtained_at": time.time()})
    monkeypatch.setattr(instagram, "refresh_if_needed", lambda c: c)
    monkeypatch.setattr(instagram, "recent_media", lambda conn, stop_at=None: [])

    def gone(conn, mid, fields=None):
        raise instagram.IgError(f"Insights failed for {mid}: Object with ID does not exist")

    monkeypatch.setattr(instagram, "media_node", gone)
    monkeypatch.setattr(instagram, "cache_thumbnail", lambda conn, m: None)

    monkeypatch.setattr(instagram, "media_insights", gone)
    summary = calibration.sync()
    assert summary["tombstoned"] == 1
    view = calibration.overview()
    row = [r for r in view["linked"] if r["media_id"] == "MDEAD"][0]
    assert row["media_deleted"] is True


# --- instagram helpers ---------------------------------------------------------


def test_drop_named_field():
    fields = "views,reach,reels_skip_rate"
    assert instagram._drop_named_field(fields, "reels_skip_rate is not valid") == "views,reach"
    assert instagram._drop_named_field(fields, "something unrelated") is None
    assert instagram._drop_named_field("views", "views is not valid") is None  # nothing left
