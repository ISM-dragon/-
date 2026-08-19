"""Gateway boundary for launching the existing publikclip pipeline.

The Gateway owns server-side credentials and maps the canonical GEMINI_API_KEY
into the provider environment. Secrets never travel in job JSON, CLI arguments,
URLs, or log messages.
"""

from __future__ import annotations

import os
import shutil
import sys
from pathlib import Path


def pipeline_environment(processing_root: Path, pipeline_dir: Path, gemini_api_key: str) -> dict[str, str]:
    environment = os.environ.copy()
    environment["PUBLIKCLIP_HOME"] = str(processing_root)
    environment["PYTHONPATH"] = f"{pipeline_dir}{os.pathsep}{environment.get('PYTHONPATH', '')}".rstrip(os.pathsep)
    # The Gateway is the source of truth for remote processing. This prevents
    # a server-side secrets.json from silently overriding the Gateway config.
    environment["PUBLIKCLIP_DISABLE_LOCAL_SECRETS"] = "1"
    if gemini_api_key:
        environment["GEMINI_API_KEY"] = gemini_api_key
        environment.pop("PUBLIKCLIP_GEMINI_API_KEY", None)
    else:
        environment.pop("GEMINI_API_KEY", None)
        environment.pop("PUBLIKCLIP_GEMINI_API_KEY", None)
    return environment


def pipeline_command(pipeline_dir: Path, pipeline_bin: str, environment: dict[str, str]) -> list[str]:
    if pipeline_bin:
        return [pipeline_bin, "--jsonl"]
    uv = shutil.which("uv", path=environment.get("PATH"))
    if uv and (pipeline_dir / "pyproject.toml").exists():
        return [uv, "run", "--project", str(pipeline_dir), "publikclip", "--jsonl"]
    return [sys.executable, "-m", "publikclip_pipeline.cli", "--jsonl"]


def pipeline_available(pipeline_dir: Path, pipeline_bin: str) -> tuple[bool, str]:
    if pipeline_bin:
        path = Path(pipeline_bin)
        if path.is_file() and os.access(path, os.X_OK):
            return True, "Configured pipeline executable is available."
        return False, "Configured pipeline executable was not found or is not executable."
    if not (pipeline_dir / "publikclip_pipeline" / "cli.py").is_file():
        return False, "Pipeline CLI is missing."
    return True, "Pipeline CLI is present; runtime dependencies are checked when the worker starts."


def classify_gemini_error(error: BaseException) -> tuple[str, str]:
    code = getattr(error, "code", "")
    if code == "GEMINI_NOT_CONFIGURED":
        return "not_configured", code
    if code == "GEMINI_AUTH_FAILED":
        return "auth_failed", code
    if code == "GEMINI_QUOTA_EXCEEDED":
        return "quota", code
    if code == "GEMINI_TIMEOUT":
        return "timeout", code
    if code == "GEMINI_NETWORK_ERROR":
        return "network_error", code
    if code == "GEMINI_RESPONSE_INVALID":
        return "unknown_error", code
    text = str(error).lower()
    if "not configured" in text or "no gemini api key" in text:
        return "not_configured", "GEMINI_NOT_CONFIGURED"
    if "401" in text or "403" in text or "rejected" in text:
        return "auth_failed", "GEMINI_AUTH_FAILED"
    if "429" in text or "quota" in text or "billing" in text:
        return "quota", "GEMINI_QUOTA_EXCEEDED"
    if "timeout" in text or "timed out" in text:
        return "timeout", "GEMINI_TIMEOUT"
    return "unknown_error", "AI_FAILED"
