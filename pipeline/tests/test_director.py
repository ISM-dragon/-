"""Director + switch-machine tests — pure logic over synthetic ASD tracks."""

import numpy as np

from publikclip_pipeline.camera import director
from publikclip_pipeline.camera.asd import AsdAnalysis, ScoredTrack
from publikclip_pipeline.camera.tracks import median_filter


def _two_speaker_analysis(n=250):
    """Track 0 (left, x=0.3) speaks frames 0-99; track 1 (right, x=0.7)
    speaks frames 100-249. Logits: +2 while speaking, -2 while silent."""
    t0 = ScoredTrack(
        start=0,
        centres=[0.3] * n,
        centres_y=[0.5] * n,
        areas=[0.04] * n,
        scores=[2.0] * 100 + [-2.0] * (n - 100),
    )
    t1 = ScoredTrack(
        start=0,
        centres=[0.7] * n,
        centres_y=[0.5] * n,
        areas=[0.04] * n,
        scores=[-2.0] * 100 + [2.0] * (n - 100),
    )
    return AsdAnalysis(tracks=[t0, t1], frame_count=n, scene_cuts=[], fps=25)


def test_switch_machine_switches_once_with_hysteresis():
    analysis = _two_speaker_analysis()
    timeline = director.active_speaker_timeline(analysis)
    assert timeline[50] == 0
    assert timeline[200] == 1
    switches = sum(1 for a, b in zip(timeline, timeline[1:]) if a != b)
    assert switches == 1  # exactly one switch, no flapping


def test_switch_needs_confirm_frames():
    analysis = _two_speaker_analysis()
    # one-frame glitch: track 1 spikes at frame 50 only
    analysis.tracks[1].scores[50] = 5.0
    timeline = director.active_speaker_timeline(analysis)
    assert timeline[51] == 0  # single-frame spike must NOT switch the camera


def test_cooldown_blocks_rapid_switchback():
    n = 250
    analysis = _two_speaker_analysis(n)
    # speaker 1 takes over at 100 but speaker 0 interjects 8 frames later
    analysis.tracks[0].scores[108:112] = [5.0] * 4
    timeline = director.active_speaker_timeline(analysis)
    # inside the 25-frame cooldown the interjection is ignored
    assert timeline[110] == 1


def test_cut_frames_from_target_jumps():
    targets = [(0.3, 0.5)] * 100 + [(0.7, 0.5)] * 100
    cuts = director.switch_cut_frames(targets)
    assert cuts == [100]


def test_trajectory_hard_cut_no_pan():
    """With cut mode, the crop x must JUMP at the switch, not glide."""
    analysis = _two_speaker_analysis()

    class Cam:
        speaker_change = "cut"
        punch_in = False
        punch_in_sensitivity = 1.0
        zoom_lock_per_scene = True

    traj = director.build_trajectory(
        analysis, [], [], np.zeros(0), 0.1, 0.0, 10.0, 1920, 1080, Cam()
    )
    xs = [f[0] for f in traj.frames]
    # find the largest single-frame move — it should be a jump of roughly
    # (0.7-0.3)*1920 = 768 px, i.e. a cut, not spread over many frames
    deltas = np.abs(np.diff(xs))
    assert deltas.max() > 300
    big_moves = (deltas > 50).sum()
    assert big_moves <= 2  # the cut itself, not a pan ramp


def test_punch_envelope_shape():
    punches = [director.Punch(start=1.0, trigger="laugh")]
    env = director.punch_envelope(200, 25, punches)
    assert env[20] == 1.0                      # before
    assert abs(env[int((1.0 + 0.3) * 25)] - director.PUNCH_ZOOM) < 0.01  # hold
    assert env[-1] == 1.0                      # released
    assert np.all(env >= 1.0)


def test_punch_schedule_spacing_and_cap():
    timeline = [
        {"type": "laugh", "start": 11.0, "end": 12.0, "confidence": 0.9},
        {"type": "laugh", "start": 12.5, "end": 13.0, "confidence": 0.9},  # too close
        {"type": "gasp", "start": 20.0, "end": 21.0, "confidence": 0.8},
    ]
    punches = director.punch_schedule(timeline, np.zeros(0), 0.1, 10.0, 50.0)
    starts = [p.start for p in punches]
    assert 1.0 in starts and 10.0 in starts
    assert 2.5 not in starts


def test_crop_stays_in_bounds():
    analysis = _two_speaker_analysis()
    # park a speaker at the extreme left edge
    analysis.tracks[0].centres = [0.02] * 250

    class Cam:
        speaker_change = "cut"
        punch_in = True
        punch_in_sensitivity = 1.0
        zoom_lock_per_scene = True

    traj = director.build_trajectory(
        analysis, [], [], np.zeros(0), 0.1, 0.0, 10.0, 1920, 1080, Cam()
    )
    for x, y, w, h in traj.frames:
        assert x >= 0 and y >= 0
        assert x + w <= 1920 + 0.01
        assert y + h <= 1080 + 0.01


def test_median_filter_suppresses_interior_spikes():
    # A single-frame detection glitch must not survive the filter at its own
    # index (edge behavior matches upstream: upper median on even windows).
    out = median_filter([1.0, 1.0, 100.0, 1.0, 1.0], 3)
    assert out[2] == 1.0
    assert median_filter([5.0], 3) == [5.0]
