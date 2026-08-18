# ISM — improvement audit

## Executive summary

The project has a strong desktop-first processing concept and now includes a mobile shell, Social Hub, a publishing calendar, OAuth-start links, approval controls, and a Gateway API contract. The most important architectural limitation is that the bundled Python/uv video pipeline is not an Android runtime: Tauri stores Android resources as APK assets, while the current pipeline depends on desktop Python and machine-learning binaries. The safest product direction is therefore to keep local processing on desktop and make Android a lightweight client for remote processing and social publishing, unless a dedicated Android-native processing engine is funded.

The visible application name has been changed to **ISM**. The technical package identifier remains `com.publikhq.publikclip` and the internal data directory remains `.publikclip` so existing installations do not lose data and future updates do not become a different Android application. A later major release can migrate the identifier and storage paths deliberately, but doing it now would behave like a new app installation.

## Implemented in this review

| Change | Result |
|---|---|
| Visible product name | `ISM` in Tauri metadata, window title, HTML title, onboarding, Studio, Social Hub, and Android display resources |
| Gateway security | Gateway API calls now require HTTPS, except for localhost development addresses |
| Android edge-to-edge order | `MainActivity` now calls `super.onCreate()` before `enableEdgeToEdge()` |
| Compatibility | The existing technical identifier and `.publikclip` data path remain unchanged |

## Recommended backlog

| Priority | Area | Recommended modification | Expected value | Current state |
|---|---|---|---|---|
| P0 | Processing architecture | Add a production remote-processing service for Android. The app should upload or reference media, start a job, stream progress, and download results. Keep the desktop Python pipeline as the local mode. | Makes the mobile app genuinely useful without pretending that desktop Python dependencies run inside Android. | Partially prepared through Social Hub Gateway contract; service is not yet deployed. |
| P0 | Secret storage | Replace plain JSON storage of Gemini/Pexels secrets with Android Keystore-backed storage or keep provider secrets only on the Gateway. Rotate tokens and provide revoke buttons. | Reduces damage if app data is copied and matches Android privacy expectations. | Not yet implemented. |
| P0 | OAuth completion | Add a verified callback/deep-link flow from the Gateway back to the Android app, with state validation and account status polling. | Converts the current “open browser and return manually” flow into a complete account-connection experience. | OAuth-start link exists; callback completion is still Gateway-dependent. |
| P0 | Scheduling reliability | Make the Gateway the source of truth for scheduled jobs. Add idempotency keys, retry with exponential backoff, provider status polling, and a server-to-device sync endpoint. | Prevents duplicate posts and avoids losing schedules when Android clears local storage. | Calendar currently keeps a local copy and calls schedule/update/cancel endpoints when configured. |
| P1 | Release quality | Produce a signed release APK and Android App Bundle, not only a debug APK. Add a reproducible signing configuration held outside GitHub. | Required for serious distribution and safer updates. | Current downloadable builds are debug builds. |
| P1 | Package size | Exclude desktop Python/uv resources from the Android artifact. Use remote processing or Android-specific feature delivery instead. | The current APK is approximately 326 MB, which is too heavy for a mobile-first experience. | Not yet optimized. |
| P1 | Mobile UX | Add native media picker/share-sheet integration, upload progress, background upload state, resumable upload, and a “retry” action. | Removes the need for users to paste public media URLs and improves real-world mobile workflows. | Current Social Hub expects a public media URL. |
| P1 | Accessibility | Ensure all touch targets are at least 48 dp, add accessible labels, improve contrast, support Android back navigation, and test orientation changes. | Improves usability and aligns with Android quality guidance. | Responsive CSS and safe-area handling were added; full device validation is still needed. |
| P1 | Testing | Add automated TypeScript tests for scheduling, date conversion, status transitions, and API error mapping; add Rust tests for storage paths and Android capability errors; run emulator/device smoke tests. | Prevents regressions in the calendar and the Android-specific behavior. | Build checks pass; a full test suite is not present. |
| P1 | Observability | Add structured job IDs, correlation IDs, redacted logs, provider error categories, and a diagnostic export that never includes secrets. | Makes support and failed publishing recoverable. | Errors are displayed, but structured diagnostics are limited. |
| P2 | Internationalization | Add Arabic and English language support, locale-aware dates, right-to-left layout, and translated provider errors. | Makes the app suitable for Arabic-speaking users and more markets. | UI copy is currently predominantly English. |
| P2 | Content workflow | Add reusable content templates, per-platform character/format validation, hashtag limits, preview cards, and duplicate-content detection. | Reduces failed posts and improves publishing quality across the five platforms. | Generic fields exist; platform-specific validation is still missing. |
| P2 | Account management | Show connected account health, token expiry, scopes, last successful publish, and a revoke/disconnect action per platform. | Makes the OAuth system understandable and safer to operate. | Connect buttons exist; account state is not yet a complete dashboard. |
| P2 | Privacy | Add a clear privacy screen, data deletion action, export/delete job artifacts, retention controls, and a disclosure for AI-generated content. | Helps with user trust and future store compliance. | Not yet implemented. |
| P3 | Product analytics | Track local funnel events without sending media or secrets: onboarding completion, job success/failure category, calendar usage, and publish outcomes. | Identifies where users get stuck without collecting sensitive content. | Not yet implemented. |
| P3 | Visual polish | Replace text symbols with vector icons, add a proper ISM icon set, improve empty states, and add a first-run demo job. | Makes the product feel finished and reduces confusion on small screens. | Existing visual identity is distinctive but still desktop-oriented. |

## Recommended implementation order

First, deploy the Gateway and complete OAuth callbacks, remote processing, idempotent scheduling, and secure token storage. Second, cut the Android package down by removing desktop-only resources and switch distribution to a signed AAB. Third, add native media picking, resumable uploads, provider-specific validation, and device smoke tests. Fourth, add Arabic localization, accessibility, privacy controls, and account-health dashboards. This order addresses correctness and security before cosmetic expansion.

## Evidence and source notes

Tauri's official documentation says that Android bundled resources are stored in the APK as assets rather than normal filesystem paths; files that must be executed need a separate extraction or sidecar strategy [1]. Tauri's sidecar documentation also requires architecture-specific binaries and explicit shell permissions for external binaries [2]. Android's core quality guidance emphasizes adaptive layouts, state preservation, touch-target sizing, contrast, background-work discipline, secure sensitive-data storage, SSL-only network traffic, and testing on representative devices [3]. Android's edge-to-edge guidance explains that Android 15 enforces edge-to-edge for apps targeting SDK 35 or higher and requires handling system-bar and display-cutout insets [4].

## References

[1]: https://v2.tauri.app/develop/resources/ "Tauri — Embedding Additional Files"
[2]: https://v2.tauri.app/develop/sidecar/ "Tauri — Embedding External Binaries"
[3]: https://developer.android.com/docs/quality-guidelines/core-app-quality "Android Developers — Core app quality guidelines"
[4]: https://developer.android.com/develop/ui/views/layout/edge-to-edge "Android Developers — Display content edge-to-edge in views"
