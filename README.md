# Wheelfile: Car Maintenance Log

A global car and motorbike logbook: fuel, EV charging, service, expenses, documents and reminders. Your vehicle logs are stored on your device. No account. Free with ads, with an optional one-time Remove ads and a one-time Pro.

Package: `com.postpole.wheelfile`. This repo started from the Postpole Apps Android template (`Postpole-Apps/android-app-template`) via `scripts/new-app.sh`. The build source of truth is [`docs/`](docs/README.md) (spec, design pack, decisions).

## What's inside
- Kotlin, Jetpack Compose (Material 3, Service-Book Ochre theme, bundled Roboto Flex, light/dark/system, edge-to-edge)
- Single activity, Navigation Compose: Garage (placeholder in V1), Settings, Pro
- Hilt, Room (schema v1 exported to `app/schemas`), DataStore Preferences
- Pure Kotlin domain code: `domain/units` (unit constants and conversions) and `domain/economy` (full-to-full fuel and energy economy engine)
- minSdk 26, compileSdk/targetSdk 36, version catalog in `gradle/libs.versions.toml`
- GA4 + Crashlytics are required; AD_ID / Ad ID collection stays ON (Google Ads)

## Build
Requires JDK 21 and the Android SDK (`ANDROID_HOME` or `local.properties` with `sdk.dir`).

```
./gradlew assembleDebug testDebugUnitTest lint
./gradlew assembleRelease
```

## Release and signing
`app/build.gradle.kts` signs the release build from `KEYSTORE_BASE64` (or `KEYSTORE_FILE`), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` (environment or `local.properties`); the build is unsigned if absent. The release workflow lives in `ci-templates/release.yml`.

## Analytics and crash reporting
Google Analytics for Firebase (GA4) and Crashlytics are wired through the `Analytics` interface. The build works without `app/google-services.json` (a no-op `Analytics` is used); add your own `app/google-services.json` (git-ignored) and the google-services / crashlytics plugins are applied automatically. Ad ID collection is intentionally left enabled.

## CI
The workflows are in `ci-templates/` (`ci.yml`, `release.yml`); copy them to `.github/workflows/`.
