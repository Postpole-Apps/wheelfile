# Creating a new app from this template

1. **Create the repo.** On GitHub click **Use this template** on `Postpole-Apps/android-app-template`, then clone the new repo.
2. **Run the script** from the repo root:
   ```
   scripts/new-app.sh com.postpole.myapp "My App"
   ```
   It renames package/namespace/applicationId, moves the source dirs and Room schema dirs, sets `app_name`, resets `versionCode 1` / `versionName 1.0.0` and prints a checklist. Add `--dry-run` (or `-n`) first to preview the moves and changed files without writing anything. Works with GNU or BSD `sed`.
3. **Edit `app/src/main/res/values/app_config.xml`**: `privacy_policy_url` and `support_email`.
4. **Firebase (required: GA4 + Crashlytics).** Create a Firebase project, register the new applicationId, enable Analytics and Crashlytics, download `google-services.json` into `app/` (it is git-ignored). The Firebase plugins are applied automatically when it exists.
5. **Signing.** Create an upload keystore and add repo secrets `KEYSTORE_BASE64` (`base64 -w0 upload.jks`), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. For local release builds put `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` in `local.properties`. Never commit keys. The Release workflow fails if any signing secret is missing. Also add repo secret `GOOGLE_SERVICES_JSON` (the contents of `app/google-services.json`) so CI-built AABs include GA4 + Crashlytics; a Play upload fails without it.
6. **Play Console.** Create the app, fill the store listing, content rating, data safety (Ad ID is collected for Google Ads) and privacy policy. Create the one-time in-app product (default id `lifetime_full_access`, or set `-PproProductId`). Create a service account with release access and add its JSON as secret `PLAY_SERVICE_ACCOUNT_JSON`. The first AAB must be uploaded manually once.
7. **Workflows.** Copy `ci-templates/ci.yml` and `ci-templates/release.yml` to `.github/workflows/`. Bump `versionCode` before each Play upload. Pushes to `main` build a signed AAB artifact; run **Release** manually with `upload_to_internal_track` to upload a draft to the internal track.
8. **Verify:** `./gradlew assembleDebug testDebugUnitTest lint`.
