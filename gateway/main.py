from __future__ import annotations

import asyncio
import hashlib
import importlib.util
import ipaddress
import json
import os
import secrets
import shutil
import socket
import sqlite3
import subprocess
import sys
import time as time_module
from urllib.error import HTTPError, URLError
from urllib.request import Request as UrlRequest, urlopen
from contextlib import closing
from dataclasses import asdict
from datetime import date, datetime, time, timedelta, timezone
from time import perf_counter
from pathlib import Path
from typing import Any
from urllib.parse import quote, urlparse

from fastapi import Depends, FastAPI, HTTPException, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse, HTMLResponse, RedirectResponse
from pydantic import BaseModel, Field, HttpUrl

try:
    from .processing_service import classify_gemini_error, pipeline_available, pipeline_command as build_pipeline_command, pipeline_environment
    from .worker_queue import PersistentWorkerQueue, WorkerResourceError, validate_media_artifact
    from .provider_registry import (
        BUILT_IN_PROVIDERS,
        ModelDefinition,
        ProviderDefinition,
        ProviderHealth,
        check_provider_health,
        get_model,
        get_provider,
        health_public_dict,
        init_registry_schema,
        list_models,
        list_providers,
        provider_public_dict,
        read_health,
        register_provider,
        remove_provider,
        set_provider_enabled,
        store_health,
        update_provider,
    )
    from .secret_vault import SecretVault, SecretVaultError
except ImportError:  # pragma: no cover - uvicorn main:app from gateway/
    from processing_service import classify_gemini_error, pipeline_available, pipeline_command as build_pipeline_command, pipeline_environment
    from worker_queue import PersistentWorkerQueue, WorkerResourceError, validate_media_artifact
    from provider_registry import (
        BUILT_IN_PROVIDERS,
        ModelDefinition,
        ProviderDefinition,
        ProviderHealth,
        check_provider_health,
        get_model,
        get_provider,
        health_public_dict,
        init_registry_schema,
        list_models,
        list_providers,
        provider_public_dict,
        read_health,
        register_provider,
        remove_provider,
        set_provider_enabled,
        store_health,
        update_provider,
    )
    from secret_vault import SecretVault, SecretVaultError

ROOT = Path(__file__).resolve().parent
DB_PATH = Path(os.getenv("ISM_GATEWAY_DB", str(ROOT / "gateway.db")))
GATEWAY_TOKEN = os.getenv("GATEWAY_TOKEN", "")
PROVIDER_MODE = os.getenv("PROVIDER_MODE", "mock")
PUBLISH_INTERVAL_SECONDS = max(10, int(os.getenv("PUBLISH_INTERVAL_SECONDS", "30")))
PROCESSING_ROOT = Path(os.getenv("ISM_PROCESSING_ROOT", str(ROOT / "processing")))
PIPELINE_DIR = Path(os.getenv("ISM_PIPELINE_DIR", str(ROOT.parent / "pipeline")))
PIPELINE_BIN = os.getenv("ISM_PIPELINE_BIN", "").strip()
YTDLP_BIN = os.getenv("ISM_YTDLP_BIN", "yt-dlp").strip()
SOURCE_ROOT = Path(os.getenv("ISM_SOURCE_ROOT", str(ROOT / "sources")))
PUBLIC_BASE_URL = os.getenv("PUBLIC_BASE_URL", "http://127.0.0.1:8787").rstrip("/")
GEMINI_API_KEY = os.getenv("GEMINI_API_KEY", "").strip()
REQUIRE_GATEWAY_TOKEN = os.getenv("REQUIRE_GATEWAY_TOKEN", "").lower() in {"1", "true", "yes"}
GEMINI_MODEL = os.getenv("GEMINI_MODEL", "gemini-flash-latest")
ACCOUNT_DAILY_LIMIT = max(1, int(os.getenv("ACCOUNT_DAILY_LIMIT", "10")))
ACCOUNT_MIN_GAP_SECONDS = max(0, int(os.getenv("ACCOUNT_MIN_GAP_SECONDS", "60")))
MAX_PROVIDER_ATTEMPTS = max(1, int(os.getenv("MAX_PROVIDER_ATTEMPTS", "3")))
MAX_ACTIVE_PROCESSING_JOBS = max(1, int(os.getenv("MAX_ACTIVE_PROCESSING_JOBS", "1")))
MAX_ACTIVE_SOURCE_JOBS = max(1, int(os.getenv("MAX_ACTIVE_SOURCE_JOBS", "2")))
MIN_FREE_DISK_GB = max(0.0, float(os.getenv("MIN_FREE_DISK_GB", "2")))
GEMINI_KEY_FILE = Path(os.getenv("ISM_GEMINI_KEY_FILE", str(ROOT / "secrets" / "gemini.key")))
AI_SECRET_FILE = Path(os.getenv("ISM_AI_SECRET_FILE", str(ROOT / "secrets" / "ai-vault.json")))
AI_VAULT = SecretVault(AI_SECRET_FILE)
MAX_UPLOAD_BYTES = max(1, int(os.getenv("ISM_MAX_UPLOAD_BYTES", str(2 * 1024 * 1024 * 1024))))

app = FastAPI(title="ISM Social Gateway", version="0.10.0")
app.add_middleware(
    CORSMiddleware,
    allow_origins=[origin.strip() for origin in os.getenv("CORS_ORIGINS", "http://localhost:1430,http://tauri.localhost,tauri://localhost").split(",") if origin.strip()],
    allow_credentials=False,
    allow_methods=["*"],
    allow_headers=["*"],
)

_scheduler_task: asyncio.Task | None = None
_processing_workers = PersistentWorkerQueue("processing", PROCESSING_ROOT, MAX_ACTIVE_PROCESSING_JOBS, MIN_FREE_DISK_GB)
_source_workers = PersistentWorkerQueue("sources", SOURCE_ROOT, MAX_ACTIVE_SOURCE_JOBS, MIN_FREE_DISK_GB)


class PolicyDeferred(Exception):
    def __init__(self, message: str, retry_at: datetime):
        self.message = message
        self.retry_at = retry_at


class ProviderResultPayload(BaseModel):
    ok: bool
    status_code: int = 200
    error: str | None = None
    retry_after_seconds: int | None = Field(default=None, ge=0, le=86400)


def now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


def parse_time(value: str | None) -> datetime | None:
    if not value:
        return None
    parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    if parsed.tzinfo is None:
        parsed = parsed.replace(tzinfo=timezone.utc)
    return parsed.astimezone(timezone.utc)


def db() -> sqlite3.Connection:
    connection = sqlite3.connect(DB_PATH, check_same_thread=False)
    connection.row_factory = sqlite3.Row
    return connection


def idempotency_key(payload: PostPayload, scheduled_iso: str | None) -> str:
    explicit = payload.idempotencyKey.strip() if payload.idempotencyKey else ""
    if explicit:
        return explicit[:180]
    raw = "|".join([payload.platform, payload.account_id or payload.account, str(payload.mediaUrl), scheduled_iso or "", payload.title, payload.caption])
    return hashlib.sha256(raw.encode("utf-8")).hexdigest()


def account_for_post(connection: sqlite3.Connection, payload: PostPayload) -> sqlite3.Row | None:
    if payload.account_id:
        return connection.execute("SELECT * FROM accounts WHERE id=?", (payload.account_id,)).fetchone()
    if payload.account:
        return connection.execute("SELECT * FROM accounts WHERE platform=? AND account_name=? ORDER BY created_at DESC LIMIT 1", (payload.platform, payload.account)).fetchone()
    return None


def check_account_policy(account: sqlite3.Row | None, now: datetime) -> None:
    if not account:
        return
    if account["status"] != "connected":
        raise PolicyDeferred("Account is paused; publishing is stopped until it is resumed.", now + timedelta(hours=1))
    cooldown = parse_time(account["cooldown_until"])
    if cooldown and cooldown > now:
        raise PolicyDeferred("Account is in a safety cooldown after a provider warning.", cooldown)
    if account["publish_count_day"] != now.date().isoformat():
        return
    if int(account["publish_count"] or 0) >= int(account["daily_limit"] or ACCOUNT_DAILY_LIMIT):
        tomorrow = datetime.combine(now.date() + timedelta(days=1), time.min, tzinfo=timezone.utc)
        raise PolicyDeferred("Daily account publishing limit reached.", tomorrow)
    last = parse_time(account["last_publish_at"])
    if last:
        allowed = last + timedelta(seconds=int(account["min_gap_seconds"] or ACCOUNT_MIN_GAP_SECONDS))
        if allowed > now:
            raise PolicyDeferred("Minimum interval between account posts has not elapsed.", allowed)


def init_db() -> None:
    with closing(db()) as connection:
        connection.executescript(
            """
            CREATE TABLE IF NOT EXISTS accounts (
                id TEXT PRIMARY KEY,
                platform TEXT NOT NULL,
                account_name TEXT NOT NULL,
                provider_account_id TEXT,
                access_token TEXT,
                refresh_token TEXT,
                token_expires_at TEXT,
                status TEXT NOT NULL DEFAULT 'connected',
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL,
                daily_limit INTEGER NOT NULL DEFAULT 10,
                min_gap_seconds INTEGER NOT NULL DEFAULT 60,
                last_publish_at TEXT,
                publish_count_day TEXT,
                publish_count INTEGER NOT NULL DEFAULT 0,
                pause_reason TEXT,
                cooldown_until TEXT
            );
            CREATE TABLE IF NOT EXISTS posts (
                id TEXT PRIMARY KEY,
                platform TEXT NOT NULL,
                account_id TEXT,
                account_name TEXT,
                media_url TEXT NOT NULL,
                title TEXT NOT NULL DEFAULT '',
                caption TEXT NOT NULL DEFAULT '',
                description TEXT NOT NULL DEFAULT '',
                hashtags TEXT NOT NULL DEFAULT '',
                keywords TEXT NOT NULL DEFAULT '',
                scheduled_at TEXT,
                auto_publish INTEGER NOT NULL DEFAULT 1,
                status TEXT NOT NULL DEFAULT 'scheduled',
                provider_post_id TEXT,
                permalink TEXT,
                error TEXT,
                attempts INTEGER NOT NULL DEFAULT 0,
                idempotency_key TEXT UNIQUE,
                next_attempt_at TEXT,
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL,
                FOREIGN KEY(account_id) REFERENCES accounts(id)
            );
            CREATE INDEX IF NOT EXISTS idx_posts_due ON posts(status, auto_publish, scheduled_at);
            CREATE INDEX IF NOT EXISTS idx_posts_idempotency ON posts(idempotency_key);
            CREATE TABLE IF NOT EXISTS processing_jobs (
                id TEXT PRIMARY KEY,
                pipeline_job_id TEXT,
                source TEXT NOT NULL,
                llm TEXT NOT NULL DEFAULT 'gemini',
                captions TEXT NOT NULL DEFAULT 'classic',
                mode TEXT NOT NULL DEFAULT 'balanced',
                status TEXT NOT NULL DEFAULT 'queued',
                stage TEXT,
                fraction REAL,
                message TEXT,
                error TEXT,
                error_code TEXT,
                result_json TEXT,
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL
            );
            CREATE INDEX IF NOT EXISTS idx_processing_jobs_updated ON processing_jobs(updated_at);
            CREATE TABLE IF NOT EXISTS source_jobs (
                id TEXT PRIMARY KEY,
                source TEXT NOT NULL,
                max_items INTEGER NOT NULL DEFAULT 0,
                status TEXT NOT NULL DEFAULT 'queued',
                total INTEGER NOT NULL DEFAULT 0,
                completed INTEGER NOT NULL DEFAULT 0,
                message TEXT,
                error TEXT,
                items_json TEXT,
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL
            );
            CREATE TABLE IF NOT EXISTS analytics_snapshots (
                id TEXT PRIMARY KEY,
                account_id TEXT NOT NULL,
                platform TEXT NOT NULL,
                metric_date TEXT NOT NULL,
                views INTEGER NOT NULL DEFAULT 0,
                likes INTEGER NOT NULL DEFAULT 0,
                comments INTEGER NOT NULL DEFAULT 0,
                followers INTEGER,
                watch_time_seconds INTEGER,
                source TEXT NOT NULL,
                fetched_at TEXT NOT NULL,
                UNIQUE(account_id, metric_date),
                FOREIGN KEY(account_id) REFERENCES accounts(id) ON DELETE CASCADE
            );
            CREATE INDEX IF NOT EXISTS idx_analytics_account_date ON analytics_snapshots(account_id, metric_date);
            """
        )
        migrations = [
            ("accounts", "daily_limit", "INTEGER NOT NULL DEFAULT 10"),
            ("accounts", "min_gap_seconds", "INTEGER NOT NULL DEFAULT 60"),
            ("accounts", "last_publish_at", "TEXT"),
            ("accounts", "publish_count_day", "TEXT"),
            ("accounts", "publish_count", "INTEGER NOT NULL DEFAULT 0"),
            ("accounts", "pause_reason", "TEXT"),
            ("accounts", "cooldown_until", "TEXT"),
            ("posts", "idempotency_key", "TEXT"),
            ("posts", "next_attempt_at", "TEXT"),
            ("processing_jobs", "error_code", "TEXT"),
            ("processing_jobs", "mode", "TEXT NOT NULL DEFAULT 'balanced'"),
        ]
        for table, column, definition in migrations:
            existing = {row[1] for row in connection.execute(f"PRAGMA table_info({table})")}
            if column not in existing:
                connection.execute(f"ALTER TABLE {table} ADD COLUMN {column} {definition}")
        connection.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_posts_idempotency ON posts(idempotency_key)")
        init_registry_schema(connection)
        connection.commit()


async def auth(request: Request) -> None:
    if not GATEWAY_TOKEN:
        if REQUIRE_GATEWAY_TOKEN:
            raise HTTPException(status_code=503, detail="Gateway token is not configured on this remote Gateway.")
        return
    supplied = request.headers.get("authorization", "")
    if not secrets.compare_digest(supplied, f"Bearer {GATEWAY_TOKEN}"):
        raise HTTPException(status_code=401, detail="Invalid Gateway token")


class ProcessingPayload(BaseModel):
    source: HttpUrl
    llm: str = Field(default="gemini", pattern=r"^(gemini|ollama)$")
    captions: str = Field(default="classic", min_length=1, max_length=40)
    mode: str = Field(default="balanced", pattern=r"^(fast|balanced|quality|maximum)$")


class SourcePayload(BaseModel):
    source: HttpUrl
    max_items: int = Field(default=0, ge=0, le=1000)


class AIModelPayload(BaseModel):
    model_id: str = Field(min_length=1, max_length=160)
    display_name: str = Field(min_length=1, max_length=200)
    capabilities: list[str] = Field(default_factory=list, max_length=20)
    context_window: int | None = Field(default=None, ge=1)
    supports_structured_output: bool = False
    supports_vision: bool = False
    enabled: bool = True


class AIProviderCreatePayload(BaseModel):
    id: str = Field(min_length=2, max_length=80, pattern=r"^[a-z][a-z0-9_-]*$")
    name: str = Field(min_length=1, max_length=160)
    type: str = Field(pattern=r"^(openai_compatible|gemini|openai|anthropic|ollama)$")
    base_url: str | None = Field(default=None, max_length=500)
    credential_ref: str | None = Field(default=None, min_length=2, max_length=120)
    capabilities: list[str] = Field(default_factory=list, max_length=20)
    models: list[AIModelPayload] = Field(default_factory=list, max_length=100)
    api_key: str | None = Field(default=None, min_length=1, max_length=500)


class AIProviderUpdatePayload(BaseModel):
    name: str | None = Field(default=None, min_length=1, max_length=160)
    base_url: str | None = Field(default=None, max_length=500)
    credential_ref: str | None = Field(default=None, min_length=2, max_length=120)
    capabilities: list[str] | None = Field(default=None, max_length=20)
    enabled: bool | None = None
    api_key: str | None = Field(default=None, min_length=1, max_length=500)


class AIProviderPayload(BaseModel):
    id: str = Field(min_length=1, max_length=80, pattern=r"^[a-zA-Z0-9_-]+$")
    name: str = Field(min_length=1, max_length=160)
    type: str = Field(min_length=1, max_length=40)
    base_url: str = ""
    credential_ref: str = ""
    default_model: str = Field(min_length=1, max_length=160)
    fallback_model: str = ""
    enabled: bool = True
    capabilities: dict[str, bool] = Field(default_factory=dict)


class PersonalEventPayload(BaseModel):
    event_type: str = Field(min_length=2, max_length=20)
    clip_id: str = ""
    candidate_id: str = ""
    job_id: str = ""
    reason: str = ""
    timestamp: int | None = None
    features: dict[str, Any] = Field(default_factory=dict)


class PersonalSearchPayload(BaseModel):
    selected: dict[str, Any]
    candidates: list[dict[str, Any]] = Field(default_factory=list, max_length=500)
    limit: int = Field(default=10, ge=1, le=20)


class FindBetterPayload(PersonalSearchPayload):
    threshold: float = Field(default=5.0, ge=0.0, le=50.0)


class AnalyticsSnapshotPayload(BaseModel):
    account_id: str = Field(min_length=1, max_length=160)
    metric_date: date
    views: int = Field(default=0, ge=0)
    likes: int = Field(default=0, ge=0)
    comments: int = Field(default=0, ge=0)
    followers: int | None = Field(default=None, ge=0)
    watch_time_seconds: int | None = Field(default=None, ge=0)
    source: str = Field(min_length=2, max_length=80)


class AccountCreate(BaseModel):
    platform: str = Field(pattern=r"^(instagram|facebook|tiktok|youtube|x)$")
    account_name: str = Field(min_length=1, max_length=160)


class PostPayload(BaseModel):
    id: str | None = None
    platform: str = Field(pattern=r"^(instagram|facebook|tiktok|youtube|x)$")
    account: str = ""
    account_id: str | None = None
    mediaUrl: HttpUrl
    title: str = ""
    caption: str = ""
    description: str = ""
    hashtags: str = ""
    keywords: str = ""
    scheduledAt: str | None = None
    autoPublish: bool = True
    status: str = "scheduled"
    idempotencyKey: str | None = None


class AccountPolicyPayload(BaseModel):
    status: str = Field(pattern=r"^(connected|paused)$")
    daily_limit: int = Field(default=10, ge=1, le=1000)
    min_gap_seconds: int = Field(default=60, ge=0, le=86400)


class StatusUpdate(BaseModel):
    status: str
    error: str | None = None


def source_dict(row: sqlite3.Row) -> dict[str, Any]:
    result = dict(row)
    result["items"] = json.loads(result["items_json"]) if result.get("items_json") else []
    result.pop("items_json", None)
    return result


def validate_processing_source(value: str) -> str:
    parsed = urlparse(value)
    public = urlparse(PUBLIC_BASE_URL)
    if parsed.scheme in {"http", "https"} and parsed.hostname and public.hostname and parsed.hostname.lower() == public.hostname.lower():
        prefix = f"{PUBLIC_BASE_URL}/v1/sources/jobs/"
        if value.startswith(prefix) and "/media/" in value:
            return value
    return validate_public_source(value)


def validate_public_source(value: str) -> str:
    parsed = urlparse(value)
    if parsed.scheme not in {"http", "https"} or not parsed.hostname:
        raise HTTPException(status_code=422, detail="Source must be an HTTP or HTTPS URL.")
    host = parsed.hostname.lower().rstrip(".")
    blocked = {"localhost", "localhost.localdomain", "0.0.0.0", "::1"}
    if host in blocked:
        raise HTTPException(status_code=422, detail="Local network sources are not allowed.")

    def reject_private(address_text: str) -> None:
        address = ipaddress.ip_address(address_text)
        if address.is_private or address.is_loopback or address.is_link_local or address.is_reserved or address.is_multicast:
            raise HTTPException(status_code=422, detail="Private network sources are not allowed.")

    try:
        reject_private(host)
    except ValueError:
        try:
            addresses = {entry[4][0] for entry in socket.getaddrinfo(host, parsed.port or 443, type=socket.SOCK_STREAM)}
        except socket.gaierror as error:
            raise HTTPException(status_code=422, detail="Source hostname could not be resolved.") from error
        for address_text in addresses:
            reject_private(address_text)
    return value


def ytdlp_module():
    if str(PIPELINE_DIR) not in sys.path:
        sys.path.insert(0, str(PIPELINE_DIR))
    from publikclip_pipeline.ingest import ytdlp
    return ytdlp


def ytdlp_binary(module):
    configured = Path(YTDLP_BIN)
    if configured.is_file() and os.access(configured, os.X_OK):
        return configured
    discovered = shutil.which(YTDLP_BIN)
    if discovered:
        return Path(discovered)
    return module.ensure_ytdlp(lambda _fraction, _message: None)


def source_preview(source: str, max_items: int) -> list[dict[str, Any]]:
    module = ytdlp_module()
    binary = ytdlp_binary(module)
    args = ["-J", "--flat-playlist", "--no-warnings"]
    if max_items:
        args += ["--playlist-end", str(max_items)]
    raw = module._run(binary, args + [source])
    payload = json.loads(raw)
    entries = payload.get("entries") if isinstance(payload, dict) else None
    if not entries:
        entries = [payload]
    result = []
    for index, item in enumerate(entries):
        if not isinstance(item, dict) or not item.get("id"):
            continue
        webpage = item.get("webpage_url") or item.get("url") or source
        result.append({
            "index": index,
            "id": str(item.get("id")),
            "title": str(item.get("title") or "Untitled"),
            "duration": item.get("duration"),
            "url": str(webpage),
            "thumbnail": item.get("thumbnail"),
        })
    if not result:
        raise ValueError("The source did not contain downloadable video entries.")
    return result


def update_source_job(job_id: str, **values: Any) -> None:
    if not values:
        return
    values["updated_at"] = now_iso()
    assignments = ", ".join(f"{key}=?" for key in values)
    parameters = list(values.values()) + [job_id]
    with closing(db()) as connection:
        connection.execute(f"UPDATE source_jobs SET {assignments} WHERE id=?", parameters)
        connection.commit()


def run_source_download(job_id: str, source: str, max_items: int) -> None:
    target_dir = (SOURCE_ROOT / job_id).resolve()
    target_dir.mkdir(parents=True, exist_ok=True)
    try:
        module = ytdlp_module()
        binary = ytdlp_binary(module)
        output_template = str(target_dir / "%(playlist_index)05d - %(title)s.%(ext)s")
        args = [
            "--newline", "--no-warnings", "--yes-playlist", "-f", "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best",
            "--merge-output-format", "mp4", "-o", output_template,
        ]
        if max_items:
            args += ["--playlist-end", str(max_items)]
        update_source_job(job_id, status="running", message="Downloading source entries")
        module._run(binary, args + [source], on_line=lambda line: update_source_job(job_id, message=line[-300:]))
        files = sorted(target_dir.glob("*.mp4"))
        if not files:
            raise ValueError("Download finished without MP4 files.")
        items = [
            {"index": index, "title": path.stem, "path": str(path), "media_url": f"{PUBLIC_BASE_URL}/v1/sources/jobs/{job_id}/media/{quote(path.name, safe='')}"}
            for index, path in enumerate(files)
        ]
        update_source_job(job_id, status="done", total=len(items), completed=len(items), message="Downloads ready", items_json=json.dumps(items, ensure_ascii=False))
    except Exception as error:  # noqa: BLE001 — persist user-facing worker failure
        update_source_job(job_id, status="failed", error=str(error), message="Source download failed")


def provider_config_path() -> Path:
    PROCESSING_ROOT.mkdir(parents=True, exist_ok=True)
    return PROCESSING_ROOT / "providers.json"


def read_provider_profiles() -> list[dict[str, Any]]:
    path = provider_config_path()
    try:
        payload = json.loads(path.read_text()) if path.exists() else []
    except (OSError, json.JSONDecodeError):
        return []
    profiles = payload if isinstance(payload, list) else payload.get("providers", [])
    return [item for item in profiles if isinstance(item, dict) and item.get("id")]


def write_provider_profiles(profiles: list[dict[str, Any]]) -> None:
    path = provider_config_path()
    temporary = path.with_suffix(".tmp")
    temporary.write_text(json.dumps({"providers": profiles}, ensure_ascii=False, indent=2))
    temporary.replace(path)


def public_provider_profile(profile: dict[str, Any]) -> dict[str, Any]:
    return {
        "id": profile.get("id", ""),
        "name": profile.get("name", ""),
        "type": profile.get("type", ""),
        "base_url": profile.get("base_url", ""),
        "credential_ref": profile.get("credential_ref", ""),
        "default_model": profile.get("default_model", ""),
        "fallback_model": profile.get("fallback_model", ""),
        "enabled": bool(profile.get("enabled", True)),
        "capabilities": profile.get("capabilities", {}),
        "credential_configured": bool(profile.get("credential_ref")),
    }


def processing_dict(row: sqlite3.Row) -> dict[str, Any]:
    result = dict(row)
    result["results"] = json.loads(result["result_json"]) if result.get("result_json") else None
    result.pop("result_json", None)
    return result


def update_processing_job(job_id: str, **values: Any) -> None:
    if not values:
        return
    values["updated_at"] = now_iso()
    assignments = ", ".join(f"{key}=?" for key in values)
    parameters = list(values.values()) + [job_id]
    with closing(db()) as connection:
        connection.execute(f"UPDATE processing_jobs SET {assignments} WHERE id=?", parameters)
        connection.commit()


def read_pipeline_checkpoint(job_dir: Path, name: str) -> dict[str, Any] | None:
    path = job_dir / f"{name}.json"
    try:
        payload = json.loads(path.read_text())
        data = payload.get("data")
        return data if isinstance(data, dict) else None
    except (OSError, json.JSONDecodeError):
        return None


def run_source_job(job_id: str) -> None:
    with closing(db()) as connection:
        row = connection.execute("SELECT source, max_items FROM source_jobs WHERE id=?", (job_id,)).fetchone()
    if not row:
        return
    run_source_download(job_id, row["source"], int(row["max_items"] or 0))


def processing_results(external_id: str, pipeline_job_id: str) -> dict[str, Any]:
    job_dir = PROCESSING_ROOT / "jobs" / pipeline_job_id
    ingest = read_pipeline_checkpoint(job_dir, "ingest") or {}
    score = read_pipeline_checkpoint(job_dir, "score")
    render = read_pipeline_checkpoint(job_dir, "render") or {}
    events = read_pipeline_checkpoint(job_dir, "events")
    candidates = read_pipeline_checkpoint(job_dir, "candidates")
    outputs = []
    for output in render.get("outputs", []):
        raw_path = Path(str(output.get("path", "")))
        filename = raw_path.name
        valid, _reason = validate_media_artifact(raw_path)
        if not filename or not valid:
            continue
        item = dict(output)
        item["path"] = f"{PUBLIC_BASE_URL}/v1/processing/jobs/{external_id}/media/{filename}"
        outputs.append(item)
    return {
        "job_id": external_id,
        "ingest": {key: ingest.get(key) for key in ("title", "heatmap", "probe")} if ingest else None,
        "score": score,
        "render": {**render, "outputs": outputs} if render else None,
        "artifacts": outputs,
        "events": events,
        "candidates": candidates,
    }


def read_server_gemini_key() -> str | None:
    for key in (GEMINI_API_KEY.strip(), os.getenv("GEMINI_API_KEY", "").strip(), os.getenv("PUBLIKCLIP_GEMINI_API_KEY", "").strip()):
        if key:
            return key
    try:
        key = AI_VAULT.get("GEMINI_API_KEY")
    except SecretVaultError:
        key = None
    if key:
        return key
    try:
        key = GEMINI_KEY_FILE.read_text(encoding="utf-8").strip()
    except OSError:
        return None
    return key or None


def pipeline_checks() -> dict[str, Any]:
    manifest = PIPELINE_DIR / "pyproject.toml"
    package = PIPELINE_DIR / "publikclip_pipeline"
    uv_path = shutil.which("uv")
    ffmpeg_path = shutil.which("ffmpeg")
    ffprobe_path = shutil.which("ffprobe")
    storage_ok = False
    try:
        PROCESSING_ROOT.mkdir(parents=True, exist_ok=True)
        storage_ok = os.access(PROCESSING_ROOT, os.W_OK)
    except OSError:
        storage_ok = False
    return {
        "pipeline": manifest.is_file() and package.is_dir(),
        "pipeline_dir_configured": manifest.is_file() and package.is_dir(),
        "python": sys.version_info >= (3, 12) or bool(uv_path),
        "uv": bool(uv_path),
        "ffmpeg": bool(ffmpeg_path),
        "ffprobe": bool(ffprobe_path),
        "storage": storage_ok,
        "gemini_configured": bool(read_server_gemini_key()),
        "ollama": ollama_available(),
        "youtube_urls": True,
        "https_urls": True,
        "android_remote_processing": True,
    }


def ollama_available() -> bool:
    try:
        with urlopen(UrlRequest("http://127.0.0.1:11434/api/tags"), timeout=1.5) as response:
            return response.status == 200
    except (OSError, URLError):
        return False


def pipeline_command() -> tuple[list[str], dict[str, str]]:
    environment = pipeline_environment(PROCESSING_ROOT, PIPELINE_DIR, read_server_gemini_key() or "")
    return pipeline_command_for_config(PIPELINE_DIR, PIPELINE_BIN, environment), environment


def pipeline_command_for_config(pipeline_dir: Path, pipeline_bin: str, environment: dict[str, str]) -> list[str]:
    return build_pipeline_command(pipeline_dir, pipeline_bin, environment)


def uploaded_source_path(source: str) -> str | None:
    parsed = urlparse(source)
    public = urlparse(PUBLIC_BASE_URL)
    if parsed.hostname != public.hostname:
        return None
    parts = [part for part in parsed.path.split("/") if part]
    if len(parts) < 5 or parts[:3] != ["v1", "sources", "jobs"] or parts[4] != "media":
        return None
    job_id = parts[3]
    filename = Path("/".join(parts[5:])).name
    if not filename or job_id.startswith("."):
        return None
    candidate = (SOURCE_ROOT / job_id / filename).resolve()
    root = SOURCE_ROOT.resolve()
    return str(candidate) if root in candidate.parents and candidate.is_file() else None


def run_processing_job(external_id: str, source: str | None = None, llm: str | None = None, captions: str | None = None, mode: str | None = None) -> None:
    with closing(db()) as connection:
        row = connection.execute("SELECT source, llm, captions, mode, pipeline_job_id FROM processing_jobs WHERE id=?", (external_id,)).fetchone()
    if not row:
        return
    source = source or row["source"]
    llm = llm or row["llm"]
    captions = captions or row["captions"]
    mode = mode or row["mode"] or "balanced"
    if llm == "gemini" and not read_server_gemini_key():
        update_processing_job(external_id, status="failed", error="Gemini is not configured on the Gateway.", error_code="GEMINI_NOT_CONFIGURED", message="Gemini configuration is required")
        return
    command, environment = pipeline_command()
    pipeline_source = uploaded_source_path(source) or source
    if row["pipeline_job_id"]:
        command.extend(["resume", str(row["pipeline_job_id"]), "--llm", llm, "--captions", captions, "--mode", mode])
    else:
        command.extend(["run", pipeline_source, "--llm", llm, "--captions", captions, "--mode", mode])
    try:
        process = subprocess.Popen(
            command,
            cwd=str(PIPELINE_DIR.parent),
            env=environment,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            bufsize=1,
        )
        update_processing_job(external_id, status="running", message="Pipeline started")
        final: dict[str, Any] | None = None
        assert process.stdout is not None
        for line in process.stdout:
            try:
                event = json.loads(line)
            except json.JSONDecodeError:
                continue
            if event.get("event") == "job":
                update_processing_job(external_id, pipeline_job_id=event.get("job_id"), message="Job created")
            elif event.get("event") == "progress":
                update_processing_job(
                    external_id,
                    status="running",
                    stage=event.get("stage"),
                    fraction=event.get("fraction"),
                    message=event.get("message"),
                )
            elif event.get("event") == "result":
                final = event
        process.stdout.close()
        return_code = process.wait()
        if return_code != 0 or not final or not final.get("ok"):
            detail = (final or {}).get("error") or f"Pipeline exited with code {return_code}."
            error_code = (final or {}).get("error_code") or ("PIPELINE_START_FAILED" if return_code == 127 else "PIPELINE_FAILED")
            update_processing_job(external_id, status="failed", error=str(detail), error_code=error_code, message="Pipeline failed")
            return
        pipeline_job_id = str(final.get("job_id") or "")
        if not pipeline_job_id:
            update_processing_job(external_id, status="failed", error="Pipeline returned no job id.", error_code="PIPELINE_RESULT_INVALID", message="Pipeline failed")
            return
        results = processing_results(external_id, pipeline_job_id)
        update_processing_job(external_id, pipeline_job_id=pipeline_job_id, status="done", fraction=1.0, stage="render", message="Clips ready", result_json=json.dumps(results, ensure_ascii=False))
    except FileNotFoundError as error:
        update_processing_job(external_id, status="failed", error="Pipeline executable could not be started.", error_code="PIPELINE_START_FAILED", message="Gateway worker failed")
    except Exception as error:  # noqa: BLE001 — persist user-facing worker failure
        update_processing_job(external_id, status="failed", error="Pipeline worker failed.", error_code="PIPELINE_WORKER_FAILED", message="Gateway worker failed")


def account_dict(row: sqlite3.Row) -> dict[str, Any]:
    return {
        "id": row["id"],
        "platform": row["platform"],
        "account_name": row["account_name"],
        "status": row["status"],
        "token_expires_at": row["token_expires_at"],
        "daily_limit": row["daily_limit"],
        "min_gap_seconds": row["min_gap_seconds"],
        "last_publish_at": row["last_publish_at"],
        "publish_count_day": row["publish_count_day"],
        "publish_count": row["publish_count"],
        "pause_reason": row["pause_reason"],
        "cooldown_until": row["cooldown_until"],
        "created_at": row["created_at"],
    }


def row_dict(row: sqlite3.Row) -> dict[str, Any]:
    result = dict(row)
    result["autoPublish"] = bool(result.pop("auto_publish", 0))
    result["scheduledAt"] = result.pop("scheduled_at", None)
    result["mediaUrl"] = result.pop("media_url", None)
    result["account"] = result.pop("account_name", "")
    result["accountId"] = result.pop("account_id", None)
    result["providerPostId"] = result.pop("provider_post_id", None)
    result["createdAt"] = result.pop("created_at", None)
    result["updatedAt"] = result.pop("updated_at", None)
    return result


def save_post(payload: PostPayload, post_id: str | None = None) -> dict[str, Any]:
    identifier = post_id or payload.id or f"post_{secrets.token_urlsafe(10)}"
    try:
        scheduled = parse_time(payload.scheduledAt)
    except ValueError as error:
        raise HTTPException(status_code=422, detail="scheduledAt must be a valid ISO-8601 timestamp") from error
    scheduled_iso = scheduled.isoformat() if scheduled else None
    media_url = validate_public_source(str(payload.mediaUrl))
    timestamp = now_iso()
    status = payload.status if payload.status in {"draft", "awaiting_approval", "scheduled"} else "scheduled"
    with closing(db()) as connection:
        account = account_for_post(connection, payload)
        if account and account["platform"] != payload.platform:
            raise HTTPException(status_code=422, detail="Account platform does not match post platform")
        if (payload.autoPublish or status == "scheduled") and not account:
            raise HTTPException(status_code=422, detail="A connected account is required for scheduled or automatic publishing")
        account_id = account["id"] if account else payload.account_id
        account_name = account["account_name"] if account else payload.account
        key = idempotency_key(payload, scheduled_iso)
        existing = connection.execute("SELECT id FROM posts WHERE idempotency_key=? AND id<>?", (key, identifier)).fetchone()
        if existing:
            row = connection.execute("SELECT * FROM posts WHERE id=?", (existing["id"],)).fetchone()
            return row_dict(row)
        connection.execute(
            """
            INSERT INTO posts (id, platform, account_id, account_name, media_url, title, caption, description,
              hashtags, keywords, scheduled_at, auto_publish, status, idempotency_key, next_attempt_at, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
              platform=excluded.platform, account_id=excluded.account_id, account_name=excluded.account_name,
              media_url=excluded.media_url, title=excluded.title, caption=excluded.caption,
              description=excluded.description, hashtags=excluded.hashtags, keywords=excluded.keywords,
              scheduled_at=excluded.scheduled_at, auto_publish=excluded.auto_publish,
              status=CASE WHEN posts.status IN ('published', 'cancelled', 'publishing') THEN posts.status ELSE excluded.status END,
              idempotency_key=excluded.idempotency_key, next_attempt_at=NULL, error=NULL, updated_at=excluded.updated_at
            """,
            (
                identifier, payload.platform, account_id, account_name, media_url,
                payload.title, payload.caption, payload.description, payload.hashtags, payload.keywords,
                scheduled_iso, int(payload.autoPublish), status, key, None, timestamp, timestamp,
            ),
        )
        connection.commit()
        row = connection.execute("SELECT * FROM posts WHERE id = ?", (identifier,)).fetchone()
    return row_dict(row)


async def provider_publish(row: sqlite3.Row) -> ProviderResultPayload:
    if PROVIDER_MODE == "mock":
        mock_status = int(os.getenv("MOCK_PROVIDER_STATUS", "200"))
        if mock_status != 200:
            return ProviderResultPayload(
                ok=False,
                status_code=mock_status,
                retry_after_seconds=int(os.getenv("MOCK_RETRY_AFTER", "60")),
                error=f"Mock provider returned HTTP {mock_status}",
            )
        await asyncio.sleep(0.15)
        return ProviderResultPayload(ok=True)
    return ProviderResultPayload(ok=False, status_code=501, error="Live provider adapters are not configured.")


async def publish_job(post_id: str) -> dict[str, Any] | None:
    now = datetime.now(timezone.utc)
    with closing(db()) as connection:
        row = connection.execute("SELECT * FROM posts WHERE id = ?", (post_id,)).fetchone()
        if not row or row["status"] not in {"scheduled", "failed"}:
            return row_dict(row) if row else None
        next_attempt = parse_time(row["next_attempt_at"])
        if next_attempt and next_attempt > now:
            return row_dict(row)
        account = connection.execute("SELECT * FROM accounts WHERE id=?", (row["account_id"],)).fetchone() if row["account_id"] else None
        try:
            check_account_policy(account, now)
        except PolicyDeferred as deferred:
            connection.execute(
                "UPDATE posts SET status='scheduled', error=?, next_attempt_at=?, updated_at=? WHERE id=?",
                (deferred.message, deferred.retry_at.isoformat(), now_iso(), post_id),
            )
            connection.commit()
            return await get_post(post_id)
        claimed = connection.execute(
            "UPDATE posts SET status='publishing', attempts=attempts+1, error=NULL, next_attempt_at=NULL, updated_at=? WHERE id=? AND status IN ('scheduled', 'failed')",
            (now_iso(), post_id),
        )
        connection.commit()
        if claimed.rowcount == 0:
            current = connection.execute("SELECT * FROM posts WHERE id=?", (post_id,)).fetchone()
            return row_dict(current) if current else None
        row = connection.execute("SELECT * FROM posts WHERE id=?", (post_id,)).fetchone()

    result = await provider_publish(row)
    if not result.ok:
        current_attempts = int(row["attempts"] or 0)
        warning = result.status_code in {401, 403, 429}
        transient = result.status_code == 429 or result.status_code >= 500
        if warning and row["account_id"]:
            cooldown = now + timedelta(hours=24 if result.status_code in {401, 403} else 1)
            with closing(db()) as connection:
                connection.execute(
                    "UPDATE accounts SET status=?, pause_reason=?, cooldown_until=?, updated_at=? WHERE id=?",
                    ("paused" if result.status_code in {401, 403} else "connected", result.error or f"Provider warning HTTP {result.status_code}", cooldown.isoformat(), now_iso(), row["account_id"]),
                )
                connection.commit()
        retry_seconds = result.retry_after_seconds or min(3600, 60 * (2 ** max(0, current_attempts - 1)))
        should_retry = transient and current_attempts < MAX_PROVIDER_ATTEMPTS
        next_attempt_at = (now + timedelta(seconds=retry_seconds)).isoformat() if should_retry else None
        final_status = "scheduled" if should_retry else "failed"
        with closing(db()) as connection:
            connection.execute(
                "UPDATE posts SET status=?, error=?, next_attempt_at=?, updated_at=? WHERE id=?",
                (final_status, result.error or f"Provider returned HTTP {result.status_code}", next_attempt_at, now_iso(), post_id),
            )
            connection.commit()
        return await get_post(post_id)

    provider_id = f"mock_{secrets.token_urlsafe(8)}"
    permalink = f"{PUBLIC_BASE_URL}/mock/published/{provider_id}"
    with closing(db()) as connection:
        connection.execute(
            "UPDATE posts SET status='published', provider_post_id=?, permalink=?, error=NULL, next_attempt_at=NULL, updated_at=? WHERE id=?",
            (provider_id, permalink, now_iso(), post_id),
        )
        if row["account_id"]:
            day = now.date().isoformat()
            connection.execute(
                """
                UPDATE accounts SET last_publish_at=?, publish_count_day=?,
                  publish_count=CASE WHEN publish_count_day=? THEN publish_count+1 ELSE 1 END,
                  updated_at=? WHERE id=?
                """,
                (now_iso(), day, day, now_iso(), row["account_id"]),
            )
        connection.commit()
    return await get_post(post_id)


async def get_post(post_id: str) -> dict[str, Any] | None:
    with closing(db()) as connection:
        row = connection.execute("SELECT * FROM posts WHERE id = ?", (post_id,)).fetchone()
    return row_dict(row) if row else None


def mark_processing_worker_error(job_id: str, error: str) -> None:
    update_processing_job(job_id, status="failed", error=error, message="Processing worker stopped the job")


def mark_source_worker_error(job_id: str, error: str) -> None:
    update_source_job(job_id, status="failed", error=error, message="Source worker stopped the job")


async def scheduler_loop() -> None:
    while True:
        try:
            current = datetime.now(timezone.utc)
            with closing(db()) as connection:
                rows = connection.execute(
                    "SELECT id FROM posts WHERE status='scheduled' AND auto_publish=1 AND scheduled_at IS NOT NULL AND scheduled_at <= ? AND (next_attempt_at IS NULL OR next_attempt_at <= ?) LIMIT 20",
                    (current.isoformat(), current.isoformat()),
                ).fetchall()
            await asyncio.gather(*(publish_job(row["id"]) for row in rows))
        except asyncio.CancelledError:
            raise
        except Exception as error:
            print(json.dumps({"scheduler_error": str(error)}), flush=True)
        await asyncio.sleep(PUBLISH_INTERVAL_SECONDS)


@app.on_event("startup")
async def startup() -> None:
    global _scheduler_task
    init_db()
    with closing(db()) as connection:
        timestamp = now_iso()
        connection.execute("UPDATE processing_jobs SET status='queued', error=NULL, message='Requeued after Gateway restart', updated_at=? WHERE status IN ('queued', 'running')", (timestamp,))
        connection.execute("UPDATE source_jobs SET status='queued', error=NULL, message='Requeued after Gateway restart', updated_at=? WHERE status IN ('queued', 'running')", (timestamp,))
        connection.commit()
        processing_ids = [row["id"] for row in connection.execute("SELECT id FROM processing_jobs WHERE status='queued' ORDER BY created_at").fetchall()]
        source_ids = [row["id"] for row in connection.execute("SELECT id FROM source_jobs WHERE status='queued' ORDER BY created_at").fetchall()]
    await _processing_workers.start(lambda job_id: run_processing_job(job_id), mark_processing_worker_error)
    await _source_workers.start(run_source_job, mark_source_worker_error)
    for job_id in processing_ids:
        _processing_workers.submit(job_id)
    for job_id in source_ids:
        _source_workers.submit(job_id)
    _scheduler_task = asyncio.create_task(scheduler_loop())


@app.on_event("shutdown")
async def shutdown() -> None:
    if _scheduler_task:
        _scheduler_task.cancel()
        await asyncio.gather(_scheduler_task, return_exceptions=True)
    await _processing_workers.stop()
    await _source_workers.stop()


def _ffmpeg_capability() -> dict[str, Any]:
    try:
        if str(PIPELINE_DIR) not in sys.path:
            sys.path.insert(0, str(PIPELINE_DIR))
        from publikclip_pipeline.render import ffmpeg_bin

        binary = ffmpeg_bin.ffmpeg()
        ready = Path(binary).is_file() or shutil.which(binary) is not None
        return {"ready": ready, "captions": bool(ready and ffmpeg_bin.supports_captions()), "message": "FFmpeg is available." if ready else "FFmpeg was not found."}
    except Exception:  # noqa: BLE001 — capability endpoint must never crash
        return {"ready": False, "captions": False, "message": "FFmpeg capability could not be checked."}


def _gemini_diagnostic_sync() -> dict[str, Any]:
    key = read_server_gemini_key()
    if not key:
        return {"status": "not_configured", "code": "GEMINI_NOT_CONFIGURED", "provider": "gemini", "message": "Gemini is not configured on the Gateway."}
    if not (PIPELINE_DIR / "publikclip_pipeline" / "scoring" / "llm.py").is_file():
        return {"status": "pipeline_unavailable", "code": "PIPELINE_UNAVAILABLE", "provider": "gemini", "message": "The Pipeline Gemini provider is not available."}
    try:
        if str(PIPELINE_DIR) not in sys.path:
            sys.path.insert(0, str(PIPELINE_DIR))
        from publikclip_pipeline.scoring.llm import GeminiClient

        schema = {"type": "OBJECT", "properties": {"answer": {"type": "STRING"}}, "required": ["answer"]}
        started = perf_counter()
        client = GeminiClient(api_key=key)
        client.generate_json("Return exactly the word OK.", schema, use_cache=False)
        return {"status": "ready", "provider": "gemini", "model": client.model, "latency_ms": round((perf_counter() - started) * 1000)}
    except Exception as error:  # noqa: BLE001 — convert provider errors to stable safe status
        status, code = classify_gemini_error(error)
        messages = {
            "auth_failed": "Gemini rejected the configured API key.",
            "quota": "Gemini quota or rate limit was reached.",
            "timeout": "Gemini diagnostic timed out.",
            "network_error": "The Gateway could not reach Gemini.",
            "unknown_error": "Gemini diagnostic failed.",
        }
        return {"status": status, "code": code, "provider": "gemini", "message": messages.get(status, "Gemini diagnostic failed.")}
def gemini_probe() -> dict[str, Any]:
    result = _gemini_diagnostic_sync()
    return {"configured": bool(result.get("configured", result.get("status") != "not_configured")), "reachable": result.get("status") == "ready", "model": result.get("model", GEMINI_MODEL), "latency_ms": result.get("latency_ms"), "error_code": result.get("code") or result.get("error_code")}


@app.get("/health")
async def health() -> dict[str, Any]:
    checks = pipeline_checks()
    with closing(db()) as connection:
        processing_active = connection.execute("SELECT COUNT(*) AS count FROM processing_jobs WHERE status IN ('queued', 'running')").fetchone()["count"]
        source_active = connection.execute("SELECT COUNT(*) AS count FROM source_jobs WHERE status IN ('queued', 'running')").fetchone()["count"]
    processing_worker = _processing_workers.info().as_dict()
    source_worker = _source_workers.info().as_dict()
    ready = bool(checks["pipeline"] and checks["ffmpeg"] and checks["storage"] and processing_worker["status"] != "STOPPED")
    return {"status": "ok" if ready else "degraded", "ok": ready, "provider_mode": PROVIDER_MODE, "auth_configured": bool(GATEWAY_TOKEN), "auth_required": REQUIRE_GATEWAY_TOKEN, "pipeline": checks["pipeline"], "python": checks["python"], "ffmpeg": checks["ffmpeg"], "gemini_configured": bool(read_server_gemini_key()), "storage": checks["storage"], "scheduler_interval_seconds": PUBLISH_INTERVAL_SECONDS, "processing_active": processing_active, "source_active": source_active, "workers": {"processing": processing_worker, "sources": source_worker}, "min_free_disk_gb": MIN_FREE_DISK_GB}


@app.get("/v1/processing/capabilities", dependencies=[Depends(auth)])
async def processing_capabilities() -> dict[str, Any]:
    checks = pipeline_checks()
    ffmpeg = _ffmpeg_capability()
    gemini_configured = bool(read_server_gemini_key())
    return {**checks, "gateway": True, "pipeline": bool(checks["pipeline"]), "gemini": gemini_configured, "ffmpeg": bool(ffmpeg["ready"]), "details": {"pipeline": {"ready": bool(checks["pipeline"]), "message": "Pipeline CLI is present; runtime dependencies are checked when the worker starts."}, "gemini": {"configured": gemini_configured, "provider": "gemini", "status": "configured" if gemini_configured else "not_configured"}, "ffmpeg": ffmpeg}}


@app.get("/v1/diagnostics/gemini", dependencies=[Depends(auth)])
async def gemini_diagnostic() -> dict[str, Any]:
    result = await asyncio.to_thread(_gemini_diagnostic_sync)
    if result.get("status") != "ready":
        from fastapi.responses import JSONResponse
        return JSONResponse(status_code=503, content=result)
    return result


@app.get("/v1/diagnostics/workers", dependencies=[Depends(auth)])
async def diagnostics_workers() -> dict[str, Any]:
    return {"workers": {"processing": _processing_workers.info().as_dict(), "sources": _source_workers.info().as_dict()}, "min_free_disk_gb": MIN_FREE_DISK_GB}


@app.post("/v1/diagnostics/gemini", dependencies=[Depends(auth)])
async def diagnostics_gemini() -> dict[str, Any]:
    return await asyncio.to_thread(gemini_probe)


@app.post("/v1/diagnostics/pipeline", dependencies=[Depends(auth)])
async def diagnostics_pipeline() -> dict[str, Any]:
    checks = pipeline_checks()
    pipeline_text = str(PIPELINE_DIR)
    if pipeline_text not in sys.path:
        sys.path.insert(0, pipeline_text)
    checks["pipeline_importable"] = bool(checks["pipeline"] and importlib.util.find_spec("publikclip_pipeline") is not None)
    ffmpeg = shutil.which("ffmpeg")
    try:
        checks["ffmpeg_usable"] = bool(ffmpeg and subprocess.run([ffmpeg, "-version"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=5, check=False).returncode == 0)
    except (OSError, subprocess.SubprocessError):
        checks["ffmpeg_usable"] = False
    checks["ready"] = bool(checks["pipeline"] and checks["pipeline_importable"] and checks["python"] and checks["ffmpeg_usable"] and checks["storage"])
    return checks


@app.get("/v1/ai/providers", dependencies=[Depends(auth)])
async def list_ai_providers() -> dict[str, Any]:
    return {"providers": [public_provider_profile(item) for item in read_provider_profiles()]}


@app.post("/v1/ai/providers", dependencies=[Depends(auth)])
async def save_ai_provider(payload: AIProviderPayload) -> dict[str, Any]:
    profiles = read_provider_profiles()
    item = payload.model_dump()
    profiles = [profile for profile in profiles if profile.get("id") != payload.id]
    profiles.append(item)
    write_provider_profiles(profiles)
    return {"provider": public_provider_profile(item), "status": "saved"}


@app.delete("/v1/ai/providers/{provider_id}", dependencies=[Depends(auth)])
async def delete_ai_provider(provider_id: str) -> dict[str, Any]:
    profiles = read_provider_profiles()
    filtered = [profile for profile in profiles if profile.get("id") != provider_id]
    if len(filtered) == len(profiles):
        raise HTTPException(status_code=404, detail="AI provider not found")
    write_provider_profiles(filtered)
    return {"id": provider_id, "status": "deleted"}


def personal_state_path() -> Path:
    PROCESSING_ROOT.mkdir(parents=True, exist_ok=True)
    return PROCESSING_ROOT / "personal_taste.json"


@app.get("/v1/personal/profile", dependencies=[Depends(auth)])
async def personal_profile() -> dict[str, Any]:
    from .personal_taste import load_state
    state = load_state(personal_state_path())
    return {"profile": state["profile"], "event_count": len(state["events"])}


@app.post("/v1/personal/events", dependencies=[Depends(auth)])
async def personal_event(payload: PersonalEventPayload) -> dict[str, Any]:
    from .personal_taste import record_event
    try:
        return record_event(personal_state_path(), payload.model_dump())
    except ValueError as error:
        raise HTTPException(status_code=422, detail=str(error)) from error


@app.post("/v1/personal/more-like-this", dependencies=[Depends(auth)])
async def more_like_this(payload: PersonalSearchPayload) -> dict[str, Any]:
    from .personal_taste import load_state, similarity_recommendations
    state = load_state(personal_state_path())
    return {"results": similarity_recommendations(state["profile"], payload.selected, payload.candidates, payload.limit)}


@app.post("/v1/personal/find-better", dependencies=[Depends(auth)])
async def find_better(payload: FindBetterPayload) -> dict[str, Any]:
    from .personal_taste import better_recommendations, load_state
    state = load_state(personal_state_path())
    return {"results": better_recommendations(state["profile"], payload.selected, payload.candidates, payload.threshold), "threshold": payload.threshold}


@app.get("/", response_class=HTMLResponse)
async def dashboard() -> str:
    return DASHBOARD_HTML


@app.post("/v1/sources/inspect", dependencies=[Depends(auth)])
async def inspect_source(payload: SourcePayload) -> dict[str, Any]:
    source = validate_public_source(str(payload.source))
    preview_limit = payload.max_items or 50
    try:
        items = await asyncio.to_thread(source_preview, source, preview_limit)
    except Exception as error:  # noqa: BLE001 — normalize extractor errors
        raise HTTPException(status_code=422, detail=str(error)) from error
    return {"source": source, "count": len(items), "items": items}


@app.post("/v1/sources/upload", dependencies=[Depends(auth)])
async def upload_source(request: Request) -> dict[str, Any]:
    content_length = request.headers.get("content-length")
    if content_length and int(content_length) > MAX_UPLOAD_BYTES:
        raise HTTPException(status_code=413, detail="Uploaded video exceeds the configured size limit.")
    upload_id = f"upl_{secrets.token_urlsafe(12)}"
    directory = (SOURCE_ROOT / upload_id).resolve()
    directory.mkdir(parents=True, exist_ok=False)
    target = directory / "source.mp4"
    size = 0
    try:
        with target.open("wb") as output:
            async for chunk in request.stream():
                if not chunk:
                    continue
                size += len(chunk)
                if size > MAX_UPLOAD_BYTES:
                    raise HTTPException(status_code=413, detail="Uploaded video exceeds the configured size limit.")
                output.write(chunk)
        if size == 0:
            raise HTTPException(status_code=400, detail="Uploaded video is empty.")
        timestamp = now_iso()
        source_url = f"{PUBLIC_BASE_URL}/v1/sources/jobs/{upload_id}/media/source.mp4"
        items = [{"index": 0, "title": "Uploaded video", "url": source_url, "filename": "source.mp4", "bytes": size}]
        with closing(db()) as connection:
            connection.execute(
                "INSERT INTO source_jobs (id, source, max_items, status, total, completed, items_json, created_at, updated_at) VALUES (?, ?, 0, 'done', 1, 1, ?, ?, ?)",
                (upload_id, f"upload:{upload_id}", json.dumps(items, ensure_ascii=False), timestamp, timestamp),
            )
            connection.commit()
        return {"id": upload_id, "status": "done", "source": source_url, "filename": "source.mp4", "bytes": size}
    except HTTPException:
        target.unlink(missing_ok=True)
        shutil.rmtree(directory, ignore_errors=True)
        raise
    except Exception as error:
        target.unlink(missing_ok=True)
        shutil.rmtree(directory, ignore_errors=True)
        raise HTTPException(status_code=500, detail=f"Video upload failed: {error}") from error


@app.post("/v1/sources/download", dependencies=[Depends(auth)])
async def download_source(payload: SourcePayload) -> dict[str, Any]:
    source = validate_public_source(str(payload.source))
    job_id = f"src_{secrets.token_urlsafe(12)}"
    timestamp = now_iso()
    SOURCE_ROOT.mkdir(parents=True, exist_ok=True)
    with closing(db()) as connection:
        existing = connection.execute("SELECT id, status FROM source_jobs WHERE source=? AND max_items=? AND status IN ('queued', 'running') ORDER BY created_at DESC LIMIT 1", (source, payload.max_items)).fetchone()
        if existing:
            return {"id": existing["id"], "status": existing["status"], "reused": True}
        active = connection.execute("SELECT COUNT(*) AS count FROM source_jobs WHERE status IN ('queued', 'running')").fetchone()["count"]
        if active >= MAX_ACTIVE_SOURCE_JOBS:
            raise HTTPException(status_code=429, detail="Too many source downloads are active. Wait for one to finish.")
        connection.execute(
            "INSERT INTO source_jobs (id, source, max_items, status, created_at, updated_at) VALUES (?, ?, ?, 'queued', ?, ?)",
            (job_id, source, payload.max_items, timestamp, timestamp),
        )
        connection.commit()
    if not _source_workers.submit(job_id):
        raise HTTPException(status_code=503, detail="Source worker is not ready. Retry shortly.")
    return {"id": job_id, "status": "queued"}


@app.get("/v1/sources/jobs/{job_id}", dependencies=[Depends(auth)])
async def source_status(job_id: str) -> dict[str, Any]:
    with closing(db()) as connection:
        row = connection.execute("SELECT * FROM source_jobs WHERE id=?", (job_id,)).fetchone()
    if not row:
        raise HTTPException(status_code=404, detail="Source job not found")
    return source_dict(row)


@app.get("/v1/sources/jobs/{job_id}/media/{filename:path}")
async def source_media(job_id: str, filename: str) -> FileResponse:
    with closing(db()) as connection:
        row = connection.execute("SELECT status FROM source_jobs WHERE id=?", (job_id,)).fetchone()
    if not row or row["status"] != "done":
        raise HTTPException(status_code=404, detail="Source media is not ready")
    base = (SOURCE_ROOT / job_id).resolve()
    target = (base / filename).resolve()
    if base not in target.parents or not target.is_file() or target.suffix.lower() != ".mp4":
        raise HTTPException(status_code=404, detail="Source media not found")
    return FileResponse(target, media_type="video/mp4", filename=target.name)


@app.post("/v1/processing/jobs", dependencies=[Depends(auth)])
async def start_processing(payload: ProcessingPayload) -> dict[str, Any]:
    source = validate_processing_source(str(payload.source))
    if payload.llm == "gemini" and not read_server_gemini_key():
        raise HTTPException(status_code=503, detail="GEMINI_NOT_CONFIGURED: Configure Gemini on the personal Gateway.")
    checks = pipeline_checks()
    if not checks["pipeline"]:
        raise HTTPException(status_code=503, detail="PIPELINE_UNAVAILABLE: Pipeline is not available on the processing server.")
    if not checks["storage"]:
        raise HTTPException(status_code=503, detail="STORAGE_UNAVAILABLE: Processing storage is not writable.")
    ffmpeg = _ffmpeg_capability()
    if not ffmpeg["ready"]:
        raise HTTPException(status_code=503, detail="FFMPEG_UNAVAILABLE: Install FFmpeg on the processing server.")
    job_id = f"proc_{secrets.token_urlsafe(12)}"
    timestamp = now_iso()
    PROCESSING_ROOT.mkdir(parents=True, exist_ok=True)
    with closing(db()) as connection:
        existing = connection.execute("SELECT id, status FROM processing_jobs WHERE source=? AND llm=? AND captions=? AND status IN ('queued', 'running') ORDER BY created_at DESC LIMIT 1", (source, payload.llm, payload.captions)).fetchone()
        if existing:
            return {"id": existing["id"], "status": existing["status"], "reused": True}
        active = connection.execute("SELECT COUNT(*) AS count FROM processing_jobs WHERE status IN ('queued', 'running')").fetchone()["count"]
        if active >= MAX_ACTIVE_PROCESSING_JOBS:
            raise HTTPException(status_code=429, detail="A processing job is already active. Wait for it to finish.")
        connection.execute(
            "INSERT INTO processing_jobs (id, source, llm, captions, mode, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, 'queued', ?, ?)",
            (job_id, source, payload.llm, payload.captions, payload.mode, timestamp, timestamp),
        )
        connection.commit()
    if not _processing_workers.submit(job_id):
        raise HTTPException(status_code=503, detail="Processing worker is not ready. Retry shortly.")
    return {"id": job_id, "status": "queued"}


@app.get("/v1/processing/jobs/{job_id}", dependencies=[Depends(auth)])
async def processing_status(job_id: str) -> dict[str, Any]:
    with closing(db()) as connection:
        row = connection.execute("SELECT * FROM processing_jobs WHERE id=?", (job_id,)).fetchone()
    if not row:
        raise HTTPException(status_code=404, detail="Processing job not found")
    return processing_dict(row)


@app.get("/v1/processing/jobs/{job_id}/media/{filename:path}")
async def processing_media(job_id: str, filename: str) -> FileResponse:
    with closing(db()) as connection:
        row = connection.execute("SELECT pipeline_job_id, status FROM processing_jobs WHERE id=?", (job_id,)).fetchone()
    if not row or row["status"] != "done" or not row["pipeline_job_id"]:
        raise HTTPException(status_code=404, detail="Processing media is not ready")
    base = (PROCESSING_ROOT / "jobs" / row["pipeline_job_id"] / "clips").resolve()
    target = (base / filename).resolve()
    if base not in target.parents or not target.is_file() or target.suffix.lower() != ".mp4":
        raise HTTPException(status_code=404, detail="Media not found")
    return FileResponse(target, media_type="video/mp4", filename=target.name)


@app.post("/v1/analytics/snapshots", dependencies=[Depends(auth)])
async def save_analytics_snapshot(payload: AnalyticsSnapshotPayload) -> dict[str, Any]:
    timestamp = now_iso()
    with closing(db()) as connection:
        account = connection.execute("SELECT id, platform FROM accounts WHERE id=?", (payload.account_id,)).fetchone()
        if not account:
            raise HTTPException(status_code=404, detail="Account not found")
        if account["platform"] != payload.source.split(":", 1)[0].lower() and payload.source != "manual":
            raise HTTPException(status_code=422, detail="Analytics source does not match account platform")
        snapshot_id = f"metric_{secrets.token_urlsafe(10)}"
        connection.execute(
            """INSERT INTO analytics_snapshots (id, account_id, platform, metric_date, views, likes, comments, followers, watch_time_seconds, source, fetched_at)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
               ON CONFLICT(account_id, metric_date) DO UPDATE SET views=excluded.views, likes=excluded.likes,
                 comments=excluded.comments, followers=excluded.followers, watch_time_seconds=excluded.watch_time_seconds,
                 source=excluded.source, fetched_at=excluded.fetched_at""",
            (snapshot_id, payload.account_id, account["platform"], payload.metric_date.isoformat(), payload.views, payload.likes, payload.comments, payload.followers, payload.watch_time_seconds, payload.source, timestamp),
        )
        connection.commit()
    return {"account_id": payload.account_id, "metric_date": payload.metric_date.isoformat(), "status": "stored", "source": payload.source, "fetched_at": timestamp}


@app.get("/v1/analytics/summary", dependencies=[Depends(auth)])
async def analytics_summary(days: int = 30) -> dict[str, Any]:
    days = max(1, min(days, 90))
    cutoff = (datetime.now(timezone.utc).date() - timedelta(days=days - 1)).isoformat()
    with closing(db()) as connection:
        rows = connection.execute(
            """SELECT a.id, a.platform, a.account_name, s.metric_date, s.views, s.likes, s.comments,
                      s.followers, s.watch_time_seconds, s.source, s.fetched_at
               FROM accounts a LEFT JOIN analytics_snapshots s ON s.account_id=a.id AND s.metric_date>=?
               ORDER BY a.created_at DESC, s.metric_date ASC""",
            (cutoff,),
        ).fetchall()
    grouped: dict[str, dict[str, Any]] = {}
    for row in rows:
        item = grouped.setdefault(row["id"], {"account_id": row["id"], "platform": row["platform"], "account_name": row["account_name"], "data_available": False, "days": []})
        if row["metric_date"]:
            item["data_available"] = True
            item["days"].append({key: row[key] for key in ("metric_date", "views", "likes", "comments", "followers", "watch_time_seconds", "source", "fetched_at")})
    accounts_data = list(grouped.values())
    totals = {"views": sum(day["views"] for account in accounts_data for day in account["days"]), "likes": sum(day["likes"] for account in accounts_data for day in account["days"]), "comments": sum(day["comments"] for account in accounts_data for day in account["days"])}
    return {"days": days, "from": cutoff, "to": datetime.now(timezone.utc).date().isoformat(), "totals": totals, "accounts": accounts_data}


@app.get("/v1/dashboard/summary", dependencies=[Depends(auth)])
async def summary() -> dict[str, Any]:
    with closing(db()) as connection:
        counts = connection.execute("SELECT status, COUNT(*) AS count FROM posts GROUP BY status").fetchall()
        accounts = connection.execute("SELECT COUNT(*) AS count FROM accounts WHERE status='connected'").fetchone()["count"]
        recent = connection.execute("SELECT * FROM posts ORDER BY updated_at DESC LIMIT 12").fetchall()
    return {"accounts": accounts, "posts": {row["status"]: row["count"] for row in counts}, "recent": [row_dict(row) for row in recent]}


@app.get("/v1/accounts", dependencies=[Depends(auth)])
async def accounts() -> list[dict[str, Any]]:
    with closing(db()) as connection:
        rows = connection.execute("SELECT * FROM accounts ORDER BY created_at DESC").fetchall()
    return [account_dict(row) for row in rows]


@app.post("/v1/accounts/mock", dependencies=[Depends(auth)])
async def create_mock_account(payload: AccountCreate) -> dict[str, Any]:
    if PROVIDER_MODE != "mock":
        raise HTTPException(status_code=404, detail="Mock account endpoint is disabled outside mock mode")
    account_id = f"acct_{secrets.token_urlsafe(8)}"
    timestamp = now_iso()
    with closing(db()) as connection:
        connection.execute(
            "INSERT INTO accounts (id, platform, account_name, provider_account_id, status, daily_limit, min_gap_seconds, created_at, updated_at) VALUES (?, ?, ?, ?, 'connected', ?, ?, ?, ?)",
            (account_id, payload.platform, payload.account_name, f"mock_{account_id}", ACCOUNT_DAILY_LIMIT, ACCOUNT_MIN_GAP_SECONDS, timestamp, timestamp),
        )
        connection.commit()
        row = connection.execute("SELECT * FROM accounts WHERE id=?", (account_id,)).fetchone()
    return account_dict(row)


@app.delete("/v1/accounts/{account_id}", dependencies=[Depends(auth)])
async def disconnect_account(account_id: str) -> dict[str, str]:
    with closing(db()) as connection:
        row = connection.execute("SELECT id FROM accounts WHERE id=?", (account_id,)).fetchone()
        if not row:
            raise HTTPException(status_code=404, detail="Account not found")
        connection.execute("UPDATE posts SET account_id=NULL WHERE account_id=?", (account_id,))
        connection.execute("DELETE FROM accounts WHERE id=?", (account_id,))
        connection.commit()
    return {"id": account_id, "status": "disconnected"}


@app.patch("/v1/accounts/{account_id}/policy", dependencies=[Depends(auth)])
async def update_account_policy(account_id: str, payload: AccountPolicyPayload) -> dict[str, Any]:
    with closing(db()) as connection:
        cursor = connection.execute(
            "UPDATE accounts SET status=?, daily_limit=?, min_gap_seconds=?, pause_reason=NULL, cooldown_until=NULL, updated_at=? WHERE id=?",
            (payload.status, payload.daily_limit, payload.min_gap_seconds, now_iso(), account_id),
        )
        connection.commit()
        row = connection.execute("SELECT * FROM accounts WHERE id=?", (account_id,)).fetchone()
    if cursor.rowcount == 0:
        raise HTTPException(status_code=404, detail="Account not found")
    return account_dict(row)


@app.post("/v1/accounts/{account_id}/resume", dependencies=[Depends(auth)])
async def resume_account(account_id: str) -> dict[str, Any]:
    with closing(db()) as connection:
        cursor = connection.execute(
            "UPDATE accounts SET status='connected', pause_reason=NULL, cooldown_until=NULL, updated_at=? WHERE id=?",
            (now_iso(), account_id),
        )
        connection.commit()
        row = connection.execute("SELECT * FROM accounts WHERE id=?", (account_id,)).fetchone()
    if cursor.rowcount == 0:
        raise HTTPException(status_code=404, detail="Account not found")
    return account_dict(row)


def provider_is_configured(provider: ProviderDefinition) -> bool:
    if provider.type == "ollama":
        return True
    return bool(provider.credential_ref and AI_VAULT.has(provider.credential_ref))


def provider_health_snapshot(provider: ProviderDefinition) -> dict[str, Any]:
    with closing(db()) as connection:
        stored = read_health(connection, provider.id)
    health = health_public_dict(stored) if stored else health_public_dict(check_provider_health(provider, configured=provider_is_configured(provider)))
    return {"provider": provider_public_dict(provider), "health": health}


@app.get("/v1/ai/providers", dependencies=[Depends(auth)])
async def ai_providers() -> dict[str, Any]:
    with closing(db()) as connection:
        providers = list_providers(connection)
    return {"providers": [provider_health_snapshot(provider) for provider in providers], "secret_names": AI_VAULT.list_names()}


@app.get("/v1/ai/providers/{provider_id}", dependencies=[Depends(auth)])
async def ai_provider(provider_id: str) -> dict[str, Any]:
    with closing(db()) as connection:
        provider = get_provider(connection, provider_id)
    if not provider:
        raise HTTPException(status_code=404, detail="AI provider not found")
    return provider_health_snapshot(provider)


@app.post("/v1/ai/providers", dependencies=[Depends(auth)])
async def create_ai_provider(payload: AIProviderCreatePayload) -> dict[str, Any]:
    if payload.id in {provider.id for provider in BUILT_IN_PROVIDERS}:
        raise HTTPException(status_code=409, detail="Built-in provider IDs cannot be replaced")
    credential_ref = payload.credential_ref or (f"CUSTOM_{payload.id.upper().replace('-', '_')}_API_KEY" if payload.api_key else None)
    if payload.api_key and not credential_ref:
        raise HTTPException(status_code=422, detail="credential_ref is required when api_key is provided")
    if payload.base_url and urlparse(payload.base_url).scheme not in {"http", "https"}:
        raise HTTPException(status_code=422, detail="base_url must be HTTP or HTTPS")
    provider = ProviderDefinition(
        id=payload.id,
        name=payload.name,
        type=payload.type,
        enabled=True,
        base_url=payload.base_url,
        credential_ref=credential_ref,
        capabilities=tuple(sorted(set(payload.capabilities))),
        models=tuple(ModelDefinition(
            id=f"{payload.id}:{model.model_id}", provider_id=payload.id, model_id=model.model_id,
            display_name=model.display_name, capabilities=tuple(sorted(set(model.capabilities))),
            context_window=model.context_window, supports_structured_output=model.supports_structured_output,
            supports_vision=model.supports_vision, enabled=model.enabled,
        ) for model in payload.models),
    )
    try:
        if payload.api_key and credential_ref:
            AI_VAULT.set(credential_ref, payload.api_key)
        with closing(db()) as connection:
            result = register_provider(connection, provider)
    except (ValueError, SecretVaultError) as error:
        raise HTTPException(status_code=409 if isinstance(error, ValueError) else 422, detail=str(error)) from error
    return provider_health_snapshot(result)


@app.patch("/v1/ai/providers/{provider_id}", dependencies=[Depends(auth)])
async def edit_ai_provider(provider_id: str, payload: AIProviderUpdatePayload) -> dict[str, Any]:
    with closing(db()) as connection:
        provider = get_provider(connection, provider_id)
    if not provider:
        raise HTTPException(status_code=404, detail="AI provider not found")
    changes = payload.model_dump(exclude_unset=True, exclude={"api_key"})
    if payload.api_key:
        credential_ref = payload.credential_ref or provider.credential_ref
        if not credential_ref:
            raise HTTPException(status_code=422, detail="credential_ref is required before saving api_key")
        changes["credential_ref"] = credential_ref
        try:
            AI_VAULT.set(credential_ref, payload.api_key)
        except SecretVaultError as error:
            raise HTTPException(status_code=422, detail=str(error)) from error
    try:
        with closing(db()) as connection:
            result = update_provider(connection, provider_id, **changes)
    except (KeyError, ValueError) as error:
        raise HTTPException(status_code=422, detail=str(error)) from error
    return provider_health_snapshot(result)


@app.delete("/v1/ai/providers/{provider_id}", dependencies=[Depends(auth)])
async def delete_ai_provider(provider_id: str) -> dict[str, str]:
    with closing(db()) as connection:
        provider = get_provider(connection, provider_id)
        if not provider:
            raise HTTPException(status_code=404, detail="AI provider not found")
        try:
            remove_provider(connection, provider_id)
        except (KeyError, ValueError) as error:
            raise HTTPException(status_code=409, detail=str(error)) from error
    if provider.credential_ref:
        try:
            AI_VAULT.delete(provider.credential_ref)
        except SecretVaultError:
            pass
    return {"id": provider_id, "status": "removed"}


@app.post("/v1/ai/providers/{provider_id}/enable", dependencies=[Depends(auth)])
async def enable_ai_provider(provider_id: str) -> dict[str, Any]:
    with closing(db()) as connection:
        try:
            provider = set_provider_enabled(connection, provider_id, True)
        except KeyError as error:
            raise HTTPException(status_code=404, detail="AI provider not found") from error
    return provider_health_snapshot(provider)


@app.post("/v1/ai/providers/{provider_id}/disable", dependencies=[Depends(auth)])
async def disable_ai_provider(provider_id: str) -> dict[str, Any]:
    with closing(db()) as connection:
        try:
            provider = set_provider_enabled(connection, provider_id, False)
        except KeyError as error:
            raise HTTPException(status_code=404, detail="AI provider not found") from error
    return provider_health_snapshot(provider)


@app.post("/v1/ai/providers/{provider_id}/health", dependencies=[Depends(auth)])
async def check_ai_provider_health(provider_id: str) -> dict[str, Any]:
    with closing(db()) as connection:
        provider = get_provider(connection, provider_id)
    if not provider:
        raise HTTPException(status_code=404, detail="AI provider not found")
    configured = provider_is_configured(provider)
    if provider.id == "gemini":
        probe = await asyncio.to_thread(gemini_probe)
        state = "READY" if probe.get("reachable") else {"GEMINI_NOT_CONFIGURED": "NOT_CONFIGURED", "GEMINI_AUTH_FAILED": "AUTH_ERROR", "GEMINI_MODEL_NOT_FOUND": "MODEL_ERROR", "GEMINI_QUOTA_OR_RATE_LIMIT": "RATE_LIMITED", "GEMINI_UNREACHABLE": "NETWORK_ERROR"}.get(probe.get("error_code"), "UNKNOWN_ERROR")
        health = {"provider_id": provider.id, "state": state, "configured": bool(probe.get("configured")), "reachable": bool(probe.get("reachable")), "authenticated": state not in {"AUTH_ERROR", "NOT_CONFIGURED"}, "selected_model_available": state == "READY", "required_capabilities": [], "checked_at": now_iso(), "latency_ms": probe.get("latency_ms"), "error": probe.get("error_code")}
    else:
        health = health_public_dict(await asyncio.to_thread(check_provider_health, provider, configured=configured))
    with closing(db()) as connection:
        store_health(connection, ProviderHealth(
            provider_id=health["provider_id"], state=health["state"], configured=health["configured"],
            reachable=health["reachable"], authenticated=health["authenticated"],
            selected_model_available=health["selected_model_available"],
            required_capabilities=health["required_capabilities"], checked_at=health["checked_at"],
            latency_ms=health["latency_ms"], error=health["error"],
        ))
    return {"provider": provider_public_dict(provider), "health": health}


@app.get("/v1/ai/models", dependencies=[Depends(auth)])
async def ai_models(provider_id: str | None = None) -> dict[str, Any]:
    with closing(db()) as connection:
        return {"models": [asdict(model) for model in list_models(connection, provider_id)]}


@app.get("/v1/social/capabilities", dependencies=[Depends(auth)])
async def social_capabilities() -> dict[str, Any]:
    configured = {platform: PROVIDER_MODE == "mock" for platform in ("instagram", "facebook", "tiktok", "youtube", "x")}
    credentials_present = {
        "instagram": bool(os.getenv("META_CLIENT_ID") and os.getenv("META_CLIENT_SECRET")),
        "facebook": bool(os.getenv("META_CLIENT_ID") and os.getenv("META_CLIENT_SECRET")),
        "tiktok": bool(os.getenv("TIKTOK_CLIENT_KEY") and os.getenv("TIKTOK_CLIENT_SECRET")),
        "youtube": bool(os.getenv("GOOGLE_CLIENT_ID") and os.getenv("GOOGLE_CLIENT_SECRET")),
        "x": bool(os.getenv("X_CLIENT_ID") and os.getenv("X_CLIENT_SECRET")),
    }
    return {
        "mode": PROVIDER_MODE,
        "providers": [
            {"platform": "instagram", "configured": configured["instagram"], "credentials_present": credentials_present["instagram"], "publish_mode": "professional account direct post", "analytics": "Instagram Insights", "note": "Requires Instagram professional account and Meta permissions."},
            {"platform": "facebook", "configured": configured["facebook"], "credentials_present": credentials_present["facebook"], "publish_mode": "Page publishing", "analytics": "Page insights", "note": "Requires Page access and Meta app review where applicable."},
            {"platform": "tiktok", "configured": configured["tiktok"], "credentials_present": credentials_present["tiktok"], "publish_mode": "Direct Post or Draft", "analytics": "Display/approved business scope", "note": "The provider may require product approval; Draft mode is supported by design."},
            {"platform": "youtube", "configured": configured["youtube"], "credentials_present": credentials_present["youtube"], "publish_mode": "videos.insert", "analytics": "YouTube Analytics", "note": "Uses Google OAuth and resumable uploads."},
            {"platform": "x", "configured": configured["x"], "credentials_present": credentials_present["x"], "publish_mode": "chunked media + post", "analytics": "public/private metrics", "note": "Private metrics require user context and have provider-specific windows."},
        ],
    }


@app.get("/v1/social/oauth/{platform}/start", dependencies=[Depends(auth)])
async def oauth_start(platform: str) -> dict[str, str]:
    if platform not in {"instagram", "facebook", "tiktok", "youtube", "x"}:
        raise HTTPException(status_code=400, detail="Unsupported platform")
    if PROVIDER_MODE == "mock":
        return {"url": f"{PUBLIC_BASE_URL}/oauth/mock/complete?platform={platform}"}
    raise HTTPException(status_code=501, detail=f"Live OAuth adapter for {platform} is not configured in this Gateway build")


@app.get("/oauth/mock/complete", response_class=HTMLResponse)
async def oauth_complete(platform: str) -> str:
    if PROVIDER_MODE != "mock":
        raise HTTPException(status_code=404, detail="Mock OAuth callback is disabled outside mock mode")
    account_id = f"acct_{secrets.token_urlsafe(8)}"
    timestamp = now_iso()
    with closing(db()) as connection:
        connection.execute(
            "INSERT INTO accounts (id, platform, account_name, provider_account_id, status, daily_limit, min_gap_seconds, created_at, updated_at) VALUES (?, ?, ?, ?, 'connected', ?, ?, ?, ?)",
            (account_id, platform, f"mock_{platform}_account", f"mock_{account_id}", ACCOUNT_DAILY_LIMIT, ACCOUNT_MIN_GAP_SECONDS, timestamp, timestamp),
        )
        connection.commit()
    return f"<h1>ISM mock OAuth complete</h1><p>Connected {platform}. You can close this tab and return to ISM.</p>"


@app.get("/v1/social/schedule", dependencies=[Depends(auth)])
async def list_scheduled() -> list[dict[str, Any]]:
    with closing(db()) as connection:
        rows = connection.execute("SELECT * FROM posts ORDER BY scheduled_at IS NULL, scheduled_at ASC").fetchall()
    return [row_dict(row) for row in rows]


@app.post("/v1/social/schedule", dependencies=[Depends(auth)])
async def schedule(payload: PostPayload) -> dict[str, Any]:
    return save_post(payload)


@app.patch("/v1/social/schedule/{post_id}", dependencies=[Depends(auth)])
async def update_schedule(post_id: str, payload: PostPayload) -> dict[str, Any]:
    existing = await get_post(post_id)
    if not existing:
        raise HTTPException(status_code=404, detail="Post not found")
    if existing["status"] in {"publishing", "published", "cancelled"}:
        raise HTTPException(status_code=409, detail=f"Post cannot be edited while status is {existing['status']}")
    return save_post(payload, post_id=post_id)


@app.delete("/v1/social/schedule/{post_id}", dependencies=[Depends(auth)])
async def cancel_schedule(post_id: str) -> dict[str, Any]:
    with closing(db()) as connection:
        cursor = connection.execute("UPDATE posts SET status='cancelled', updated_at=? WHERE id=? AND status IN ('draft', 'awaiting_approval', 'scheduled', 'failed')", (now_iso(), post_id))
        connection.commit()
    if cursor.rowcount == 0:
        raise HTTPException(status_code=404, detail="Post not found or already final")
    return {"id": post_id, "status": "cancelled"}


@app.post("/v1/social/publish", dependencies=[Depends(auth)])
async def publish(payload: PostPayload) -> dict[str, Any]:
    saved = save_post(payload)
    result = await publish_job(saved["id"])
    return result or saved


@app.get("/mock/published/{provider_id}")
async def mock_published(provider_id: str) -> dict[str, str]:
    if PROVIDER_MODE != "mock":
        raise HTTPException(status_code=404, detail="Mock publication endpoint is disabled outside mock mode")
    return {"status": "published", "provider_post_id": provider_id, "mode": "mock"}


DASHBOARD_HTML = r"""<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8" />
<meta name="viewport" content="width=device-width, initial-scale=1" />
<title>ISM Social Gateway</title>
<style>
:root{font-family:Inter,system-ui,sans-serif;color:#f4f0e8;background:#111214;--panel:#1b1c20;--line:#383a40;--amber:#ffb224;--muted:#a5a5aa;--red:#ff6b5f;--green:#78d49a}*{box-sizing:border-box}body{margin:0;padding:24px;background:radial-gradient(circle at top right,#2b2414,transparent 45%),#111214}main{max-width:1200px;margin:auto}.head{display:flex;justify-content:space-between;gap:16px;align-items:end;border-bottom:1px solid var(--line);padding-bottom:20px;margin-bottom:20px}.eyebrow{color:var(--amber);font-size:12px;letter-spacing:.16em;text-transform:uppercase}.head h1{margin:7px 0;font-size:36px}.muted{color:var(--muted)}button,input,select{font:inherit}button{border:1px solid var(--amber);background:transparent;color:var(--amber);padding:9px 13px;cursor:pointer;border-radius:3px}button:hover{background:var(--amber);color:#111}.cards{display:grid;grid-template-columns:repeat(5,1fr);gap:10px;margin-bottom:18px}.card,.panel{background:rgba(27,28,32,.92);border:1px solid var(--line);padding:16px;border-radius:4px}.card b{display:block;font-size:28px;margin-top:5px}.grid{display:grid;grid-template-columns:1fr 2fr;gap:18px}.panel h2{font-size:16px;margin:0 0 12px}.field{display:grid;gap:6px;margin:10px 0;color:var(--muted);font-size:13px}.field input,.field select{background:#101114;color:#f4f0e8;border:1px solid var(--line);padding:10px;border-radius:3px}.row{display:flex;gap:8px}.row>*{flex:1}.post{border-top:1px solid var(--line);padding:12px 0;display:flex;justify-content:space-between;gap:12px}.post:first-child{border-top:0}.post strong,.post small{display:block}.post small{color:var(--muted);margin-top:4px}.status{font-size:11px;color:var(--amber);white-space:nowrap}.published{color:var(--green)}.failed{color:var(--red)}.account{display:flex;justify-content:space-between;border-top:1px solid var(--line);padding:10px 0}.account:first-child{border-top:0}.notice{min-height:22px;color:var(--amber);margin-top:10px}@media(max-width:800px){.cards{grid-template-columns:repeat(2,1fr)}.grid{grid-template-columns:1fr}.head{display:block}.head button{margin-top:12px}}
</style>
</head>
<body><main>
<header class="head"><div><div class="eyebrow">ISM / private social gateway</div><h1>Publishing control room.</h1><div class="muted">Accounts, schedule, automation, and provider status.</div></div><button onclick="refresh()">Refresh</button></header>
<section class="cards" id="cards"></section>
<div class="grid"><section class="panel"><h2>Connect a test account</h2><div class="muted">Local mock mode only. Production OAuth belongs in the Gateway.</div><label class="field">Platform<select id="platform"><option>instagram</option><option>facebook</option><option>tiktok</option><option>youtube</option><option>x</option></select></label><label class="field">Account name<input id="account" value="demo-account" /></label><button onclick="addAccount()">Add mock account</button><div id="accounts" style="margin-top:14px"></div></section>
<section class="panel"><h2>Schedule a post</h2><div class="row"><label class="field">Platform<select id="postPlatform"><option>youtube</option><option>instagram</option><option>facebook</option><option>tiktok</option><option>x</option></select></label><label class="field">Account<input id="postAccount" value="demo-account" /></label></div><label class="field">Public media URL<input id="media" value="https://example.com/demo.mp4" /></label><label class="field">Title<input id="title" value="ISM local test post" /></label><label class="field">Caption<input id="caption" value="Testing automatic publishing locally." /></label><label class="field">Due time<input id="due" type="datetime-local" /></label><button onclick="schedulePost()">Schedule auto-publish</button><div class="notice" id="notice"></div></section></div>
<section class="panel" style="margin-top:18px"><h2>Recent jobs</h2><div id="posts"></div></section>
</main>
<script>
const $=id=>document.getElementById(id); const api=(path,opts)=>fetch(path,{headers:{'Content-Type':'application/json'},...opts}).then(async r=>{const data=await r.json();if(!r.ok)throw new Error(data.detail||r.status);return data});
function localDue(){const d=new Date(Date.now()+120000);d.setSeconds(0,0);$('due').value=d.toISOString().slice(0,16)}
async function refresh(){try{const [s,a,p]=await Promise.all([api('/v1/dashboard/summary'),api('/v1/accounts'),api('/v1/social/schedule')]);$('cards').innerHTML=[['Accounts',s.accounts],['Scheduled',s.posts.scheduled||0],['Publishing',s.posts.publishing||0],['Published',s.posts.published||0],['Failed',s.posts.failed||0]].map(x=>`<div class="card"><span class="muted">${x[0]}</span><b>${x[1]}</b></div>`).join('');$('accounts').innerHTML=a.map(x=>`<div class="account"><span>${x.platform} · ${x.account_name}<small>${x.publish_count||0}/${x.daily_limit||0} today · gap ${x.min_gap_seconds||0}s${x.pause_reason?` · ${x.pause_reason}`:''}</small></span><span class="status ${x.status}">${x.status}</span></div>`).join('')||'<div class="muted">No accounts yet.</div>';$('posts').innerHTML=p.map(x=>`<div class="post"><div><strong>${x.title||x.platform}</strong><small>${x.platform} · ${x.scheduledAt||'no time'} · ${x.account||''}</small>${x.error?`<small class="failed">${x.error}</small>`:''}</div><span class="status ${x.status}">${x.status}</span></div>`).join('')||'<div class="muted">No jobs yet.</div>'}catch(e){$('notice').textContent=e.message}}
async function addAccount(){try{await api('/v1/accounts/mock',{method:'POST',body:JSON.stringify({platform:$('platform').value,account_name:$('account').value})});$('notice').textContent='Mock account connected.';await refresh()}catch(e){$('notice').textContent=e.message}}
async function schedulePost(){try{await api('/v1/social/schedule',{method:'POST',body:JSON.stringify({platform:$('postPlatform').value,account:$('postAccount').value,mediaUrl:$('media').value,title:$('title').value,caption:$('caption').value,scheduledAt:new Date($('due').value).toISOString(),autoPublish:true,status:'scheduled'})});$('notice').textContent='Scheduled. The background worker will publish it in mock mode.';await refresh()}catch(e){$('notice').textContent=e.message}}
localDue();refresh();setInterval(refresh,5000)
</script></body></html>"""
