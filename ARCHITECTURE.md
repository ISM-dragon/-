# Opus Pro AI Architecture

## Overview

The app follows a practical layered architecture. Compose screens render state, screen-level ViewModels coordinate user actions, repositories expose application data, Room stores durable local data, and remote services handle AI providers and publishing.

```text
Compose Screen
     |
     v
Screen ViewModel / UI State
     |
     v
OpusRepository + use-case-shaped methods
     |                    \
     v                     v
Room DAOs               GeminiClipService
     |                     |
     v                     v
SQLite              AI providers / publishing APIs
```

## Processing lifecycle

Video processing is persisted before it is scheduled. `OpusRepository.processNewVideo()` stores a `ProcessingRequestEntity`, enqueues a unique `VideoProcessingWorker`, and waits for the WorkManager result. The Worker loads the request from Room, executes `processNewVideoInternal()`, and returns the generated project ID. Temporary network errors use WorkManager retry with exponential backoff.

Auto-publishing is scheduled as a separate unique `AutoPublishWorker` after processing succeeds. This avoids coupling publishing to the screen lifecycle and prevents duplicate scheduling for the same project.

## Data and pagination

Projects, favorite clips, and repurposing history expose Paging 3 streams. Search is debounced in `ProjectsScreen` and applied in Room queries, rather than filtering complete tables in memory. The cache dashboard intentionally exposes a bounded recent-cache stream, while aggregate counters remain SQL queries.

Room schema versions are migrated explicitly. Destructive migration is no longer used. Project deletion runs inside `RoomDatabase.withTransaction` so clips, metrics, history, cache, and project rows are removed atomically.

## Secrets

API keys and OAuth tokens are stored through `SecureSettingsStore`. An AES-GCM key is retained by Android Keystore, while encrypted values are kept in a dedicated preferences file. The store includes a one-time migration path from the old plaintext preference files and removes the legacy value after migration. Backup rules exclude both the encrypted store and legacy credential files.

The settings UI must not claim that data is stored in Keystore alone: the encryption key is protected by Keystore and encrypted ciphertext is stored locally.

## Network behavior

`GeminiClipService` uses a shared OkHttp client and `RetryInterceptor`. Only requests marked with `X-Opus-Retryable: true` are retried, and only transient HTTP statuses such as 408, 429, and 5xx are eligible. Direct publishing requests are not automatically retried to avoid duplicate posts.

Cancellation is rethrown through suspend network paths. Response logging is summarized and redacted rather than writing complete provider responses to Logcat.

## Demo fallback

Local synthetic clip generation is controlled through the `allow_demo_fallback` setting. It remains enabled by default for backward compatibility with the existing prototype. Before production release, disable it through `OpusRepository.setDemoFallbackEnabled(false)` so an unavailable provider produces an explicit error rather than demo-looking content.

## Verification commands

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export ANDROID_HOME=/home/ubuntu/android-sdk
export ANDROID_SDK_ROOT=/home/ubuntu/android-sdk
./gradlew :app:compileDebugKotlin
./gradlew :app:testDebugUnitTest --tests com.example.OpusArchitectureCoreTest
./gradlew :app:assembleDebug
```

The project includes the Gradle Wrapper, so local and CI builds use the same Gradle version.
