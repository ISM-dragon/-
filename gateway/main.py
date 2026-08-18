from __future__ import annotations

import asyncio
import hashlib
import json
import os
import secrets
import sqlite3
from contextlib import closing
from datetime import datetime, time, timedelta, timezone
from pathlib import Path
from typing import Any

from fastapi import Depends, FastAPI, HTTPException, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import HTMLResponse, RedirectResponse
from pydantic import BaseModel, Field, HttpUrl

ROOT = Path(__file__).resolve().parent
DB_PATH = Path(os.getenv("ISM_GATEWAY_DB", str(ROOT / "gateway.db")))
GATEWAY_TOKEN = os.getenv("GATEWAY_TOKEN", "")
PROVIDER_MODE = os.getenv("PROVIDER_MODE", "mock")
PUBLISH_INTERVAL_SECONDS = max(10, int(os.getenv("PUBLISH_INTERVAL_SECONDS", "30")))
PUBLIC_BASE_URL = os.getenv("PUBLIC_BASE_URL", "http://127.0.0.1:8787").rstrip("/")
ACCOUNT_DAILY_LIMIT = max(1, int(os.getenv("ACCOUNT_DAILY_LIMIT", "10")))
ACCOUNT_MIN_GAP_SECONDS = max(0, int(os.getenv("ACCOUNT_MIN_GAP_SECONDS", "60")))
MAX_PROVIDER_ATTEMPTS = max(1, int(os.getenv("MAX_PROVIDER_ATTEMPTS", "3")))

app = FastAPI(title="ISM Social Gateway", version="0.1.0")
app.add_middleware(
    CORSMiddleware,
    allow_origins=os.getenv("CORS_ORIGINS", "*").split(","),
    allow_credentials=False,
    allow_methods=["*"],
    allow_headers=["*"],
)

_scheduler_task: asyncio.Task | None = None


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
        ]
        for table, column, definition in migrations:
            existing = {row[1] for row in connection.execute(f"PRAGMA table_info({table})")}
            if column not in existing:
                connection.execute(f"ALTER TABLE {table} ADD COLUMN {column} {definition}")
        connection.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_posts_idempotency ON posts(idempotency_key)")
        connection.commit()


async def auth(request: Request) -> None:
    if not GATEWAY_TOKEN:
        return
    supplied = request.headers.get("authorization", "")
    if supplied != f"Bearer {GATEWAY_TOKEN}":
        raise HTTPException(status_code=401, detail="Invalid Gateway token")


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
    scheduled = parse_time(payload.scheduledAt)
    scheduled_iso = scheduled.isoformat() if scheduled else None
    timestamp = now_iso()
    status = payload.status if payload.status in {"draft", "awaiting_approval", "scheduled"} else "scheduled"
    with closing(db()) as connection:
        account = account_for_post(connection, payload)
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
              status=CASE WHEN posts.status IN ('published', 'cancelled') THEN posts.status ELSE excluded.status END,
              idempotency_key=excluded.idempotency_key, next_attempt_at=NULL, error=NULL, updated_at=excluded.updated_at
            """,
            (
                identifier, payload.platform, account_id, account_name, str(payload.mediaUrl),
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
        connection.execute(
            "UPDATE posts SET status='publishing', attempts=attempts+1, error=NULL, next_attempt_at=NULL, updated_at=? WHERE id=?",
            (now_iso(), post_id),
        )
        connection.commit()
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
    _scheduler_task = asyncio.create_task(scheduler_loop())


@app.on_event("shutdown")
async def shutdown() -> None:
    if _scheduler_task:
        _scheduler_task.cancel()
        await asyncio.gather(_scheduler_task, return_exceptions=True)


@app.get("/health")
async def health() -> dict[str, Any]:
    return {"ok": True, "provider_mode": PROVIDER_MODE, "scheduler_interval_seconds": PUBLISH_INTERVAL_SECONDS}


@app.get("/", response_class=HTMLResponse)
async def dashboard() -> str:
    return DASHBOARD_HTML


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


@app.get("/v1/social/oauth/{platform}/start", dependencies=[Depends(auth)])
async def oauth_start(platform: str) -> dict[str, str]:
    if platform not in {"instagram", "facebook", "tiktok", "youtube", "x"}:
        raise HTTPException(status_code=400, detail="Unsupported platform")
    return {"url": f"{PUBLIC_BASE_URL}/oauth/mock/complete?platform={platform}"}


@app.get("/oauth/mock/complete", response_class=HTMLResponse)
async def oauth_complete(platform: str) -> str:
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
    if not await get_post(post_id):
        raise HTTPException(status_code=404, detail="Post not found")
    return save_post(payload, post_id=post_id)


@app.delete("/v1/social/schedule/{post_id}", dependencies=[Depends(auth)])
async def cancel_schedule(post_id: str) -> dict[str, Any]:
    with closing(db()) as connection:
        cursor = connection.execute("UPDATE posts SET status='cancelled', updated_at=? WHERE id=? AND status NOT IN ('published', 'cancelled')", (now_iso(), post_id))
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
