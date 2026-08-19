"""Versioned cross-validation constants (decision #13: auto-apply, loudly).

The four invented multipliers start as research defaults (version 1). Once
the Instagram loop has enough real outcomes, the auto-fit appends a new
version — it never edits history, and every score records the version that
produced it, so old clips are never silently rescored. The changelog this
table forms is user-visible in the app.
"""

from __future__ import annotations

import json
import sqlite3
import time

from .. import config

DEFAULTS: dict[str, float] = {
    "funny_no_laugh": 0.55,
    "funny_corroborated": 1.25,
    "shock_no_arousal": 0.6,
    "heatmap_boost": 1.15,
}

# Bounds for the auto-fit: a weird account's data can nudge the constants,
# never invert their meaning (a discount stays a discount).
CLAMPS: dict[str, tuple[float, float]] = {
    "funny_no_laugh": (0.3, 0.8),
    "funny_corroborated": (1.0, 1.5),
    "shock_no_arousal": (0.4, 0.8),
    "heatmap_boost": (1.0, 1.3),
}

SCHEMA = """
CREATE TABLE IF NOT EXISTS scoring_config (
    version INTEGER PRIMARY KEY AUTOINCREMENT,
    constants_json TEXT NOT NULL,
    fitted_from_n INTEGER,
    spearman_rho REAL,
    pairwise_acc REAL,
    note TEXT,
    created_at REAL NOT NULL
);
"""


def _connect() -> sqlite3.Connection:
    config.ensure_home()
    conn = sqlite3.connect(config.db_path(), timeout=30.0)
    conn.row_factory = sqlite3.Row
    conn.executescript(SCHEMA)
    return conn


def active() -> dict:
    """Latest config, or the version-1 defaults when nothing was ever fitted."""
    try:
        with _connect() as conn:
            row = conn.execute(
                "SELECT * FROM scoring_config ORDER BY version DESC LIMIT 1"
            ).fetchone()
    except sqlite3.Error:
        row = None
    if row is None:
        return {"version": 1, "constants": dict(DEFAULTS), "note": "research defaults"}
    constants = dict(DEFAULTS)
    constants.update(json.loads(row["constants_json"]))
    return {
        "version": row["version"] + 1,  # stored rows start after the implicit v1
        "constants": constants,
        "fitted_from_n": row["fitted_from_n"],
        "spearman_rho": row["spearman_rho"],
        "pairwise_acc": row["pairwise_acc"],
        "note": row["note"],
        "created_at": row["created_at"],
    }


def history() -> list[dict]:
    """Full changelog, oldest first, including the implicit version 1."""
    out = [{"version": 1, "constants": dict(DEFAULTS), "note": "research defaults"}]
    try:
        with _connect() as conn:
            rows = conn.execute("SELECT * FROM scoring_config ORDER BY version ASC").fetchall()
    except sqlite3.Error:
        return out
    for row in rows:
        constants = dict(DEFAULTS)
        constants.update(json.loads(row["constants_json"]))
        out.append(
            {
                "version": row["version"] + 1,
                "constants": constants,
                "fitted_from_n": row["fitted_from_n"],
                "spearman_rho": row["spearman_rho"],
                "pairwise_acc": row["pairwise_acc"],
                "note": row["note"],
                "created_at": row["created_at"],
            }
        )
    return out


def clamp(constants: dict[str, float]) -> dict[str, float]:
    out = {}
    for key, (lo, hi) in CLAMPS.items():
        out[key] = min(hi, max(lo, float(constants.get(key, DEFAULTS[key]))))
    return out


def save(
    constants: dict[str, float],
    *,
    fitted_from_n: int,
    spearman_rho: float | None,
    pairwise_acc: float | None,
    note: str,
) -> int:
    """Append a new version; returns its user-facing version number."""
    with _connect() as conn:
        cur = conn.execute(
            "INSERT INTO scoring_config"
            " (constants_json, fitted_from_n, spearman_rho, pairwise_acc, note, created_at)"
            " VALUES (?, ?, ?, ?, ?, ?)",
            (
                json.dumps(clamp(constants)),
                fitted_from_n,
                spearman_rho,
                pairwise_acc,
                note,
                time.time(),
            ),
        )
        return cur.lastrowid + 1
