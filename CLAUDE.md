# CLAUDE.md — Wheelfile (Postpole Apps)

This repo is Wheelfile (`com.postpole.wheelfile`); `docs/` is the build source of truth. It started from the shared Postpole Apps Android template for small apps whose data stays on the device.

## Rules
- Work only on the issue you were given. One issue = one branch `issue-<number>` = one PR to `main`. Never push to `main`, never force-push, never merge.
- Kotlin + Jetpack Compose (Material 3), single-activity, Hilt, Room, DataStore, Coroutines/Flow. minSdk 26, compileSdk/targetSdk 36. Google Play Billing Library 8+ (prefer latest 9.x).
- The only cost rule: no paid APIs, servers, AI fees or paid databases. Your data stays on your device (no backend, no accounts).
- GA4 (Firebase Analytics) and Crashlytics are REQUIRED in every app. The AD_ID permission and Ad ID collection stay ON (for Google Ads); never add tools:node="remove" for them. The google-services/crashlytics plugins are applied when `app/google-services.json` exists, so the bare template still builds in CI without it.
- Keep the build green: run `./gradlew assembleDebug testDebugUnitTest lint` before opening the PR and paste the result summary in the PR body.
- No secrets in code. Release signing reads from environment variables / local.properties only.
- Write clear PR descriptions: what changed, how to test, screenshots not required.
- If something in the issue is unclear or impossible, comment on the issue explaining why instead of guessing.
