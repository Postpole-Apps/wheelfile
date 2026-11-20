# Wheelfile — screenshot captions (en-US)

Eight caption lines for the English Play listing, in suggested order. Each caption is 40 characters or fewer, counted with Python `len()` on the exact NFC string inside the code block. None use the brand name, so a Wheelfile → Gearfile swap does not change these lines.

Visual briefs follow spec §3.3 (garage with two vehicles; fill-up form; economy chart; reminders/documents; import result; EV charging log) plus two extra frames the MVP also ships: the Pro PDF report, and the privacy / no-account message. **No competitor names, logos or file-format badges on any graphic.** No prices, star ratings, connectivity claims, "no ads" or "ad-free". Prefer a clean (ads-hidden) frame on list screens so the listing does not depend on live ad creatives; forms, import, export preview and the "how calculated" sheet have no ads in the spec anyway.

Use global, neutral examples in the UI: "fuel" and "fill-up" rather than "gas"; show kilometers or an obvious unit control, or prepare a miles variant for the US storefront without putting "MPG" in the caption; reminders should use "Inspection", "Insurance" or "Oil change", not only a UK "MOT".

## 1. Garage (24/40)

```
Your garage, at a glance
```

**Visual brief:** The Garage screen with two vehicles (for example a petrol or diesel car and an electric car or motorbike), showing last economy and cost-per-distance cards. Units visible. No competitor names. Keep the frame clean of banner ads.

## 2. Fill-up (24/40)

```
Log a fill-up in seconds
```

**Visual brief:** The fill-up form (this screen has no ads). Fields for odometer, volume and cost, with any two of price / total / volume completed and the third calculated. Full-tank toggle on. Last odometer reading shown as a hint. Neutral station/note optional and empty is fine.

## 3. Economy (23/40)

```
Fuel economy, explained
```

**Visual brief:** The economy-over-time chart on Reports or the vehicle screen, with units visible (L/100km, km/L, or a miles display — not in the caption). Include or peek the "How is this calculated?" sheet so the explainable full-to-full method is visible. 12-month window is the free-tier truth; do not imply all-time history unless this is clearly a Pro frame.

## 4. Reminders and documents (30/40)

```
Service and document reminders
```

**Visual brief:** Reminders list and/or the document vault for one vehicle: an upcoming oil change (date or odometer) and an insurance or inspection expiry. Use globally readable labels, not a single-country preset name as the only example. No ads required on the reminder form; a documents-list frame should stay clean of banners.

## 5. Import (28/40)

```
Import your existing history
```

**Visual brief:** The import result screen, using the spec example: "Imported 312 fill-ups, 41 services; skipped 3 duplicates" (and, if space, a "couldn't be read" row). **Do not name or show Fuelio, Drivvo, aCar, Simply Auto, or any other app.** No logos, no "imported from …" badge. This screen has no ads.

## 6. EV charging (18/40)

```
Log EV charges too
```

**Visual brief:** A charge form or an EV vehicle history: kWh added, cost, optional charger type (Home / AC public / DC fast) and optional battery percentage. Same calm form pattern as the fill-up screen. No ads on the form.

## 7. PDF report, Pro (25/40)

```
PDF service history (Pro)
```

**Visual brief:** On-device PDF preview of the vehicle history / resale report: vehicle details, service history, a costs summary, an economy chart, optional attachments index. This is a Pro feature, so the caption says so. No ads in the export preview (spec §2.8, §5.2). No prices. No competitor names.

## 8. Privacy (35/40)

```
Your data is stored on your device.
```

**Visual brief:** Welcome / About / a short privacy frame that also supports "No account needed." Do not use any connectivity claim (the banned words in the studio copy rules, "no internet" or "without data"). Do not claim cloud sync. If this frame is a Settings transfer note instead, the on-screen text should follow spec §2.8: Android's phone-to-phone transfer brings vehicles, logs and attachments along; keep a backup file too.

## Order and counts (Python `len()`, NFC)

| # | Caption | Chars | Screen (spec) |
|---|---|---|---|
| 1 | Your garage, at a glance | 24/40 | S2 Garage (§3.3 item 1) |
| 2 | Log a fill-up in seconds | 24/40 | S6 Fill-up form (§3.3 item 2) |
| 3 | Fuel economy, explained | 23/40 | S13 Reports / vehicle cards (§3.3 item 3) |
| 4 | Service and document reminders | 30/40 | S9 Reminders, S11 Documents (§3.3 item 4) |
| 5 | Import your existing history | 28/40 | S14 Import result (§3.3 item 5) |
| 6 | Log EV charges too | 18/40 | S6 Charge form or EV history (§3.3 item 6) |
| 7 | PDF service history (Pro) | 25/40 | S16 PDF preview (extra; Pro, §2.8) |
| 8 | Your data is stored on your device. | 35/40 | Welcome / About (extra; §2.1, §6.2) |

The first six match the screenshot set asked for in spec §3.3. Frames 7 and 8 are optional extras if Play has room; if the set must be cut to six, drop 7 and 8 first, or swap 7 in for 3 if the PDF is the Pro story we want to lead with later.
