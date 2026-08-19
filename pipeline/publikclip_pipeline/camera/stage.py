"""Camera stage: run the director over the selected finalist clips only —
the single biggest GPU/CPU saving vs reference implementations that reframe
the whole hour (ARCHITECTURE-DRAFT stage 7)."""

from __future__ import annotations

import json
from pathlib import Path

from ..jobs.queue import Stage, StageContext, StageError
from ..models import registry, specs


class CameraStage(Stage):
    name = "camera"
    schema_version = 1

    def artifacts_ok(self, ctx: StageContext, data: dict) -> bool:
        if data.get("camera_settings") != ctx.settings.camera.__dict__:
            return False  # camera style changed → re-direct
        return all(Path(p).exists() for p in data.get("trajectories", {}).values())

    def run(self, ctx: StageContext) -> dict:
        import numpy as np

        from . import asd as asd_mod
        from . import director
        from .detect import FaceDetector

        prior = ctx.prior or {}
        ingest = prior.get("ingest")
        diarize = prior.get("diarize")
        events = prior.get("events")
        score = prior.get("score")
        if not (ingest and diarize and events and score):
            raise StageError("Camera needs ingest + diarize + events + score outputs.")

        media = ingest["media_path"]
        probe = ingest["probe"]
        src_w, src_h = int(probe["width"]), int(probe["height"])

        ctx.emit(-1, "Loading vision models…")
        uf = registry.ensure(specs.ULTRAFACE, lambda f, m: ctx.emit(-1, m))
        fe = registry.ensure(specs.LR_ASD_FRONTEND, lambda f, m: ctx.emit(-1, m))
        be = registry.ensure(specs.LR_ASD_BACKEND, lambda f, m: ctx.emit(-1, m))
        detector = FaceDetector(str(uf))
        model = asd_mod.AsdModel(str(fe), str(be))

        curves = json.loads(Path(events["curves_path"]).read_text())
        dynamics = np.asarray(curves["dynamics"], dtype=float)
        grid = float(curves["grid_sec"])
        turns = diarize["turns"]
        timeline = events["timeline"]

        clips = score["clips"]
        trajectories: dict[str, str] = {}
        stats = []
        for i, clip in enumerate(clips):
            start, end = clip["start"], clip["end"]
            ctx.emit(i / max(1, len(clips)), f"Directing clip {i + 1}/{len(clips)}…")
            analysis = asd_mod.analyze_clip(media, start, end, detector, model, src_w, src_h)
            clip_turns = [t for t in turns if t["end"] > start and t["start"] < end]
            traj = director.build_trajectory(
                analysis, clip_turns, timeline, dynamics, grid,
                start, end, src_w, src_h, ctx.settings,
            )
            out_path = ctx.job_dir / f"trajectory_{i:02d}.json"
            out_path.write_text(
                json.dumps(
                    {
                        "clip_start": start,
                        "clip_end": end,
                        "fps": traj.fps,
                        "frames": traj.frames,
                        "cuts": traj.cuts,
                        "punches": traj.punches,
                        "meta": traj.meta,
                    }
                )
            )
            trajectories[str(i)] = str(out_path)
            stats.append(
                {
                    "clip": i,
                    "tracks": traj.meta["tracks"],
                    "switch_cuts": traj.meta["switch_cuts"],
                    "shot_cuts": traj.meta["shot_cuts"],
                    "punches": len(traj.punches),
                }
            )

        return {
            "trajectories": trajectories,
            "stats": stats,
            "camera_settings": ctx.settings.camera.__dict__.copy(),
        }
