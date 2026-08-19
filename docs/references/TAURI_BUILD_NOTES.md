# Tauri Android Build Notes

The official Tauri development guide states that mobile applications are developed with `tauri android dev`, and that using Android Studio requires the Tauri CLI process to remain running and not be killed. This explains why the generated Gradle `android-studio-script` task depends on a live CLI/IPC process during IDE-oriented flows.

Source: https://v2.tauri.app/develop/ (Tauri Develop, last updated Jul 22, 2026).

The official environment-variable reference documents `TAURI_ANDROID_PROJECT_PATH` for the generated Android project and `CI` for non-interactive CLI mode. It also documents `TAURI_ENV_DEBUG`, `TAURI_ENV_TARGET_TRIPLE`, and related hook variables.

Source: https://v2.tauri.app/reference/environment-variables/ (Tauri Environment Variables, last updated Jan 30, 2026).

For this repository, the successful build workaround was to use the generated Android project with the installed SDK/NDK, Java 21 JDK, Rust stable with `aarch64-linux-android`, constrained Gradle memory, and `CI=true`; the resulting v0.9.3 APK was built successfully before the later Git merge.
