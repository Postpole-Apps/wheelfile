# Wheelfile — importer store copy (en-US)

Store-facing copy for the import feature. Counts are Python `len()` of the exact NFC string inside each code block. Brand name is kept out of these snippets so a Wheelfile → Gearfile swap does not change their length.

**Sources:** `../spec.md` §2.7 (flow, preview, duplicate skipping, result report, free on all tiers, vehicle-cap archive rule), §4.4 (formats and fixture status), §5.6 and D3 (competitor names only in the full description, nominative use, no affiliation), D4 (Drivvo ships as beta). Reference affiliation style: `/workspace/app-research/recipe-vault/listing-en-US.md` ("Paprika is a trademark of its owner; this app is not affiliated with it.").

**Rule:** A competitor app may be named as a factual import source only in the full description, never in the title, short description, graphics or screenshot captions. This file follows that rule: only the full-description section and the affiliation line name Fuelio, Drivvo, aCar or Simply Auto.

## What the copy may claim (MVP only)

| Source | Formats the spec actually supports | Spec status | May we name it in the full description? |
|---|---|---|---|
| Fuelio | CSV (per-vehicle sections) and Fuelio "Backup All" ZIP of CSVs; also the Drive sync `vehicle-N-sync.csv.zip` shape described in §4.4 | Verified structure (§4.4) | Yes |
| aCar | `.abp` ZIP backup and aCar CSV export (§4.4) | Structure verified from community samples; schema drift possible, but the import is specified for MVP | Yes |
| Simply Auto | Publisher's CSV set (`Vehicles.csv`, `Fuel_Log.csv`, optional `Services.csv`, `Trip_Log.csv`) from the "Simply Auto" backup folder (§4.4) | Verified from the publisher's own import guide | Yes |
| Drivvo | Drivvo CSV export (sectioned, locale-dependent, partly index-based) (§4.4) | Partially verified. **Ships as beta** (D4, §4.4) until a real fixture exists | Yes, but only as a beta import |
| Generic CSV mapping wizard | Column-mapping wizard | **v1.1, not MVP** (§4.4) | No — do not promise it |
| Road Trip / Road Trip MPG, MileIQ, Fuel Manager, Fuelly.com website, Fuel Log, My Cars, and others named by competitors | — | **Not in spec §4.4** | No |

Entry points specified for MVP: Settings → Import, and the first-run secondary CTA "Import from another app". Files come in through the Storage Access Framework (`.csv`, `.zip`, `.abp`). The app auto-detects the format, shows a preview (vehicles, entry counts, date range, units), lets the user map each source vehicle, skips duplicates, and reports "imported / skipped / couldn't be read". Import, CSV export and backup/restore are free on every tier. An import that would exceed 2 active vehicles on the free tier keeps the extra vehicles as archived, with their history; it does not drop data (§2.7, pricing §1.4).

In-app labels stay neutral unless D3 is later changed ("Import from another vehicle app"), per spec D3.

## 1. Full-description section (526 chars)

This is the "BRING IN A HISTORY FILE" section already used in `listing-en-US.md`. Competitor names appear only here (and in the affiliation line in section 5).

```
BRING IN A HISTORY FILE
• Import is free on every plan. You can import a history file from Fuelio, aCar or Simply Auto, and a Drivvo import is available as a beta
• Preview the file, map each vehicle, and skip duplicates. You get a clear report of what was imported, skipped, or could not be read
• Export a CSV or a full backup any time, free. Restoring a backup is never blocked
• Moving to a new phone? Android's phone-to-phone transfer brings your vehicles, logs and attachments along. Keep a backup file too, just in case
```

## 2. Short feature blurb (179 chars)

For a feature graphic, a later short-description experiment, or an in-app empty state. **No competitor names.**

```
Import is free on every plan. Pick a history file from another vehicle app, preview it, map your vehicles, and see what was imported, skipped as a duplicate, or could not be read.
```

## 3. Screenshot caption (28/40 chars)

Matches screenshot 5 in `screenshot-captions.md`. **No competitor names, logos or file-format badges on the graphic.** The on-screen result copy can follow the spec example ("Imported 312 fill-ups, 41 services; skipped 3 duplicates") without naming the source app.

```
Import your existing history
```

## 4. In-app or store FAQ (51 + 267 chars)

No competitor names, so this can be used in the app or in a Play FAQ without breaking the title/short/graphics rule.

**Question**

```
Can I bring in a log I already kept in another app?
```

**Answer**

```
Yes. Import is free. Choose a history file, preview the vehicles and dates, map them to new or existing vehicles, and you will get a clear report of what was imported, skipped as a duplicate, or could not be read. CSV export and a full backup stay free on every plan.
```

If a later D3 decision allows nominative format labels in the app, the FAQ can then name the sources. Until then, keep names in the full description only.

## 5. No-affiliation line (106 chars)

Include at the end of the full description, in the same style as the Spoonfile Paprika line. This is the only other store-text place competitor names may appear.

```
Fuelio, Drivvo, aCar and Simply Auto are trademarks of their owners; this app is not affiliated with them.
```

## What this copy deliberately does not say

- It does not promise a generic "import any CSV" wizard (v1.1).
- It does not name Road Trip, Road Trip MPG, MileIQ, Fuel Manager, Fuelly.com, Fuel Log or My Cars as sources.
- It does not treat the Drivvo importer as equivalent to the verified Fuelio / aCar / Simply Auto importers; it is labelled beta.
- It does not claim affiliation, official partnership, or a guaranteed one-tap transfer from inside another app. The user picks a history file they already exported.
- It does not say import is unique to this app (Drivvo, aCar and Simply Auto also mention imports on their listings).
- It makes no connectivity claim and does not use "no ads", prices, or "your data stays on your device". Backup and transfer wording follows the Spoonfile / spec §2.8 pattern: Android's phone-to-phone transfer, plus a backup file.
