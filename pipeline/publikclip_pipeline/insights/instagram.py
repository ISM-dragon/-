"""Instagram feedback loop — the user's OWN Meta app, no middleman.

Decision #4 + the verified Meta reality (2026-08, raw/meta-insights-
feedback-loop.md): "Instagram API with Instagram Login" (Business Login),
Standard Access covers self-serving apps with NO App Review for
instagram_business_basic + instagram_business_manage_insights. Each user
creates their own app, pastes its App ID/Secret here, and completes OAuth
in their browser against a localhost callback. Tokens: 60-day long-lived,
refreshable after 24 h, indefinitely renewable.

Scope honesty: content_publish exists, but Meta ingests publish media BY
PUBLIC URL — a local-first app has none, so v1 tracks manually-posted
Reels (one-click link after posting the exported clip) and pulls real
outcomes. Auto-publish would require a hosting hop; deferred, documented.

Reels metrics (2026 names — `plays`/`impressions` were retired Apr 2025):
views, reach, likes, comments, saved, shares, reposts, total_interactions,
ig_reels_avg_watch_time (ms), ig_reels_video_view_total_time (ms),
reels_skip_rate. Metrics stabilize within ~48 h of posting.
"""

from __future__ import annotations

import http.server
import json
import secrets as pysecrets
import threading
import time
import urllib.parse
import webbrowser
from dataclasses import dataclass

import httpx

from .. import config

AUTH_URL = "https://www.instagram.com/oauth/authorize"
TOKEN_URL = "https://api.instagram.com/oauth/access_token"
GRAPH = "https://graph.instagram.com"
API_VERSION = "v23.0"
SCOPES = "instagram_business_basic,instagram_business_manage_insights"
CALLBACK_PORT = 8137
REEL_METRICS = (
    "views,reach,likes,comments,saved,shares,reposts,total_interactions,"
    "ig_reels_avg_watch_time,ig_reels_video_view_total_time,reels_skip_rate"
)
# thumbnail_url: the Reel's cover (VIDEO media only). media_url: downloadable
# MP4 — omitted by Meta whenever the media contains copyrighted material,
# i.e. most manually-posted Reels with trending audio. Both are signed CDN
# URLs that EXPIRE: download immediately, persist only files + permalink.
MEDIA_FIELDS = (
    "id,caption,media_type,media_product_type,permalink,timestamp,"
    "thumbnail_url,media_url"
)
MEDIA_PAGE_CAP = 8  # 8 × 25 media — nobody's calibration window is deeper
HTTP_TIMEOUT = 30.0


class IgError(Exception):
    """User-actionable Instagram API failure."""


def _store_path():
    return config.home_dir() / "instagram.json"


def load_connection() -> dict | None:
    path = _store_path()
    if not path.exists():
        return None
    try:
        return json.loads(path.read_text())
    except (json.JSONDecodeError, OSError):
        return None


def save_connection(data: dict) -> None:
    path = _store_path()
    config.ensure_home()
    path.write_text(json.dumps(data, indent=1))
    path.chmod(0o600)


@dataclass
class CallbackResult:
    code: str | None = None
    error: str | None = None


def _wait_for_callback(state: str, timeout_sec: float = 300.0) -> CallbackResult:
    """Tiny localhost server that catches the OAuth redirect once."""
    result = CallbackResult()
    done = threading.Event()

    class Handler(http.server.BaseHTTPRequestHandler):
        def do_GET(self):  # noqa: N802
            query = urllib.parse.parse_qs(urllib.parse.urlparse(self.path).query)
            if query.get("state", [""])[0] != state:
                result.error = "OAuth state mismatch — try connecting again."
            elif "code" in query:
                result.code = query["code"][0].split("#")[0]
            else:
                result.error = query.get("error_description", ["Authorization was denied."])[0]
            self.send_response(200)
            self.send_header("Content-Type", "text/html")
            self.end_headers()
            body = (
                "<html><body style='font-family:sans-serif;background:#0e0f12;color:#ece9e2;"
                "display:grid;place-items:center;height:100vh'><div><h2>"
                + ("publikclip is connected." if result.code else "Connection failed.")
                + "</h2><p>You can close this tab.</p></div></body></html>"
            )
            self.wfile.write(body.encode())
            done.set()

        def log_message(self, *args):  # silence
            pass

    server = http.server.HTTPServer(("127.0.0.1", CALLBACK_PORT), Handler)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    try:
        if not done.wait(timeout_sec):
            result.error = "Timed out waiting for the browser authorization."
    finally:
        server.shutdown()
    return result


def redirect_uri() -> str:
    return f"http://localhost:{CALLBACK_PORT}/callback"


def connect(app_id: str, app_secret: str, open_browser: bool = True) -> dict:
    """Full OAuth dance against the user's own app. Blocks until the browser
    round-trip completes. Returns + persists the connection."""
    state = pysecrets.token_urlsafe(16)
    params = {
        "client_id": app_id.strip(),
        "redirect_uri": redirect_uri(),
        "scope": SCOPES,
        "response_type": "code",
        "state": state,
        "enable_fb_login": "0",
        "force_authentication": "0",
    }
    url = AUTH_URL + "?" + urllib.parse.urlencode(params)
    if open_browser:
        webbrowser.open(url)
    result = _wait_for_callback(state)
    if not result.code:
        raise IgError(result.error or "Authorization failed.")

    res = httpx.post(
        TOKEN_URL,
        data={
            "client_id": app_id.strip(),
            "client_secret": app_secret.strip(),
            "grant_type": "authorization_code",
            "redirect_uri": redirect_uri(),
            "code": result.code,
        },
        timeout=HTTP_TIMEOUT,
    )
    if res.status_code != 200:
        raise IgError(f"Token exchange failed ({res.status_code}): {res.text[:300]}")
    short = res.json()

    res = httpx.get(
        f"{GRAPH}/access_token",
        params={
            "grant_type": "ig_exchange_token",
            "client_secret": app_secret.strip(),
            "access_token": short["access_token"],
        },
        timeout=HTTP_TIMEOUT,
    )
    if res.status_code != 200:
        raise IgError(f"Long-lived token exchange failed: {res.text[:300]}")
    long_lived = res.json()

    me = httpx.get(
        f"{GRAPH}/{API_VERSION}/me",
        params={"fields": "user_id,username", "access_token": long_lived["access_token"]},
        timeout=HTTP_TIMEOUT,
    ).json()

    connection = {
        "app_id": app_id.strip(),
        "username": me.get("username"),
        "user_id": me.get("user_id") or me.get("id"),
        "access_token": long_lived["access_token"],
        "token_obtained_at": time.time(),
        "expires_in": long_lived.get("expires_in"),
    }
    save_connection(connection)
    return connection


def refresh_if_needed(connection: dict) -> dict:
    """Refresh past the 24 h mark; tokens live 60 days but refreshing on
    every pull keeps the clock permanently reset."""
    age = time.time() - connection.get("token_obtained_at", 0)
    if age < 24 * 3600:
        return connection
    res = httpx.get(
        f"{GRAPH}/refresh_access_token",
        params={"grant_type": "ig_refresh_token", "access_token": connection["access_token"]},
        timeout=HTTP_TIMEOUT,
    )
    if res.status_code == 200:
        data = res.json()
        connection["access_token"] = data["access_token"]
        connection["token_obtained_at"] = time.time()
        connection["expires_in"] = data.get("expires_in")
        save_connection(connection)
    return connection


def _drop_named_field(fields: str, error_text: str) -> str | None:
    """If the API error names one of our comma-joined fields/metrics, return
    the list without it; None when nothing matches (nothing left to retry)."""
    remaining = [f for f in fields.split(",") if f and f not in error_text]
    if len(remaining) == len(fields.split(",")) or not remaining:
        return None
    return ",".join(remaining)


def recent_media(
    connection: dict,
    limit: int = 25,
    stop_at: set[str] | None = None,
) -> list[dict]:
    """Newest-first media list, paginated until a known id (`stop_at`) is
    reached or the page cap runs out. Field names the account's token rejects
    (e.g. media_product_type on some Instagram-Login setups) are dropped and
    the request retried — thumbnails must not die on an optional field."""
    fields = MEDIA_FIELDS
    url = f"{GRAPH}/{API_VERSION}/{connection['user_id']}/media"
    params: dict = {
        "fields": fields,
        "limit": limit,
        "access_token": connection["access_token"],
    }
    out: list[dict] = []
    for _ in range(MEDIA_PAGE_CAP):
        res = httpx.get(url, params=params, timeout=HTTP_TIMEOUT)
        if res.status_code == 400:
            trimmed = _drop_named_field(params["fields"], res.text)
            if trimmed:
                params["fields"] = trimmed
                continue
        if res.status_code != 200:
            raise IgError(f"Media list failed: {res.text[:300]}")
        body = res.json()
        page = body.get("data", [])
        for m in page:
            if stop_at and m.get("id") in stop_at:
                return out
            out.append(m)
        next_url = (body.get("paging") or {}).get("next")
        if not next_url or not page:
            return out
        url, params = next_url, {}  # paging.next embeds all params
    return out


def media_node(connection: dict, media_id: str, fields: str = MEDIA_FIELDS) -> dict:
    """Single-node fetch — used to mint a fresh signed CDN URL when a cached
    one expired between the list call and the download."""
    res = httpx.get(
        f"{GRAPH}/{API_VERSION}/{media_id}",
        params={"fields": fields, "access_token": connection["access_token"]},
        timeout=HTTP_TIMEOUT,
    )
    if res.status_code != 200:
        raise IgError(f"Media fetch failed for {media_id}: {res.text[:300]}")
    return res.json()


def media_insights(connection: dict, media_id: str) -> dict:
    """Insights with graceful metric fallback: if the API rejects a metric
    name (accounts/API versions differ, e.g. reels_skip_rate), drop it and
    retry — a missing column must never cost the whole snapshot."""
    metrics = REEL_METRICS
    while True:
        res = httpx.get(
            f"{GRAPH}/{API_VERSION}/{media_id}/insights",
            params={"metric": metrics, "access_token": connection["access_token"]},
            timeout=HTTP_TIMEOUT,
        )
        if res.status_code == 200:
            break
        trimmed = _drop_named_field(metrics, res.text) if res.status_code == 400 else None
        if trimmed is None:
            raise IgError(f"Insights failed for {media_id}: {res.text[:300]}")
        metrics = trimmed
    out: dict = {}
    for item in res.json().get("data", []):
        values = item.get("values") or [{}]
        out[item["name"]] = values[0].get("value")
    return out


# --- Thumbnail cache + duration probe ---------------------------------------


def thumbs_dir():
    d = config.home_dir() / "ig_thumbs"
    d.mkdir(parents=True, exist_ok=True)
    return d


def cache_thumbnail(connection: dict, media: dict) -> str | None:
    """Download the Reel's cover image to a stable local path. The signed URL
    expires, so this runs at sync time; on a stale URL the node is re-fetched
    once for a fresh one. Returns the local path, or None (caller falls back
    to the linked clip's own thumbnail)."""
    media_id = media["id"]
    dest = thumbs_dir() / f"{media_id}.jpg"
    if dest.exists():
        return str(dest)
    url = media.get("thumbnail_url") or (
        media.get("media_url") if media.get("media_type") == "IMAGE" else None
    )
    for attempt in range(2):
        if not url:
            return None
        try:
            res = httpx.get(url, timeout=HTTP_TIMEOUT, follow_redirects=True)
            if res.status_code == 200 and res.content:
                dest.write_bytes(res.content)
                return str(dest)
        except httpx.HTTPError:
            pass
        if attempt == 0:
            try:
                url = media_node(connection, media_id, "id,thumbnail_url").get("thumbnail_url")
            except IgError:
                return None
    return None


def probe_duration(media_url: str) -> float | None:
    """Header-only ffprobe over the CDN URL (no full download). Returns None
    on copyright-omitted media (no URL reaches here then), network trouble,
    or a missing ffprobe — matching degrades gracefully without it."""
    import subprocess

    from ..render import ffmpeg_bin

    try:
        out = subprocess.run(
            [
                ffmpeg_bin.ffprobe(), "-v", "error", "-print_format", "json",
                "-show_entries", "format=duration", media_url,
            ],
            capture_output=True,
            timeout=config.PROBE_TIMEOUT,
            check=True,
        )
        duration = json.loads(out.stdout).get("format", {}).get("duration")
        return round(float(duration), 2) if duration else None
    except Exception:  # noqa: BLE001 — every failure mode means "unknown"
        return None
