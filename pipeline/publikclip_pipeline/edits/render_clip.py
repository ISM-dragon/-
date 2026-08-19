"""Single-clip render with edits applied: free bounds, dead-space keep-
ranges, per-clip caption preset + camera mode, and visual overlays.

One ffmpeg graph: split → per-range trim/atrim → concat → sendcmd crop
(remapped trajectory) → scale → overlays (enable windows, opt-in fade
animations) → caption burn (remapped words) → loudnorm. Camera re-directs
only when bounds or camera mode differ from what the run produced.
"""

from __future__ import annotations

import json
import subprocess
from pathlib import Path

from .. import config
from ..captions import ass as ass_mod
from ..render import ffmpeg_bin, renderer
from . import store
from .timeline import ClipEdit, TimeRemap, detect_dead_space, keep_ranges


def _load_stage(job_dir: Path, stage: str) -> dict:
    return json.loads((job_dir / f"{stage}.json").read_text())["data"]


def context_for_clip(job_dir: Path, clip_idx: int, pad: float = 45.0) -> dict:
    """Everything the timeline UI needs, in one JSON blob."""
    ingest = _load_stage(job_dir, "ingest")
    diarize = _load_stage(job_dir, "diarize")
    events = _load_stage(job_dir, "events")
    score = _load_stage(job_dir, "score")
    clip = score["clips"][clip_idx]
    edit = store.edit_for_clip(job_dir, clip_idx, clip)

    duration = float(ingest["probe"]["duration_sec"])
    win_a = max(0.0, edit.start - pad)
    win_b = min(duration, edit.end + pad)

    words = [
        {"word": w["word"], "start": w["start"], "end": w["end"], "speaker": w.get("speaker", 0)}
        for seg in diarize["segments"]
        for w in seg.get("words", [])
        if win_a <= w["start"] <= win_b
    ]
    curves = json.loads(Path(events["curves_path"]).read_text())
    grid = float(curves["grid_sec"])
    rms = curves["rms"][int(win_a / grid) : int(win_b / grid)]
    clip_events = [
        e for e in events["timeline"]
        if e["type"] != "pause" and e["end"] > win_a and e["start"] < win_b
    ]
    all_words = [
        w for seg in diarize["segments"] for w in seg.get("words", [])
    ]
    cuts = detect_dead_space(all_words, events["timeline"], edit.start, edit.end)

    trajectory = None
    camera_path = job_dir / "camera.json"
    if camera_path.exists():
        cam = json.loads(camera_path.read_text())["data"]
        traj_file = cam.get("trajectories", {}).get(str(clip_idx))
        if traj_file and Path(traj_file).exists():
            t = json.loads(Path(traj_file).read_text())
            trajectory = {"fps": t.get("fps", 25), "frames": t.get("frames", [])}

    return {
        "clip_index": clip_idx,
        "window": {"start": win_a, "end": win_b},
        "media_path": ingest["media_path"],
        "probe": {"width": ingest["probe"]["width"], "height": ingest["probe"]["height"]},
        "trajectory": trajectory,
        "source_duration": duration,
        "edit": edit.to_json(),
        "words": words,
        "rms": rms,
        "rms_grid": grid,
        "events": clip_events,
        "auto_cuts": cuts,
        "run_caption_preset": _load_stage(job_dir, "render").get("caption_preset", "classic")
        if (job_dir / "render.json").exists()
        else "classic",
    }


def _camera_needs_redirect(job_dir: Path, clip_idx: int, edit: ClipEdit, score_clip: dict) -> bool:
    if abs(edit.start - score_clip["start"]) > 0.05 or abs(edit.end - score_clip["end"]) > 0.05:
        return True
    if edit.camera_mode:
        camera = _load_stage(job_dir, "camera")
        run_mode = (camera.get("camera_settings") or {}).get("speaker_change", "cut")
        return edit.camera_mode != run_mode
    return False


def _trajectory_for(job_dir: Path, clip_idx: int, edit: ClipEdit, score_clip: dict, settings: config.Settings, emit) -> dict:
    if not _camera_needs_redirect(job_dir, clip_idx, edit, score_clip):
        traj_path = _load_stage(job_dir, "camera")["trajectories"][str(clip_idx)]
        return json.loads(Path(traj_path).read_text())

    emit(-1, "Re-directing camera for new bounds…")
    import numpy as np

    from ..camera import asd as asd_mod
    from ..camera import director
    from ..camera.detect import FaceDetector
    from ..models import registry, specs

    ingest = _load_stage(job_dir, "ingest")
    diarize = _load_stage(job_dir, "diarize")
    events = _load_stage(job_dir, "events")
    curves = json.loads(Path(events["curves_path"]).read_text())

    detector = FaceDetector(str(registry.ensure(specs.ULTRAFACE, lambda f, m: None)))
    model = asd_mod.AsdModel(
        str(registry.ensure(specs.LR_ASD_FRONTEND, lambda f, m: None)),
        str(registry.ensure(specs.LR_ASD_BACKEND, lambda f, m: None)),
    )
    cam_settings = config.CameraSettings(**{**settings.camera.__dict__})
    if edit.camera_mode:
        cam_settings.speaker_change = edit.camera_mode

    src_w, src_h = int(ingest["probe"]["width"]), int(ingest["probe"]["height"])
    analysis = asd_mod.analyze_clip(
        ingest["media_path"], edit.start, edit.end, detector, model, src_w, src_h
    )
    clip_turns = [t for t in diarize["turns"] if t["end"] > edit.start and t["start"] < edit.end]
    traj = director.build_trajectory(
        analysis, clip_turns, events["timeline"],
        np.asarray(curves["dynamics"], dtype=float), float(curves["grid_sec"]),
        edit.start, edit.end, src_w, src_h, cam_settings,
    )
    return {"fps": traj.fps, "frames": traj.frames, "cuts": traj.cuts, "punches": traj.punches}


def _overlay_filters(overlays, input_offset: int, out_w: int, out_h: int) -> tuple[list[str], list[str], str]:
    """(extra -i args, filter chains, final label). Base video label [vb]."""
    inputs: list[str] = []
    chains: list[str] = []
    label = "vb"
    added = 0
    for k, ov in enumerate(overlays):
        if not ov.image_path or not Path(ov.image_path).exists():
            continue
        idx = input_offset + added
        added += 1
        # Bound the looped image stream — an infinite input slows the final
        # filter flush and burns CPU decoding frames nothing will consume.
        inputs += ["-loop", "1", "-t", f"{ov.end + 1.0:.2f}", "-i", ov.image_path]
        w_px = int(out_w * ov.scale)
        pre = f"[{idx}:v]scale={w_px}:-2,pad=iw+16:ih+16:8:8:white@0.95,format=rgba"
        if ov.animation == "ping":
            dur = max(0.3, ov.end - ov.start)
            pre += (
                f",fade=in:st={ov.start:.2f}:d=0.18:alpha=1"
                f",fade=out:st={max(ov.start, ov.end - 0.18):.2f}:d=0.18:alpha=1"
            )
        elif ov.animation == "pop":
            pre += f",fade=in:st={ov.start:.2f}:d=0.1:alpha=1"
        chains.append(pre + f"[ov{k}]")
        x = f"(W-w)*{ov.x:.3f}"
        y = f"(H-h)*{ov.y:.3f}"
        nxt = f"vo{k}"
        chains.append(
            f"[{label}][ov{k}]overlay=x='{x}':y='{y}':enable='between(t,{ov.start:.2f},{ov.end:.2f})'[{nxt}]"
        )
        label = nxt
    return inputs, chains, label


def render_clip_edit(job_dir: Path, clip_idx: int, emit) -> dict:
    """The per-clip render path. Returns the updated output entry."""
    ingest = _load_stage(job_dir, "ingest")
    diarize = _load_stage(job_dir, "diarize")
    events = _load_stage(job_dir, "events")
    score = _load_stage(job_dir, "score")
    settings = config.Settings.from_json(json.loads((job_dir / "settings.json").read_text()))
    clip = score["clips"][clip_idx]
    edit = store.edit_for_clip(job_dir, clip_idx, clip)

    # --- keep ranges + remap ------------------------------------------------
    if edit.remove_dead_space:
        all_words = [w for seg in diarize["segments"] for w in seg.get("words", [])]
        cuts = detect_dead_space(all_words, events["timeline"], edit.start, edit.end)
        ranges = keep_ranges(edit.start, edit.end, cuts, edit.disabled_cuts)
    else:
        ranges = [(edit.start, edit.end)]
    remap = TimeRemap(ranges)

    # --- camera -------------------------------------------------------------
    trajectory = _trajectory_for(job_dir, clip_idx, edit, clip, settings, emit)
    fps = float(trajectory.get("fps", 25))
    # Trajectory frames start at edit.start whether reused (bounds unchanged
    # → edit.start == run start) or freshly re-directed for new bounds.
    frames = remap.remap_trajectory(trajectory["frames"], fps, edit.start)

    src_w, src_h = int(ingest["probe"]["width"]), int(ingest["probe"]["height"])
    boxes = renderer.crop_boxes(frames, src_w, src_h)
    if not boxes:
        boxes = [(src_h * 9 // 16 // 2 * 2, src_h - src_h % 2, 0, 0)]

    # --- captions (remapped) ------------------------------------------------
    words_src = [
        {"word": w["word"], "start": w["start"], "end": w["end"]}
        for seg in diarize["segments"]
        for w in seg.get("words", [])
        if edit.start <= w["start"] < edit.end
    ]
    words_out = remap.remap_words(words_src)
    curves = json.loads(Path(events["curves_path"]).read_text())
    cap_words = [ass_mod.Word(text=w["word"], start=w["start"], end=w["end"]) for w in words_out]
    # Emphasis is a SOURCE-time property (per-word RMS in the original
    # audio): mark it on source-timed copies, then carry each surviving
    # word's flag across in order.
    src_cap = [
        ass_mod.Word(text=w["word"], start=w["start"] - edit.start, end=w["end"] - edit.start)
        for w in words_src
    ]
    ass_mod.mark_emphasis(src_cap, curves["rms"], float(curves["grid_sec"]), clip_start=edit.start)
    out_idx = 0
    for w_src, w_flagged in zip(words_src, src_cap):
        survives = remap.to_output((w_src["start"] + w_src["end"]) / 2) is not None
        if survives and out_idx < len(cap_words):
            cap_words[out_idx].emphasized = w_flagged.emphasized
            out_idx += 1

    clip_events_out = []
    for e in events["timeline"]:
        if e["type"] == "pause" or e["end"] <= edit.start or e["start"] >= edit.end:
            continue
        mid = (e["start"] + e["end"]) / 2
        if remap.to_output(mid) is None:
            continue
        clip_events_out.append(
            {
                "type": e["type"],
                "start": remap.to_output_clamped(e["start"]),
                "end": remap.to_output_clamped(e["end"]),
            }
        )

    preset = edit.caption_preset or settings.caption_preset
    captions_ok = ffmpeg_bin.supports_captions()
    emoji_ok = ass_mod.emoji_probe() if captions_ok else False
    out_dir = job_dir / "clips"
    out_dir.mkdir(exist_ok=True)
    ass_path = out_dir / f"clip_{clip_idx:02d}.ass"
    ass_path.write_text(ass_mod.build_ass(cap_words, clip_events_out, preset_name=preset, emoji_ok=emoji_ok))

    # --- build the graph ----------------------------------------------------
    emit(-1, "Rendering clip…")
    span_a = ranges[0][0]
    span_b = ranges[-1][1]
    n = len(ranges)
    trims = []
    for i, (a, b) in enumerate(ranges):
        ra, rb = a - span_a, b - span_a
        trims.append(f"[0:v]trim=start={ra:.3f}:end={rb:.3f},setpts=PTS-STARTPTS[v{i}]")
        trims.append(f"[0:a]atrim=start={ra:.3f}:end={rb:.3f},asetpts=PTS-STARTPTS[a{i}]")
    concat_in = "".join(f"[v{i}][a{i}]" for i in range(n))
    graph = trims + [f"{concat_in}concat=n={n}:v=1:a=1[vc][ac]"]

    cmd_path = out_dir / f"clip_{clip_idx:02d}.cmd"
    cmd_path.write_text("\n".join(renderer.sendcmd_lines(boxes, fps)) + "\n")
    vchain = (
        f"[vc]sendcmd=f={renderer._q(cmd_path)},"  # noqa: SLF001
        f"crop@c=w={boxes[0][0]}:h={boxes[0][1]}:x={boxes[0][2]}:y={boxes[0][3]},"
        f"scale={renderer.OUT_W}:{renderer.OUT_H}:flags=lanczos,setsar=1[vb]"
    )
    graph.append(vchain)

    ov_inputs, ov_chains, vlabel = _overlay_filters(edit.overlays, 1, renderer.OUT_W, renderer.OUT_H)
    graph.extend(ov_chains)
    if captions_ok:
        graph.append(
            f"[{vlabel}]subtitles=filename={renderer._q(ass_path)}:fontsdir={renderer._q(ass_mod.FONTS_DIR)}[vf]"  # noqa: SLF001
        )
        vlabel = "vf"
    graph.append(f"[ac]loudnorm=I={settings.lufs_target}:TP={settings.true_peak_db}:LRA=11[af]")

    if renderer.videotoolbox_available():
        vcodec = ["-c:v", "h264_videotoolbox", "-b:v", renderer.VT_BITRATE, "-allow_sw", "1"]
    else:
        vcodec = ["-c:v", "libx264", "-preset", "medium", "-crf", str(renderer.X264_CRF)]

    out_path = out_dir / f"clip_{clip_idx:02d}.mp4"
    args = [
        ffmpeg_bin.ffmpeg(), "-y", "-v", "error",
        "-ss", f"{span_a:.3f}", "-t", f"{span_b - span_a:.3f}", "-i", ingest["media_path"],
        *ov_inputs,
        "-filter_complex", ";".join(graph),
        "-map", f"[{vlabel}]", "-map", "[af]",
        *vcodec,
        "-pix_fmt", "yuv420p",
        "-c:a", "aac", "-b:a", "192k", "-ar", "48000",
        "-movflags", "+faststart", "-map_metadata", "-1",
        str(out_path),
    ]
    proc = subprocess.run(args, capture_output=True, text=True, timeout=1800)
    cmd_path.unlink(missing_ok=True)
    if proc.returncode != 0:
        raise RuntimeError(f"Clip render failed: {(proc.stderr or '')[-800:]}")

    check = renderer.verify_output(out_path, remap.output_duration)
    entry = {
        "clip": clip_idx,
        "path": str(out_path),
        "ass": str(ass_path),
        "score": clip["score"],
        "best_platform": clip["best_platform"],
        "duration": round(check["duration"], 2),
        "words": len(cap_words),
        "event_tags": len(clip_events_out),
        "edited": True,
    }

    # keep render.json in sync so the review UI reflects the new file
    render_ckpt_path = job_dir / "render.json"
    if render_ckpt_path.exists():
        ckpt = json.loads(render_ckpt_path.read_text())
        outputs = ckpt["data"].get("outputs", [])
        for i, o in enumerate(outputs):
            if o["clip"] == clip_idx:
                outputs[i] = entry
                break
        else:
            outputs.append(entry)
        tmp = render_ckpt_path.with_suffix(".tmp")
        tmp.write_text(json.dumps(ckpt))
        tmp.replace(render_ckpt_path)
    return entry
