# Android app template

Shared starter for Postpole Apps small, offline Android apps.

## What's inside
- Kotlin, Jetpack Compose (Material 3, light/dark/dynamic colour, edge-to-edge)
- Single activity, Navigation Compose: Home (Room-backed list + add dialog), Settings, Pro (placeholders)
- Hilt, Room (schemas exported to `app/schemas`), DataStore Preferences
- minSdk 26, compileSdk/targetSdk 36, version catalog in `gradle/libs.versions.toml`
- Only cost rule: no paid APIs, servers, AI fees or paid databases. GA4 + Crashlytics are required in every app; AD_ID / Ad ID collection stays ON (Google Ads)
- Unit tests: Room DAO (Robolectric) and ViewModel

## Build
Requires JDK 21 and the Android SDK (`ANDROID_HOME` or `local.properties` with `sdk.dir`).

```
./gradlew assembleDebug testDebugUnitTest lint
./gradlew assembleRelease
```

## New app from this template
Use GitHub "Use this template", then run `scripts/new-app.sh <applicationId> "<App Name>"`. Full steps: [docs/NEW_APP.md](docs/NEW_APP.md).

## Release and signing
`app/build.gradle.kts` signs the release build from `KEYSTORE_BASE64` (or `KEYSTORE_FILE`), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` (environment or `local.properties`); the build is unsigned if absent. The release workflow lives in `ci-templates/release.yml` (signed AAB artifact on push to main; optional draft upload to the Play internal track via `workflow_dispatch`).

## Repo-ready status
- Ready: Compose/Hilt/Room/DataStore skeleton, Pro unlock (Play Billing), Settings, JSON/CSV backup, GA4 + Crashlytics, in-app review/update, release signing, new-app script.
- Needs a maintainer: copy `ci-templates/ci.yml` and `ci-templates/release.yml` to `.github/workflows/` (the bot cannot push there), and add repo secrets.

## Analytics and crash reporting (required in every app)
Google Analytics for Firebase (GA4) and Crashlytics are wired through the `Analytics` interface. The template builds without Firebase (a no-op `Analytics` is used); each app adds its own `app/google-services.json` (git-ignored), and the google-services / crashlytics plugins are then applied automatically. Release builds upload the R8 mapping file and native symbols (`ndk.debugSymbolLevel = "FULL"`). Ad ID collection is intentionally left enabled for Google Ads conversion tracking. Set `privacy_policy_url` and `support_email` in `res/values/app_config.xml`.

Events: `app_open`, `first_launch`, `screen_view`, `pro_screen_view`, `purchase_started`, `purchase` (value + currency), `purchase_restored`, `backup_exported`, `backup_imported`.

## Backup
Settings > Back up to file / Restore from backup file use the Storage Access Framework (no storage permissions) and a versioned JSON format (`BackupRepository`); Items can also be exported as CSV. Android Auto Backup is off (`allowBackup=false` plus explicit `data_extraction_rules.xml`) so this is the only backup path.

## CI
The complete workflows are in `ci-templates/` (`ci.yml`, `release.yml`); copy them to `.github/workflows/`.
