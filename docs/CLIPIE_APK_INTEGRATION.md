# Clipie APK integration boundary

This repository now contains a small, source-level capability policy inspired by the static review of the authorized Clipie Android APK.

## What was added

- `ClipieLocalCapability`: an explicit description of local ASR, rendering, timed-caption, model-download, and secure-key capabilities.
- `ClipieLocalCapabilityPolicy`: chooses between the canonical Gateway route, a bounded offline fallback, and unavailable.
- Unit tests covering route selection and secure model-download validation.

## What was intentionally not copied

The APK is a Flutter AOT artifact. Its original Dart source cannot be reconstructed faithfully from `libapp.so`, and the archive also contains third-party/runtime binaries. No decompiled Flutter framework, native `.so`, model, API key, or opaque binary was added to this repository.

The existing Android media and Gateway implementations remain the source of truth. Local processing must be advertised as an offline/preview capability unless it passes the same golden-media checks as the server pipeline.

## Security boundary

Provider keys belong in the Android Keystore or, preferably, on the private Gateway. A model-download capability must not be enabled with plaintext provider-key storage. Self-update flows must verify HTTPS, package identity, certificate/signature, and monotonic version before installation.

## Validation

Run the Android unit suite for the `app` module. Device-level parity with Clipie still requires a real emulator/device and representative media fixtures.
