from __future__ import annotations

import json
import math
import time
from pathlib import Path
from typing import Any

DIMENSIONS = (
    "hook",
    "emotion",
    "curiosity",
    "novelty",
    "clarity",
    "value",
    "story",
    "quotability",
    "visual_interest",
    "ending_strength",
    "duration",
    "topic",
)
EVENT_SIGNALS = {
    "LIKE": 1.0,
    "DISLIKE": -1.0,
    "SELECT": 0.45,
    "REJECT": -0.55,
    "EDIT": 0.15,
    "EXPORT": 0.35,
    "PUBLISH": 0.5,
}
REASON_DIMENSIONS = {
    "strong_hook": "hook",
    "strong_story": "story",
    "high_emotion": "emotion",
    "high_value": "value",
    "funny": "emotion",
    "surprising": "novelty",
    "too_slow": "duration",
    "too_generic": "novelty",
    "needs_context": "clarity",
    "weak_ending": "ending_strength",
    "bad_crop": "visual_interest",
    "bad_captions": "clarity",
    "too_long": "duration",
}


def empty_profile() -> dict[str, dict[str, float | int]]:
    return {dimension: {"weight": 0.0, "confidence": 0.0, "sample_count": 0} for dimension in DIMENSIONS}


def empty_state() -> dict[str, Any]:
    return {"version": 1, "events": [], "profile": empty_profile()}


def load_state(path: Path) -> dict[str, Any]:
    try:
        payload = json.loads(path.read_text()) if path.exists() else empty_state()
        if not isinstance(payload, dict):
            return empty_state()
        payload.setdefault("events", [])
        payload.setdefault("profile", empty_profile())
        for dimension in DIMENSIONS:
            payload["profile"].setdefault(dimension, {"weight": 0.0, "confidence": 0.0, "sample_count": 0})
        return payload
    except (OSError, json.JSONDecodeError):
        return empty_state()


def save_state(path: Path, state: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(".tmp")
    temporary.write_text(json.dumps(state, ensure_ascii=False, indent=2))
    temporary.replace(path)


def _feature_value(features: dict[str, Any], dimension: str) -> float:
    value = features.get(dimension, 0.0)
    try:
        return max(-1.0, min(1.0, float(value)))
    except (TypeError, ValueError):
        return 0.0


def record_event(path: Path, event: dict[str, Any]) -> dict[str, Any]:
    event_type = str(event.get("event_type", "")).upper()
    if event_type not in EVENT_SIGNALS:
        raise ValueError("Unsupported personal preference event")
    state = load_state(path)
    reason = str(event.get("reason", "")).strip().lower()
    dimensions = [REASON_DIMENSIONS[reason]] if reason in REASON_DIMENSIONS else list(DIMENSIONS)
    features = event.get("features") if isinstance(event.get("features"), dict) else {}
    signal = EVENT_SIGNALS[event_type]
    for dimension in dimensions:
        observed = _feature_value(features, dimension)
        if observed == 0.0:
            observed = 1.0
        profile = state["profile"][dimension]
        count = int(profile.get("sample_count", 0))
        learning_rate = 1.0 / min(20, count + 1)
        profile["weight"] = round(max(-1.0, min(1.0, float(profile.get("weight", 0.0)) + signal * observed * learning_rate * 0.12)), 6)
        profile["sample_count"] = count + 1
        profile["confidence"] = round(min(1.0, (count + 1) / 20.0), 6)
    stored = {
        "id": str(event.get("id") or f"pref_{int(time.time() * 1000)}"),
        "clip_id": str(event.get("clip_id", "")),
        "candidate_id": str(event.get("candidate_id", "")),
        "job_id": str(event.get("job_id", "")),
        "event_type": event_type,
        "reason": reason,
        "timestamp": event.get("timestamp") or int(time.time()),
        "features": features,
    }
    state["events"].append(stored)
    state["events"] = state["events"][-500:]
    save_state(path, state)
    return {"event": stored, "profile": state["profile"]}


def personal_adjustment(profile: dict[str, Any], features: dict[str, Any]) -> float:
    total = 0.0
    active = 0
    for dimension in DIMENSIONS:
        value = _feature_value(features, dimension)
        if value == 0:
            continue
        weight = float(profile.get(dimension, {}).get("weight", 0.0))
        total += weight * value
        active += 1
    return max(-20.0, min(20.0, total / max(1, active) * 20.0))


def _topic_similarity(left: Any, right: Any) -> float:
    a = {word.lower() for word in str(left or "").split() if len(word) > 2}
    b = {word.lower() for word in str(right or "").split() if len(word) > 2}
    if not a or not b:
        return 0.0
    return len(a & b) / max(1, len(a | b))


def _duration_similarity(left: Any, right: Any) -> float:
    try:
        a, b = float(left), float(right)
    except (TypeError, ValueError):
        return 0.0
    return max(0.0, 1.0 - abs(a - b) / max(a, b, 1.0))


def similarity_recommendations(profile: dict[str, Any], selected: dict[str, Any], candidates: list[dict[str, Any]], limit: int) -> list[dict[str, Any]]:
    selected_features = selected.get("features", {}) if isinstance(selected.get("features"), dict) else {}
    results = []
    for candidate in candidates:
        features = candidate.get("features", {}) if isinstance(candidate.get("features"), dict) else {}
        topic = _topic_similarity(selected.get("topic"), candidate.get("topic"))
        duration = _duration_similarity(selected.get("duration_sec"), candidate.get("duration_sec"))
        style = 0.0
        style_count = 0
        for dimension in ("hook", "emotion", "story", "clarity", "visual_interest", "ending_strength"):
            if dimension in selected_features and dimension in features:
                style += 1.0 - abs(_feature_value(selected_features, dimension) - _feature_value(features, dimension)) / 2.0
                style_count += 1
        style /= max(1, style_count)
        personal = personal_adjustment(profile, features)
        score = round((topic * 0.35 + duration * 0.2 + style * 0.3 + max(0.0, (float(candidate.get("score", 0)) / 100.0)) * 0.15) * 100 + personal, 2)
        reasons = []
        if topic >= 0.25: reasons.append("same topic")
        if duration >= 0.7: reasons.append("similar duration")
        if style >= 0.65: reasons.append("similar hook/story structure")
        if personal > 1: reasons.append(f"personal preference +{round(personal)}")
        results.append({**candidate, "similarity_score": score, "explanation": " + ".join(reasons) or "similar evidence available"})
    return sorted(results, key=lambda item: item["similarity_score"], reverse=True)[: max(1, min(limit, 20))]


def better_recommendations(profile: dict[str, Any], selected: dict[str, Any], candidates: list[dict[str, Any]], threshold: float) -> list[dict[str, Any]]:
    current_score = float(selected.get("score", 0))
    selected_features = selected.get("features", {}) if isinstance(selected.get("features"), dict) else {}
    results = []
    for candidate in candidates:
        features = candidate.get("features", {}) if isinstance(candidate.get("features"), dict) else {}
        base = float(candidate.get("score", 0))
        intent = _topic_similarity(selected.get("topic"), candidate.get("topic"))
        completeness = max(0.0, _feature_value(features, "clarity") - _feature_value(selected_features, "clarity")) * 3
        completeness += max(0.0, _feature_value(features, "ending_strength") - _feature_value(selected_features, "ending_strength")) * 3
        hook_delta = max(0.0, _feature_value(features, "hook") - _feature_value(selected_features, "hook")) * 3
        personal = personal_adjustment(profile, features)
        final_score = base + intent * 2 + completeness + hook_delta + personal
        if final_score - current_score < threshold:
            continue
        differences = []
        if hook_delta > 1: differences.append("stronger hook")
        if completeness > 1: differences.append("more complete ending")
        if intent > 0.35: differences.append("same intent/topic")
        results.append({**candidate, "better_score": round(final_score, 2), "score_delta": round(final_score - current_score, 2), "explanation": " + ".join(differences) or "higher verified score"})
    return sorted(results, key=lambda item: item["score_delta"], reverse=True)[:20]
