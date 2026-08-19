# ISM Gateway Synchronization Report

Date: 2026-08-19

This branch synchronizes the backend contract from `ISM-dragon/-1` into the primary Android repository `ISM-dragon/-` without replacing the Kotlin/Jetpack Compose application. The Android application files were intentionally left untouched; the synchronized scope is the shared Gateway and Python Pipeline layer.

## Synchronized scope

The primary repository now includes the secure Gemini server-side configuration and diagnostics, `processing_service.py`, `provider_registry.py`, `secret_vault.py`, `worker_queue.py`, persistent worker support, stable Pipeline error codes, provider routing, and the corresponding Gateway tests and documentation. The Pipeline provider lookup accepts canonical `GEMINI_API_KEY` while preserving legacy compatibility variables.

The primary Gateway exposes the shared source, processing, analytics, accounts, social scheduling, Gemini diagnostic, AI provider registry, worker diagnostic, and personal AI routes. The primary Android UI remains native Kotlin and is not replaced by the React/Tauri UI.

## Runtime inspection

The current sandbox does not expose a remote production host or a systemd/Docker deployment for the user's external server. The only visible Gateway process is a local test process on `127.0.0.1:8799` running from the Tauri repository. Port `8787` is not listening, and no Gateway systemd unit or Docker container is visible.

The local process health endpoint returns HTTP 200. Authenticated capabilities report `pipeline=true` and `ffmpeg=true`, but `gemini=false`. The authenticated Gemini diagnostic returns HTTP 503 with stable code `GEMINI_NOT_CONFIGURED`. The process environment contains `GATEWAY_TOKEN` and `ISM_PIPELINE_DIR`, but not `GEMINI_API_KEY` or `ISM_PROCESSING_ROOT`. The recent log contains normal startup and health requests, one expected Gemini 503, one expected unauthenticated 401, and one processing-job 503 caused by missing Gemini configuration; no traceback or process crash was observed.

## Validation

The synchronized primary Gateway passes 27 Gateway tests and 27 Pipeline tests, plus Python compilation and `git diff --check`. The Android Kotlin app was not changed by this backend synchronization. Running the Gateway on a real remote server still requires that host's credentials or connected shell; this sandbox check only describes the locally visible runtime.

## Required server configuration

Set `GATEWAY_TOKEN`, `GEMINI_API_KEY`, `ISM_PIPELINE_DIR`, `ISM_PROCESSING_ROOT`, `PUBLIC_BASE_URL`, and `REQUIRE_GATEWAY_TOKEN=true` on the actual server. Do not place the Gemini key in Android, payloads, Git, or the release assets. Restart the Gateway, then verify `/health`, authenticated `/v1/processing/capabilities`, and authenticated `/v1/diagnostics/gemini` before using Android `CUT IT`.
