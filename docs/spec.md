# Wheelfile (App #4, Vehicle Logbook): product spec v0.1

**Author:** Idea Researcher · **Date:** 20 Nov 2026 (IST) · **For:** Ram (team lead) → App Designer, Monetisation Manager, ASO Manager, Claude (build)
**Status:** DRAFT. Brand, package, pricing and ad cadence are proposals until Ajith / the owning role locks them.
**Sibling files:** `name-options.md` (naming screen), `trademark-check.md` (preliminary TM check). Format mirrors `/workspace/app-research/recipe-vault/spec.md` (Spoonfile).
**Changelog (spec fixes approved by Ram on 20 Nov 2026, from `pricing-recommendation.md` §4; marked inline):**
1. §5.2: RC key `ads_interstitial_min_interval_seconds` renamed to `ads_interstitial_min_interval_sec`, and `ads_interstitial_block_first_session` added.
2. §6.2: the copy-test ban on `stay[s] on your (phone|device)` is removed, so "Your data stays on your device" passes. The ban on the connectivity word stays.
3. §3.1, §5.2, §5.3: `wf_interstitial_after_history` renamed to `wf_interstitial_after_review` everywhere.
4. §5.2 (and the V1 `AdsPolicyTest`): pump-session rule added. There is no interstitial for the rest of a session that saved a fill-up or charge.
5. §3.1 S4: banner rules added for the History, Documents and Reports tabs inside Vehicle detail.
6. §5.4: analytics event names aligned to the template: `pro_screen_view`, `purchase_started`, `purchase`, `purchase_restored`.
7. §6.1: Data safety now marks App interactions and Diagnostics as shared, and adds the Fraud prevention, security and compliance purpose.
8. §5.1 (and the §5.3 paywall trigger): free attachment cap changed from ~25 to 30 (Remove ads also 30), to match the approved plan.
9. §0.3 D4, §4 Drivvo importer row, §8 risks: Ajith declined the US$24.90 Drivvo fixture purchase (20 Nov 2026). The Drivvo CSV importer stays "beta", built from the published format.

Estimates are marked **GUESS** or **ESTIMATE**; anything not marked that way is sourced (URL or local file) or is a design decision.

---

## §0 Header

### §0.1 Sanity check: does Vehicle Logbook still beat Home Inventory & Warranty Vault in the ads era?

**Verdict: YES, proceed.** In the 14 Nov global scan (`/workspace/app-research/global-scan-2026-11.md`) Vehicle Logbook scored 34/40 (#2) and Home Inventory 30/40 (#3), under the original paid-only model. On 18 Nov the studio moved to ads on the free tier plus a lifetime Pro and a Remove-ads purchase (`/workspace/monetisation/ads-plan-2026-11.md`). I re-pulled Play data on 20 Nov 2026 to check that the switch does not flip the ranking.

| Check (20 Nov 2026, Play US unless noted) | Vehicle logbook | Home inventory / warranty |
|---|---|---|
| Is an ad-supported free tier already accepted in the category? | **Yes.** Drivvo (≈5.30M installs, 4.35★, 116,228 ratings, updated 16 Nov, "Contains ads"), aCar (≈1.46M, 1.95★, "Contains ads"), Simply Auto (≈946K, 4.47★, "Contains ads", last updated 10 Oct 2024) | **No.** None of HouseBook (≈119K, 4.51★), Find my stuff (≈93K), InvenDo (≈97K), Itemtopia (≈75K), Sortly (≈373K), Encircle (≈134K) or Warranty Keeper (≈29K) carries the "Contains ads" label |
| Demand (largest consumer app installs) | Fuelio ≈6.45M, Drivvo ≈5.30M, CARFAX ≈1.81M | HouseBook ≈119K (largest consumer app) |
| Session pattern (drives ad impressions) | Recurring: a log per fill-up / charge, plus reminders (weekly-ish per vehicle, **GUESS**) | Burst: a big inventory session, then rare visits (**GUESS**) |
| Fresh pain (1–2★ since 14 Nov) | Drivvo: "Removed many useful reports (fuel efficiency) and added nothing but bloated ads everywhere… asking a person with one vehicle to pay the same as a customer with 10 vehicles" (1★, 18 Nov 2026); "Locks your own data. Can't export or synchronize without paying" (1★, 16 Nov); "cost suddenly went up from R30pa to R99pa" (1★, 15 Nov). aCar: "Won't let you use the app without creating an account" (1★, 17 Nov). Fuelio: "₹1000" yearly subscription complaint (2★, 16 Nov) | Almost no new low-star reviews in the sampled apps |
| Ad tolerance evidence | Drivvo: "I didn't mind the ads as a way to support the app" (2★, 13 Oct 2026); "I happily used Drivvo's free version with ads for years" (1★, 10 Oct). The anger is about **paywalled export, price hikes and ad overload**, not ads as such. Ads appear in 52/328 Drivvo and 32/298 aCar 1–3★ reviews (my theme count) | No data (no ad-supported apps to learn from) |
| Advertiser relevance | Auto, insurance, fuel-card advertisers plausibly relevant (**JUDGEMENT**; no eCPM data retrieved) | Home/insurance plausibly relevant (**JUDGEMENT**) |
| Main weakness | Crowded; several indie clones (Bitácar, Karvyn, CarStamp, ServiceVault) prove the gap is distribution/ASO, not product | Household sharing is the top ask, and we cannot do multi-device sharing at zero running cost |

**Why the ads switch helps Vehicle Logbook more:** ads monetise recurring sessions, and the vehicle category has them plus proven ad acceptance. Home inventory's sessions are bursty and its top request (household sharing) needs a backend. The wedge (below) is built on what Drivvo/aCar users are angry about: export behind a paywall, logins, ad overload and price hikes. Our free tier keeps export, import and backup free, and caps ads well below theirs (ads plan caps). Risk acknowledged: a market of free clones means ASO, not features, decides the outcome (§8).

### §0.2 Quick facts

| Item | Value |
|---|---|
| Brand / Play title | **Wheelfile** / **"Wheelfile: Car Maintenance Log"** (30 chars, Python `len()`); alternative titles "Wheelfile: Fuel & Service Log" (29), "Wheelfile: Car Expense Tracker" (30) |
| Package (applicationId) | **`com.postpole.wheelfile`** (Play details page 404 on 20 Nov 2026 = free); provisional until Ajith locks the brand |
| Short description (draft, ASO to own) | "Log fuel, charging, service and car costs. Service and document reminders." (74/80) |
| One-liner | A global car and motorbike logbook (fuel, EV charging, service, expenses, documents, reminders) where your vehicle logs are stored on your device, importing from Fuelio, Drivvo, aCar and Simply Auto, with free CSV export and backup |
| Platforms | Android only (phones; tablets/foldables adaptive, §3) |
| Account / login | None |
| Languages at launch | EN, DE, FR, ES (same as Spoonfile); next pt-BR, IT, JA |
| Monetisation | Free with ads (AdMob banner + capped interstitial); Pro `lifetime_full_access` one-time (draft US$9.99); `remove_ads` one-time (draft US$3.99). Pricing is a draft for the Monetisation Manager |
| Running cost | Zero (no backend; Firebase GA4/Crashlytics/Remote Config free tier) |
| Effort | **GUESS ~6–8 working days** of Claude PRs (V1–V7) after V0 manual setup |
| Support / privacy | admin@postpole.com · privacy URL `https://pocket-ledger-postpole.web.app/wheelfile/privacy` (draft: `/workspace/monetisation/ads-compliance-drafts/wheelfile-privacy-ads.md`) **[Ram-approved change, 20 Nov 2026: privacy URL decided]** |

### §0.3 Template baseline
- Repo **`Postpole-Apps/android-app-template`**, branch `main` @ **`496d40d8c8427b65d9c091ff8257e6e348b32be0`** (committed 12 Nov 2026 19:33 IST, "Allow Claude to run new-app.sh and file moves"). Read-only inspection; nothing was cloned or changed.
- Stack at that SHA: AGP 8.13.0, Kotlin 2.2.20, KSP 2.2.20-2.0.3, Compose BOM 2025.10.00 (Material 3), Hilt 2.57.1, Room 2.8.1, DataStore 1.1.7, Navigation 2.9.5, Lifecycle 2.9.4, Coroutines 1.10.2, **Play Billing 9.1.0**, Firebase BOM 34.5.0 (GA4 + Crashlytics), Play Review 2.0.2, App Update 2.1.0, Robolectric 4.16. minSdk 26, compile/target SDK 36, Java 21. `PRO_PRODUCT_ID` default `lifetime_full_access`.
- Manifest: INTERNET + `com.google.android.gms.permission.AD_ID`; `allowBackup=false`; `data_extraction_rules.xml` excludes everything.
- **Not in the template:** AdMob, UMP, Firebase Remote Config, WorkManager. These are added in V1/V3 following the Spoonfile ads issues (`/workspace/monetisation/issues/sf-01*`, `sf-02*`).
- Bootstrap: `scripts/new-app.sh com.postpole.wheelfile "Wheelfile"` (supports `--dry-run`). CLAUDE.md rules: one issue per branch per PR (`issue-<n>`), GA4 + Crashlytics required, `./gradlew assembleDebug testDebugUnitTest lint` must pass.
- Play requirement to plan for: Google announced on 18 Nov 2026 that adaptive app quality (Tier 3 adaptive + foldable screenshots) is required from **April 2027**, and target API 37 from **August 2027**. V7 builds to that bar.

### §0.4 Open decisions (owner)

| # | Decision | Owner | Default if no answer |
|---|---|---|---|
| D1 | Lock brand **Wheelfile** + package `com.postpole.wheelfile` (or pick Gearfile #2) | **Ajith** | Hold V0 until locked |
| D2 | Lawyer-led TM clearance before first upload? (`trademark-check.md` is preliminary) | **Ajith** | Proceed on preliminary Low-risk verdict |
| D3 | Importer labels in-app: neutral ("Import from another vehicle app (auto-detect)") vs naming apps ("Import from Fuelio / Drivvo / aCar / Simply Auto") | Ram + ASO Manager | Neutral in-app labels; app names only in the full description, never in title/short description (Spoonfile rule) |
| D4 | Drivvo fixture: Drivvo CSV export needs a **paid Drivvo plan**. | **Decided: declined by Ajith (20 Nov 2026)** | Drivvo importer ships as "beta", built from the published format (open-source parser description), plus user-reported fixtures **[Ram-approved change, 20 Nov 2026: Ajith declined the US$24.90 Drivvo purchase; no real fixture will be bought]** |
| D5 | Prices (Pro, Remove ads), free-tier caps, ad cadence | Monetisation Manager | Draft values in §5 |
| D6 | Visual direction, icon, screenshots | App Designer | Design brief §3.3 |
| D7 | Listing copy, keywords, which competitor names appear in the full description | ASO Manager | §5.6 draft |
| D8 | AdMob app + ad units creation; `app-ads.txt` already at `https://pocket-ledger-postpole.web.app/app-ads.txt` | Ajith / Monetisation Manager | Test ad unit IDs until created (§5.3) |
| D9 | Privacy policy URL | Ram / Ajith | Placeholder |

---

## §1 Evidence and wedge

### §1.1 Competitors (Play US, 20 Nov 2026; installs are approximate Play figures)

| App | Installs | Rating (ratings) | Model (verified) | Key pain |
|---|---|---|---|---|
| Fuelio | ≈6.45M | 4.23 (136,203); GB 4.47, DE 4.34 | No ads; IAP US$4.99–15.99; Android Premium mostly route planner; iOS Pro US$17.99/yr or US$4.99/mo | Consumption maths complaints ("calculation wrong", 3★, 16 Oct 2026; partial-fill glitch, 2★, 12 Apr 2026; AdBlue mixed into fuel, DE 1★, 5 May 2026); import "0 of 40 entries copied" (1★, 14 Sep 2026); "unable to add a second car" (2★, 4 Dec 2025) |
| Drivvo | ≈5.30M | 4.35 (116,228); GB 4.54, DE 4.43 | Free: 2 active vehicles, unlimited fill-ups, reminders, reports, **ads, no CSV export**. Individual US$24.90/yr (5 vehicles, export, no ads). PRO US$49.90/yr or US$4.90/mo (drivvo.com pricing page) | Export paywall ("data export is now a premium feature", 1★, 10 Oct 2026); price "from a reasonable $6 a year to over $50 a year" (Reddit); ad overload (18 Nov 2026); location shared for ads (2★, 6 Sep 2026) |
| aCar (Fuelly) | ≈1.46M | 1.95 (21,066) | Contains ads; Fuelly login push | "Won't let you use the app without creating an account" (1★, 17 Nov 2026); "half of the screen is covered by ads" (1★, 2 Sep 2026); "can't get rid of ads despite having paid" (1★, 4 Aug 2026) |
| Simply Auto | ≈946K | 4.47 (24,426) | Contains ads | **Abandoned** (last update 10 Oct 2024); all cars must share units/currency (1★, 23 Aug 2025) |
| CARFAX Car Care | ≈1.81M | 4.74 (42,768) | Free, US-centric | "An update erased all of my service records" (1★, 6 Nov 2026) |
| My Car | ≈628K | 4.38 (5,736) | — | Could not import a Drivvo CSV (3★, 12 Aug 2026); mpg inaccurate (1★, 15 Jan 2025) |
| Loggy | ≈85K | 4.26 (731) | — | "why would a maintenance log app require a login?" (1★, 14 Aug 2026) |
| Indie: Bitácar | — | — | Free 2 cars, no ads, free CSV export; Pro US$12.99 one-time (PDF, unlimited cars); ES/EN/PT only | — |
| Indie: CarStamp | 67 | — | Free 1 car, no account/ads/analytics, free import (Fuelio/Drivvo/Fuelly/aCar/Road Trip) and export; Pro sub or lifetime (US$3.99–79.99 IAP range); EN/BG only | — |
| Indie: Karvyn / ServiceVault | 4 / 6 | — | US$14.99 / US$9.99 | Shows clones do not win on product alone |

### §1.2 Wedge (what Wheelfile does that the leaders don't, at zero running cost)
1. **Your data is never held hostage:** CSV export, full backup/restore and importers are **free**, on every tier. (Drivvo paywalls export; aCar pushes logins.)
2. **Switch in one tap:** importers for **Fuelio, Drivvo, aCar, Simply Auto** with a preview, duplicate skipping and an honest "imported N / skipped M" report (answers "0 of 40 entries copied" and "couldn't import Drivvo CSV").
3. **Correct, explainable fuel economy:** full-to-full maths with partial and missed fills handled and a "how was this calculated?" sheet (§4.3). Answers Fuelio/My Car/Drivvo maths complaints.
4. **Global by default:** per-vehicle units and currency (km/mi; L, US gal, UK gal, kWh, kg; any ISO 4217 currency); EVs and plug-in hybrids first-class; regional document presets (MOT, HU/TÜV, CT, ITV, rego, smog, insurance) with expiry alerts.
5. **Fair ads:** banners on list screens only, a capped interstitial at natural breaks, never on forms or while entering a fill-up; one-time Remove-ads and lifetime Pro, **no subscription**.
6. **No account, no login.** Your vehicle logs are stored on your device. Phone-to-phone transfer plus a backup file covers a new phone.
7. **Fair free tier:** 2 active vehicles (sold/archived vehicles don't count), unlimited logs, all reminders.

What we deliberately do **not** do (MVP): cloud sync, multi-device or family sharing, trip/GPS mileage-for-tax logging, OBD-II, fuel-price feeds, station maps, receipt OCR (v1.1 candidate, §2.9).

---

## §2 MVP flows

### §2.1 First run
1. Welcome (one screen): "Track fuel, charging, service and costs for your car or bike. Your vehicle logs are stored on your device." CTA **Add a vehicle** · secondary **Import from another app**.
2. Add vehicle sheet: name (required), type (Car / Motorbike / Van / Other), powertrain (**Petrol/Gasoline, Diesel, LPG, CNG, Electric, Plug-in hybrid, Hybrid, Hydrogen**), make/model/year (optional), plate, VIN (optional, 17-char validation soft), photo (optional).
3. Units step (prefilled from device locale, editable per vehicle): distance km/mi; volume L / US gal / UK gal (or kWh, kg); economy display (L/100km, km/L, MPG US, MPG UK; kWh/100km, mi/kWh, kWh/100mi); currency (ISO 4217, default from locale).
4. Region presets offer (§2.6): "Add common reminders for <country>?" (MOT/vehicle tax for GB, HU for DE, CT for FR, ITV for ES, etc.). Skippable.
5. Notification permission is asked **in context**: only when the user creates the first reminder (Android 13+ POST_NOTIFICATIONS).
6. GA4 events: `onboarding_complete`, `vehicle_added` (powertrain, unit system; no free text).

### §2.2 Log a fill-up / charge (core loop, target < 15 s, **GUESS**)
- FAB on Garage and Vehicle screens → **Fill-up** (or **Charge** for EV; PHEV shows both).
- Fields: date/time (now), odometer (blank, with the last reading shown as a hint), volume/energy, **any two of price-per-unit / total / volume** (third computed), full tank / charged to usual target (default on), "I missed logging a fill-up before this" toggle, fuel grade (optional), station / note (optional, free text), attachment (receipt photo, optional).
- Validation: odometer lower than previous → soft warning ("Lower than your last entry (12,345 km). Save anyway?"), never a block (odometer replacement, typos fixed later).
- Save → back to Vehicle screen with updated economy card. **No ad on this form or immediately on Save** (ads plan).
- EV: kWh added, cost, charger type (Home / AC public / DC fast, optional), % before/after (optional); "full" = reached your usual target (e.g. 80%).

### §2.3 Service and expense
- **Service** entry: date, odometer, service items (multi-select from a list: oil & filter, tyres, brakes, battery, inspection, etc. + custom), cost, workshop, notes, attachments. Option "Create next reminder from this" (prefilled interval).
- **Expense** entry: category (insurance, tax/registration, parking, tolls, washing, fines, loan/lease, accessories, other), amount, date, odometer (optional), recurring flag (monthly/yearly display only), notes, attachment.

### §2.4 Reminders (on-device)
- Types: by **date**, by **odometer**, or **whichever comes first**; repeat (every N months / N km/mi).
- Odometer-based reminders project the due date from average daily distance (last 90 days) and notify "about 500 km left" (projection shown as estimate).
- Scheduling via **WorkManager** daily periodic check (no exact alarms, no `SCHEDULE_EXACT_ALARM`); notifications channel "Reminders". Reboot-safe (WorkManager persists).
- Notification copy: "Oil change for Golf due in ~300 km" / "MOT due in 14 days".

### §2.5 Document vault
- Per vehicle: insurance, registration, inspection certificate, licence, warranty, roadside cover, lease, custom.
- Fields: title, type, number (optional), issue date, **expiry date**, attachments (photo/PDF via Photo Picker / SAF / TakePicture), notes.
- Expiry alerts at **30 / 14 / 7 / 1 days** before (user-editable), using the same WorkManager reminder worker.
- Attachments are copied into app-private storage (not referenced by URI), so they survive the source being deleted and are included in backup.

### §2.6 Regional document presets (suggestions only, user-editable, not legal advice)

| Region | Preset | Default rule (source) |
|---|---|---|
| GB | MOT | First at 3 years, then every year (https://www.gov.uk/getting-an-mot) |
| GB | Vehicle tax | Yearly/6-monthly renewal date reminder (https://www.gov.uk/vehicle-tax) |
| DE | HU (Hauptuntersuchung, "TÜV") | New cars at 36 months, then every 24 months (StVZO Anlage VIII, gesetze-im-internet.de) |
| FR | Contrôle technique | Within the 6 months before the 4th anniversary, then every 2 years (https://www.service-public.gouv.fr/particuliers/vosdroits/F2878) |
| ES | ITV | Exempt to 4 years, then every 2 years, yearly after 10 years (BOE-A-2017-12841; administracion.gob.es) |
| US-CA | Smog check | Reminder with user-set date (https://www.dmv.ca.gov smog page); other states: generic "Emissions / inspection" |
| AU-NSW | Registration (rego) | Yearly renewal (https://www.service.nsw.gov.au renew page) |
| AU (all) | Safety inspection ("pink slip" in NSW) | **Verify per state**: official NSW page returned 404 during research; preset ships as user-set date only |
| All | Insurance renewal, driving licence expiry | User-set date |
UI copy under presets: "Suggested schedule. Rules vary; check your local authority." Rules live in a versioned Kotlin table (`RegionPresets.kt`) with a unit test per region.

### §2.7 Import from another app (free, all tiers)
- Entry: Settings → **Import** and the first-run secondary CTA. File via SAF (`ACTION_OPEN_DOCUMENT`, `*/*`, accepting .csv, .zip, .abp).
- Auto-detect format from content (section headers / ZIP entries), show **preview**: vehicles found, entries per type, date range, detected units; user maps each source vehicle to new or existing vehicle; currency picker (sources don't carry currency).
- Import runs in a transaction; duplicates (same vehicle + date ± 1 min + odometer + volume) are **skipped and excluded from counts**, shown as "skipped". Result: "Imported 312 fill-ups, 41 services; skipped 3 duplicates; 2 rows couldn't be read (view)". Unreadable rows listed with line number.
- Free-tier vehicle cap: an import that would exceed 2 active vehicles imports the extra vehicles as **archived** (read-only, data kept), with a Pro upsell, never dropping data.
- Formats and fixture status: §4.4.

### §2.8 Reports, export, backup
- **Vehicle screen cards:** last economy, average economy (period), cost per km/mi, cost per month, total spent (12 months on free).
- **Charts (free):** economy over time, monthly cost by category, fuel price per unit; 12-month window. **Pro:** all-time / multi-year, vehicle comparison, custom date ranges.
- **CSV export (free):** per vehicle or all; a documented Wheelfile CSV (one file per table, zipped), UTF-8, ISO dates, dot decimals, unit columns explicit.
- **Backup file (free):** `.wheelfile` ZIP = Room data as JSON + attachments + manifest (schema version, app version, created at). Restore is **never capped** (restoring > 2 vehicles on free restores all, extras archived per §2.7 rule).
- **PDF vehicle history / resale report (Pro):** `PdfDocument` on device: vehicle details, service history, costs summary, economy chart, optional attachments index. Share via share sheet. No ads in export preview.
- Settings transfer note (Spoonfile pattern): "Moving to a new phone? Android's phone-to-phone transfer brings your vehicles, logs and attachments along. Keep a backup file too, just in case."

### §2.9 Receipt OCR (ML Kit) decision: **defer to v1.1**
- Reasons: (1) manual quick-add with any-two-of-three price fields is already fast; (2) ML Kit text recognition v2 adds app size (bundled model) or a Play services dependency and adds a diagnostics/data-safety line to verify; (3) Ajith's goal is a fast, small V1. CarStamp and Fuelio (iOS) already offer receipt scanning, so it is a **v1.1 candidate** to keep parity (docs verified: https://developers.google.com/ml-kit/vision/text-recognition/v2/android).

### §2.10 Settings
Units defaults, currency default, theme (**System / Light / Dark**, default System, DataStore), reminders (default lead times, quiet hours), Import, Export CSV, Backup / Restore, **Privacy options** (UMP form entry; shown when consent is required), Remove ads / Pro / Restore purchases, Languages (follows system; per-app language on Android 13+), About, Support (admin@postpole.com), Privacy policy. **No ads anywhere in Settings.**

---

## §3 Screens and design brief

### §3.1 Screen list

| # | Screen | Ads | Notes |
|---|---|---|---|
| S1 | Welcome | None | One screen; first session has no interstitial |
| S2 | **Garage** (vehicle list; cards with photo, odometer, next reminder, last economy) | **Banner** `wf_banner_garage` (bottom, anchored adaptive) | Sold/archived section collapsed |
| S3 | Add / edit vehicle (sheet) | None | Form |
| S4 | **Vehicle detail** (economy card, cost card, next reminders, recent entries, quick actions) | None (detail screen; keeps the core loop clean) | Tabs or segmented: Overview · History · Documents · Reports. **[Ram-approved change, 20 Nov 2026: tab banners]** Banners follow the content type, not the route. The History tab uses `wf_banner_history`, the Documents tab uses `wf_banner_documents` and the Reports tab uses `wf_banner_reports`, each with exactly one slot. The Overview tab never shows a banner, and there is never a second banner slot on S4 |
| S5 | **History** list (fill-ups/charges, services, expenses; filter chips) | **Banner** `wf_banner_history` | Leaving History back to Garage is the interstitial opportunity (`wf_interstitial_after_review` **[Ram-approved change, 20 Nov 2026: renamed from wf_interstitial_after_history]**), subject to caps |
| S6 | Fill-up / Charge form | **None** | Core loop |
| S7 | Service form · S8 Expense form | **None** | Forms |
| S9 | Reminders list | None (**GUESS**: low traffic; keep clean) | |
| S10 | Reminder form | None | |
| S11 | **Documents** list | **Banner** `wf_banner_documents` (list only) | |
| S12 | Document detail / attachment viewer | None | |
| S13 | **Reports / charts** | **Banner** `wf_banner_reports` | |
| S14 | Import (pick, preview, mapping, result) | None | |
| S15 | Export / Backup / Restore | None | Never ads (ads plan) |
| S16 | PDF preview (Pro) | None | Export preview: never ads |
| S17 | Paywall (Pro + Remove ads) | None | Never ads on paywall/purchase |
| S18 | Settings, Privacy options, About | None | |
| S19 | "How is economy calculated?" sheet | None | Explains §4.3 in plain words |

### §3.2 Adaptive layout (Play requirements from April 2027)
- Window size classes: compact = single pane; medium/expanded = list-detail (Garage | Vehicle; History | Entry) with `NavigableListDetailPaneScaffold`; forms as dialogs/side sheets on expanded.
- Foldables: table-top posture not required; hinge-aware panes via the adaptive library. Screenshots: phone, 7" and 10" tablet, foldable (V7).
- No orientation locks; all screens tested in landscape; keyboard/mouse support for forms on tablets/Chromebooks (tab order, Enter to save).

### §3.3 Design brief (for App Designer)
- Personality: calm, trustworthy, "glovebox notebook". Material 3 with dynamic colour **off by default** for brand consistency (**GUESS**, designer to decide), a single brand hue (suggest a deep teal or road-sign blue), large numbers for odometer/economy.
- Icon: a wheel + folder/file tab motif (ties to "-file" family with Spoonfile). Avoid tyre-brand lookalikes and dashboards with gauges (crowded).
- Economy colours: neutral by default; green/red only for change vs own average, with text labels (colour-blind safe).
- Empty states with one clear action; numbers formatted by locale; units always visible next to values.
- Accessibility: 48dp targets, TalkBack labels for charts (summary text), dynamic type to 200%.
- Screenshots (ASO): 1) Garage with two vehicles; 2) fill-up form; 3) economy chart; 4) reminders/documents; 5) import result "Imported 312 fill-ups"; 6) EV charging log. No competitor names or logos in screenshots.

---

## §4 Data model (Room) and maths

### §4.1 Entities (Room 2.8.1; money as `Long` minor units + ISO currency; distances and quantities stored as entered in the vehicle's units and normalised to km/L/kWh in the engine)

```kotlin
@Entity data class Vehicle(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String, val type: VehicleType,            // CAR, MOTORBIKE, VAN, OTHER
  val powertrain: Powertrain,                           // PETROL, DIESEL, LPG, CNG, ELECTRIC, PHEV, HYBRID, HYDROGEN
  val make: String?, val model: String?, val year: Int?,
  val plate: String?, val vin: String?,
  val distanceUnit: DistanceUnit,                       // KM, MI
  val volumeUnit: VolumeUnit,                           // L, US_GAL, UK_GAL, KG
  val economyDisplay: EconomyUnit,                      // L_PER_100KM, KM_PER_L, MPG_US, MPG_UK, KWH_PER_100KM, MI_PER_KWH, KWH_PER_100MI, KG_PER_100KM
  val currency: String,                                 // ISO 4217
  val tankCapacity: Double?, val batteryKwh: Double?,
  val photoPath: String?, val archived: Boolean = false, val soldAt: Long?,
  val createdAt: Long, val updatedAt: Long)

@Entity(foreignKeys=[ForeignKey(Vehicle::class, ["id"], ["vehicleId"], onDelete = CASCADE)], indices=[Index("vehicleId","odometer"), Index("vehicleId","dateTime")])
data class EnergyLog(                                   // fill-up OR charge
  @PrimaryKey(autoGenerate = true) val id: Long = 0, val vehicleId: Long,
  val stream: EnergyStream,                             // LIQUID (fuel), ELECTRIC, GAS_KG
  val dateTime: Long, val odometer: Double,             // in vehicle.distanceUnit
  val quantity: Double,                                 // in vehicle.volumeUnit, or kWh for ELECTRIC
  val pricePerUnitMinor: Long?, val totalMinor: Long?,  // currency = vehicle.currency
  val isFull: Boolean, val missedPrevious: Boolean,
  val fuelGrade: String?, val chargerType: ChargerType?, val socStart: Int?, val socEnd: Int?,
  val station: String?, val note: String?,
  val importSource: String?, val importKey: String?,    // de-dup key
  val createdAt: Long, val updatedAt: Long)

@Entity data class ServiceRecord(id, vehicleId, dateTime, odometer: Double?, items: List<String> /*TypeConverter*/, totalMinor: Long?, workshop: String?, note: String?, importSource: String?, importKey: String?, ...)
@Entity data class Expense(id, vehicleId, dateTime, odometer: Double?, category: ExpenseCategory, customCategoryId: Long?, amountMinor: Long, recurring: Recurrence?, note: String?, importSource: String?, importKey: String?, ...)
@Entity data class Reminder(id, vehicleId, title, kind: ReminderKind /*DATE, ODOMETER, FIRST_OF*/, dueDate: Long?, dueOdometer: Double?, repeatMonths: Int?, repeatDistance: Double?, leadDays: Int, leadDistance: Double?, presetCode: String?, linkedDocumentId: Long?, done: Boolean, lastNotifiedAt: Long?)
@Entity data class VehicleDocument(id, vehicleId, type: DocumentType, title, number: String?, issueDate: Long?, expiryDate: Long?, alertDays: List<Int> /*30,14,7,1*/, note: String?)
@Entity data class Attachment(id, ownerType: OwnerType /*ENERGY, SERVICE, EXPENSE, DOCUMENT, VEHICLE*/, ownerId, fileName /*app-private*/, mimeType, sizeBytes, createdAt)
@Entity data class CustomCategory(id, name, kind)        // Pro
```
- Counts for the free cap: `SELECT COUNT(*) FROM Vehicle WHERE archived = 0`.
- Attachment count (free cap, §5.1) counts `Attachment` rows; restore/import never blocked by caps.
- Migrations: exported schemas from v1; `MigrationTest` per version (template pattern).

### §4.2 Units and constants (unit tests pin these)
- 1 mi = **1.609344 km**; 1 US gal = **3.785411784 L**; 1 UK gal = **4.54609 L**.
- MPG(US) = **235.215 / (L/100km)**; MPG(UK) = **282.481 / (L/100km)**; km/L = 100 / (L/100km).
- kWh/100km ↔ mi/kWh: mi/kWh = 62.1371 / (kWh/100km); kWh/100mi = kWh/100km × 1.609344.
- Internally normalise to km and L (or kWh, kg) as `Double`; money as `Long` minor units with `Currency.getInstance(code).defaultFractionDigits`.

### §4.3 Fuel-economy algorithm (full-to-full; the "explainable" wedge)
For each vehicle and energy stream, ordered by odometer then dateTime:
1. A **segment** runs from one full entry (exclusive) to the next full entry (inclusive).
2. Segment distance = odometer(full_end) − odometer(full_start). Segment quantity = Σ quantity of **all** entries after full_start up to and including full_end (partials are summed into the next full).
3. If any entry in the segment has `missedPrevious = true`, the segment is **invalid** (excluded) and the baseline resets at its closing full entry.
4. Segment economy = quantity / distance × 100 (L/100km, kWh/100km), converted for display.
5. **Period average = Σ quantity / Σ distance over valid segments** (not an average of segment averages).
6. Entries before the first full entry are a baseline only (no economy).
7. Odometer decreasing → soft warning on save; segment with non-positive distance is invalid and flagged in History ("check odometer").
8. EV: identical logic with "full" = charged to usual target; PHEV: separate LIQUID and ELECTRIC streams, each with own economy; **cost per distance** combines both streams' costs over total distance.
9. Cost per distance = (Σ energy cost + Σ service + Σ expenses in period) / distance driven in period (from min/max odometer of all entries in period).
Unit tests (V1): partial-then-full, missed fill reset, unit conversions (US/UK gallons, miles), out-of-order entry insert, odometer correction, PHEV mixed stream, EV partial charges, zero/negative distance, rounding at display only.

### §4.4 Import formats (verified vs needs fixture)

| Source | Format (what is verified from public sources) | Status |
|---|---|---|
| **Fuelio** | CSV per vehicle with sections `## Vehicle`, `## Log`, `## CostCategories`, `## Costs`, `## FavStations`, `## Pictures`, `## Category`. Vehicle: Name, Description, DistUnit (0 km, 1 mi), FuelUnit (0 L, 1 US gal, 2 UK gal), ConsumptionUnit (0 L/100km, 1 MPG US, 2 MPG UK, 3 km/L, 4 km/gal US, 5 km/gal UK), ImportCSVDateFormat, VIN, Insurance, Plate, Make, Model, Year, TankCount, Tank1Type, Tank2Type, Active, Tank1Capacity, Tank2Capacity (newer files add FuelUnitTank2, FuelConsumptionTank2, guid, lastupdated). Log: Data (`yyyy-MM-dd HH:mm`), Odo, Fuel, Full, Price, l/100km (opt), latitude, longitude, City, Notes, Missed, TankNumber, FuelType, VolumePrice, StationID, ExcludeDistance, UniqueId, TankCalc; header labels carry units ("Odo (mi)", "Fuel (litres)"). Costs: CostTitle, Date, Odo, CostTypeID, Notes, Cost, flag, idR, read, RemindOdo, RemindDate, isTemplate, RepeatOdo, RepeatMonths, isIncome, UniqueId. No currency in file. "Backup All" = ZIP of CSVs + `pictures.data`; Drive sync file `vehicle-N-sync.csv.zip`. Sources: github.com/Programistyk/FuelioImport (MIT), github.com/tigattack/lubelogger-fuelio-importer (MIT), fuel.io/faq/import-from-other-apps | **Verified structure**; write synthetic fixture from the published column lists; ask testers for a real export |
| **Drivvo** | From FuelioImport's Drivvo provider: sections `##Vehicle`, `##Refuelling` (or localised `#Reabastecimiento`), `##Service` (`#Servicio`), `##Expense`. **Locale-dependent:** full-tank flag is a translated yes/no (Si/Yes/Tak/Ja; No/Nie/Nein); dates may be D/M/YYYY. Refuelling read by column index: 0 odometer, 1 date, 2 fuel type, 3 price/unit, 4 total, 5 volume, 6 full flag, 18 notes. Service: 0 odo, 1 date, 2 cost, 3 title, 5 notes. Expense: 0 odo, 1 date, 2 cost, 3 title, 6 notes. Vehicle row: name, model, plate, ?, year, notes | **Partially verified** (index-based, header names unknown). No paid fixture will be bought (D4 declined). Ship as "beta", built from the published format, with index + header-sniffing and a "send us a sample" support path |
| **aCar** | `.abp` = ZIP with `metadata.inf` (`acar.backup.type=Full-Backup`), `vehicles.xml` (vehicle: name, make, model, year, license-plate, vin, insurance-policy, distance-unit, volume-unit; `fillup-records/fillup-record`: date, volume, total-cost, odometer-reading, partial, previous-missed-fillups, fuel-type-id, fuel-brand, fueling-station-address, notes, latitude, longitude; `service-records`, `expense-records`, or newer `event-records` with a type), `preferences.xml`, `event-subtypes.xml` (older `services.xml`/`expenses.xml`), `fuel-types.xml`, `trip-types.xml`. CSV export: sections Metadata, Vehicles, Fill-Up Records, Service Records, Expense Records, Trip Records; fill-up header (2014 sample) "Vehicle","Date","Time","Odometer Reading","Distance Unit","Volume","Volume Unit","Price per Unit","Total Cost","Payment","Partial Fill-Up?","Previously Missed Fill-Ups?","Fuel Efficiency",… ,"Notes"; numbers **locale-formatted** ("134,417"; currency symbols). Fuelly (2018): CSV export and backup/restore free for all aCar users. Sources: fuelly forum thread 16432, a public gist sample, fuelly.com/acar, docs.fuelly.com | **Structure verified from community samples**; XML schema version drift likely → fixture needed (aCar export is free, so a tester can provide one) |
| **Simply Auto** | Google Drive backup folder "Simply Auto": `Vehicles.csv` (Row ID, Make, Model, Year, Lic#, VIN, Insurance#, Notes, Picture Path, Vehicle ID); `Fuel_Log.csv` (Row ID, Vehicle ID, Odometer, Qty, Partial Tank 0/1, Missed Fill Up 0/1, Total Cost, Distance Traveled, Eff, Octane, Fuel Brand, Filling Station, Notes, Day, Month, Year, Receipt Path, Record Type 0 fuel / 1 service / 2 expense, Record Desc); optional `Services.csv`, `Trip_Log.csv`. Source: simplyauto.app/importguide.php | **Verified (publisher's own import guide)**; synthetic fixture OK |
| **Generic CSV** | Column-mapping wizard (date, odometer, quantity, total, full flag) | v1.1 (**GUESS**) |
Importer code: `importer/{fuelio,drivvo,acar,simplyauto}/` with a `FormatDetector`, pure-Kotlin parsers (no network), fixtures under `app/src/test/resources/import/` (synthetic, built from the published columns; no third-party data committed). Competitor brand names appear only in the full description and in the format picker if D3 allows; never in title, short description or icon.

---

## §5 Monetisation and ads (aligned with `/workspace/monetisation/ads-plan-2026-11.md`; all prices **DRAFT** for Monetisation Manager)

### §5.1 Tiers

| Feature | Free (with ads) | Remove ads (`remove_ads`, draft **US$3.99** one-time INAPP) | Pro (`lifetime_full_access`, draft **US$9.99** one-time INAPP) |
|---|---|---|---|
| Active vehicles | **2** (archived/sold don't count, read-only) | 2 | **Unlimited** |
| Fill-ups, charges, services, expenses | Unlimited | Unlimited | Unlimited |
| Reminders (date/odometer/first-of), document expiry alerts | All | All | All |
| Import (all formats), CSV export, backup & restore | **Free** | Free | Free |
| Charts | 12-month basic | 12-month basic | All-time, vehicle comparison, custom ranges |
| PDF vehicle history / resale report | — | — | ✓ |
| Attachments (photos/PDFs) | 30 total **[Ram-approved change, 20 Nov 2026: free attachment cap set to 30]** | 30 | Unlimited |
| Custom expense/service categories | — | — | ✓ |
| Ads | Yes (capped) | **No** | **No** |
Entitlement: `adsHidden = owns(remove_ads) || owns(lifetime_full_access)`; `isPro = owns(lifetime_full_access)`. Restore queries both products; refund of one leaves the other (ads plan). Product IDs are read from BuildConfig (`PRO_PRODUCT_ID` default `lifetime_full_access`; add `REMOVE_ADS_PRODUCT_ID` = `remove_ads`). Prices displayed from `ProductDetails` only (**NoHardcodedPriceTest**). No subscriptions.
Local prices: reuse the Spoonfile tables (Remove ads US$3.99 → GBP 3.49, EUR 3.99, CAD 5.49, AUD 5.99; Pro per Spoonfile §5a) unless Monetisation Manager changes them.

### §5.2 Ad placement rules
- **Banners** (anchored adaptive, bottom): Garage (S2), History (S5), Documents list (S11), Reports (S13). List/browse screens only.
- **Interstitial** `wf_interstitial_after_review` **[Ram-approved change, 20 Nov 2026: renamed from wf_interstitial_after_history]**: when the user navigates **back from History or Reports to Garage**, never directly on Save, never after a form, never mid-import/export. Caps from Remote Config: none in the first session (`ads_interstitial_block_first_session` = true **[Ram-approved change, 20 Nov 2026: key added]**); none until `ads_interstitial_min_install_days` = 3; ≥ `ads_interstitial_min_interval_sec` = 480 **[Ram-approved change, 20 Nov 2026: renamed from ads_interstitial_min_interval_seconds]**; ≤ 1 per session; ≤ 3 per day.
- **Pump-session rule** **[Ram-approved change, 20 Nov 2026: pump-session rule]**: if the current session has saved a fill-up or EV charge, no interstitial is shown for the rest of that session. This is a hard-coded rule in `AdsPolicy`, not a new RC key. It covers the common pattern of saving at the pump, checking History, then going back to Garage. Service and expense saves do not trigger the rule. No interstitial fires after export, backup, restore or a PDF share either.
- **Never ads on:** Welcome, any form or sheet (fill-up, charge, service, expense, reminder, document, vehicle), paywall/purchase, Settings, Privacy options, import/export/backup/restore, currency/unit pickers, PDF/export preview, attachment viewer, "how calculated" sheet.
- **Off by default:** app-open, rewarded (`wf_rewarded` defined, `ads_rewarded_enabled` = false), native.
- Max ad content rating **PG** (`ads_max_content_rating`); target audience 18+ (drivers).
- Consent: **UMP** consent info update + form on launch **before** `MobileAds.initialize`; "Privacy options" entry in Settings when required (sf-01 pattern).
- Remote Config keys (ads plan names): `ads_enabled`, `ads_banner_enabled`, `ads_interstitial_enabled`, `ads_rewarded_enabled` (false), `ads_native_enabled` (false), `ads_app_open_enabled` (false), `ads_interstitial_block_first_session` (true), `ads_interstitial_min_install_days` (3), `ads_interstitial_min_interval_sec` (480) **[Ram-approved change, 20 Nov 2026: studio key names: _sec, and block_first_session added]**, `ads_interstitial_max_per_session` (1), `ads_interstitial_max_per_day` (3), `ads_max_content_rating` ("PG"), `ads_rewarded_daily_limit` (1). Defaults bundled in `remote_config_defaults.xml` so the app behaves the same with no fetch.

### §5.3 Ad unit IDs
- **No real AdMob IDs exist for this app** (`/workspace/monetisation/admob-ids.md` has PocketLedger, Ghar Khata, Spoonfile only). Do not invent IDs.
- Debug and until created: Google's official test units: banner `ca-app-pub-3940256099942544/9214589741`, interstitial `ca-app-pub-3940256099942544/1033173712`, rewarded `ca-app-pub-3940256099942544/5224354917`.
- Proposed unit names for D8: `wf_banner_garage`, `wf_banner_history`, `wf_banner_documents`, `wf_banner_reports`, `wf_interstitial_after_review` **[Ram-approved change, 20 Nov 2026: renamed from wf_interstitial_after_history]**, `wf_rewarded`. IDs injected via `BuildConfig`/`gradle.properties` per build type.
- `app-ads.txt`: add the Wheelfile developer listing to the existing file at `https://pocket-ledger-postpole.web.app/app-ads.txt` (same publisher) when the Play listing goes live.

### §5.4 Paywall
- Triggers: adding a 3rd active vehicle; tapping PDF report; selecting all-time charts; adding the 31st attachment (cap 30) **[Ram-approved change, 20 Nov 2026: trigger follows the 30 cap]**; Settings → Pro / Remove ads. No "Remove ads" link under banners (keep banners clean; Settings + paywall only, **JUDGEMENT**).
- Paywall shows both options side by side: "Remove ads" (ads off, free limits stay) and "Wheelfile Pro" (everything, no ads). Prices from Play. No countdowns, no fake discounts. Copy never promises features that are not built.
- GA4: `pro_screen_view` (trigger), `purchase_started`, `purchase` (product), `purchase_restored` **[Ram-approved change, 20 Nov 2026: event names aligned to the template]**. Remove-ads events: `remove_ads_paywall_view`, `remove_ads_purchase_start`, `remove_ads_purchase_success` / `remove_ads_purchase_fail` (no success event on restore).

### §5.5 Guardrails (from ads plan)
Stop/roll back ads cadence via Remote Config if D1 or D7 retention falls ≥ 5 pp vs a no-interstitial cohort, or Pro conversion drops ≥ 25%. Report weekly via the existing Play/GA4 review skills.

### §5.6 Listing notes (for ASO Manager)
- Title "Wheelfile: Car Maintenance Log" (30). Short description draft (74 chars) in §0.2.
- Full description may say, nominatively, "Import your history from Fuelio, Drivvo, aCar or Simply Auto" (Drivvo's own description names competitors as import sources); never imply affiliation.
- Allowed privacy wording: "Your vehicle logs are stored on your device." "No account needed." "Export your data any time, free." Not allowed: see §6.2.
- "Contains ads" = **Yes** from the first Play-bound build (once the AdMob SDK ships, which is V1).

---

## §6 Policy and copy rules

### §6.1 Data safety (draft; verify against the final SDK list)
- **Collected / shared:** **[Ram-approved change, 20 Nov 2026: App interactions and Diagnostics marked shared; Fraud prevention purpose added]** Device or other IDs (Advertising ID, app-set ID, Firebase installation IDs): collected and **shared** (AdMob), for Advertising, Analytics, and Fraud prevention, security and compliance. Approximate location (from IP, by the ad SDK): collected and **shared**, same purposes. App interactions (AdMob and GA4): collected and **shared**, same purposes. Diagnostics (AdMob SDK): collected and **shared**, same purposes. Crash logs (Crashlytics): collected, not shared. Purchase history (Play Billing): collected, not shared. Full matrix: `pricing-recommendation.md` §3.6.
- **Not collected by us:** vehicle logs, attachments, documents, plates/VINs (stored on device only; not transmitted). Users' free-text notes never go to analytics (event params are enums/counts only).
- Encrypted in transit: Yes (SDK traffic). Deletion: uninstall or "Delete all data" in Settings (local); analytics/ads data per Google's policies, link in privacy policy.
- **GA4, Crashlytics, the AdMob SDK and the Ad ID permission stay ON.** Do not remove `AD_ID`.
- Ads declaration: Contains ads = Yes. Target audience: 18+. Content rating questionnaire: no user-generated sharing, no location sharing.
- Permissions: INTERNET, AD_ID, POST_NOTIFICATIONS (runtime, in context), RECEIVE_BOOT_COMPLETED only if WorkManager needs it (it declares its own). **No** CAMERA (use `TakePicture` intent), **no** storage permissions (Photo Picker / SAF), **no** location, **no** exact alarms.
- Play policy pages checked (200 on 20 Nov 2026): support.google.com/googleplay/android-developer/answer/9898842 and /9888072. Also note the 29 Oct 2026 Play policy announcement (trader status etc.) for the listing.

### §6.2 Copy rules (enforced by `CopyRulesTest`, scanning `res/values*/strings.xml`, the listing text files under `fastlane/` or `listing/`, and the privacy policy draft)
Fail the build if any string matches (case-insensitive):
- `off\x6cine` (the connectivity word; do not claim the app works without internet; describe storage instead)
- **[Ram-approved change, 20 Nov 2026: removed the ban on stay[s] on your (phone|device), so the studio sentence "Your data stays on your device." passes; the connectivity-word ban above stays]**
- `no\s+data\s+collected` and `(100\s*%|completely)\s+private` and `zero\s+tracking` and `no\s+tracking`
- `future\s+pro` and `coming\s+soon` in paywall strings (no promises of unbuilt Pro features)
- `\b(sync|cloud\s+backup|family\s+sharing|share\s+with\s+family|multi-?device)\b` (we have none; phone-to-phone transfer is described only as Android's transfer)
- Competitor names (`fuelio|drivvo|acar|simply\s*auto|fuelly`) in `app_name`, title, short description, or in-app labels unless D3 approves nominative format labels
- Required positive phrasing (test asserts presence in EN): "Your vehicle logs are stored on your device." in onboarding or About. "Your data stays on your device." is also allowed (studio sentence).
The test reads patterns from one list so translators' strings are checked in DE/FR/ES too (add language equivalents for the connectivity word in DE/FR/ES; keep them as escaped regex in the test, not literal text in docs).

### §6.3 Backup and transfer XML (copy Spoonfile §7.7)
- `android:allowBackup="true"`, `android:dataExtractionRules="@xml/data_extraction_rules"`, `android:fullBackupContent="@xml/backup_rules"`.
- `data_extraction_rules.xml`: `<cloud-backup>` excludes all domains (no cloud copy); `<device-transfer>` includes database, `files/attachments/`, and DataStore; excludes Firebase/ads caches.
- `backup_rules.xml` (API ≤ 30): exclude everything by default; `res/xml-v28/backup_rules.xml` includes database/attachments/DataStore with `requireFlags="deviceToDeviceTransfer"` (verify the attribute behaves on API 28–30 in V6 test).
- Robolectric test parses both XMLs and asserts cloud = none, device transfer = db + attachments.

### §6.4 Other
- Regional presets labelled "suggested; check your local authority" (not legal advice).
- No health/finance claims; costs are user records only, not tax advice ("mileage for tax" is not offered).
- Delete-vehicle and delete-all-data confirmations; no data leaves the device on delete.

---

## §7 Milestones (Claude-ready; one issue per branch per PR, branch `issue-<n>`; each PR must pass `./gradlew assembleDebug testDebugUnitTest lint`)

Effort column is **GUESS** in working days of Claude PR time, not including review.

### V0: Manual setup (Ajith / Ram; not Claude)
- Lock brand (D1). Create repo from template; run `scripts/new-app.sh com.postpole.wheelfile "Wheelfile"` (dry-run first).
- Firebase project/app (GA4, Crashlytics, Remote Config), `google-services.json`.
- Play Console app "Wheelfile: Car Maintenance Log", products `lifetime_full_access` and `remove_ads` (prices per D5), internal testing track.
- AdMob app + units (D8) when ready; until then test IDs.
- **AC:** repo builds with new package; Firebase connected; products created (inactive OK).

### V1: Foundation (**GUESS 1–1.5 d**)
- Room schema v1 (§4.1) + DAOs + exported schema; units/conversions (§4.2) and economy engine (§4.3) as pure Kotlin.
- Ads scaffold: AdMob SDK, UMP consent before init, Remote Config defaults + fetch, `AdsPolicy` (caps, session/install-day logic), Privacy options entry; billing entitlements (`remove_ads`, `lifetime_full_access`) via template billing.
- **AC:** consent flow shows on EEA test device; `MobileAds.initialize` only after consent info; entitlement hides ads for either product.
- **Tests:** `EconomyEngineTest` (cases listed in §4.3), `UnitConversionTest` (constants), `AdsPolicyTest` (first session, 3 days, 480 s, 1/session, 3/day, pump-session suppression **[Ram-approved change, 20 Nov 2026: pump-session test]**), `EntitlementTest` (each product, both, refund of one), `MigrationTest` baseline.
- **Files:** `data/db/*`, `domain/economy/*`, `domain/units/*`, `ads/*`, `billing/Entitlements.kt`, `res/xml/remote_config_defaults.xml`.

### V2: Garage, vehicles, fill-ups/charges (**GUESS 1 d**)
- Garage, add/edit vehicle (units step, locale defaults), Vehicle detail with economy/cost cards, Fill-up/Charge form (any-two-of-three price maths, odometer soft warning), History list, theme setting, banners on Garage/History.
- **AC:** 2-vehicle free cap with archive; PHEV shows two streams; no ad on forms; "how calculated" sheet.
- **Tests:** ViewModel tests for form validation, price maths, cap logic; Compose UI test for add-vehicle → fill-up → card updates.

### V3: Service, expenses, attachments, reminders (**GUESS 1 d**)
- Service/expense forms, attachments (Photo Picker, TakePicture, SAF, copy to app-private), reminder engine (date/odometer/first-of, projection), WorkManager daily worker, notification channel, POST_NOTIFICATIONS prompt in context.
- **AC:** reminder fires on test clock; odometer projection shown as estimate; attachments survive source deletion; free attachment cap with paywall.
- **Tests:** `ReminderSchedulerTest` (WorkManager test helpers), projection maths, attachment copy.

### V4: Document vault and regional presets (**GUESS 0.5–1 d**)
- Documents list/detail, expiry alerts 30/14/7/1, `RegionPresets.kt` (GB, DE, FR, ES, US-CA, AU-NSW + generic), presets offer in onboarding.
- **AC:** each preset generates correct first/next due dates; copy "Suggested schedule…" visible; Documents banner on list only.
- **Tests:** `RegionPresetsTest` per region (e.g. DE HU 36 then 24 months; FR CT before 4th anniversary then 2 years; ES ITV 4/2/1 rule; GB MOT 3 then 1).

### V5: Importers (**GUESS 1–1.5 d**)
- `FormatDetector`; Fuelio (CSV + Backup-All ZIP), aCar (.abp XML + CSV), Simply Auto (CSVs), Drivvo (beta, index-based + header sniffing, localised yes/no and date parsing); preview, vehicle mapping, currency picker, transaction import, de-dup, result report, archived-overflow rule.
- **AC:** synthetic fixtures import with exact counts; re-import of same file imports 0, skips N; unreadable rows reported with line numbers; never drops data due to cap.
- **Tests:** one parser test per format + locale variants (comma decimals, D/M/YYYY, Tak/Ja/Si), round-trip counts; fixtures in `test/resources/import/` (synthetic only).

### V6: Reports, export, backup, PDF (**GUESS 1 d**)
- Charts (12-month free; all-time/comparison Pro), CSV export (zip), `.wheelfile` backup/restore (JSON + attachments + manifest), PDF report (Pro, `PdfDocument`), backup XMLs (§6.3), Settings transfer note.
- **AC:** backup → wipe → restore gives identical DB hash and attachments; restore never capped; cloud backup excluded, device transfer included.
- **Tests:** `BackupRoundTripTest`, `CsvExportTest` (headers, ISO dates, dot decimals), `BackupRulesXmlTest`, PDF smoke test (page count > 0).

### V7: Polish, interstitial, paywall, languages, listing readiness (**GUESS 1 d**)
- Interstitial on History/Reports → Garage with RC caps; paywall (both products, Play prices); DE/FR/ES strings; `CopyRulesTest` (§6.2) and `NoHardcodedPriceTest`; adaptive list-detail layouts and foldable checks; listing text files + screenshots set (phone, 7", 10", foldable); Data safety draft; Contains ads = Yes.
- **AC:** no ad on any screen in the never-list (UI test walks all screens with ads enabled); caps hold under fast navigation; lint clean; CopyRulesTest passes in all four languages.
- **Tests:** `CopyRulesTest`, `NoHardcodedPriceTest`, `AdPlacementTest`, screenshot tests at compact/medium/expanded.

**Total: GUESS ~6–8 working days** of Claude PR time (V1–V7) plus review; the scan's earlier estimate was "M (4–6 weeks)" for a solo human build.

### Later (not MVP)
v1.1: receipt OCR (ML Kit), generic CSV mapping, widgets, pt-BR/IT/JA. v1.2: home-screen quick-add tile, per-trip notes (not GPS). Never at zero cost: cloud sync, family sharing.

---

## §8 Risks

| Risk | Likelihood / impact | Mitigation |
|---|---|---|
| Crowded market; indie clones with near-zero installs show distribution, not product, is the bottleneck (X anecdote: "$31 spent, 6 installs… 1 lifetime sale: $9.99", x.com/fatihbuilds/status/2120583541784514660) | High / High | ASO Manager owns listing; import-from-competitor angle in full description; 4 languages at launch; screenshots show import result |
| Ads annoy the very users we court (Drivvo/aCar anger) | Medium / High | Strict placement, RC caps, Remove-ads US$3.99 (draft), guardrails §5.5; never ads on forms |
| Importer breakage (Drivvo headers unknown, aCar schema drift, locale formats) | High / Medium | Beta label for Drivvo; lenient parsers; row-level error report; support path for samples (no fixture purchase; D4 declined) |
| Fuel-economy maths disputes | Medium / Medium | Full-to-full algorithm, "how calculated" sheet, tests pinned to constants |
| Trademark (preliminary only) | Low / High | `trademark-check.md` Low risk; lawyer search before first upload (D2); fallback Gearfile |
| Regional preset rules wrong or change | Medium / Low | Suggestions only, user-editable, sourced, versioned table, disclaimer |
| Data loss (CARFAX-style "update erased records") | Low / High | Room migrations with tests, free backup file, device-transfer rules, no destructive migrations |
| Play adaptive requirement (Apr 2027) and target API 37 (Aug 2027) | Certain / Medium | V7 adaptive layouts and screenshots; plan API 37 bump in 2027 |
| No real AdMob IDs yet | Certain / Low | Test IDs until D8; never invent IDs |
| Store policy on competitor names | Low / Medium | Names only in full description, nominative; neutral in-app labels unless D3 |
| Notification permission denied → reminders silent | Medium / Medium | In-context prompt; in-app reminder badges on Garage regardless |
| eCPM / revenue unknown | Certain / Medium | No numbers assumed; weekly review after launch (**no eCPM data retrieved**) |

---
*End of spec v0.1. Name, package, prices and ad cadence remain proposals until locked by Ajith / the owning roles.*
