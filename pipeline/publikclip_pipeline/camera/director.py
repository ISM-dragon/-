"""The director: ASD scores + diarization turns + events → a per-frame crop
trajectory with cuts, pans, and punch-ins.

Camera philosophy (decision #7, defaults; every constant user-tunable):
  - HARD CUT on speaker change and shot boundary (a cut has no nausea cost)
  - eased pan only for within-shot drift beyond a deadzone
  - zoom locked per scene (continuous zoom is looming optical flow — the
    top nausea trigger; a zoom change across a cut reads as a new shot)
  - punch-ins fired by laughter/gasp/scream events and vocal-energy peaks,
    rendered as a multiplicative envelope (openshorts's tuned
    rise 0.25 s / hold 1.3 s / fall 0.55 s)

Switch machine (clip-forge speaker.ts design): a challenger track must beat
the incumbent by RATIO_MARGIN for CONFIRM_FRAMES consecutive frames, with a
COOLDOWN_SEC lockout after each switch. During cross-talk (both speaking)
the margin becomes absolute and the camera may take the union box instead.
Diarization fusion (opensource-clipping's two-pass canonical-position): pass
one learns each diarized speaker's canonical screen position from the frames
where ASD is confident; pass two uses turn boundaries to place cuts even
where ASD wavers.
"""

from __future__ import annotations

from dataclasses import dataclass, field

import numpy as np

from ..vendor.clippyme.reframe_ops import build_smoothed_trajectory
from .asd import ASD_FPS, AsdAnalysis

RATIO_MARGIN = 1.5
CONFIRM_FRAMES = 2
COOLDOWN_SEC = 1.0
CROSSTALK_ABS_MARGIN = 0.8   # logit units, when both tracks are speaking
SPEAKING_THRESHOLD = 0.0     # logit > 0 = speaking (reference convention)
PUNCH_RISE = 0.25
PUNCH_HOLD = 1.3
PUNCH_FALL = 0.55
PUNCH_ZOOM = 1.12
SAVGOL_WINDOW_SEC = 0.8
SAVGOL_POLY = 2


@dataclass
class Punch:
    start: float   # seconds relative to clip start
    trigger: str   # 'laugh' | 'gasp' | 'scream' | 'energy'

    @property
    def end(self) -> float:
        return self.start + PUNCH_RISE + PUNCH_HOLD + PUNCH_FALL


@dataclass
class Trajectory:
    fps: int
    frames: list           # per frame [x, y, w, h] in source px (crop rect)
    cuts: list[int]        # frame indices where a hard cut lands
    punches: list[dict]
    meta: dict = field(default_factory=dict)


def active_speaker_timeline(analysis: AsdAnalysis, fps: int = ASD_FPS) -> list[int | None]:
    """Per-frame winning track index via the hysteresis switch machine."""
    n = analysis.frame_count
    if not analysis.tracks:
        return [None] * n

    score_grid = np.full((len(analysis.tracks), n), -1e9)
    for ti, track in enumerate(analysis.tracks):
        end = min(n, track.start + len(track.scores))
        score_grid[ti, track.start : end] = track.scores[: end - track.start]

    cooldown = round(COOLDOWN_SEC * fps)
    current: int | None = None
    confirm = 0
    challenger: int | None = None
    last_switch = -cooldown
    out: list[int | None] = []

    for f in range(n):
        scores = score_grid[:, f]
        present = scores > -1e8
        if not present.any():
            out.append(current if current is not None else None)
            confirm, challenger = 0, None
            continue
        best = int(np.argmax(np.where(present, scores, -1e9)))
        if current is None or not present[current]:
            current = best  # cold start / incumbent left frame: snap, no hysteresis
            out.append(current)
            confirm, challenger = 0, None
            last_switch = f
            continue

        cur_score = scores[current]
        best_score = scores[best]
        crosstalk = (
            best != current
            and cur_score > SPEAKING_THRESHOLD
            and best_score > SPEAKING_THRESHOLD
        )
        if best != current and f - last_switch >= cooldown:
            beats = (
                best_score - cur_score >= CROSSTALK_ABS_MARGIN
                if crosstalk
                else best_score > SPEAKING_THRESHOLD
                and (cur_score <= 0 or best_score >= cur_score * RATIO_MARGIN)
            )
            if beats:
                if challenger == best:
                    confirm += 1
                else:
                    challenger, confirm = best, 1
                if confirm >= CONFIRM_FRAMES:
                    current = best
                    last_switch = f
                    confirm, challenger = 0, None
            else:
                confirm, challenger = 0, None
        else:
            confirm, challenger = 0, None
        out.append(current)
    return out


def canonical_positions(
    analysis: AsdAnalysis,
    speaker_frames: list[int | None],
    turns: list[dict],
    clip_start: float,
    fps: int = ASD_FPS,
) -> dict[int, tuple[float, float]]:
    """Pass one of the diarization fusion: each diarized speaker's canonical
    (cx, cy) — the median position of the ASD-winning track during that
    speaker's turns (opensource-clipping render_camera_switch.py pattern)."""
    votes: dict[int, list[tuple[float, float]]] = {}
    for turn in turns:
        t0 = int((turn["start"] - clip_start) * fps)
        t1 = int((turn["end"] - clip_start) * fps)
        for f in range(max(0, t0), min(len(speaker_frames), t1)):
            ti = speaker_frames[f]
            if ti is None:
                continue
            track = analysis.tracks[ti]
            i = f - track.start
            if 0 <= i < len(track.centres):
                votes.setdefault(turn["speaker"], []).append(
                    (track.centres[i], track.centres_y[i])
                )
    return {
        spk: (float(np.median([p[0] for p in pts])), float(np.median([p[1] for p in pts])))
        for spk, pts in votes.items()
        if len(pts) >= fps // 2  # at least half a second of evidence
    }


def fuse_with_turns(
    speaker_frames: list[int | None],
    analysis: AsdAnalysis,
    canon: dict[int, tuple[float, float]],
    turns: list[dict],
    clip_start: float,
    fps: int = ASD_FPS,
) -> list[tuple[float, float] | None]:
    """Pass two: per-frame camera target (normalized coords). ASD-tracked
    positions where available; the diarized speaker's canonical position
    where ASD lost the plot but diarization knows who is talking."""
    n = len(speaker_frames)
    targets: list[tuple[float, float] | None] = [None] * n

    turn_at = np.full(n, -1, dtype=int)
    for turn in turns:
        t0 = max(0, int((turn["start"] - clip_start) * fps))
        t1 = min(n, int((turn["end"] - clip_start) * fps))
        turn_at[t0:t1] = turn["speaker"]

    for f in range(n):
        ti = speaker_frames[f]
        if ti is not None:
            track = analysis.tracks[ti]
            i = f - track.start
            if 0 <= i < len(track.centres):
                targets[f] = (track.centres[i], track.centres_y[i])
                continue
        spk = int(turn_at[f])
        if spk >= 0 and spk in canon:
            targets[f] = canon[spk]
    return targets


def switch_cut_frames(targets: list, threshold: float = 0.12) -> list[int]:
    """Frames where the target jumps far enough to read as a different person
    — these become hard cuts (virtual scene boundaries), so the smoother
    never pans across them."""
    cuts = []
    for f in range(1, len(targets)):
        a, b = targets[f - 1], targets[f]
        if a is not None and b is not None and abs(b[0] - a[0]) > threshold:
            cuts.append(f)
    return cuts


def punch_schedule(
    timeline: list[dict],
    energy_dynamics: np.ndarray,
    dynamics_grid: float,
    clip_start: float,
    clip_end: float,
    sensitivity: float = 1.0,
) -> list[Punch]:
    """Punch-ins from events (laugh/gasp/scream at their onset) plus vocal
    energy peaks, min 3 s apart, capped at one per 8 s of clip."""
    punches: list[Punch] = []
    for event in timeline:
        if event["type"] not in ("laugh", "gasp", "scream"):
            continue
        onset = event["start"]
        if clip_start + 1.0 <= onset <= clip_end - 2.0 and event.get("confidence", 0) >= 0.5 / sensitivity:
            punches.append(Punch(start=onset - clip_start, trigger=event["type"]))

    if len(energy_dynamics):
        a = int(clip_start / dynamics_grid)
        b = min(len(energy_dynamics), int(clip_end / dynamics_grid))
        window = energy_dynamics[a:b]
        if len(window):
            thresh = np.percentile(energy_dynamics, 100 - 4 * sensitivity)
            for i in np.where(window >= thresh)[0]:
                t = i * dynamics_grid
                if 1.0 <= t <= (clip_end - clip_start) - 2.0:
                    punches.append(Punch(start=float(t), trigger="energy"))

    punches.sort(key=lambda p: p.start)
    spaced: list[Punch] = []
    for p in punches:
        if not spaced or p.start - spaced[-1].start >= 3.0:
            spaced.append(p)
    cap = max(1, int((clip_end - clip_start) / 8))
    return spaced[:cap]


def punch_envelope(n_frames: int, fps: int, punches: list[Punch]) -> np.ndarray:
    """Multiplicative zoom envelope: smoothstep rise, hold, smoothstep fall
    (openshorts punch_in.py's tuned asymmetric shape)."""
    env = np.ones(n_frames)
    for p in punches:
        f0 = int(p.start * fps)
        rise_f = max(1, int(PUNCH_RISE * fps))
        hold_f = int(PUNCH_HOLD * fps)
        fall_f = max(1, int(PUNCH_FALL * fps))
        for i in range(rise_f + hold_f + fall_f):
            f = f0 + i
            if not 0 <= f < n_frames:
                continue
            if i < rise_f:
                t = i / rise_f
                z = 1 + (PUNCH_ZOOM - 1) * (t * t * (3 - 2 * t))
            elif i < rise_f + hold_f:
                z = PUNCH_ZOOM
            else:
                t = (i - rise_f - hold_f) / fall_f
                z = PUNCH_ZOOM - (PUNCH_ZOOM - 1) * (t * t * (3 - 2 * t))
            env[f] = max(env[f], z)
    return env


def build_trajectory(
    analysis: AsdAnalysis,
    turns: list[dict],
    timeline: list[dict],
    energy_dynamics: np.ndarray,
    dynamics_grid: float,
    clip_start: float,
    clip_end: float,
    src_w: int,
    src_h: int,
    camera: "object",
) -> Trajectory:
    """The full director pass for one clip → per-frame crop rects."""
    fps = ASD_FPS
    n = analysis.frame_count or int((clip_end - clip_start) * fps)

    speaker_frames = active_speaker_timeline(analysis, fps)
    canon = canonical_positions(analysis, speaker_frames, turns, clip_start, fps)
    targets = fuse_with_turns(speaker_frames, analysis, canon, turns, clip_start, fps)

    # Fallback for faceless spans: centre crop.
    filled = [(t if t is not None else (0.5, 0.5)) for t in targets] or [(0.5, 0.5)] * n

    cut_mode = getattr(getattr(camera, "camera", camera), "speaker_change", "cut") == "cut"
    switch_cuts = switch_cut_frames(filled) if cut_mode else []
    shot_cuts = list(analysis.scene_cuts)
    all_cuts = sorted(set(switch_cuts + shot_cuts))

    # Virtual scene ids: a new id after every cut → the smoother never pans
    # across a cut (clippyme's per-scene segmentation contract).
    scene_ids = np.zeros(n, dtype=int)
    for ci, f in enumerate(all_cuts):
        scene_ids[f:] = ci + 1

    raw = [(t[0] * src_w, t[1] * src_h, 1.0) for t in filled[:n]]
    window = max(5, int(SAVGOL_WINDOW_SEC * fps)) | 1
    smoothed = build_smoothed_trajectory(
        raw, list(scene_ids), window, SAVGOL_POLY,
        float(src_w), float(src_h),
        min_zoom=1.0, max_zoom=1.6,
        method="savgol",
        stationary_threshold=0.15,   # AutoFlip tripod lock on near-static scenes
        lock_zoom=getattr(getattr(camera, "camera", camera), "zoom_lock_per_scene", True),
    )

    settings = getattr(camera, "camera", camera)
    punches = (
        punch_schedule(
            timeline, energy_dynamics, dynamics_grid, clip_start, clip_end,
            sensitivity=getattr(settings, "punch_in_sensitivity", 1.0),
        )
        if getattr(settings, "punch_in", True)
        else []
    )
    env = punch_envelope(n, fps, punches)

    # Crop geometry: full-height 9:16 window, zoom tightens it.
    base_h = float(src_h)
    base_w = base_h * 9.0 / 16.0
    if base_w > src_w:  # narrower-than-9:16 source: pillar-fit width instead
        base_w = float(src_w)
        base_h = base_w * 16.0 / 9.0
    frames = []
    for f in range(n):
        cx, cy, zoom = smoothed[f] if smoothed[f] is not None else (src_w / 2, src_h / 2, 1.0)
        z = zoom * env[f]
        w = base_w / z
        h = base_h / z
        x = min(max(0.0, cx - w / 2), src_w - w)
        y = min(max(0.0, cy - h / 2), src_h - h)
        frames.append([round(x, 2), round(y, 2), round(w, 2), round(h, 2)])

    return Trajectory(
        fps=fps,
        frames=frames,
        cuts=all_cuts,
        punches=[{"start": round(p.start, 3), "end": round(p.end, 3), "trigger": p.trigger} for p in punches],
        meta={
            "tracks": len(analysis.tracks),
            "speakers_canonical": {str(k): [round(v[0], 3), round(v[1], 3)] for k, v in canon.items()},
            "switch_cuts": len(switch_cuts),
            "shot_cuts": len(shot_cuts),
        },
    )
