# Wheelfile docs (build source of truth)

Wheelfile: Car Maintenance Log, package `com.postpole.wheelfile`. Code is written by the Claude Code GitHub Action, one issue per `issue-<n>` branch and PR.

| Path | What |
|---|---|
| `spec.md` | Product spec with Ram-approved fixes 1–9 (attachment cap 30, paywall on the 31st; Drivvo importer ships as beta, no Drivvo fixture purchase). §7 has the milestone plan. |
| `pricing-recommendation.md` | FINAL pricing, ad cadence and Data safety matrix (§3.6). |
| `design/DECISIONS.md` | Ram's design decisions of 20 Nov 2026. **These override `design/README.md` and the PNGs where they differ.** |
| `design/README.md` | Final design pack: screen list, component spec, per-screen dp tables, tokens, ad rules, build handoff. |
| `design/00-overview.png` | Every screen, light and dark, plus icon and feature graphic. Start here. |
| `design/screens/<id>-{light,dark}.png` | 29 frames at 360×800 dp (@2x). `design/screens/redlines/` has dp redlines. |
| `design/tokens/` | `Color.kt`, `Theme.kt`, `Type.kt`, `Shape.kt`, `Dimens.kt`, `tokens.json`, bundled `fonts/roboto_flex.ttf`. |
| `design/icon/` | Hub Tab adaptive icon: `drawable/`, `mipmap-anydpi-v26/`, `svg/`, `play-512.png`, handoff `README.md`. |
| `design/store/feature-graphic-1024x500.png` | Play feature graphic. |
| `aso/` | Listing copy, screenshot captions, importer copy. |
| `admob.md` | AdMob App ID and ad unit IDs (release) and Google test IDs (debug). |

Privacy policy URL: https://pocket-ledger-postpole.web.app/wheelfile/privacy · Support: admin@postpole.com

## Studio rules (binding for every PR)
- Never use the connectivity word that the copy test bans (regex `off\x6cine`, case-insensitive) anywhere: code, strings, comments, docs, issues, PR text. Describe storage instead: "Your vehicle logs are stored on your device." / "Your data stays on your device."
- Global-first: no INR, ₹ or UPI defaults. Currency and units come from the device locale and the user's choice.
- One PR at a time, branched from up-to-date `main` as `issue-<n>`. No stacked PRs.
- GA4, Crashlytics and the AD_ID permission stay on. Never commit `google-services.json`, keystores or any secret.
