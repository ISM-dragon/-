# Processing Gateway Audit

## Existing contract

The Gateway keeps the existing SQLite `processing_jobs` table, external job IDs, status values `queued`, `running`, `done`, and `failed`, and the Android paths `POST /v1/processing/jobs`, `GET /v1/processing/jobs/{id}`, and `/media/{filename}`.

## Execution boundary

`gateway/main.py` validates the public source and concurrency limit, inserts the job, and starts a background worker. The worker calls the small `gateway/processing_service.py` boundary, which prepares `PUBLIKCLIP_HOME`, `PYTHONPATH`, the canonical `GEMINI_API_KEY`, and `PUBLIKCLIP_DISABLE_LOCAL_SECRETS=1`. The existing `publikclip` CLI remains responsible for the Python stages and JSONL progress.

## Stages and results

The worker stores observed JSONL progress stages without fabricating video progress. A completed result is assembled from the existing ingest, score, render, events, and candidates checkpoints. Local `dir` paths are removed from the Android response, while rendered outputs are exposed as safe Gateway media URLs in both `render.outputs` and `artifacts`.

## Restart and concurrency

Startup marks stale `queued` and `running` jobs as failed with a restart message. `MAX_ACTIVE_PROCESSING_JOBS` prevents unbounded CPU/GPU work. A duplicate active request for the same source, LLM mode, and caption preset reuses the existing active job.

## Failure contract

Worker failures persist an `error_code` alongside the existing user-facing message. Examples include `GEMINI_NOT_CONFIGURED`, `GEMINI_AUTH_FAILED`, `GEMINI_QUOTA_EXCEEDED`, `PIPELINE_UNAVAILABLE`, `PIPELINE_START_FAILED`, `PIPELINE_RESULT_INVALID`, and `FFMPEG_UNAVAILABLE`.

## Android health flow

Android calls `/health`, `/v1/processing/capabilities`, and, for Gemini mode, `/v1/diagnostics/gemini` before creating a job. The Studio `PROCESSING ENGINE` card shows Gateway, Pipeline, Gemini, and FFmpeg states. If a required check fails, `CUT IT` does not create a processing job.

## Known validation boundary

Unit and contract tests can verify the worker contract, environment isolation, error classification, endpoint behavior, and URL safety. A real MP4 end-to-end PASS requires the owner’s processing machine to have FFmpeg, yt-dlp, Pipeline dependencies, a valid Gemini key, and an authenticated Gateway; no real secret is used in repository tests.
