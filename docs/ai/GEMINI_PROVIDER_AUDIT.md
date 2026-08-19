# Gemini Provider Audit

## Source of truth

The actual provider is `pipeline/publikclip_pipeline/scoring/llm.py`. `GeminiClient` uses the Google Generative Language REST endpoint with the rolling model alias `gemini-flash-latest`, JSON response schemas, and optional frame inputs for the visual pass.

The canonical Gateway credential is `GEMINI_API_KEY`. The provider also accepts the legacy desktop variable `PUBLIKCLIP_GEMINI_API_KEY` for local desktop compatibility. When Gateway starts a remote Pipeline child it sets `GEMINI_API_KEY` and `PUBLIKCLIP_DISABLE_LOCAL_SECRETS=1`; it does not pass a key in a job payload, URL, command-line argument, or log line.

## Configuration and fallback

Desktop local runs may still use the existing private `~/.publikclip/secrets.json` mechanism. Remote Android runs do not use that file. Ollama remains a separate local fallback and does not require a Gemini key.

## Error contract

`LlmError` now carries a stable code and a secret-safe message. The provider maps missing configuration to `GEMINI_NOT_CONFIGURED`, HTTP 401/403 to `GEMINI_AUTH_FAILED`, HTTP 429/quota and billing stops to `GEMINI_QUOTA_EXCEEDED`, timeouts to `GEMINI_TIMEOUT`, network failures to `GEMINI_NETWORK_ERROR`, and malformed provider responses to `GEMINI_RESPONSE_INVALID`.

The CLI carries `error_code` in its existing JSONL result event. The Gateway stores that code in `processing_jobs.error_code` and returns it through the existing status contract.

## Diagnostic

`GET /v1/diagnostics/gemini` is authenticated through the existing Gateway bearer token. It calls the same `GeminiClient` used by scoring with the smallest safe prompt, `Return exactly the word OK.`, and does not send user video. It returns only a status, provider, model, and optional latency. The API never returns the key, a partial key, a hash, headers, or a raw provider error.

## Verification

Unit tests cover no-key diagnostics, capability redaction, canonical child environment inheritance, removal of legacy key variables from the child environment, and stable error classification. Real-key integration testing must be run by the owner on the processing server and must never commit or print the key.
