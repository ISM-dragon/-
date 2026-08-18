# Android build

The Tauri Android project is generated under `app/src-tauri/gen/android` and is included in the repository as source. Generated build outputs, staged resources, and native build targets are intentionally excluded from version control.

## Build requirements

Install Node.js, Rust, Java 21 JDK, Android SDK command-line tools, Android platform 36, Android build tools, and NDK 27.0.12077973. Then run:

```sh
cd app
npm install
npx tauri android build --debug --target aarch64
```

The debug APK is produced at:

```text
app/src-tauri/gen/android/app/build/outputs/apk/universal/debug/app-universal-debug.apk
```

## Runtime note

publikclip was originally designed as a desktop application. Its processing pipeline uses a bundled `uv` launcher and Python machine-learning dependencies such as WhisperX, SpeechBrain, ONNX Runtime, OpenCV, and FFmpeg. The Android shell and UI are buildable. The Android UI now uses a narrow-viewport layout with safe-area padding, stacked controls, and readable error wrapping. The desktop Python pipeline is intentionally not spawned from the Android APK: Tauri stores bundled resources as APK assets, while the bundled desktop `uv` launcher is not an Android-native runtime. On Android the app returns a clear message directing users to the desktop build or to the remote processing Gateway API in Social Hub. Full local video processing on Android requires a separate Android-native runtime port.

## License

The project remains licensed under AGPL-3.0-or-later. See `LICENSE` and `VENDORED-LICENSES.md`.
