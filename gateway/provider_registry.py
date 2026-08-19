"""Provider registry and health normalization for ISM AI routing."""

from __future__ import annotations

import json
import secrets
import sqlite3
import time
from contextlib import closing
from dataclasses import asdict, dataclass, field
from datetime import datetime, timezone
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


HEALTH_STATES = {
    "READY",
    "NOT_CONFIGURED",
    "NETWORK_ERROR",
    "AUTH_ERROR",
    "MODEL_ERROR",
    "CAPABILITY_ERROR",
    "RATE_LIMITED",
    "TIMEOUT",
    "UNKNOWN_ERROR",
}


@dataclass(frozen=True)
class ModelDefinition:
    id: str
    provider_id: str
    model_id: str
    display_name: str
    capabilities: tuple[str, ...] = ()
    context_window: int | None = None
    supports_structured_output: bool = False
    supports_vision: bool = False
    enabled: bool = True


@dataclass(frozen=True)
class ProviderDefinition:
    id: str
    name: str
    type: str
    enabled: bool
    base_url: str | None
    credential_ref: str | None
    capabilities: tuple[str, ...] = ()
    models: tuple[ModelDefinition, ...] = ()
    created_at: str = ""
    updated_at: str = ""


@dataclass
class ProviderHealth:
    provider_id: str
    state: str
    configured: bool
    reachable: bool
    authenticated: bool | None
    selected_model_available: bool | None
    required_capabilities: list[str] = field(default_factory=list)
    checked_at: str = ""
    latency_ms: float | None = None
    error: str | None = None

    def as_dict(self) -> dict[str, Any]:
        return asdict(self)


BUILT_IN_PROVIDERS: tuple[ProviderDefinition, ...] = (
    ProviderDefinition(
        id="gemini",
        name="Google Gemini",
        type="gemini",
        enabled=True,
        base_url="https://generativelanguage.googleapis.com/v1beta",
        credential_ref="GEMINI_API_KEY",
        capabilities=("json", "structured_output", "vision"),
        models=(ModelDefinition("gemini-flash-latest", "gemini", "gemini-flash-latest", "Gemini Flash Latest", ("json", "structured_output", "vision"), supports_structured_output=True, supports_vision=True),),
    ),
    ProviderDefinition(
        id="openai",
        name="OpenAI",
        type="openai",
        enabled=True,
        base_url="https://api.openai.com/v1",
        credential_ref="OPENAI_API_KEY",
        capabilities=("json", "structured_output", "vision"),
        models=(),
    ),
    ProviderDefinition(
        id="anthropic",
        name="Anthropic",
        type="anthropic",
        enabled=True,
        base_url="https://api.anthropic.com/v1",
        credential_ref="ANTHROPIC_API_KEY",
        capabilities=("json", "vision"),
        models=(),
    ),
    ProviderDefinition(
        id="openrouter",
        name="OpenRouter",
        type="openai_compatible",
        enabled=True,
        base_url="https://openrouter.ai/api/v1",
        credential_ref="OPENROUTER_API_KEY",
        capabilities=("json", "vision"),
        models=(),
    ),
    ProviderDefinition(
        id="ollama",
        name="Ollama",
        type="ollama",
        enabled=True,
        base_url="http://127.0.0.1:11434",
        credential_ref=None,
        capabilities=("json",),
        models=(),
    ),
)


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat()


def init_registry_schema(connection: sqlite3.Connection) -> None:
    connection.executescript(
        """
        CREATE TABLE IF NOT EXISTS ai_providers (
            id TEXT PRIMARY KEY,
            name TEXT NOT NULL,
            type TEXT NOT NULL,
            enabled INTEGER NOT NULL DEFAULT 1,
            base_url TEXT,
            credential_ref TEXT,
            capabilities_json TEXT NOT NULL DEFAULT '[]',
            created_at TEXT NOT NULL,
            updated_at TEXT NOT NULL
        );
        CREATE TABLE IF NOT EXISTS ai_models (
            id TEXT PRIMARY KEY,
            provider_id TEXT NOT NULL,
            model_id TEXT NOT NULL,
            display_name TEXT NOT NULL,
            capabilities_json TEXT NOT NULL DEFAULT '[]',
            context_window INTEGER,
            supports_structured_output INTEGER NOT NULL DEFAULT 0,
            supports_vision INTEGER NOT NULL DEFAULT 0,
            enabled INTEGER NOT NULL DEFAULT 1,
            FOREIGN KEY(provider_id) REFERENCES ai_providers(id) ON DELETE CASCADE,
            UNIQUE(provider_id, model_id)
        );
        CREATE TABLE IF NOT EXISTS ai_provider_health (
            provider_id TEXT PRIMARY KEY,
            state TEXT NOT NULL,
            configured INTEGER NOT NULL DEFAULT 0,
            reachable INTEGER NOT NULL DEFAULT 0,
            authenticated INTEGER,
            selected_model_available INTEGER,
            required_capabilities_json TEXT NOT NULL DEFAULT '[]',
            checked_at TEXT NOT NULL,
            latency_ms REAL,
            error TEXT,
            FOREIGN KEY(provider_id) REFERENCES ai_providers(id) ON DELETE CASCADE
        );
        """
    )
    for provider in BUILT_IN_PROVIDERS:
        register_provider(connection, provider, allow_existing=True)


def _row_to_provider(connection: sqlite3.Connection, row: sqlite3.Row) -> ProviderDefinition:
    models = tuple(_row_to_model(row) for row in connection.execute("SELECT * FROM ai_models WHERE provider_id=? ORDER BY model_id", (row["id"],)).fetchall())
    return ProviderDefinition(
        id=row["id"], name=row["name"], type=row["type"], enabled=bool(row["enabled"]),
        base_url=row["base_url"], credential_ref=row["credential_ref"],
        capabilities=tuple(json.loads(row["capabilities_json"] or "[]")), models=models,
        created_at=row["created_at"], updated_at=row["updated_at"],
    )


def _row_to_model(row: sqlite3.Row) -> ModelDefinition:
    return ModelDefinition(
        id=row["id"], provider_id=row["provider_id"], model_id=row["model_id"], display_name=row["display_name"],
        capabilities=tuple(json.loads(row["capabilities_json"] or "[]")), context_window=row["context_window"],
        supports_structured_output=bool(row["supports_structured_output"]), supports_vision=bool(row["supports_vision"]), enabled=bool(row["enabled"]),
    )


def register_provider(connection: sqlite3.Connection, provider: ProviderDefinition, *, allow_existing: bool = False) -> ProviderDefinition:
    now = utc_now()
    existing = connection.execute("SELECT id FROM ai_providers WHERE id=?", (provider.id,)).fetchone()
    if existing and not allow_existing:
        raise ValueError(f"Provider ID already exists: {provider.id}")
    if existing:
        connection.execute(
            "UPDATE ai_providers SET name=?, type=?, enabled=?, base_url=?, credential_ref=?, capabilities_json=?, updated_at=? WHERE id=?",
            (provider.name, provider.type, int(provider.enabled), provider.base_url, provider.credential_ref, json.dumps(list(provider.capabilities)), now, provider.id),
        )
        connection.execute("DELETE FROM ai_models WHERE provider_id=?", (provider.id,))
    else:
        connection.execute(
            "INSERT INTO ai_providers (id,name,type,enabled,base_url,credential_ref,capabilities_json,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?)",
            (provider.id, provider.name, provider.type, int(provider.enabled), provider.base_url, provider.credential_ref, json.dumps(list(provider.capabilities)), provider.created_at or now, now),
        )
    for model in provider.models:
        connection.execute(
            "INSERT INTO ai_models (id,provider_id,model_id,display_name,capabilities_json,context_window,supports_structured_output,supports_vision,enabled) VALUES (?,?,?,?,?,?,?,?,?)",
            (model.id, provider.id, model.model_id, model.display_name, json.dumps(list(model.capabilities)), model.context_window, int(model.supports_structured_output), int(model.supports_vision), int(model.enabled)),
        )
    connection.commit()
    row = connection.execute("SELECT * FROM ai_providers WHERE id=?", (provider.id,)).fetchone()
    return _row_to_provider(connection, row)


def get_provider(connection: sqlite3.Connection, provider_id: str) -> ProviderDefinition | None:
    row = connection.execute("SELECT * FROM ai_providers WHERE id=?", (provider_id,)).fetchone()
    return _row_to_provider(connection, row) if row else None


def list_providers(connection: sqlite3.Connection, *, enabled_only: bool = False) -> list[ProviderDefinition]:
    query = "SELECT * FROM ai_providers"
    if enabled_only:
        query += " WHERE enabled=1"
    query += " ORDER BY name"
    return [_row_to_provider(connection, row) for row in connection.execute(query).fetchall()]


def update_provider(connection: sqlite3.Connection, provider_id: str, **changes: Any) -> ProviderDefinition:
    provider = get_provider(connection, provider_id)
    if not provider:
        raise KeyError(provider_id)
    allowed = {"name", "type", "base_url", "credential_ref", "capabilities", "enabled"}
    unknown = set(changes) - allowed
    if unknown:
        raise ValueError(f"Unsupported provider fields: {', '.join(sorted(unknown))}")
    values: dict[str, Any] = {}
    for key, value in changes.items():
        values[key] = json.dumps(value) if key == "capabilities" else int(value) if key == "enabled" else value
    values["updated_at"] = utc_now()
    assignments = ", ".join(f"{key if key != 'capabilities' else 'capabilities_json'}=?" for key in values)
    connection.execute(f"UPDATE ai_providers SET {assignments} WHERE id=?", (*values.values(), provider_id))
    connection.commit()
    result = get_provider(connection, provider_id)
    assert result is not None
    return result


def remove_provider(connection: sqlite3.Connection, provider_id: str) -> None:
    if provider_id in {item.id for item in BUILT_IN_PROVIDERS}:
        raise ValueError("Built-in providers cannot be removed; disable them instead.")
    cursor = connection.execute("DELETE FROM ai_providers WHERE id=?", (provider_id,))
    connection.commit()
    if cursor.rowcount == 0:
        raise KeyError(provider_id)


def set_provider_enabled(connection: sqlite3.Connection, provider_id: str, enabled: bool) -> ProviderDefinition:
    return update_provider(connection, provider_id, enabled=enabled)


def get_model(connection: sqlite3.Connection, model_id: str) -> ModelDefinition | None:
    row = connection.execute("SELECT * FROM ai_models WHERE id=? OR model_id=? LIMIT 1", (model_id, model_id)).fetchone()
    return _row_to_model(row) if row else None


def list_models(connection: sqlite3.Connection, provider_id: str | None = None) -> list[ModelDefinition]:
    if provider_id:
        rows = connection.execute("SELECT * FROM ai_models WHERE provider_id=? ORDER BY model_id", (provider_id,)).fetchall()
    else:
        rows = connection.execute("SELECT * FROM ai_models ORDER BY provider_id, model_id").fetchall()
    return [_row_to_model(row) for row in rows]


def provider_public_dict(provider: ProviderDefinition) -> dict[str, Any]:
    return {
        "id": provider.id,
        "name": provider.name,
        "type": provider.type,
        "enabled": provider.enabled,
        "base_url": provider.base_url,
        "credential_ref": provider.credential_ref,
        "capabilities": list(provider.capabilities),
        "models": [asdict(model) for model in provider.models],
        "created_at": provider.created_at,
        "updated_at": provider.updated_at,
    }


def health_public_dict(health: ProviderHealth) -> dict[str, Any]:
    return health.as_dict()


def check_provider_health(provider: ProviderDefinition, *, configured: bool, required_capabilities: list[str] | None = None, timeout: float = 3.0) -> ProviderHealth:
    required = required_capabilities or []
    checked = utc_now()
    if not provider.enabled:
        return ProviderHealth(provider.id, "NOT_CONFIGURED", configured, False, None, None, required, checked, error="Provider disabled")
    if not configured:
        return ProviderHealth(provider.id, "NOT_CONFIGURED", False, False, None, None, required, checked, error="Credential not configured")
    if provider.type == "gemini":
        # The detailed Gemini probe lives in Gateway; registry health is intentionally
        # conservative until that probe is attached to a credential-aware adapter.
        return ProviderHealth(provider.id, "READY", True, True, True, bool(provider.models) if provider.models else None, required, checked)

    if provider.type == "ollama":
        url = (provider.base_url or "http://127.0.0.1:11434").rstrip("/") + "/api/tags"
    else:
        url = (provider.base_url or "").rstrip("/")
    started = time.monotonic()
    try:
        with urlopen(Request(url, headers={"Accept": "application/json"}), timeout=timeout) as response:
            latency = round((time.monotonic() - started) * 1000, 1)
            if response.status in {401, 403}:
                return ProviderHealth(provider.id, "AUTH_ERROR", True, True, False, None, required, checked, latency, f"HTTP {response.status}")
            if response.status >= 400:
                return ProviderHealth(provider.id, "MODEL_ERROR", True, True, None, False, required, checked, latency, f"HTTP {response.status}")
            return ProviderHealth(provider.id, "READY", True, True, True, bool(provider.models) if provider.models else None, required, checked, latency)
    except HTTPError as error:
        state = "AUTH_ERROR" if error.code in {401, 403} else "MODEL_ERROR" if error.code == 404 else "UNKNOWN_ERROR"
        return ProviderHealth(provider.id, state, True, True, error.code not in {401, 403}, None, required, checked, round((time.monotonic() - started) * 1000, 1), f"HTTP {error.code}")
    except TimeoutError:
        return ProviderHealth(provider.id, "TIMEOUT", True, False, None, None, required, checked, round((time.monotonic() - started) * 1000, 1), "Provider health check timed out")
    except (OSError, URLError) as error:
        return ProviderHealth(provider.id, "NETWORK_ERROR", True, False, None, None, required, checked, round((time.monotonic() - started) * 1000, 1), str(error))


def store_health(connection: sqlite3.Connection, health: ProviderHealth) -> None:
    connection.execute(
        """INSERT INTO ai_provider_health (provider_id,state,configured,reachable,authenticated,selected_model_available,required_capabilities_json,checked_at,latency_ms,error)
           VALUES (?,?,?,?,?,?,?,?,?,?)
           ON CONFLICT(provider_id) DO UPDATE SET state=excluded.state, configured=excluded.configured, reachable=excluded.reachable,
             authenticated=excluded.authenticated, selected_model_available=excluded.selected_model_available,
             required_capabilities_json=excluded.required_capabilities_json, checked_at=excluded.checked_at, latency_ms=excluded.latency_ms, error=excluded.error""",
        (health.provider_id, health.state, int(health.configured), int(health.reachable), None if health.authenticated is None else int(health.authenticated), None if health.selected_model_available is None else int(health.selected_model_available), json.dumps(health.required_capabilities), health.checked_at, health.latency_ms, health.error),
    )
    connection.commit()


def read_health(connection: sqlite3.Connection, provider_id: str) -> ProviderHealth | None:
    row = connection.execute("SELECT * FROM ai_provider_health WHERE provider_id=?", (provider_id,)).fetchone()
    if not row:
        return None
    return ProviderHealth(row["provider_id"], row["state"], bool(row["configured"]), bool(row["reachable"]), None if row["authenticated"] is None else bool(row["authenticated"]), None if row["selected_model_available"] is None else bool(row["selected_model_available"]), json.loads(row["required_capabilities_json"] or "[]"), row["checked_at"], row["latency_ms"], row["error"])
