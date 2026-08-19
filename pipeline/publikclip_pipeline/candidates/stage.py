"""Candidates stage: build every free channel, weigh them into the interest
curve, extract ~35 sentence-snapped candidate windows. No LLM spend here —
this count is the cost gate for T1/T2."""

from __future__ import annotations

import json
from pathlib import Path

from .. import config
from ..jobs.queue import Stage, StageContext, StageError


def detect_scenes(media_path: str, progress=None) -> list[float]:
    """Scene-change timestamps via PySceneDetect ContentDetector (BSD-3) on
    a downscaled decode. On a static podcast this returns camera cuts; on
    gaming/vlog footage it captures visual pacing."""
    from scenedetect import ContentDetector, open_video
    from scenedetect.scene_manager import SceneManager

    video = open_video(media_path)
    manager = SceneManager()
    manager.add_detector(ContentDetector(threshold=27.0))
    manager.auto_downscale = True
    manager.detect_scenes(video, show_progress=False)
    return [start.get_seconds() for start, _ in manager.get_scene_list()]


class CandidatesStage(Stage):
    name = "candidates"
    schema_version = 1

    def run(self, ctx: StageContext) -> dict:
        import numpy as np

        from . import curve as curve_mod
        from . import windows as windows_mod

        prior = ctx.prior or {}
        ingest = prior.get("ingest")
        diarize = prior.get("diarize")
        events = prior.get("events")
        if not (ingest and diarize and events):
            raise StageError("Candidates need ingest + diarize + events outputs.")

        segments = diarize["segments"]
        duration = float(ingest["probe"]["duration_sec"])
        n = int(np.ceil(duration))

        curves_path = Path(events["curves_path"])
        if not curves_path.exists():
            raise StageError("curves.json missing — re-run events.")
        curves = json.loads(curves_path.read_text())

        ctx.emit(-1, "Detecting scene changes…")
        try:
            scene_times = detect_scenes(ingest["media_path"])
        except Exception:  # noqa: BLE001 — scenes are a minor channel; degrade
            scene_times = []
        (ctx.job_dir / "scenes.json").write_text(json.dumps(scene_times))

        ctx.emit(0.6, "Building interest curve…")
        channels = {
            "heatmap": curve_mod.heatmap_channel(ingest.get("heatmap"), n),
            "dynamics": curve_mod.dynamics_channel(curves["dynamics"], curves["grid_sec"], n),
            "events": curve_mod.events_channel(events["timeline"], n),
            "turns": curve_mod.turns_channel(diarize["turns"], n),
            "arousal": curve_mod.arousal_channel(
                curves.get("arousal", []), curves.get("arousal_grid_sec", 0.5), n
            ),
            "scenes": curve_mod.scenes_channel(scene_times, n),
            "lexical": curve_mod.lexical_channel(segments, n),
        }
        curve, effective_weights = curve_mod.interest_curve(channels)

        ctx.emit(0.8, "Extracting candidate windows…")
        candidates = windows_mod.extract(curve, channels, segments, duration)
        budget = config.candidate_budget(ctx.settings.processing_mode, duration)
        if len(candidates) > budget:
            candidates = sorted(candidates, key=lambda item: item.curve_score, reverse=True)[:budget]
            candidates = sorted(candidates, key=lambda item: item.start)
        if not candidates:
            raise StageError(
                "No candidate moments found — the video may be too short or too quiet."
            )

        # Persist the curve for the review UI's timeline visualization.
        (ctx.job_dir / "interest_curve.json").write_text(
            json.dumps({"per_sec": np.round(curve, 4).tolist()})
        )

        return {
            "candidates": [c.to_json() for c in candidates],
            "count": len(candidates),
            "candidate_budget": budget,
            "processing_mode": ctx.settings.processing_mode,
            "effective_weights": effective_weights,
            "scene_count": len(scene_times),
            "heatmap_present": bool(ingest.get("heatmap")),
        }
