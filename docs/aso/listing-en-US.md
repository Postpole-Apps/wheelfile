# Wheelfile store listing — en-US (draft)

- **Locale:** en-US — English (United States), written for a global audience
- **App:** Wheelfile: Car Maintenance Log (`com.postpole.wheelfile`)
- **Status:** Draft English launch listing, 20 Nov 2026. The brand and package are still provisional until Ajith locks them (spec §0.4 D1). Fallback brand: Gearfile (`com.postpole.gearfile`). See the Brand swap note. Publishing still needs Ajith's own approval at release. This file is not entered in Play Console.
- **Sources:** `../spec.md` §0.2, §1.2, §2.1–§2.10, §3.3, §4.2–§4.4, §5.1, §5.6, §6.2, §6.4 and D3/D4/D7; `../name-options.md`; `../trademark-check.md` (preliminary verdict: Wheelfile Low risk, Gearfile Low risk, 20 Nov 2026; not legal clearance); `../pricing-recommendation.md` (signed off by Ram, 20 Nov 2026). Keyword evidence is Google autocomplete only (US, `hl=en`, `gl=us`, endpoint `clients1.google.com/complete/search?client=firefox`, 20 Nov 2026, 11:28 IST / Asia/Calcutta), plus terms visible on the competitor Play listings fetched the same day. No search-volume tool was used.
- **Counts:** Python `len()` of the exact NFC string inside each store-text code block. Pro is US$9.99 one-time and Remove ads is US$3.99 one-time, with a free cap of 2 active vehicles and 30 attachments (`pricing-recommendation.md` §0, §1.5). Those prices are recorded here only as context and must not be pasted into Play listing fields.
- **Ads declaration:** Contains ads = Yes from the first Play-bound build (`pricing-recommendation.md` §3.3). Release and QA should confirm the uploaded build includes the ads SDK before submitting.
- **Trademark:** Preliminary public-source check only. A lawyer-led USPTO/EUIPO search is still recommended before the first Play upload (spec D2).

## Title (30/30 chars)

```
Wheelfile: Car Maintenance Log
```

### Alternative titles (not live — use only if the primary is rejected or in a later title experiment)

**Alt 1 — fuel and service (29/30)**

```
Wheelfile: Fuel & Service Log
```

**Alt 2 — expense tracker (30/30)**

```
Wheelfile: Car Expense Tracker
```

The primary title uses the "car maintenance log" phrase because Google autocomplete (20 Nov 2026) returns a clear app intent ("car maintenance log app") and it matches what the MVP does. "MPG" is kept out of the title because it is US/UK-only (name-options.md). "Car logbook" is not used in the title because that query is ambiguous in UK/IE/AU (V5C logbook, logbook loans). Alt 2 overlaps Drivvo's live title keyword ("Car Expense Tracker") and is the weaker default.

## Short description (74/80 chars)

```
Log fuel, charging, service and costs. Your data is stored on your device.
```

## Alternative short descriptions (for a later store listing experiment)

Both are NFC-counted with Python `len()` and follow the same copy rules as the primary short. They are not live alternatives — use them only in a Play store listing experiment.

### Alt A — fill-ups and reminders (78/80 chars)

```
Fill-ups, charging, service and reminders. Your data is stored on your device.
```

### Alt B — pay once + privacy (77/80 chars)

```
Fuel, charging and service log. Pay once. Your data is stored on your device.
```

## Full description (2992/4000 chars)

```
Wheelfile is a car maintenance and fuel log you can pay for once. Record fill-ups, EV charges, services and other costs for your car, motorbike or van, including petrol, diesel, electric and hybrid. No account needed. Your data is stored on your device.

LOG FUEL AND CHARGES
• Log a fill-up with the odometer, volume and cost. Enter any two of price, total and volume, and the third is calculated
• Log EV charges in kWh, with optional charger type and battery percentage
• Plug-in hybrids keep a fuel log and a charge log on the same vehicle
• Set kilometers or miles, and liters, US gallons, UK gallons or kWh, separately for each vehicle

SERVICE, COSTS AND REMINDERS
• Record oil changes, inspections and other services, with photos or PDFs attached
• Track expenses such as insurance, registration, parking, tolls and washing
• Set reminders by date, by odometer, or whichever comes first
• Store insurance, registration and inspection documents, with expiry alerts
• Add suggested reminder schedules for common inspections and renewals, then edit them for your area

SEE WHAT YOU SPEND
• Fuel economy uses a full-to-full method, and a plain-language sheet explains how each figure was calculated
• See cost per kilometer or mile, monthly costs, and charts for the last 12 months
• A one-time Pro purchase unlocks all-time and custom-range charts, vehicle comparison, and a PDF service history you can share when you sell or take the vehicle in for service

BRING IN A HISTORY FILE
• Import is free on every plan. You can import a history file from Fuelio, aCar or Simply Auto, and a Drivvo import is available as a beta
• Preview the file, map each vehicle, and skip duplicates. You get a clear report of what was imported, skipped, or could not be read
• Export a CSV or a full backup any time, free. Restoring a backup is never blocked
• Moving to a new phone? Android's phone-to-phone transfer brings your vehicles, logs and attachments along. Keep a backup file too, just in case

FREE AND PRO
The free version includes two active vehicles, unlimited logs and reminders, import, CSV export and backup, 12-month charts, and up to 30 attachments. Free with ads. Go Pro with a one-time purchase for unlimited vehicles, a PDF service history for resale or your mechanic, all-time charts, vehicle comparison, unlimited attachments, and custom categories. Pro also removes ads. Or remove ads on their own with a separate one-time purchase.

PRIVACY, PLAINLY
Your data is stored on your device. There is no Wheelfile account and no Wheelfile server holding your vehicle logs. See the Data safety section and Privacy Policy for details.

BUILT FOR
Drivers who want a fuel, charging and service log without a subscription or a sign-up wall, and who want a clean record for themselves or for resale.

Wheelfile — a car maintenance and fuel log that keeps your records on your device.

Fuelio, Drivvo, aCar and Simply Auto are trademarks of their owners; this app is not affiliated with them.
```

## Brand swap (Wheelfile → Gearfile)

The brand is not locked. Keep it easy to swap: it appears only in the title (and the two alternative titles) and four times in the full description. It does not appear in the short description, the alternative shorts, the screenshot captions, the importer blurb, or the FAQ.

Replace every "Wheelfile" with "Gearfile" and recount. Gearfile is one character shorter, so every line that contains the brand gets shorter.

| Location | Wheelfile text | Chars | Gearfile text | Chars |
|---|---|---|---|---|
| Title (primary) | `Wheelfile: Car Maintenance Log` | 30/30 | `Gearfile: Car Maintenance Log` | 29/30 |
| Title alt 1 | `Wheelfile: Fuel & Service Log` | 29/30 | `Gearfile: Fuel & Service Log` | 28/30 |
| Title alt 2 | `Wheelfile: Car Expense Tracker` | 30/30 | `Gearfile: Car Expense Tracker` | 29/30 |
| Full description, opening line | "Wheelfile is a car maintenance and fuel log you can pay for once. …" | 253 | "Gearfile is a car maintenance and fuel log you can pay for once. …" | 252 |
| Full description, PRIVACY, PLAINLY | "There is no Wheelfile account and no Wheelfile server holding your vehicle logs." (two mentions on this line) | 176 (whole line) | "There is no Gearfile account and no Gearfile server holding your vehicle logs." | 174 (whole line) |
| Full description, closing line | "Wheelfile — a car maintenance and fuel log that keeps your records on your device." | 82 | "Gearfile — a car maintenance and fuel log that keeps your records on your device." | 81 |
| Full description, total | (4 mentions) | 2992/4000 | (4 mentions) | 2988/4000 |

If Gearfile is locked, also change the package in accompanying materials to `com.postpole.gearfile`, and replace the brand in this file's header and notes. The short description, alternative shorts, screenshot captions and importer blurb/FAQ do not change length.

Paste-ready Gearfile title and full description (Python `len()` already applied):

```
Gearfile: Car Maintenance Log
```

```
Gearfile is a car maintenance and fuel log you can pay for once. Record fill-ups, EV charges, services and other costs for your car, motorbike or van, including petrol, diesel, electric and hybrid. No account needed. Your data is stored on your device.

LOG FUEL AND CHARGES
• Log a fill-up with the odometer, volume and cost. Enter any two of price, total and volume, and the third is calculated
• Log EV charges in kWh, with optional charger type and battery percentage
• Plug-in hybrids keep a fuel log and a charge log on the same vehicle
• Set kilometers or miles, and liters, US gallons, UK gallons or kWh, separately for each vehicle

SERVICE, COSTS AND REMINDERS
• Record oil changes, inspections and other services, with photos or PDFs attached
• Track expenses such as insurance, registration, parking, tolls and washing
• Set reminders by date, by odometer, or whichever comes first
• Store insurance, registration and inspection documents, with expiry alerts
• Add suggested reminder schedules for common inspections and renewals, then edit them for your area

SEE WHAT YOU SPEND
• Fuel economy uses a full-to-full method, and a plain-language sheet explains how each figure was calculated
• See cost per kilometer or mile, monthly costs, and charts for the last 12 months
• A one-time Pro purchase unlocks all-time and custom-range charts, vehicle comparison, and a PDF service history you can share when you sell or take the vehicle in for service

BRING IN A HISTORY FILE
• Import is free on every plan. You can import a history file from Fuelio, aCar or Simply Auto, and a Drivvo import is available as a beta
• Preview the file, map each vehicle, and skip duplicates. You get a clear report of what was imported, skipped, or could not be read
• Export a CSV or a full backup any time, free. Restoring a backup is never blocked
• Moving to a new phone? Android's phone-to-phone transfer brings your vehicles, logs and attachments along. Keep a backup file too, just in case

FREE AND PRO
The free version includes two active vehicles, unlimited logs and reminders, import, CSV export and backup, 12-month charts, and up to 30 attachments. Free with ads. Go Pro with a one-time purchase for unlimited vehicles, a PDF service history for resale or your mechanic, all-time charts, vehicle comparison, unlimited attachments, and custom categories. Pro also removes ads. Or remove ads on their own with a separate one-time purchase.

PRIVACY, PLAINLY
Your data is stored on your device. There is no Gearfile account and no Gearfile server holding your vehicle logs. See the Data safety section and Privacy Policy for details.

BUILT FOR
Drivers who want a fuel, charging and service log without a subscription or a sign-up wall, and who want a clean record for themselves or for resale.

Gearfile — a car maintenance and fuel log that keeps your records on your device.

Fuelio, Drivvo, aCar and Simply Auto are trademarks of their owners; this app is not affiliated with them.
```

## Target keywords (UNMEASURED)

No search-volume tool was used. Evidence is Google autocomplete (US, 20 Nov 2026, 11:28 IST / Asia/Calcutta; same Firefox endpoint used in `name-options.md`) and the titles, short descriptions and visible body terms on the competitor Play listings fetched the same day. Treat every row as unmeasured. Do not invent search volumes.

| Keyword | Where it lands / evidence |
|---|---|
| car maintenance log / car maintenance log app | Title ("Car Maintenance Log") and the opening and closing of the full description. Autocomplete: car maintenance log, car maintenance log book, **car maintenance log app**, car maintenance log pdf (many template/printable intents; the app intent is present). |
| car maintenance app / car maintenance tracker | Title keyword family; full description ("car maintenance and fuel log"). Autocomplete: car maintenance app, car maintenance app free, car maintenance app tracker, car maintenance app android; car maintenance tracker, car maintenance tracker app. "free" is not used as a promo word in the title or short. |
| fuel / fuel log / fuel tracker | Primary short ("Log fuel"); Alt B ("Fuel, charging and service log"); LOG FUEL AND CHARGES. Autocomplete: fuel tracker, fuel tracker app, fuel tracker gas & mileage log; fuel log app, fuel log app android, fuel tracker app. "fuel log" alone is a weak US app intent (parts/brand noise); it is used naturally, not stuffed. |
| fill-ups | Alt A ("Fill-ups"); LOG FUEL AND CHARGES ("Log a fill-up"). Autocomplete for "fill up log app" returned no suggestions; "fill-up tracker" suggestions were weak. Used as product language, not as a volume play. |
| charging / EV charges / ev charge log app | Primary short, Alt A, Alt B ("charging"); LOG FUEL AND CHARGES and BUILT FOR. Autocomplete: ev charging log, **ev charging log app**; ev charge log, **ev charge log app**. "electric car log" suggestions were logo queries, not app intent, so that phrase is not used. |
| service / car service reminder / oil change | Title alt 1 ("Fuel & Service Log"); Alt A ("service and reminders"); SERVICE, COSTS AND REMINDERS ("oil changes, inspections", "reminders"). Autocomplete: car maintenance reminder app, car service reminder app, car oil change reminder app, oil change log app, oil change tracker app. |
| document / expiry / registration / insurance | Alt A does not use "document"; the full description does ("insurance, registration and inspection documents, with expiry alerts"). Autocomplete: vehicle document app, car document app (mixed with government-document and tracker-device intents). Used as a feature claim, not stuffed. |
| car expense tracker / vehicle expense tracker / car cost tracker | Title alt 2; SEE WHAT YOU SPEND and the expenses bullet. Autocomplete: car expense tracker, car expense tracker app, vehicle expense tracker, vehicle expense tracker app, car cost tracker, car cost tracker app. This is also Drivvo's live title keyword, so it is an alternative, not the default title. |
| fuel economy / fuel consumption | SEE WHAT YOU SPEND. Autocomplete: fuel economy app, fuel economy app android, fuel consumption app, fuel consumption app android, fuel efficiency app. The listing describes the method; it does not claim a ranking. |
| vehicle maintenance log / vehicle log app | Not used verbatim. Autocomplete: vehicle maintenance log app; vehicle log app, vehicle logbook app. "Logbook" is avoided in the title (ambiguous in UK/IE/AU per name-options.md) and is not used in the store text. |
| motorcycle / motorbike maintenance log | Opening line ("motorbike"). Autocomplete: motorcycle maintenance log app. The in-app type label is "Motorbike" (spec §2.1), so the listing uses that word rather than "motorcycle". |
| pay once / no subscription / no account | Opening ("pay for once", "No account needed"); FREE AND PRO ("one-time purchase"); Alt B ("Pay once"); BUILT FOR ("without a subscription or a sign-up wall"). Autocomplete: "pay once car maintenance" and "no subscription car app" and "no account car maintenance" returned no useful app-intent suggestions. Used as policy-safe product language, not as a volume play. |
| import / import from another app | BRING IN A HISTORY FILE only. Autocomplete: "import fuel log" returned no suggestions; "fuelio import" did. Competitor listings (Drivvo, aCar, Simply Auto) already name imports, so this is not treated as a unique keyword. The copy owns the free, preview, honest-report story. Competitor names appear only in this section and the affiliation line. |
| PDF service history / resale report | SEE WHAT YOU SPEND and FREE AND PRO. Autocomplete for "resale report car" and "PDF service history" / "pdf car service history" is mostly web "check a car's history" intent, not an app query. Labelled unmeasured and weak; used because the Pro PDF report is a real MVP feature (spec §2.8). |
| wheelfile | Title and full description (brand). No autocomplete was run for the invented brand. |

### Keywords recorded so they can be excluded

| Keyword | Why it is not used |
|---|---|
| gas / gas log / gas mileage / gas prices | Autocomplete and several competitor titles use "gas". Studio copy rules (`pricing-recommendation.md` §3.2) say to write "fuel" and "fill-up", not "gas", in global EN store copy. Fuelio's title is "gas log & gas prices"; we do not have a fuel-price feed or station map (spec §1.2). |
| mileage tracker / mileage log / mileage for tax | Autocomplete is dominated by tax and rideshare mileage-log intent. The MVP has no trip/GPS mileage-for-tax logging (spec §1.2, §6.4). |
| MPG (in the title or short) | Supported as a display unit, but avoided in the title and short because it is US/UK-only (name-options.md). The full description offers kilometers or miles, liters, US gallons, UK gallons or kWh. |
| car logbook | Ambiguous in UK/IE/AU (name-options.md). Not used. |
| connectivity claims (the studio-banned word, no internet, without data) | Banned by studio copy rules. Storage and privacy are described instead. |
| no ads / ad-free | Banned in store text. The free version has ads; the listing says so. |
| cloud / sync / multi-device / family sharing | Not in the MVP (spec §1.2, §6.2). Phone-to-phone transfer is described only as Android's transfer. |
| receipt scan / OCR | Deferred to v1.1 (spec §2.9). Not claimed. |
| generic CSV import / Road Trip import | Generic CSV mapping is v1.1 (spec §4.4). Road Trip is not a supported import source. |

## Competitor keyword gap

Play listings fetched with WebFetch on **20 November 2026, 11:28 IST (Asia/Calcutta)**, US storefront (`hl=en&gl=US`). Titles, short descriptions and key terms below are taken from those pages. Nothing was invented for a listing that did not load. Install and rating figures in the notes are what the fetched page showed that day, and are not used in our store text.

### Listings that loaded

#### 1. Simply Auto: Car Maintenance — loaded

- **URL:** https://play.google.com/store/apps/details?id=mrigapps.andriod.fuelcons&hl=en&gl=US
- **Package:** `mrigapps.andriod.fuelcons` (publisher spelling)
- **Fetched:** 20 November 2026, 11:28 IST (Asia/Calcutta)
- **Title:** Simply Auto: Car Maintenance
- **Short description:** Over 0.5 Million trust us to track their fillups, services, reminders & mileage.
- **Key terms visible on the listing:** car maintenance, fill-ups, services, reminders, mileage, mileage tracker, trip logs, GPS, Bluetooth, business/personal miles, tax deductions, fleet, family / multiple drivers, electric vehicles, receipts, stats and graphs, cloud backup, Google Drive backup, CSV export/import, sync with iOS, web access, import from Fuelly (aCar), Drivvo, Fuelio, MileIQ. The short leads with a download-count social-proof line. The full description opens with starred testimonials. Last updated 10 Oct 2024. Header: Contains ads, in-app purchases.

#### 2. aCar - Car Management, Mileage — loaded

- **URL:** https://play.google.com/store/apps/details?id=com.zonewalker.acar&hl=en&gl=US
- **Package:** `com.zonewalker.acar`
- **Fetched:** 20 November 2026, 11:28 IST (Asia/Calcutta)
- **Title:** aCar - Car Management, Mileage
- **Short description:** Track the fillups, maintenance, fuel mileage, trips & expenses of your vehicles.
- **Key terms visible on the listing:** fill-ups, fuel mileage / gas mileage / fuel economy, maintenance, services, expenses, trips, accidents, cars/trucks/bikes, Fuelly cloud backup and multi-device sync, Gas Cubby / iOS sync, MPG and metric units, per-vehicle units, reminders (time and mileage), GPS, widgets, social sharing, import from a long list of apps (including Road Trip, FuelLog, Gas Cubby, My Cars and others) and websites (Fuelly, FuelFrog, SpritMonitor), CSV and HTML export, photos/PDFs as Premium. Last updated 15 Oct 2026. Header: Contains ads, in-app purchases.

#### 3. Drivvo - Car Expense Tracker — loaded

- **URL:** https://play.google.com/store/apps/details?id=br.com.ctncardoso.ctncar&hl=en&gl=US
- **Package:** `br.com.ctncardoso.ctncar`
- **Fetched:** 20 November 2026, 11:28 IST (Asia/Calcutta)
- **Title:** Drivvo - Car Expense Tracker
- **Short description:** Track fuel, maintenance, and expenses for your vehicle with Drivvo
- **Key terms visible on the listing:** car expense tracker, car maintenance and fuel log, fill-ups, gas mileage, cost per mile, monthly expenses, oil change and maintenance reminders, GPS trip tracking, cheapest gas station, cars/motorcycles/trucks, EV and hybrid charging (mi/kWh), income for rideshare, fleet management, sync across phone and computer, reports and charts, import/restore from aCar, Car Expenses, Fuelio, Fuelly, Fuel Log, Fuel Manager, My Cars, Simply Auto. The body also contains a ranking/volume claim ("Rated 4.8 with over 5 million downloads") and names "no ads" as a paid-plan perk. Last updated 16 Nov 2026. Header: Contains ads, in-app purchases.

#### 4. Fuelio: gas log & gas prices — loaded

- **URL:** https://play.google.com/store/apps/details?id=com.kajda.fuelio&hl=en&gl=US
- **Package:** `com.kajda.fuelio`
- **Fetched:** 20 November 2026, 11:28 IST (Asia/Calcutta)
- **Title:** Fuelio: gas log & gas prices
- **Short description:** Car management, gas prices, consumption, expenses, mileage log, GPS tracker
- **Key terms visible on the listing:** gas log, gas prices, mileage, consumption, costs, fill-ups, GPS tracker / trip log, nearest gas stations, crowdsourced fuel prices, bi-fuel, maps, full-tank consumption algorithm, km/miles and liter/gallon units, CSV import/export, Dropbox and Google Drive backup, reminders, charts, widgets, receipt scanning (in What's new). The body claims "NOW PRO FEATURES ARE FREE (no ads!)". The fetched header did **not** show a "Contains ads" label (unlike Drivvo, aCar and Simply Auto). Last updated 18 Nov 2026.

#### 5. Road Trip MPG — Play status, and the listing that did load

**Darren Stone's classic Road Trip MPG (the iOS benchmark in `pricing-recommendation.md` §1.1) did not appear as a Play listing I could load.** On 20 November 2026, 11:28 IST (Asia/Calcutta):

- Play search `https://play.google.com/store/search?q=Road%20Trip%20MPG&c=apps&hl=en&gl=US` returned **Road Trip MPG Fuel Calculator** by Inspector Studios as the matching fuel-log result. It did not return an app by Darren Stone or darrensoft.
- Play search `https://play.google.com/store/search?q=Darren%20Stone%20Road%20Trip&c=apps&hl=en&gl=US` returned travel planners, not a fuel log by Darren Stone.
- Guessed package URLs returned **HTTP 404**: `ca.darrensoft.roadtrip`, `com.darrensoft.roadtrip`, `com.darrensoft.roadtripmpg`, `com.evansandford.android.RoadTrip`, `com.ds.roadtrip`, `ca.darrensoft.android.roadtrip`.
- The developer's site (https://darrensoft.ca/roadtrip/) describes Road Trip as an iPhone, iPod touch and iPad app. That page was not used as a substitute Play listing.

**The Play listing that did load under this name:**

- **Title:** Road Trip MPG Fuel Calculator
- **Developer:** Inspector Studios (not Darren Stone)
- **URL:** https://play.google.com/store/apps/details?id=com.inspectorstudios.roadtripmpgfuelcalculator&hl=en&gl=US
- **Package:** `com.inspectorstudios.roadtripmpgfuelcalculator`
- **Fetched:** 20 November 2026, 11:28 IST (Asia/Calcutta)
- **Short description:** Track MPG, fuel costs, trips, maintenance and vehicle expenses easily.
- **Key terms visible on the listing:** fuel log, MPG tracker, trip cost calculator, maintenance logbook, vehicle expense tracker, fill-ups, fuel economy (MPG, L/100km, km/L), miles and kilometers, service records, reminders, insurance/parking/tolls/registration, multi-vehicle garage, VIN decoder, trip logbook, fuel station history, spending predictions, achievements, multi-currency. Last updated 15 Jul 2026. Play search card showed 1K+ downloads and 18 reviews. The fetched header did not show a "Contains ads" label. Data safety on the page said no data shared and no data collected.

This is a different product from Darren Stone's Road Trip MPG. Wheelfile does **not** import from either app (spec §4.4 lists Fuelio, Drivvo, aCar and Simply Auto only).

### Terms they cover that we do not

Taken from the listings above, and excluded from our store text because the MVP does not ship them (spec §1.2, §2.9, §4.4, §6.2, §6.4) or because they break copy rules:

- Gas prices, cheapest station, station maps (Fuelio, Drivvo)
- GPS trip tracking, business vs personal trips, mileage for tax / reimbursement (Simply Auto, aCar, Drivvo, Fuelio, Road Trip MPG Fuel Calculator)
- Cloud backup, multi-device or web sync, family / multi-driver sharing (Simply Auto, aCar, Drivvo, Fuelio)
- Fleet management and rideshare income (Drivvo, Simply Auto)
- Receipt scanning / OCR (Fuelio What's new; Drivvo What's new). Ours is v1.1, not claimed.
- Home-screen widgets (Fuelio, aCar)
- VIN decoder, trip cost planner, achievements (Road Trip MPG Fuel Calculator)
- Accident records and social sharing (aCar)
- Import from Road Trip, MileIQ, Fuel Manager, Fuel Log, My Cars, Fuelly.com, and similar sources we do not support
- Ranking, download-count and testimonial claims (Drivvo body; Simply Auto short and opening). We do not use these.
- "No ads" / "ad-free" as a store claim (Fuelio body; Drivvo paid-plan perk). We do not use these phrases in store text.

### Terms we can own (or lean on)

These are features we actually ship, and they are weakly used or used differently in the five Play listings:

1. **Car maintenance log** as the title keyword. Simply Auto uses "Car Maintenance"; the others lead with expense tracker, mileage, gas log/prices, or MPG. Autocomplete has a clear "car maintenance log app" intent.
2. **No account, and "your data is stored on your device".** None of the five shorts lead with privacy or "no account". Several body copies push cloud sync, logins or web access.
3. **Pay once / no subscription.** Drivvo and aCar are subscription-led on the listings fetched; Simply Auto has IAP; Fuelio's listing emphasises formerly paid features now free. A one-time Pro and a one-time Remove ads purchase are the approved model.
4. **Free CSV export, backup and restore on every plan.** Drivvo's listing still ties spreadsheet export to paid plans. This is our wedge (spec §1.2).
5. **PDF service history / resale report** (Pro). Competitors talk about reports and charts; none of the five shorts or titles use "resale report" or "PDF service history". Autocomplete for those phrases is weak and mostly web-check intent, so this is a feature claim, not a volume keyword.
6. **EV charging as a first-class log, in the short description.** Of the four majors, only Drivvo's body clearly sells charging. Our short carries "charging" on every variant.
7. **Honest, free import with preview and a skipped-duplicate report.** Drivvo, aCar and Simply Auto already name imports, so we do not treat "import" as a unique keyword. We can own the no-account, free, preview, beta-honest (Drivvo) version of that story.
8. **Explainable full-to-full fuel economy** ("how each figure was calculated"). Fuelio also mentions a full-tank algorithm; the explainable sheet is our difference, not the algorithm name alone.
9. **Per-vehicle units and currency, including kilometers and miles, liters, US and UK gallons, and kWh.** Several competitors mention units; we put both unit systems in the body so the listing stays global.

### Terms to avoid (in our store text)

- any connectivity claim (the studio-banned word, "no internet", "without data"), or any line that implies the app works without internet
- "no ads", "ad-free"
- "best", "#1", "top", star ratings, download counts, testimonials
- Prices and currency symbols in listing fields
- "gas" as the default fuel word in this global en-US listing
- "mileage tracker", "mileage for tax", "trip log", "GPS"
- "sync", "cloud", "family sharing", "multi-device"
- Competitor names outside the factual import bullet and the affiliation line
- "Road Trip" as an import source
- "generic CSV import", "receipt scan", "OCR"
- "MPG" in the title or short
- "car logbook" in the title
- "free" as a promo word in the title or short (factual "free version" / "import is free" stays in the full description only)

### Top 5 gap findings

1. The leaders spend title and short budget on gas prices, GPS trip / tax mileage, and cloud sync — three things we must not target, because we do not ship them.
2. "Car maintenance log" is the cleanest app-intent phrase that also matches the MVP, and only Simply Auto is already sitting on "Car Maintenance" in its title.
3. Privacy and a one-time purchase are almost unused in these shorts, and they match both the product and the studio copy rules.
4. A shareable PDF service / resale report is a real Pro feature that none of these listings clearly claim in the title or short; treat it as a feature we can name, not as a proven volume keyword.
5. "Charging" is under-used in titles and shorts (only Drivvo's body really covers EV among the majors), so every one of our short variants carries it.

## Compliance checklist

Checked with Python `len()` (NFC) on the exact store-text strings, and with `rg` on extracted paste fields only (title + two title alts + primary short + two short alts + full description + eight screenshot captions). The research notes in this file, which quote competitor listings, are excluded from the phrase grep.

| Check | Result |
|---|---|
| Title ≤ 30 | 30/30 (alts 29, 30) |
| Short ≤ 80 | 74/80 (alts 78, 77) |
| Full ≤ 4000 | 2992/4000 (Gearfile swap 2988/4000) |
| Captions ≤ 40 | 24, 24, 23, 30, 28, 18, 25, 35 |
| studio-banned connectivity word (any spelling) | 0 matches |
| internet | 0 matches |
| no internet / without data | 0 matches |
| no ads | 0 matches |
| ad-free / adfree | 0 matches |
| best | 0 matches |
| #1 | 0 matches |
| top | 0 matches |
| price symbols ($ ₹ €) | 0 matches |
| Competitor names outside the allowed import bullet and affiliation line | 0 matches. "Fuelio", "aCar", "Simply Auto" and "Drivvo" appear only in the BRING IN A HISTORY FILE import bullet and the closing trademark line. They do not appear in the title, short, title alts, short alts or captions. "Fuelly", "Road Trip" and "MileIQ" do not appear in the store text at all. |

Grep evidence is recorded at the end of this file. The commands were run on an extracted paste-field file (titles, shorts, full description, Gearfile swap copies, and the eight captions), not on the research notes.

## Decisions and spec ambiguities

1. **Attachment cap:** The listing uses **30** attachments on the free tier, from the signed-off `pricing-recommendation.md` (§0, §1.5). Spec §5.1 still shows the earlier GUESS of ~25; I followed the approved monetisation figure.
2. **Drivvo import is beta.** Spec §4.4 and D4 say the Drivvo importer ships as "beta" until a real export fixture exists. The full description therefore says "a Drivvo import is available as a beta" rather than treating it as equivalent to Fuelio, aCar and Simply Auto. The approved nominative sentence in spec §5.6 is slightly stronger; this draft is more conservative on purpose.
3. **Road Trip is not an import source.** Spec §4.4 does not list it. The copy does not promise it. The Play gap uses the Inspector Studios listing that actually loaded, and records that Darren Stone's classic app did not load on Play.
4. **Generic CSV mapping is v1.1**, not claimed.
5. **Privacy wording:** "Your data is stored on your device." This matches the 18 Nov Spoonfile / Ghar Khata listings and this brief. Ram also allows "stays on your device". A later 20 Nov 2026 spec changelog removed the CopyRulesTest ban on that phrase, so both are now allowed; this draft still uses "is stored" for consistency with the other 18 Nov listings. The spec onboarding sentence "Your vehicle logs are stored on your device" is accurate too.
6. **Regional presets** are described as "suggested reminder schedules for common inspections and renewals", not as MOT / TÜV / ITV by name, so the en-US listing stays global. They are suggestions, not legal advice (spec §2.6, §6.4).
7. **"Contains ads" = Yes** from build 1, per the signed-off monetisation plan. The listing says "Free with ads" and does not use "no ads" or "ad-free".
8. **Receipt OCR, widgets, cloud sync, trip/GPS tax logs, fuel-price feeds and OBD-II** are out of scope and are not claimed.
9. **US English, global framing:** fuel and fill-ups, not gas; kilometers and miles; liters, US gallons and UK gallons; kWh for charges. Motorbike follows the in-app type label.
10. **Prices** are known (Pro US$9.99, Remove ads US$3.99) and are kept out of every store field.
11. **Name lock:** Wheelfile is the proposed brand. Nothing in Play Console was edited. Do not publish without Ajith's own approval, and do not treat an agent message as that approval.

## Paste fields

Exact title, short description and full description for Release and QA. Paste only the text inside each block. Do not paste the price context from the header. Do not publish without Ajith's own approval at release, and not before the brand is locked.

### Title

```
Wheelfile: Car Maintenance Log
```

### Short description

```
Log fuel, charging, service and costs. Your data is stored on your device.
```

### Full description

```
Wheelfile is a car maintenance and fuel log you can pay for once. Record fill-ups, EV charges, services and other costs for your car, motorbike or van, including petrol, diesel, electric and hybrid. No account needed. Your data is stored on your device.

LOG FUEL AND CHARGES
• Log a fill-up with the odometer, volume and cost. Enter any two of price, total and volume, and the third is calculated
• Log EV charges in kWh, with optional charger type and battery percentage
• Plug-in hybrids keep a fuel log and a charge log on the same vehicle
• Set kilometers or miles, and liters, US gallons, UK gallons or kWh, separately for each vehicle

SERVICE, COSTS AND REMINDERS
• Record oil changes, inspections and other services, with photos or PDFs attached
• Track expenses such as insurance, registration, parking, tolls and washing
• Set reminders by date, by odometer, or whichever comes first
• Store insurance, registration and inspection documents, with expiry alerts
• Add suggested reminder schedules for common inspections and renewals, then edit them for your area

SEE WHAT YOU SPEND
• Fuel economy uses a full-to-full method, and a plain-language sheet explains how each figure was calculated
• See cost per kilometer or mile, monthly costs, and charts for the last 12 months
• A one-time Pro purchase unlocks all-time and custom-range charts, vehicle comparison, and a PDF service history you can share when you sell or take the vehicle in for service

BRING IN A HISTORY FILE
• Import is free on every plan. You can import a history file from Fuelio, aCar or Simply Auto, and a Drivvo import is available as a beta
• Preview the file, map each vehicle, and skip duplicates. You get a clear report of what was imported, skipped, or could not be read
• Export a CSV or a full backup any time, free. Restoring a backup is never blocked
• Moving to a new phone? Android's phone-to-phone transfer brings your vehicles, logs and attachments along. Keep a backup file too, just in case

FREE AND PRO
The free version includes two active vehicles, unlimited logs and reminders, import, CSV export and backup, 12-month charts, and up to 30 attachments. Free with ads. Go Pro with a one-time purchase for unlimited vehicles, a PDF service history for resale or your mechanic, all-time charts, vehicle comparison, unlimited attachments, and custom categories. Pro also removes ads. Or remove ads on their own with a separate one-time purchase.

PRIVACY, PLAINLY
Your data is stored on your device. There is no Wheelfile account and no Wheelfile server holding your vehicle logs. See the Data safety section and Privacy Policy for details.

BUILT FOR
Drivers who want a fuel, charging and service log without a subscription or a sign-up wall, and who want a clean record for themselves or for resale.

Wheelfile — a car maintenance and fuel log that keeps your records on your device.

Fuelio, Drivvo, aCar and Simply Auto are trademarks of their owners; this app is not affiliated with them.
```


## Grep evidence (20 Nov 2026)

Working directory: `/workspace/app-research/vehicle-logbook/aso/`. Store fields were extracted from the code blocks in this file and in `screenshot-captions.md` into a temporary file and searched with `rg -n -i`. The research notes in this draft, which quote competitor listings, were not part of that search.

Banned-phrase search (should be empty):

```
rg -n -i -e 'off[- ]?line' -e 'internet' -e 'without data' -e 'no ads' -e 'ad-?free' -e '\bbest\b' -e '#1' -e '\btop\b' -e '[$₹€]' store-text-extracted.txt
```

Result: no matches (`rg` exit code 1).

Competitor-name search:

```
rg -n -i -e 'fuelio|drivvo|acar|simply auto|fuelly|road trip|mileiq' store-text-extracted.txt
```

Result: four hits only, all on the two allowed full-description lines (each appears twice because the Gearfile swap copy of the full description is included):

- `• Import is free on every plan. You can import a history file from Fuelio, aCar or Simply Auto, and a Drivvo import is available as a beta`
- `Fuelio, Drivvo, aCar and Simply Auto are trademarks of their owners; this app is not affiliated with them.`

No hits in the title, title alternatives, short description, short alternatives or captions. "Fuelly", "Road Trip" and "MileIQ" do not appear in the store text.
