# Merged manifest (release)

Checked with `./gradlew processReleaseMainManifest`; output at
`app/build/intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml`.
`ManifestPermissionsTest` guards the camera, storage, location, exact-alarm and foreground-service rules below.

## Permissions

| Permission | From | Why |
|---|---|---|
| `INTERNET` | app | Ads, Firebase Analytics and Crashlytics, Play Billing |
| `com.google.android.gms.permission.AD_ID` | app | Kept ON for Google Ads (never remove) |
| `POST_NOTIFICATIONS` | app | Reminders; asked in context when the first reminder is saved |
| `ACCESS_NETWORK_STATE` | Play services ads, Firebase | SDK network checks |
| `ACCESS_ADSERVICES_AD_ID`, `_ATTRIBUTION`, `_TOPICS` | Play services ads | Ad ID and Privacy Sandbox ad APIs |
| `com.android.vending.BILLING` | Play Billing | Purchases |
| `WAKE_LOCK` | WorkManager, Firebase | Keeps the daily reminder check awake while it runs |
| `BIND_GET_INSTALL_REFERRER_SERVICE` | Firebase Analytics | Install attribution |
| `<package>.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | AndroidX core | Signature-level guard for dynamic receivers |

## Foreground service (issue #12, item 4)

- WorkManager's library manifest declares `android.permission.FOREGROUND_SERVICE` and a `SystemForegroundService`
  (no `foregroundServiceType`). Both exist only for `setForeground()` / expedited long-running work.
- The app never calls `setForeground`, `setExpedited`, `startForeground` or `startForegroundService`. Reminders are a
  periodic daily `ReminderWorker` plus one-time runs (`ReminderScheduler`), which are normal background work.
- **Decision:** strip the permission with `tools:node="remove"` in `app/src/main/AndroidManifest.xml`. With no foreground
  service in use there is no service type to declare and **no Play Console foreground-service declaration is needed**.
  If a future feature needs one, add the permission back with the matching `FOREGROUND_SERVICE_<TYPE>` permission and
  complete the declaration in Play Console (App content) before that release.
- The unused `SystemForegroundService` entry stays in the merged manifest. It is `exported="false"` and cannot be started
  without the permission, so it has no effect.
