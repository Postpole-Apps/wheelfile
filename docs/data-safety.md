# Wheelfile: Play Data safety and App content draft

Draft for Release & QA to enter in Play Console. It matches `spec.md` §6.1 and the matrix in `pricing-recommendation.md` §3.6, and reflects the SDKs the app ships: Google Mobile Ads (AdMob), User Messaging Platform (UMP), Firebase (Analytics/GA4, Crashlytics, Remote Config) and Google Play Billing. Nothing here has been entered in Play Console. Ajith approves it before the first Play submission.

## App content declarations

| Declaration | Answer |
|---|---|
| Contains ads | **Yes** |
| Target audience | **18+ only**. Not designed for children. Not enrolled in Families |
| Advertising ID | **Yes**. Purposes: Advertising or marketing; Analytics; Fraud prevention, security and compliance |
| Content rating questionnaire | No user-generated sharing, no location sharing |
| Ads max content rating (Remote Config `ads_max_content_rating`) | PG |
| Privacy policy URL | https://pocket-ledger-postpole.web.app/wheelfile/privacy |

## Data safety matrix

| Data type | Collected | Shared | Purposes | Notes |
|---|---|---|---|---|
| Location: approximate location | Yes | **Yes** | Advertising; Analytics; Fraud prevention, security and compliance | Derived from IP by the ads SDK. The app has no location permission |
| App activity: app interactions | Yes | **Yes** | Advertising; Analytics; Fraud prevention, security and compliance | AdMob and GA4. GA4 event parameters are enums and counts only |
| App info and performance: diagnostics | Yes | **Yes** | Advertising; Analytics; Fraud prevention, security and compliance | AdMob SDK diagnostics |
| App info and performance: crash logs | Yes | No | Analytics; App functionality | Crashlytics. No plates, VINs or notes in crash messages |
| Device or other IDs | Yes | **Yes** | Advertising; Analytics; Fraud prevention, security and compliance | Advertising ID, app-set ID, Firebase installation IDs |
| Financial info: purchase history | Yes | No | App functionality; Analytics | Google Play Billing (Pro and Remove ads). Product, price and currency only |
| Photos and videos; Files and docs; Personal info; other Financial info; Messages | **No** | n/a | n/a | Vehicle logs, fuel and charge costs, receipts, documents, plates, VINs and notes are stored on the device and are not collected or shared by Postpole |

- Data is encrypted in transit: **Yes** (SDK traffic).
- Users can request deletion: **Yes**. On-device data is removed by clearing the app's storage in Android settings or by uninstalling (spec §6.1 also lists a "Delete all data" action in Settings; that action is not built yet, so confirm before the form is submitted). The Advertising ID can be reset in Android settings.
- Cloud backup is excluded in `data_extraction_rules.xml`. Device-to-device transfer includes the database, attachments and settings, between the user's own devices only.

## Permissions

`INTERNET`, `com.google.android.gms.permission.AD_ID` (kept on, never removed with `tools:node="remove"`), `POST_NOTIFICATIONS` (asked in context, for reminders). WorkManager declares its own boot receiver permission. There is no camera, storage, location or exact-alarm permission: photos use the system camera intent and the Photo Picker, files use the system file picker.

## Reminders for the listing

- Prices are never written in listing fields. They come from Google Play at purchase time.
- Free limits: 2 active vehicles and 30 attachments. Pro (`lifetime_full_access`) and Remove ads (`remove_ads`) are one-time purchases.
- The store title, short and full description are in `listing/en-US/`. `CopyRulesTest` scans them.
