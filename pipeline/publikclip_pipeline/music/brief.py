"""Music-type suggestion (decision #6: suggestion only, no tracks).

One structured LLM call per selected clip emitting the DIS'25-validated
5-field brief (genre/instruments/mood/theme/energy) extended with bpm_range,
detected_events, and duck_intensity. A deterministic mood prior computed
from the event timeline + arousal is fed INTO the prompt — the Adobe/CMU
user study found editable structured fields beat raw prompts, and the prior
keeps suggestions stable across re-runs.

The shouting rule (ARCHITECTURE-DRAFT stage 9): shout/scream events alone
are ambiguous — triumphant needs high arousal AND laughter/cheer nearby,
distressed is high arousal without them. The prior encodes that so the LLM
doesn't guess."""

from __future__ import annotations

from typing import Any

MUSIC_SCHEMA: dict[str, Any] = {
    "type": "object",
    "properties": {
        "genre": {"type": "string", "description": "e.g. lo-fi hip hop, trap, cinematic, indie folk"},
        "instruments": {"type": "array", "items": {"type": "string"}},
        "mood": {"type": "string", "description": "e.g. playful, tense, triumphant, somber, chaotic"},
        "theme": {"type": "string", "description": "what the music should say about the moment"},
        "energy": {"type": "string", "enum": ["low", "medium", "high"]},
        "bpm_range": {"type": "string", "description": "e.g. 80-95"},
        "duck_intensity": {
            "type": "string",
            "enum": ["light", "medium", "heavy"],
            "description": "how hard the music should duck under speech",
        },
        "alternatives": {
            "type": "array",
            "items": {"type": "object", "properties": {
                "genre": {"type": "string"}, "mood": {"type": "string"}, "bpm_range": {"type": "string"},
            }, "required": ["genre", "mood", "bpm_range"]},
            "description": "2 alternative directions",
        },
    },
    "required": ["genre", "instruments", "mood", "theme", "energy", "bpm_range", "duck_intensity", "alternatives"],
}


def mood_prior(events_in_window: list[dict], arousal_pct: float) -> str:
    """Deterministic prior from local signals; becomes part of the prompt."""
    types = {e["type"] for e in events_in_window}
    has_laugh = "laugh" in types
    has_cheer = bool(types & {"cheer", "applause"})
    has_shout = bool(types & {"shout", "scream"})

    if has_laugh and arousal_pct >= 0.5:
        return "comedic/playful (laughter present, lively delivery)"
    if has_laugh:
        return "light/amused (laughter present, relaxed delivery)"
    if has_shout and (has_cheer or has_laugh):
        return "triumphant/hype (shouting WITH celebration signals)"
    if has_shout and arousal_pct >= 0.6:
        return "tense/dramatic (shouting without celebration — treat as intense, not fun)"
    if arousal_pct >= 0.7:
        return "energetic/urgent (highly aroused delivery)"
    if arousal_pct <= 0.25:
        return "calm/reflective (flat, measured delivery)"
    return "neutral/conversational"


def music_prompt(summary: str, transcript_excerpt: str, prior: str, events_desc: str) -> str:
    return (
        "Suggest background music for a short-form vertical clip. Emit the "
        "structured brief only — the user edits these fields, so keep each "
        "field short and concrete.\n\n"
        f"What happens: {summary}\n"
        f"Transcript excerpt: {transcript_excerpt[:600]}\n"
        f"Audio events: {events_desc}\n"
        f"Signal-derived mood prior (trust this over your own read of the text): {prior}\n\n"
        "Rules: genre must be a real, searchable music genre. bpm_range must "
        "suit the energy. duck_intensity heavy for dense dialogue, light for "
        "sparse. Provide exactly 2 alternatives with a different genre each."
    )
