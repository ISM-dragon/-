"""Secret storage boundary for the private ISM Gateway.

Environment variables are preferred for production. The optional file backend is
only a local fallback, is written atomically with mode 0600, and is never exposed
through ordinary configuration or provider metadata APIs.
"""

from __future__ import annotations

import json
import os
import secrets
from pathlib import Path
from typing import Iterable


_ENV_ALIASES: dict[str, tuple[str, ...]] = {
    "GEMINI_API_KEY": ("PUBLIKCLIP_GEMINI_API_KEY", "GEMINI_API_KEY"),
    "OPENAI_API_KEY": ("OPENAI_API_KEY",),
    "ANTHROPIC_API_KEY": ("ANTHROPIC_API_KEY",),
    "OPENROUTER_API_KEY": ("OPENROUTER_API_KEY",),
}


class SecretVaultError(RuntimeError):
    """Raised when a secret cannot be stored or read safely."""


class SecretVault:
    """Small owner-only secret boundary.

    The vault deliberately has no method that returns all values. ``list_names``
    returns names only, and callers should use ``has`` unless they are an adapter
    about to make a provider request.
    """

    def __init__(self, file_path: Path | None = None) -> None:
        self.file_path = file_path
        if self.file_path:
            self.file_path.parent.mkdir(parents=True, exist_ok=True)

    def _environment_value(self, name: str) -> str | None:
        for alias in _ENV_ALIASES.get(name, (f"ISM_SECRET_{name}", name)):
            value = os.getenv(alias, "").strip()
            if value:
                return value
        return None

    def _read_file(self) -> dict[str, str]:
        if not self.file_path or not self.file_path.exists():
            return {}
        try:
            raw = json.loads(self.file_path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError) as error:
            raise SecretVaultError(f"Could not read secret vault: {error}") from error
        if not isinstance(raw, dict):
            raise SecretVaultError("Secret vault must contain a JSON object.")
        return {str(key): str(value) for key, value in raw.items() if isinstance(value, str)}

    def _write_file(self, values: dict[str, str]) -> None:
        if not self.file_path:
            raise SecretVaultError("File-backed secret storage is not enabled.")
        temporary = self.file_path.with_name(f".{self.file_path.name}.{secrets.token_hex(6)}.tmp")
        try:
            temporary.write_text(json.dumps(values, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
            os.chmod(temporary, 0o600)
            temporary.replace(self.file_path)
            os.chmod(self.file_path, 0o600)
        except OSError as error:
            temporary.unlink(missing_ok=True)
            raise SecretVaultError(f"Could not write secret vault: {error}") from error

    def set(self, name: str, value: str) -> None:
        normalized = name.strip().upper()
        if not normalized or not value.strip():
            raise SecretVaultError("Secret name and value are required.")
        if self._environment_value(normalized):
            raise SecretVaultError(f"Secret {normalized} is managed by the environment and cannot be overwritten at runtime.")
        values = self._read_file()
        values[normalized] = value.strip()
        self._write_file(values)

    def get(self, name: str) -> str | None:
        normalized = name.strip().upper()
        environment_value = self._environment_value(normalized)
        if environment_value:
            return environment_value
        return self._read_file().get(normalized)

    def delete(self, name: str) -> None:
        normalized = name.strip().upper()
        if self._environment_value(normalized):
            raise SecretVaultError(f"Secret {normalized} is managed by the environment and cannot be deleted at runtime.")
        values = self._read_file()
        if normalized in values:
            values.pop(normalized)
            self._write_file(values)

    def has(self, name: str) -> bool:
        return bool(self.get(name))

    def list_names(self) -> list[str]:
        names = set(self._read_file())
        for name, aliases in _ENV_ALIASES.items():
            if any(os.getenv(alias, "").strip() for alias in aliases):
                names.add(name)
        return sorted(names)

    def names(self, names: Iterable[str]) -> list[str]:
        """Return only the names from ``names`` that are present."""
        return [name for name in names if self.has(name)]
