# Wheelfile — final design pack (build-ready)

**Wheelfile: Car Maintenance Log** · `com.postpole.wheelfile` · **Direction 4 Service-Book Ochre** + **Icon Concept 3 Hub Tab** — locked by Ajith on 20 Nov 2026, 3:45 PM IST.  
Revision 2 (20 Nov 2026, after Ram’s answers): Garage is the only top-level home; History / Documents / Reports live as Vehicle-detail tabs; Reminders bell; 0 ads on the first-run Garage; banner collapses on no-fill; bundled Roboto Flex Latin subset.  
Android / Material 3 only · Roboto Flex + Material Symbols Rounded (free) · static images only · every screen at 360×800dp, rendered @2x (720×1600 RGB), light **and** dark.

**Audit:** `python3 audit.py` → **PASS** (see `audit-report.json`). **Icon safe zone:** foreground 0 px outside r33 (max r 31.465dp), monochrome 0 px outside r33 (max r 29.698dp).

## Rebuild
```bash
cd /workspace/app-research/vehicle-logbook/design/final
./build_final.sh            # tokens → icon → screens + redlines → store → overview → audit → README → audit
# or step by step:
tokens/fonts/build_subset.sh          # Roboto Flex Latin subset -> tokens/fonts/roboto_flex.ttf
python3 tokens/fonts/verify_subset.py # subset vs full render diff -> subset-verify.png/json
python3 src/tokens.py                 # tokens/*.kt, tokens.json, tokens-sheet.png
python3 icon/src/build_final_icon.py  # icon layers, play-512.png, preview.png, safe-zone-check.json
python3 src/render_final.py [id ...]  # screens/*.png, screens/redlines/*.png, screens/render-log.json
python3 store/src/build_store.py      # feature graphic + 2-up preview
python3 src/overview.py               # 00-overview.png
python3 audit.py && python3 src/gen_readme.py
```
Needs Python 3 + Playwright with `/usr/bin/google-chrome`, Pillow, numpy, materialyoucolor. Screens render with the shipped subset `tokens/fonts/roboto_flex.ttf`; Material Symbols from `design/src/assets/MaterialSymbolsRounded.woff2`; fonttools ≥ 4.65 for the subset.

## Files
| Path | Pixels | What |
|---|---|---|
| `00-overview.png` | 2754×4072 RGB | contact sheet: every screen, light section + dark section, icon + feature graphic |
| `tokens/tokens-sheet.png` (+ `.html`) | 1800×2890 RGB | all colour roles light/dark, WCAG table, type scale, shapes, spacing, elevation |
| `tokens/Color.kt` `Theme.kt` `Type.kt` `Shape.kt` `Dimens.kt` `tokens.json` | — | Compose theme, package `com.postpole.wheelfile.ui.theme` |
| `tokens/fonts/roboto_flex.ttf` | 577,916 bytes | bundled font: Latin + Latin-ext subset, axes opsz/wght/wdth (full file 1,787,292 bytes) |
| `tokens/fonts/subset-verify.png` (+ `.json`, `build_subset.sh`, `verify_subset.py`) | 1904×1620 RGB | full font (top) vs subset (bottom) at Wheelfile weights/widths |
| `screens/<id>-light.png`, `screens/<id>-dark.png` | 720×1600 RGB each (58 files) | 360×800dp phones @2x |
| `screens/redlines/<id>-redline.png` | 1600×≥1600 RGB (27 files) | numbered redline boxes + dp legend (light) |
| `screens/render-log.json` | — | live DOM metrics per frame (ads, targets, glyphs, redline geometry) used by the audit |
| `icon/play-512.png` | 512×512 RGBA | Play icon, full square, no mask |
| `icon/preview.png` | 2149×2194 RGB | masks, layers, sizes, themed, Play crop, home screens |
| `icon/svg/*`, `icon/drawable/*`, `icon/mipmap-anydpi-v26/*`, `icon/play-512.svg`, `icon/safe-zone-check.json`, `icon/README.md` | — | adaptive icon + handoff notes |
| `store/feature-graphic-1024x500.png` | 1024×500 RGB | Play feature graphic (no alpha) · source `store/src/feature-graphic.html` |
| `store/feature-graphic-preview-2up.png` | 2168×650 RGB | full graphic with 16:9 centre-crop guide + the cropped, rounded Play-style view |
| `src/*.py`, `src/html/*.html` | — | generators (fcommon = design system CSS, screens = all frames, render_final, tokens, overview, gen_readme) |

## Screens
Ad column = ad slots on the frame (rules below). Each frame has a light + dark PNG; redlines are light.

| # | Frame id | Spec | Screen | Ads | One line |
|---|---|---|---|---|---|
| 1 | `s01-welcome` | S1 | Welcome | 0 | Icon, wordmark, spec welcome copy, 3 feature rows, Add a vehicle (56dp) + Import from another app. |
| 2 | `s01b-add-vehicle` | S1 | Add vehicle (step 1/3) | 0 | Full-screen sheet: name, type segmented, powertrain chips, make/model/year, plate, VIN with scan. |
| 3 | `s01c-units` | S1 | Units (step 2/3) | 0 | Locale-prefilled units: distance, volume, fuel + electric economy, currency picker. |
| 4 | `s01d-region-presets` | S1 | Region presets (step 3/3) | 0 | FR preset offer with checkboxes and the “Rules vary” disclaimer. |
| 5 | `s02-garage` | S2 | Garage (home) | 1 | Only top-level home, no tab row: vehicle chips, odometer hero, economy delta, quick actions, due card, second vehicle (tap → Vehicle detail); banner + FAB above it. |
| 6 | `s02-garage-ads-hidden` | S2 | Garage · ads hidden (Remove ads / Pro) | 0 | Same Garage; 51dp slot collapsed, FAB drops to 16dp above the gesture inset. Identical layout is used when the banner fails to load / no fill. |
| 7 | `s04-vehicle-overview` | S4 | Vehicle detail · Overview | 0 | Back · vehicle switcher · Reminders bell (badge 2) · tabs Overview·History·Documents·Reports; economy + How calculated?, costs, Next reminders + See all, recent entries, Fill-up FAB. 0 ads. |
| 8 | `s06-add-fillup` | S6 | Add fill-up | 0 | Total hero (auto-calculated), date/time, odometer with delta, volume + price, full-tank switches, extras chips, Save. |
| 9 | `s06b-add-charge` | S6 | Add charge | 0 | EV variant: kWh, price/kWh, Home/AC/DC, state of charge 22→80%, target-as-full switch. |
| 10 | `s07-add-service` | S7 | Add service | 0 | Multi-select service items, cost, workshop, receipt, “Create next reminder from this”. |
| 11 | `s09-reminders` | S9 | Reminders | 0 | Opened from the Vehicle-detail bell or Settings › Reminders; back arrow; Due soon / Upcoming with date, odometer and whichever-first triggers; extended FAB. |
| 12 | `s10-reminder-form` | S10 | Reminder form | 0 | Date | Odometer | Whichever first, estimate copy, repeat, alert lead-time chips. |
| 13 | `s05-vehicle-history` | S5 | Vehicle detail · History | 1 | Vehicle tab History: filter chips, month cards with totals, entry rows (economy delta, service tags, receipt); banner. |
| 14 | `s11-vehicle-documents` | S11 | Vehicle detail · Documents | 1 | Vehicle tab Documents: folder-tab cards (Vehicle papers · Receipts), expiry pills, attachment counter (13 of 30 free), FR preset note; banner. |
| 15 | `s12-document-detail` | S12 | Document detail | 0 | Expiry card, PDF attachment preview + Open, detail rows, disclaimer. 0 ads (attachment viewer path). |
| 16 | `s13-vehicle-reports` | S13 | Vehicle detail · Reports | 1 | Vehicle tab Reports: 12-month free window, locked All time/Compare/Custom, economy line, monthly cost stacked bars, price per litre, TalkBack summaries; banner. |
| 17 | `s18-settings` | S18 | Settings (top) | 0 | Units & currency, theme, language, Reminders (list entry), alert lead times, import, export CSV, backup & restore. |
| 18 | `s18b-settings-more` | S18 | Settings (scrolled) | 0 | Transfer note, Pro / Remove ads / Restore purchases, Privacy options, policy, About, Support admin@postpole.com. |
| 19 | `s18c-units-currency` | S18 | Units & currency | 0 | Defaults + currency picker and a preview row showing the same entry in US units (mi / MPG US / US$). |
| 20 | `s17-paywall` | S17 | Paywall | 0 | Remove ads and Pro (lifetime) side by side, both one-time, {price} tokens from Google Play, Restore purchases. |
| 21 | `st-empty-garage` | S2 | State · empty Garage (first run) | 0 | No vehicles yet: illustration, Add a vehicle + Import. No banner (0 slots) until ≥1 vehicle exists. |
| 22 | `st-loading` | S2 | State · loading | 1 | Skeleton at final sizes; 50dp banner slot reserved while the ad loads (collapses if it fails / no fill). |
| 23 | `st-error-import` | S14 | State · error (import rows) | 0 | Spec import result copy, 2 unreadable rows, over-cap vehicle imported archived, Copy error report. |
| 24 | `st-error-save` | S6 | State · error (save failure) | 0 | Odometer soft warning (spec copy) + “Couldn’t save” snackbar with Retry. |
| 25 | `st-free-limit` | S1/S2 | State · free limit (3rd vehicle) | 0 | Dialog over Add vehicle: 2 active vehicles → See Pro / Archive a vehicle / Cancel (mentions the 30-attachment free cap). |
| 26 | `st-pro-upsell` | S13 | State · Pro upsell (locked chart) | 1 | All time chip → inline locked chart card with See Pro; 12-month cost chart still visible; banner stays. |
| 27 | `st-notification-prompt` | S10 | State · notification permission | 0 | Rationale bottom sheet when saving the first reminder, then the Android system dialog. |
| 28 | `lt-garage-200` | S2 | Large text · Garage @200% | 1 | Android 14 nonlinear 200% font scale; cards grow, banner + FAB stay anchored, content scrolls. |
| 29 | `lt-add-fillup-200` | S6 | Large text · Add fill-up @200% | 0 | 200% scale; fields grow (min-height), Save stays pinned, form scrolls under it. |

## Component spec (applies to every screen)
All sizes dp; type = M3 role in `Type.kt`; colour = M3 role in `Color.kt` (+ `WheelfileTheme.colors` extras).

| Component | Size / spacing | Type role | Colour roles | States |
|---|---|---|---|---|
| Status bar | 32 (edge-to-edge, draw behind) | — | onSurface icons on surface | light/dark icons follow theme |
| Garage top app bar | 64 high · title 16 start · Import + Settings 48 | headlineSmall | surface / onSurface; icons onSurfaceVariant | NO tab row — Garage is the only top-level home |
| Vehicle-detail top app bar | 64 high · back 48 · vehicle switcher pill · Reminders bell 48 · overflow 48 | titleSmall (pill) | surface; pill surfaceContainer | scrolled: surfaceContainer (tonal level2) |
| Reminders bell | 48 target · 24 icon `notifications` · badge min 16, top 6 / right 5 | labelSmall (badge) | icon onSurfaceVariant · badge error/onError | contentDescription "Reminders, 2 due"; no badge when nothing is due |
| Other top app bars | 64 high · back/close 48 | titleLarge | surface / onSurface | Reminders list, Settings, Document, forms |
| Vehicle switcher pill | 48 high · 40 avatar · 10 end pad | titleSmall | surfaceContainer; avatar primary/onPrimary (EV tertiary/onTertiary) | pressed state layer 10% onSurface; opens vehicle menu |
| Vehicle tabs (Overview · History · Documents · Reports) | 48 high · 3dp indicator, 20% side inset | titleSmall | active primary · inactive onSurfaceVariant · divider outlineVariant | only inside Vehicle detail; ≥200% font scale → ScrollableTabRow |
| Screen content | 16 side margins · 12 top · 12 gap between cards | — | surface | scrolls under the banner/FAB zone |
| Card | radius 20 (hero/month 24) · padding 16 | titleMedium headers, bodySmall meta | surfaceContainer; outlined = outlineVariant 1dp | pressed state layer 10% |
| Due / expiry card | min 64 · radius 20 · padding 12×14 | titleMedium + bodySmall; count numberLarge 26 | warningContainer / onWarningContainer | overdue → errorContainer / onErrorContainer |
| List row | min 56 · 40 leading icon · 16 gap · divider outlineVariant | bodyLarge/titleSmall + bodyMedium/bodySmall | onSurface / onSurfaceVariant | pressed 10%; disabled 38% content |
| Filled text field | min 56 (grows with font scale) · FieldShape 14/14/4/4 · 14 start pad | label bodySmall · value bodyLarge (tabular) | surfaceContainerHigh; indicator onSurfaceVariant 1dp | focused primary 2dp + primary label · error error 2dp + error support · warning (soft) warning 2dp + onWarningContainer support · disabled 38% |
| Total hero (forms) | radius 28 · padding 14×20 | labelLarge + numberHero 52/56 (tabular) + labelMedium chip | primaryContainer/onPrimaryContainer (EV: tertiaryContainer) | recalculates live; auto chip says which field is derived |
| Chip (filter/assist) | 40 visual + 4dp invisible touch above/below = 48 · radius 10 · gap 8 | labelLarge | outline 1dp/onSurfaceVariant · selected secondaryContainer/onSecondaryContainer + check | disabled 38%; locked (Pro) shows lock icon |
| Segmented selector | 40 visual (48 touch) · radius 12 · gap 6 | labelLarge | outline / selected secondaryContainer | single-select |
| Buttons | filled 56 (primary CTA) or 48 · PillShape · 20 side pad | labelLarge (16sp on 56dp CTAs) | filled primary/onPrimary · tonal secondaryContainer · outlined outline+primary · text primary | pressed 10% · disabled 12% container / 38% label |
| Save bar (forms) | 56 high · 16 sides · 40 from bottom (24 gesture + 16) · surface strip 88 behind | labelLarge 16sp | primary/onPrimary | never moves; content scrolls under |
| FAB | 56 · radius 16 · 16 from right | — (extended: labelLarge) | primaryContainer/onPrimaryContainer · shadow level3 | bottom 91dp with banner (16 above it) · 40dp when ads hidden |
| Switch row | min 56 · switch 52×32 | bodyLarge + bodySmall | on primary/onPrimary · off surfaceContainerHighest + outline | disabled 38% |
| Snackbar | min 48 · 12 sides · radius 4 · above Save | bodyMedium + labelLarge action | inverseSurface / inverseOnSurface · action inversePrimary | auto-dismiss 10 s for errors with action |
| Dialog | 24 sides · radius 28 · padding 24 | headlineSmall + bodyMedium | surfaceContainerHigh · scrim 32% | actions end-aligned, 48dp |
| Bottom sheet | radius 28 top · 24 pad · handle 32×4 | headlineSmall + bodyMedium | surfaceContainerLow · scrim 32% | drag to dismiss |
| Folder-tab doc card | FolderTabShape: tab h26 r12, body r20 (0 top-start) · rows min 56 | tab labelMedium caps · rows titleSmall + bodySmall · pill labelMedium | primaryContainer (ICE) / tertiaryContainer (EV) · expiry pill warningContainer | expired → errorContainer pill |
| Economy delta pill | radius 8 · 2×8 pad | labelMedium (tabular) | better: positiveContainer/onPositiveContainer · worse: negativeContainer/onNegativeContainer | arrow icon + text (never colour alone) |
| Banner slot | 360×50 + 1dp hairline · bottom 24 (above gesture inset) | ad SDK | adSlotContainer, hairline adSlotOutline | reserved only while loading · collapses to 0 on load failure / no fill · absent when adsHidden or on the first-run empty Garage |

State layers follow M3: hover 8%, focus/pressed 10%, dragged 16% of the content colour. Disabled = 38% content, 12% container.

## Per-screen spec tables
Generated from the numbered redline boxes (`screens/redlines/<id>-redline.png`); x/y/w/h are dp measured from the rendered frame (light).

### S1 · Welcome — `s01-welcome`
Icon, wordmark, spec welcome copy, 3 feature rows, Add a vehicle (56dp) + Import from another app.  
Ads: **0** (onboarding) · 48dp targets measured: 2 · chips/segments (40dp + 48dp touch): 0 · tabular number elements: 1

| # | Element · spec | x | y | w | h |
|---|---|---|---|---|---|
| 1 | App icon · Hub Tab · 96dp | 132 | 88 | 96 | 96 |
| 2 | Wordmark · headlineLarge · onSurface | 110 | 200 | 140 | 40 |
| 3 | Welcome copy (spec §2.1) · bodyLarge · onSurfaceVariant | 24 | 248 | 312 | 72 |
| 4 | Primary CTA · 56dp · primary · labelLarge 16sp | 24 | 620 | 312 | 56 |
| 5 | Secondary CTA · outlined 48dp · primary text | 24 | 684 | 312 | 48 |

### S1 · Add vehicle (step 1/3) — `s01b-add-vehicle`
Full-screen sheet: name, type segmented, powertrain chips, make/model/year, plate, VIN with scan.  
Ads: **0** (form) · 48dp targets measured: 2 · chips/segments (40dp + 48dp touch): 12 · tabular number elements: 1

| # | Element · spec | x | y | w | h |
|---|---|---|---|---|---|
| 1 | Full-screen sheet bar · close · titleLarge · step 1 of 3 | 0 | 32 | 360 | 64 |
| 2 | Filled text field · min 56dp · surfaceContainerHigh · label bodySmall · value bodyLarge | 16 | 96 | 328 | 56 |
| 3 | Segmented selector · 40dp + 48dp touch · secondaryContainer when selected | 16 | 194 | 328 | 40 |
| 4 | Filter chips · 40dp (48dp touch) · outline / secondaryContainer | 16 | 276 | 328 | 136 |
| 5 | Save button · 56dp full-width pill · primary/onPrimary · labelLarge 16sp · 40dp from bottom (0 ads) | 16 | 704 | 328 | 56 |

### S1 · Units (step 2/3) — `s01c-units`
Locale-prefilled units: distance, volume, fuel + electric economy, currency picker.  
Ads: **0** (form) · 48dp targets measured: 2 · chips/segments (40dp + 48dp touch): 12 · tabular number elements: 1

| # | Element · spec | x | y | w | h |
|---|---|---|---|---|---|
| 1 | Distance · segmented 40dp · labelLarge | 16 | 222 | 328 | 40 |
| 2 | Fuel volume · segmented 40dp · labelLarge | 16 | 306 | 328 | 40 |
| 3 | Fuel economy · segmented 40dp · labelLarge | 16 | 390 | 328 | 40 |
| 4 | Electric economy · segmented 40dp · labelLarge | 16 | 474 | 328 | 40 |
| 5 | Currency picker · dropdown field 56dp | 16 | 538 | 328 | 56 |
| 6 | Save button · 56dp full-width pill · primary/onPrimary · labelLarge 16sp · 40dp from bottom (0 ads) | 16 | 704 | 328 | 56 |

### S1 · Region presets (step 3/3) — `s01d-region-presets`
FR preset offer with checkboxes and the “Rules vary” disclaimer.  
Ads: **0** (onboarding) · 48dp targets measured: 7 · chips/segments (40dp + 48dp touch): 0 · tabular number elements: 1

| # | Element · spec | x | y | w | h |
|---|---|---|---|---|---|
| 1 | Preset list · card large 20dp · rows ≥56dp · checkbox 24dp in 48dp target | 16 | 224 | 328 | 271 |
| 2 | Disclaimer · bodySmall · dashed outline (spec §2.6) | 16 | 507 | 328 | 54 |
| 3 | Primary · 56dp · primary | 154 | 704 | 190 | 56 |

### S2 · Garage (home) — `s02-garage`
Only top-level home, no tab row: vehicle chips, odometer hero, economy delta, quick actions, due card, second vehicle (tap → Vehicle detail); banner + FAB above it.  
Ads: **1** (banner) · 48dp targets measured: 3 · chips/segments (40dp + 48dp touch): 5 · tabular number elements: 7

| # | Element · spec | x | y | w | h |
|---|---|---|---|---|---|
| 1 | Garage top app bar · 64dp · headlineSmall · Import + Settings 48dp · no tab row (Garage is the only home) | 0 | 32 | 360 | 64 |
| 2 | Vehicle chips · 40dp (48dp touch) · secondaryContainer when selected | 16 | 108 | 328 | 42 |
| 3 | Vehicle hero card · radius 24 · surfaceContainer · padding 16 | 16 | 162 | 328 | 176 |
| 4 | numberOdometer 40sp · tabular | 32 | 194 | 296 | 46 |
| 5 | numberLarge 20sp · economy | 32 | 244 | 86 | 26 |
| 6 | Quick actions · 40dp chips (48dp touch) · primaryContainer / outlined | 32 | 282 | 296 | 40 |
| 7 | Due card · warningContainer/onWarningContainer · min 64dp | 16 | 350 | 328 | 68 |
| 8 | Vehicle card · outlined · radius 20 · tap → Vehicle detail | 16 | 430 | 328 | 112 |
| 9 | FAB · 56dp · primaryContainer · bottom 91dp (16dp above banner) | 288 | 653 | 56 | 56 |
| 10 | Anchored adaptive banner wf_banner_garage · 360×50dp + 1dp outlineVariant · adSlotContainer · 24dp gesture inset below | 0 | 725 | 360 | 51 |

### S2 · Garage · ads hidden (Remove ads / Pro) — `s02-garage-ads-hidden`
Same Garage; 51dp slot collapsed, FAB drops to 16dp above the gesture inset. Identical layout is used when the banner fails to load / no fill.  
Ads: **0** (banner-hidden) · 48dp targets measured: 3 · chips/segments (40dp + 48dp touch): 5 · tabular number elements: 7

| # | Element · spec | x | y | w | h |
|---|---|---|---|---|---|
| 1 | Garage top app bar · 64dp · headlineSmall · Import + Settings 48dp · no tab row (Garage is the only home) | 0 | 32 | 360 | 64 |
| 2 | Vehicle chips · 40dp (48dp touch) · secondaryContainer when selected | 16 | 108 | 328 | 42 |
| 3 | Vehicle hero card · radius 24 · surfaceContainer · padding 16 | 16 | 162 | 328 | 176 |
| 4 | numberOdometer 40sp · tabular | 32 | 194 | 296 | 46 |
| 5 | numberLarge 20sp · economy | 32 | 244 | 86 | 26 |
| 6 | Quick actions · 40dp chips (48dp touch) · primaryContainer / outlined | 32 | 282 | 296 | 40 |
| 7 | Due card · warningContainer/onWarningContainer · min 64dp | 16 | 350 | 328 | 68 |
| 8 | Vehicle card · outlined · radius 20 · tap → Vehicle detail | 16 | 430 | 328 | 112 |
| 9 | FAB · 56dp · primaryContainer · bottom 40dp (16dp above gesture inset · no banner slot) | 288 | 704 | 56 | 56 |

### S4 · Vehicle detail · Overview — `s04-vehicle-overview`
Back · vehicle switcher · Reminders bell (badge 2) · tabs Overview·History·Documents·Reports; economy + How calculated?, costs, Next reminders + See all, recent entries, Fill-up FAB. 0 ads.  
Ads: **0** (never) · 48dp targets measured: 9 · chips/segments (40dp + 48dp touch): 0 · tabular number elements: 7

| # | Element · spec | x | y | w | h |
|---|---|---|---|---|---|
| 1 | Vehicle top app bar · 64dp · back 48dp · vehicle switcher · Reminders bell 48dp · overflow | 0 | 32 | 360 | 64 |
| 2 | Vehicle switcher · 48dp pill · surfaceContainer · titleSmall | 56 | 40 | 169 | 48 |
| 3 | Reminders bell · 48dp · contentDescription “Reminders” · badge = due count (error/onError, labelSmall) | 256 | 40 | 48 | 48 |
| 4 | Vehicle tabs · 48dp · titleSmall · Overview selected (0 ads) | 0 | 96 | 360 | 48 |
| 5 | Economy card · surfaceContainer · radius 20 | 16 | 156 | 328 | 118 |
| 6 | How calculated? link · 48dp row · opens sheet (0 ads) | 197 | 172 | 131 | 32 |
| 7 | Cost card · 3 stats · numberMedium tabular | 16 | 286 | 328 | 102 |
| 8 | See all · text button 48dp → Reminders list (same destination as the bell) | 284 | 396 | 60 | 48 |
| 9 | FAB · 56dp · primaryContainer · bottom 40dp (16dp above gesture inset · no banner slot) | 233 | 704 | 111 | 56 |

### S6 · Add fill-up — `s06-add-fillup`
Total hero (auto-calculated), date/time, odometer with delta, volume + price, full-tank switches, extras chips, Save.  
Ads: **0** (form) · 48dp targets measured: 5 · chips/segments (40dp + 48dp touch): 3 · tabular number elements: 2

| # | Element · spec | x | y | w | h |
|---|---|---|---|---|---|
| 1 | Form top bar · close 48dp · titleLarge · vehicle pill | 0 | 32 | 360 | 64 |
| 2 | Total hero · extraLarge 28dp · primaryContainer · numberHero 52sp tabular | 16 | 96 | 328 | 132 |
| 3 | Auto-calc chip · labelMedium · surface | 36 | 190 | 177 | 24 |
| 4 | Date field · 56dp min | 16 | 238 | 220 | 56 |
| 5 | Focused field · 2dp primary underline · bodySmall support | 16 | 304 | 328 | 56 |
| 6 | Switch rows · 56dp · titleMedium/bodySmall · switch 52×32 | 16 | 498 | 328 | 112 |
| 7 | Save button · 56dp full-width pill · primary/onPrimary · labelLarge 16sp · 40dp from bottom (0 ads) | 16 | 704 | 328 | 56 |

### S6 · Add charge — `s06b-add-charge`
EV variant: kWh, price/kWh, Home/AC/DC, state of charge 22→80%, target-as-full switch.  
Ads: **0** (form) · 48dp targets measured: 4 · chips/segments (40dp + 48dp touch): 5 · tabular number elements: 4

| # | Element · spec | x | y | w | h |
|---|---|---|---|---|---|
| 1 | Form top bar · close 48dp · titleLarge · vehicle pill | 0 | 32 | 360 | 64 |
| 2 | Total hero · tertiaryContainer (EV) · numberHero 52sp | 16 | 96 | 328 | 132 |
| 3 | Charge type · segmented 40dp | 16 | 452 | 328 | 40 |
| 4 | State of charge · start/end % · tertiary bar | 16 | 501 | 328 | 20 |
| 5 | Save button · 56dp full-width pill · primary/onPrimary · labelLarge 16sp · 40dp from bottom (0 ads) | 16 | 704 | 328 | 56 |

### S7 · Add service — `s07-add-service`
Multi-select service items, cost, workshop, receipt, “Create next reminder from this”.  
Ads: **0** (form) · 48dp targets measured: 4 · chips/segments (40dp + 48dp touch): 10 · tabular number elements: 1

| # | Element · spec | x | y | w | h |
|---|---|---|---|---|---|
| 1 | Form top bar · close 48dp · titleLarge · vehicle pill | 0 | 32 | 360 | 64 |
| 2 | Multi-select chips · 40dp · secondaryContainer + check when selected | 16 | 190 | 328 | 136 |
| 3 | Create-next-reminder switch row · surfaceContainer card | 16 | 514 | 328 | 64 |
| 4 | Save button · 56dp full-width pill · primary/onPrimary · labelLarge 16sp · 40dp from bottom (0 ads) | 16 | 704 | 328 | 56 |

### S9 · Reminders — `s09-reminders`
Opened from the Vehicle-detail bell or Settings › Reminders; back arrow; Due soon / Upcoming with date, odometer and whichever-first triggers; extended FAB.  
Ads: **0** (never) · 48dp targets measured: 8 · chips/segments (40dp + 48dp touch): 3 · tabular number elements: 1

| # | Element · spec | x | y | w | h |
|---|---|---|---|---|---|
| 1 | Top app bar · 64dp · titleLarge · onSurface | 0 | 32 | 360 | 64 |
| 2 | Reminder rows · ≥56dp · titleSmall + 2× bodySmall · status pill labelMedium | 16 | 204 | 328 | 161 |
| 3 | FAB · 56dp · primaryContainer · bottom 40dp (16dp above gesture inset · no banner slot) | 211 | 704 | 133 | 56 |

### S10 · Reminder form — `s10-reminder-form`
Date | Odometer | Whichever first, estimate copy, repeat, alert lead-time chips.  
Ads: **0** (form) · 48dp targets measured: 3 · chips/segments (40dp + 48dp touch): 8 · tabular number elements: 1

| # | Element · spec | x | y | w | h |
|---|---|---|---|---|---|
| 1 | Title field · 56dp | 16 | 96 | 328 | 56 |
| 2 | Trigger · segmented 40dp: Date | Odometer | Whichever first | 16 | 255 | 328 | 40 |
| 3 | Save button · 56dp full-width pill · primary/onPrimary · labelLarge 16sp · 40dp from bottom (0 ads) | 16 | 704 | 328 | 56 |

### S5 · Vehicle detail · History — `s05-vehicle-history`
Vehicle tab History: filter chips, month cards with totals, entry rows (economy delta, service tags, receipt); banner.  
Ads: **1** (banner) · 48dp targets measured: 5 · chips/segments (40dp + 48dp touch): 5 · tabular number elements: 7

| # | Element · spec | x | y | w | h |
|---|---|---|---|---|---|
| 1 | Vehicle top app bar · 64dp · back 48dp · vehicle switcher · Reminders bell 48dp · overflow | 0 | 32 | 360 | 64 |
| 2 | Vehicle switcher · 48dp pill · surfaceContainer · titleSmall | 56 | 40 | 169 | 48 |
| 3 | Reminders bell · 48dp · contentDescription “Reminders” · badge = due count (error/onError, labelSmall) | 256 | 40 | 48 | 48 |
| 4 | Vehicle tabs · 48dp · titleSmall · History selected (banner) | 0 | 96 | 360 | 48 |
| 5 | Filter chips · 40dp · single-select row | 0 | 156 | 360 | 48 |
| 6 | Month card · radius 24 · surfaceContainer · header titleLarge + total numberMedium | 16 | 216 | 328 | 339 |
| 7 | FAB · 56dp · primaryContainer · bottom 91dp (16dp above banner) | 288 | 653 | 56 | 56 |
| 8 | Anchored adaptive banner wf_banner_history · 360×50dp + 1dp outlineVariant · adSlotContainer · 24dp gesture inset below | 0 | 725 | 360 | 51 |

### S11 · Vehicle detail · Documents — `s11-vehicle-documents`
Vehicle tab Documents: folder-tab cards (Vehicle papers · Receipts), expiry pills, attachment counter (13 of 30 free), FR preset note; banner.  
Ads: **1** (banner) · 48dp targets measured: 5 · chips/segments (40dp + 48dp touch): 0 · tabular number elements: 1

| # | Element · spec | x | y | w | h |
|---|---|---|---|---|---|
| 1 | Vehicle top app bar · 64dp · back 48dp · vehicle switcher · Reminders bell 48dp · overflow | 0 | 32 | 360 | 64 |
| 2 | Vehicle switcher · 48dp pill · surfaceContainer · titleSmall | 56 | 40 | 169 | 48 |
| 3 | Reminders bell · 48dp · contentDescription “Reminders” · badge = due count (error/onError, labelSmall) | 256 | 40 | 48 | 48 |
| 4 | Vehicle tabs · 48dp · titleSmall · Documents selected (banner) | 0 | 96 | 360 | 48 |
| 5 | Folder-tab doc card · FolderTabShape (tab 12dp top radius, body 0/20/20/20) · primaryContainer | 16 | 188 | 328 | 192 |
| 6 | Folder-tab doc card · tertiaryContainer · receipts linked to entries | 16 | 418 | 328 | 120 |
| 7 | Attachment counter · bodySmall · free cap 30 (paywall at the 31st) | 16 | 550 | 328 | 16 |
| 8 | FAB · 56dp · primaryContainer · bottom 91dp (16dp above banner) | 288 | 653 | 56 | 56 |
| 9 | Anchored adaptive banner wf_banner_documents · 360×50dp + 1dp outlineVariant · adSlotContainer · 24dp gesture inset below | 0 | 725 | 360 | 51 |

### S12 · Document detail — `s12-document-detail`
Expiry card, PDF attachment preview + Open, detail rows, disclaimer. 0 ads (attachment viewer path).  
Ads: **0** (never) · 48dp targets measured: 7 · chips/segments (40dp + 48dp touch): 0 · tabular number elements: 1

| # | Element · spec | x | y | w | h |
|---|---|---|---|---|---|
| 1 | Top app bar · 64dp · titleLarge · onSurface | 0 | 32 | 360 | 64 |
| 2 | Document title · headlineSmall | 16 | 108 | 328 | 52 |
| 3 | Expiry banner card · warningContainer · titleMedium | 16 | 172 | 328 | 80 |
| 4 | Attachment preview card · tap opens viewer (0 ads) | 16 | 264 | 328 | 128 |
| 5 | Detail rows · ≥56dp · bodySmall label / bodyLarge value | 16 | 404 | 328 | 180 |

### S13 · Vehicle detail · Reports — `s13-vehicle-reports`
Vehicle tab Reports: 12-month free window, locked All time/Compare/Custom, economy line, monthly cost stacked bars, price per litre, TalkBack summaries; banner.  
Ads: **1** (banner) · 48dp targets measured: 4 · chips/segments (40dp + 48dp touch): 4 · tabular number elements: 4

| # | Element · spec | x | y | w | h |
|---|---|---|---|---|---|
| 1 | Vehicle top app bar · 64dp · back 48dp · vehicle switcher · Reminders bell 48dp · overflow | 0 | 32 | 360 | 64 |
| 2 | Vehicle switcher · 48dp pill · surfaceContainer · titleSmall | 56 | 40 | 169 | 48 |
| 3 | Reminders bell · 48dp · contentDescription “Reminders” · badge = due count (error/onError, labelSmall) | 256 | 40 | 48 | 48 |
| 4 | Vehicle tabs · 48dp · titleSmall · Reports selected (banner) | 0 | 96 | 360 | 48 |
| 5 | Range chips · 40dp · Pro chips show lock icon | 0 | 156 | 360 | 48 |
| 6 | Chart card · surfaceContainer · titleMedium + line 2.5dp primary · TalkBack summary text bodySmall | 16 | 216 | 328 | 184 |
| 7 | Chart summary (also the TalkBack description) | 32 | 352 | 296 | 32 |
| 8 | Stacked bar chart · primary fuel / tertiary service / secondary expenses | 16 | 412 | 328 | 212 |
| 9 | Anchored adaptive banner wf_banner_reports · 360×50dp + 1dp outlineVariant · adSlotContainer · 24dp gesture inset below | 0 | 725 | 360 | 51 |

### S18 · Settings (top) — `s18-settings`
Units & currency, theme, language, Reminders (list entry), alert lead times, import, export CSV, backup & restore.  
Ads: **0** (never) · 48dp targets measured: 9 · chips/segments (40dp + 48dp touch): 0 · tabular number elements: 1

| # | Element · spec | x | y | w | h |
|---|---|---|---|---|---|
| 1 | Top app bar · 64dp · titleLarge · 0 ads on Settings | 0 | 32 | 360 | 64 |
| 2 | Settings row · ≥56dp · bodyLarge + bodyMedium · 40dp leading icon | 16 | 136 | 328 | 60 |
| 3 | Reminders list entry (also reachable from the bell in Vehicle detail) | 16 | 358 | 328 | 60 |

### S18 · Settings (scrolled) — `s18b-settings-more`
Transfer note, Pro / Remove ads / Restore purchases, Privacy options, policy, About, Support admin@postpole.com.  
Ads: **0** (never) · 48dp targets measured: 8 · chips/segments (40dp + 48dp touch): 0 · tabular number elements: 1

| # | Element · spec | x | y | w | h |
|---|---|---|---|---|---|
| 1 | Top app bar · 64dp · titleLarge · onSurface | 0 | 32 | 360 | 64 |
| 2 | Transfer note (spec §2.8) · bodyMedium · surfaceContainerLow | 16 | 108 | 328 | 102 |
| 3 | Support row · email (spec §2.10) | 16 | 685 | 328 | 61 |

### S18 · Units & currency — `s18c-units-currency`
Defaults + currency picker and a preview row showing the same entry in US units (mi / MPG US / US$).  
Ads: **0** (never) · 48dp targets measured: 1 · chips/segments (40dp + 48dp touch): 9 · tabular number elements: 3

| # | Element · spec | x | y | w | h |
|---|---|---|---|---|---|
| 1 | Top app bar · 64dp · titleLarge · onSurface | 0 | 32 | 360 | 64 |
| 2 | Units preview card · same entry in two unit sets · numberMedium tabular | 16 | 484 | 328 | 156 |

### S17 · Paywall — `s17-paywall`
Remove ads and Pro (lifetime) side by side, both one-time, {price} tokens from Google Play, Restore purchases.  
Ads: **0** (never) · 48dp targets measured: 4 · chips/segments (40dp + 48dp touch): 0 · tabular number elements: 3

| # | Element · spec | x | y | w | h |
|---|---|---|---|---|---|
| 1 | Two option cards side by side (spec §5.4) · radius 24 · Pro highlighted 2dp primary | 16 | 212 | 328 | 324 |
| 2 | {price} token · filled from Google Play at runtime | 30 | 256 | 132 | 26 |
| 3 | Primary · 56dp · Get Pro | 16 | 648 | 328 | 56 |
| 4 | Tonal · 48dp · Remove ads | 16 | 712 | 328 | 48 |

### S2 · State · empty Garage (first run) — `st-empty-garage`
No vehicles yet: illustration, Add a vehicle + Import. No banner (0 slots) until ≥1 vehicle exists.  
Ads: **0** (garage-empty) · 48dp targets measured: 4 · chips/segments (40dp + 48dp touch): 0 · tabular number elements: 1

| # | Element · spec | x | y | w | h |
|---|---|---|---|---|---|
| 1 | Garage top app bar · 64dp · headlineSmall · Import + Settings 48dp · no tab row (Garage is the only home) | 0 | 32 | 360 | 64 |
| 2 | Empty state · 120dp illustration · headlineSmall + bodyMedium · CTA 48dp | 24 | 170 | 312 | 332 |

### S2 · State · loading — `st-loading`
Skeleton at final sizes; 50dp banner slot reserved while the ad loads (collapses if it fails / no fill).  
Ads: **1** (banner) · 48dp targets measured: 3 · chips/segments (40dp + 48dp touch): 0 · tabular number elements: 1

| # | Element · spec | x | y | w | h |
|---|---|---|---|---|---|
| 1 | Garage top app bar · 64dp · headlineSmall · Import + Settings 48dp · no tab row (Garage is the only home) | 0 | 32 | 360 | 64 |
| 2 | Skeleton · surfaceContainerHighest blocks at final sizes (no layout shift) | 16 | 160 | 328 | 178 |
| 3 | FAB · 56dp · primaryContainer · bottom 91dp (16dp above banner) | 288 | 653 | 56 | 56 |
| 4 | Ad slot wf_banner_garage reserved while loading · 360×50dp + 1dp · collapses to 0 if load fails / no fill | 0 | 725 | 360 | 51 |

### S14 · State · error (import rows) — `st-error-import`
Spec import result copy, 2 unreadable rows, over-cap vehicle imported archived, Copy error report.  
Ads: **0** (never) · 48dp targets measured: 5 · chips/segments (40dp + 48dp touch): 0 · tabular number elements: 1

| # | Element · spec | x | y | w | h |
|---|---|---|---|---|---|
| 1 | Top app bar · 64dp · titleLarge · onSurface | 0 | 32 | 360 | 64 |
| 2 | Result card · surfaceContainer · headlineSmall · counts numberMedium | 16 | 108 | 328 | 112 |
| 3 | Row errors · errorContainer icon · titleSmall + bodySmall | 16 | 260 | 328 | 123 |

### S6 · State · error (save failure) — `st-error-save`
Odometer soft warning (spec copy) + “Couldn’t save” snackbar with Retry.  
Ads: **0** (form) · 48dp targets measured: 6 · chips/segments (40dp + 48dp touch): 3 · tabular number elements: 2

| # | Element · spec | x | y | w | h |
|---|---|---|---|---|---|
| 1 | Form top bar · close 48dp · titleLarge · vehicle pill | 0 | 32 | 360 | 64 |
| 2 | Total hero · extraLarge 28dp · primaryContainer · numberHero 52sp tabular | 16 | 96 | 328 | 132 |
| 3 | Auto-calc chip · labelMedium · surface | 36 | 190 | 177 | 24 |
| 4 | Date field · 56dp min | 16 | 238 | 220 | 56 |
| 5 | Soft warning · 2dp warning underline · onWarningContainer support text | 16 | 304 | 328 | 56 |
| 6 | Switch rows · 56dp · titleMedium/bodySmall · switch 52×32 | 16 | 514 | 328 | 112 |
| 7 | Save button · 56dp full-width pill · primary/onPrimary · labelLarge 16sp · 40dp from bottom (0 ads) | 16 | 704 | 328 | 56 |
| 8 | Snackbar · inverseSurface · action inversePrimary 48dp | 12 | 632 | 336 | 60 |

### S1/S2 · State · free limit (3rd vehicle) — `st-free-limit`
Dialog over Add vehicle: 2 active vehicles → See Pro / Archive a vehicle / Cancel (mentions the 30-attachment free cap).  
Ads: **0** (form) · 48dp targets measured: 5 · chips/segments (40dp + 48dp touch): 12 · tabular number elements: 1

| # | Element · spec | x | y | w | h |
|---|---|---|---|---|---|
| 1 | Full-screen sheet bar · close · titleLarge · step 1 of 3 | 0 | 32 | 360 | 64 |
| 2 | Filled text field · min 56dp · surfaceContainerHigh · label bodySmall · value bodyLarge | 16 | 96 | 328 | 56 |
| 3 | Segmented selector · 40dp + 48dp touch · secondaryContainer when selected | 16 | 194 | 328 | 40 |
| 4 | Filter chips · 40dp (48dp touch) · outline / secondaryContainer | 16 | 276 | 328 | 136 |
| 5 | Save button · 56dp full-width pill · primary/onPrimary · labelLarge 16sp · 40dp from bottom (0 ads) | 16 | 704 | 328 | 56 |
| 6 | Dialog · extraLarge 28dp · surfaceContainerHigh · headlineSmall + bodyMedium · 3 actions (48dp) | 24 | 208 | 312 | 440 |

### S13 · State · Pro upsell (locked chart) — `st-pro-upsell`
All time chip → inline locked chart card with See Pro; 12-month cost chart still visible; banner stays.  
Ads: **1** (banner) · 48dp targets measured: 5 · chips/segments (40dp + 48dp touch): 4 · tabular number elements: 1

| # | Element · spec | x | y | w | h |
|---|---|---|---|---|---|
| 1 | Vehicle top app bar · 64dp · back 48dp · vehicle switcher · Reminders bell 48dp · overflow | 0 | 32 | 360 | 64 |
| 2 | Vehicle switcher · 48dp pill · surfaceContainer · titleSmall | 56 | 40 | 169 | 48 |
| 3 | Reminders bell · 48dp · contentDescription “Reminders” · badge = due count (error/onError, labelSmall) | 256 | 40 | 48 | 48 |
| 4 | Vehicle tabs · 48dp · titleSmall · Reports selected (banner) | 0 | 96 | 360 | 48 |
| 5 | Range chips · 40dp · Pro chips show lock icon | 0 | 156 | 360 | 48 |
| 6 | Pro upsell · inline locked card · blurred preview + scrim · Pro CTA 48dp (banner stays: list screen) | 16 | 216 | 328 | 256 |
| 7 | Anchored adaptive banner wf_banner_reports · 360×50dp + 1dp outlineVariant · adSlotContainer · 24dp gesture inset below | 0 | 725 | 360 | 51 |

### S10 · State · notification permission — `st-notification-prompt`
Rationale bottom sheet when saving the first reminder, then the Android system dialog.  
Ads: **0** (form) · 48dp targets measured: 5 · chips/segments (40dp + 48dp touch): 8 · tabular number elements: 1

| # | Element · spec | x | y | w | h |
|---|---|---|---|---|---|
| 1 | Title field · 56dp | 16 | 96 | 328 | 56 |
| 2 | Trigger · segmented 40dp: Date | Odometer | Whichever first | 16 | 255 | 328 | 40 |
| 3 | Save button · 56dp full-width pill · primary/onPrimary · labelLarge 16sp · 40dp from bottom (0 ads) | 16 | 704 | 328 | 56 |
| 4 | In-context permission rationale · modal bottom sheet · surfaceContainerLow · extraLarge top 28dp | 0 | 432 | 360 | 368 |

### S2 · Large text · Garage @200% — `lt-garage-200`
Android 14 nonlinear 200% font scale; cards grow, banner + FAB stay anchored, content scrolls.  
Ads: **1** (banner) · 48dp targets measured: 3 · chips/segments (40dp + 48dp touch): 5 · tabular number elements: 7 · font scale ×2.0 (Android 14 non-linear curve)

### S6 · Large text · Add fill-up @200% — `lt-add-fillup-200`
200% scale; fields grow (min-height), Save stays pinned, form scrolls under it.  
Ads: **0** (form) · 48dp targets measured: 5 · chips/segments (40dp + 48dp touch): 3 · tabular number elements: 2 · font scale ×2.0 (Android 14 non-linear curve)

## Tokens
### Colour (M3 roles)
| Role | Light | Dark |
|---|---|---|
| primary | `#99460C` | `#FFB690` |
| onPrimary | `#FFFFFF` | `#552100` |
| primaryContainer | `#FFDBCA` | `#753300` |
| onPrimaryContainer | `#331200` | `#FFDBCA` |
| secondary | `#765848` | `#E6BEAB` |
| onSecondary | `#FFFFFF` | `#432B1D` |
| secondaryContainer | `#FDE2D4` | `#5D4132` |
| onSecondaryContainer | `#2B160A` | `#FFDBCA` |
| tertiary | `#2A6370` | `#97D0DF` |
| onTertiary | `#FFFFFF` | `#003640` |
| tertiaryContainer | `#C4EBF6` | `#0B4B57` |
| onTertiaryContainer | `#001F26` | `#C4EBF6` |
| error | `#BA1A1A` | `#FFB4AB` |
| onError | `#FFFFFF` | `#690004` |
| errorContainer | `#FFDAD5` | `#930009` |
| onErrorContainer | `#410002` | `#FFDAD5` |
| surface | `#FFF8F5` | `#161210` |
| onSurface | `#221A15` | `#EFE0D9` |
| surfaceVariant | `#FCDCCD` | `#574237` |
| onSurfaceVariant | `#52443C` | `#D7C2B8` |
| surfaceDim | `#E8D7CF` | `#161210` |
| surfaceBright | `#FFF8F5` | `#413732` |
| surfaceContainerLowest | `#FFFFFF` | `#140C09` |
| surfaceContainerLow | `#FCF1EC` | `#1E1A17` |
| surfaceContainer | `#F6ECE6` | `#231D1A` |
| surfaceContainerHigh | `#EFE2DB` | `#2E2723` |
| surfaceContainerHighest | `#E9DCD5` | `#39312D` |
| outline | `#85736B` | `#A08D84` |
| outlineVariant | `#D4C3B9` | `#51443D` |
| inverseSurface | `#382E29` | `#F0DFD8` |
| inverseOnSurface | `#FFEDE6` | `#382E29` |
| inversePrimary | `#FFB68F` | `#99460C` |
| scrim | `#000000` | `#000000` |

### Semantic extras (`WheelfileTheme.colors`)
| Role | Light | Dark | Use |
|---|---|---|---|
| positive | `#385B35` | `#A9D1A1` | better-than-average economy text |
| onPositive | `#FFFFFF` | `#153716` | on positive |
| positiveContainer | `#C4EEBC` | `#214320` | economy ▼ pill |
| onPositiveContainer | `#153716` | `#C4EEBC` | text on pill |
| negative | `#BA1A1A` | `#FFB4AB` | worse economy text |
| negativeContainer | `#FFDAD5` | `#930009` | economy ▲ pill |
| onNegativeContainer | `#690004` | `#FFDAD5` | text on pill |
| warning | `#825500` | `#FFB953` | soft-warning underline (odometer) |
| onWarning | `#FFFFFF` | `#452B00` | on warning |
| warningContainer | `#FFDDB4` | `#543500` | due / expiry card + pill |
| onWarningContainer | `#543500` | `#FFDDB4` | text on due card, warning support text |
| adSlotContainer | `#FCF1EC` | `#1E1A17` | reserved banner slot |
| adSlotOutline | `#D4C3B9` | `#51443D` | banner hairline |
| adSlotLabel | `#52443C` | `#D7C2B8` | slot placeholder label |

### Contrast (WCAG 2.x; text AA ≥ 4.5:1, non-text ≥ 3:1)
| Pair | Light | Dark |
|---|---|---|
| onPrimary on primary (text AA) | 6.49 ✓ | 7.70 ✓ |
| onPrimaryContainer on primaryContainer (text AA) | 13.23 ✓ | 7.28 ✓ |
| onSecondary on secondary (text AA) | 6.44 ✓ | 7.69 ✓ |
| onSecondaryContainer on secondaryContainer (text AA) | 13.92 ✓ | 7.16 ✓ |
| onTertiary on tertiary (text AA) | 6.74 ✓ | 7.74 ✓ |
| onTertiaryContainer on tertiaryContainer (text AA) | 13.53 ✓ | 7.67 ✓ |
| onError on error (text AA) | 6.46 ✓ | 7.72 ✓ |
| onErrorContainer on errorContainer (text AA) | 13.25 ✓ | 7.24 ✓ |
| onSurface on surface (text AA) | 16.30 ✓ | 14.48 ✓ |
| onSurfaceVariant on surface (text AA) | 8.88 ✓ | 10.90 ✓ |
| onSurface on surfaceContainer (text AA) | 14.72 ✓ | 12.94 ✓ |
| onSurfaceVariant on surfaceContainerHighest (text AA) | 6.96 ✓ | 7.45 ✓ |
| primary on surface (text AA) | 6.18 ✓ | 10.94 ✓ |
| tertiary on surface (text AA) | 6.42 ✓ | 11.00 ✓ |
| error on surface (text AA) | 6.15 ✓ | 10.96 ✓ |
| inverseOnSurface on inverseSurface (text AA) | 11.63 ✓ | 10.21 ✓ |
| inversePrimary on inverseSurface (text AA) | 7.76 ✓ | 5.02 ✓ |
| onPositiveContainer on positiveContainer (text AA) | 10.25 ✓ | 8.63 ✓ |
| positive on surface (text AA) | 7.36 ✓ | 10.92 ✓ |
| onWarningContainer on warningContainer (text AA) | 8.62 ✓ | 8.62 ✓ |
| warning on surface (text AA) | 6.16 ✓ | 10.92 ✓ |
| onNegativeContainer on negativeContainer (text AA) | 10.14 ✓ | 7.24 ✓ |
| adSlotLabel on adSlotContainer (text AA) | 8.41 ✓ | 10.12 ✓ |
| outline on surface (non-text (3:1)) | 4.29 ✓ | 5.88 ✓ |

### Type (Roboto Flex variable; sp, line height sp, weight, width axis)
| Role | Size | Line | Weight | wdth | Tracking | tnum |
|---|---|---|---|---|---|---|
| displayLarge | 57 | 64 | 400 | 100 | -0.25 |  |
| displayMedium | 45 | 52 | 400 | 100 | 0 |  |
| displaySmall | 36 | 44 | 450 | 104 | 0 |  |
| headlineLarge | 32 | 40 | 700 | 112 | -0.3 |  |
| headlineMedium | 28 | 36 | 700 | 112 | -0.3 |  |
| headlineSmall | 24 | 32 | 700 | 112 | -0.2 |  |
| titleLarge | 22 | 28 | 650 | 108 | 0 |  |
| titleMedium | 16 | 24 | 600 | 100 | 0.15 |  |
| titleSmall | 14 | 20 | 600 | 100 | 0.1 |  |
| bodyLarge | 16 | 24 | 400 | 100 | 0.5 |  |
| bodyMedium | 14 | 20 | 400 | 100 | 0.25 |  |
| bodySmall | 12 | 16 | 400 | 100 | 0.4 |  |
| labelLarge | 14 | 20 | 600 | 100 | 0.1 |  |
| labelMedium | 12 | 16 | 600 | 100 | 0.5 |  |
| labelSmall | 11 | 16 | 600 | 100 | 0.5 |  |
| numberHero | 52 | 56 | 750 | 110 | -1.6 | yes |
| numberOdometer | 40 | 46 | 700 | 112 | -1.0 | yes |
| numberLarge | 20 | 26 | 700 | 108 | -0.2 | yes |
| numberMedium | 15 | 20 | 600 | 100 | 0 | yes |

The last four rows are Wheelfile extras (`WheelfileTheme.type`): `numberHero` is the oversized total on Fill-up / Charge, `numberOdometer` the Garage odometer. All numbers use `fontFeatureSettings = "tnum"`. Roboto Flex is **bundled** (no downloadable fonts): ship `tokens/fonts/roboto_flex.ttf` as `res/font/roboto_flex.ttf` — see *Font subset* below. Its digits are tabular by default; `tnum` stays for fallback fonts.

### Shape
| Token | dp | Used for |
|---|---|---|
| extraSmall | 4 | ad tag, badges |
| small | 10 | chips, pills |
| medium | 14 | menus, field top corners |
| large | 20 | cards, folder body |
| extraLarge | 28 | total hero, dialogs, sheet top |
| FieldShape | 14/14/4/4 | filled text fields |
| PillShape | 50% | buttons, Save, vehicle switcher |
| FolderTabShape | tab h26 r12 · body r20, 0 top-start | Documents cards (RTL mirrors) |

### Spacing, sizes, elevation
4dp grid: 2 · 4 · 8 · 12 · 16 · 24 · 32. Screen margin 16 · card padding 16 · row min 56 · touch min 48 · chip 40 (+4/+4 touch) · field min 56 · Save 56 · top bar 64 · tabs 48 · FAB 56 · gesture inset 24 · ad slot 50 + 1.  
Elevation is tonal first (surfaceContainer* roles at level 0); shadows only on FAB (level3, level4 pressed), snackbar, sheets and dialogs (level3). Scrolled app bar → surfaceContainer (level2).

## Ads — rules as decided by Ram (20 Nov 2026)
| Where | Ad slots | Banner unit |
|---|---|---|
| Garage, ≥ 1 vehicle (`s02-garage`, `st-loading`, `lt-garage-200`) | 1 | `wf_banner_garage` |
| Garage, first run / no vehicles (`st-empty-garage`) | **0** | — |
| Vehicle detail › History (`s05-vehicle-history`) | 1 | `wf_banner_history` |
| Vehicle detail › Documents (`s11-vehicle-documents`) | 1 | `wf_banner_documents` |
| Vehicle detail › Reports (`s13-vehicle-reports`, `st-pro-upsell`) | 1 | `wf_banner_reports` |
| Vehicle detail › Overview (`s04-vehicle-overview`) | **0** | — |
| Everything else (never-list) | **0** | — |

* **Never-list (audited = 0 slots):** onboarding (welcome, add vehicle, units, presets), every form (fill-up, charge, service, reminder, add vehicle), Reminders list + form, document detail / attachment viewer, Settings (+ Units & currency, Privacy options), paywall, import result, notification prompt, free-limit dialog, How-calculated sheet, camera / receipt capture, import / export / backup, PDF preview.
* **Slot lifecycle:** reserve 50dp + 1dp hairline **while the banner loads** (`st-loading`) so content does not jump; if the load fails or there is no fill, the slot **collapses to 0** (never an empty strip) and the layout becomes identical to `s02-garage-ads-hidden` — no separate frame was drawn for it. FAB: 16dp above the slot (bottom 91dp) while shown/loading, 16dp above the gesture inset (bottom 40dp) when collapsed.
* **Remove ads / Pro (`adsHidden = owns(remove_ads) || owns(lifetime_full_access)`):** the slot is never laid out — `s02-garage-ads-hidden`.
* No "Remove ads" link under banners (spec §5.4); purchase entry points are Settings › Purchases and the paywall.

## Navigation
* **Garage is the only top-level home** (no tab row). Tapping a vehicle opens **Vehicle detail** with tabs **Overview · History · Documents · Reports**; the vehicle switcher pill in its top bar changes vehicle without leaving the tab.
* **Reminders:** bell (`notifications`, 48dp, contentDescription "Reminders", badge = number due) in the Vehicle-detail top bar, plus Settings › Reminders. No tab. *My call:* the Overview tab keeps "Next reminders · **See all**" — it opens the same Reminders list (filtered to that vehicle), so it is a shortcut, not a competing destination. The Garage due card opens that reminder. The Reminders list top bar has a back arrow.
* Settings is reached from the Garage top bar; Import from the Garage top bar and Settings.

## Design decisions
1. **Locked Dir-4 hexes kept exactly**; the remaining M3 roles (inverse*, scrim, surfaceDim/Bright, container ladder, error group) were generated from HCT tonal palettes of the same seeds so they sit in the family. `dynamicColor` defaults to **false** so the app always matches the ochre icon and store art.
2. **Semantic colours** never stand alone: economy deltas carry an arrow + text, due cards carry the day count + date.
3. **Type**: Roboto Flex width axis 108–116 on headlines and numbers gives the "service book" voice; body stays wdth 100 for legibility. Every number is tabular. Optical size follows the sp size (`opsz` set per style in `Type.kt`).
4. **Forms** use a fixed 56dp Save bar 40dp from the bottom with a surface strip behind it; fields use min-height so they grow at large font scale (verified at 200%, `lt-*`).
5. **Large text**: at ≥ 200% the Vehicle-detail tab row becomes scrollable, vehicle chips scroll horizontally, the form vehicle pill collapses to its avatar, icons stay 24dp (dp, not sp).
6. **Charts** always ship a one-sentence summary that doubles as the TalkBack `contentDescription`; Pro ranges are visible but locked (lock icon), and the locked state is an inline card — never a full-screen interrupt.
7. **Free limits**: 2 active vehicles, 12-month charts, **30 attachments** (paywall at the 31st). The free-limit dialog sits over Add vehicle (a form, 0 ads) with See Pro / Archive a vehicle / Cancel; imports over the cap land archived (spec §2.7). Documents shows "13 of 30 free attachments used".
8. **Notification permission** is asked in context when the first reminder is saved: rationale sheet first, then the Android system dialog.
9. **Paywall** shows Remove ads and Pro (lifetime, highlighted) side by side, both one-time; every price is the literal `{price}` token filled from Google Play (`ProductDetails.oneTimePurchaseOfferDetails.formattedPrice`). No currency amounts are drawn.
10. **Documents inside a vehicle** are grouped into two folder-tab cards: *Vehicle papers* (primaryContainer) and *Receipts* (tertiaryContainer, linked to entries).
11. **Global framing**: sample data is a French garage (€, km); Settings › Units & currency shows the same fill-up in US units (mi · US gal · MPG US) and states that amounts are never converted.
12. **Feature graphic** is brand-led (icon + wordmark + tagline + 4 chips), no phone UI, all text inside the 16:9 centre crop (unchanged in revision 2).

## Decision log — Ram, 20 Nov 2026 (answers to the 7 open questions)
| # | Question | Decision | Applied in |
|---|---|---|---|
| 1 | Banner reservation vs. collapse | Reserve 50dp while loading; collapse on load failure / no fill (never an empty strip); removed for Remove ads / Pro owners | Ads section, `Dimens.kt` `Sizes.adSlotTotal` note, `tokens.json` ads.slot_rule, `st-loading` (reserved), `s02-garage-ads-hidden` (= collapsed / no-fill layout) |
| 2 | Vehicle-detail tabs | History, Documents, Reports get the banner; Overview has 0 ads | `s05-vehicle-history`, `s11-vehicle-documents`, `s13-vehicle-reports`, `st-pro-upsell` = 1; `s04-vehicle-overview` = 0 |
| 3 | Top-level IA | Garage is the only home; History / Documents / Reports exist only as Vehicle-detail tabs; top-level tab row removed everywhere; unit names unchanged | all Garage frames (no tabs); renamed frames `s05-vehicle-history`, `s11-vehicle-documents`, `s13-vehicle-reports` |
| 4 | Reminders entry | Bell (`notifications`, badge, 48dp, "Reminders") in the Vehicle-detail top bar + Settings entry; no tab; list has back arrow | every Vehicle-detail frame, `s18-settings` (Reminders row), `s09-reminders`; Overview keeps "See all" (same destination) |
| 5 | Empty Garage | First-run Garage with no vehicles has 0 banner slots; banner only once ≥ 1 vehicle exists | `st-empty-garage` = 0 |
| 6 | Paywall emphasis | Pro highlight OK. Free attachment cap is final = **30** (paywall at the 31st) — final spec item 8 | `s17-paywall`, `st-free-limit`, `s11-vehicle-documents`, README, audit (every "N attachments" must be 30) |
| 7 | Fonts | Bundle Roboto Flex in-app, Latin + Latin-ext subset, no downloadable fonts | `tokens/fonts/`, `Type.kt`, handoff notes |

No open questions remain from round 1. Minor note for Ram: no separate no-fill frame was drawn because the collapsed layout is pixel-identical to `s02-garage-ads-hidden`.

## Font subset (bundled Roboto Flex)
* **Size:** 1,787,292 bytes → **577,916 bytes** (−68%), 626 glyphs.
* **Axes kept:** opsz 8–144 (default 14), wght 100–1000 (default 400), wdth 25–151 (default 100). Pinned at default: GRAD 0, slnt 0, XOPQ, YOPQ, XTRA, YTUC, YTLC, YTAS, YTDE, YTFI.
* **Unicode:** Basic Latin, Latin-1, Latin Extended-A/B, Latin Extended Additional, IPA ə, general punctuation, super/subscripts, currency symbols (€ £ $ …), letterlike, arrows, ≈ ≠ ≤ ≥ − ∕. All OpenType features kept (liga, locl, pnum, rvrn).
* **Verified:** sample text incl. € £ é ü ç ñ ø ł ş ğ İ “ ” … × – and digit runs, rendered with full vs subset at wght 400–800 / wdth 100–116 / 14–52px: 16 of 1,530,816 pixels differ (0.001050%, anti-aliasing from pinned axes), 0 missing glyphs; all 10 digits share one advance (1156 units) → tabular. All 58 screen PNGs now render with this subset.
* **Exact commands** (`tokens/fonts/build_subset.sh`, fonttools 4.65):
```bash
fonttools varLib.instancer RobotoFlex.ttf GRAD=0 slnt=0 XOPQ=drop YOPQ=drop XTRA=drop YTUC=drop YTLC=drop YTAS=drop YTDE=drop YTFI=drop -o /tmp/rf_3axis.ttf
pyftsubset /tmp/rf_3axis.ttf --unicodes="U+0000-00FF,U+0100-024F,U+0259,U+02BB-02BC,U+02C6,U+02DA,U+02DC,U+1E00-1EFF,U+2000-206F,U+2070-209F,U+20A0-20CF,U+2100-214F,U+2190-21FF,U+2212,U+2215,U+2248,U+2260,U+2264-2265,U+FEFF,U+FFFD" --layout-features='*' --name-IDs='*' --name-languages='*' --notdef-outline --output-file=roboto_flex.ttf
```

## Handoff to the build (Claude)
* Copy `tokens/*.kt` into `app/src/main/java/com/postpole/wheelfile/ui/theme/`; copy `tokens/fonts/roboto_flex.ttf` to `app/src/main/res/font/roboto_flex.ttf` (bundled — do NOT add downloadable-font certs/providers). Wrap the app in `WheelfileTheme { }`. Keep the OFL notice in About › licences.
* Icon: follow `icon/README.md` (res copy + manifest `android:icon` / `android:roundIcon`).
* Ad slot: one `AdSlot(screen)` composable that reserves `Sizes.adSlotTotal` when `!adsHidden`, placed only on Garage (≥1 vehicle) and the Vehicle-detail History / Documents / Reports tabs; reserve while loading, collapse on `onAdFailedToLoad`; FAB bottom = gesture inset + (slot visible ? 51 + 16 : 16).
* Navigation: single `garage` start destination → `vehicle/{id}?tab=overview|history|documents|reports`; `reminders?vehicleId=` from the bell, See all and Settings.
* Treat every PNG here as the reference; numbers in the spec tables are dp.

