# Wheelfile (App #4, Vehicle Logbook): monetisation, pricing and ads-era plan

**Status: FINAL. Signed off by Ram on 20 Nov 2026.** Ajith still approves any spend, the name lock and any Play production release. **Nothing has been changed** in Play Console, AdMob, Firebase, GitHub or Gmail, and no product, ad unit or message has been created.

**App:** Wheelfile: Car Maintenance Log (`com.postpole.wheelfile`). The brand and package are still provisional until Ajith locks them (spec §0.4 D1).

**Author / date:** Monetisation Manager, 20 Nov 2026 (IST, UTC+5:30).

**Sources read in full on 20 Nov 2026:**
- The app's research files: `spec.md` v0.1, `name-options.md`, `trademark-check.md` and `design/README.md`.
- Studio monetisation files: `/workspace/monetisation/ads-plan-2026-11.md` (signed off), `admob-ids.md`, `ads-compliance-drafts/*` and `issues/INDEX.md`, `00-*`, `sf-01…sf-04`.
- `/workspace/app-research/recipe-vault/pricing-recommendation.md`, which is the Spoonfile pricing doc.

**Tags:**
- Anything marked **ESTIMATE**, **GUESS** or **JUDGEMENT** is mine and is not sourced.
- Everything else cites a URL or a local file, read on 20 Nov 2026 unless another date is given.
- No eCPM, fill-rate or conversion figure is assumed anywhere in this document, because none was retrieved.

**Copy note:** this file never writes out the connectivity word that is banned in studio copy. When it has to refer to it, it calls it "the o-word" (regex `off\x6cine`).

---

## 0. Decisions at a glance (approved by Ram, 20 Nov 2026)

**Approved by Ram on 20 Nov 2026:**
- **Tiers:** Free gets 2 active vehicles, unlimited logs and reminders, free import, free CSV and `.wheelfile` export/backup, the dark theme, and 30 attachments (§1.2–§1.5).
- **Remove ads hides ads only.** It raises no caps and unlocks no Pro features (§1.5).
- **Import-then-archive flow:** nothing is ever blocked or deleted, and the user picks which 2 vehicles stay active (§1.4).
- **No cross-SKU credit at launch** (§1.6).
- **26-market prices** matching Spoonfile: Pro US$9.99 and Remove ads US$3.99, no offers (§1.7).
- **Pump-session rule** (§2.3).
- **No interstitial after export or backup** (§2.3).
- **Rename** to `wf_interstitial_after_review` (§2.3, §3.5).
- **Ads placements and caps** (§2.1–§2.5).
- **Contains ads = Yes from build 1** (§3.3).


| # | Decision | Recommendation |
|---|---|---|
| 1 | Free vehicle cap | **Keep 2 active vehicles.** Archived and sold vehicles do not count. Do not go to 1 (§1.2). |
| 2 | Free logs, reminders, documents | **Unlimited, on every tier.** Fill-ups, charges, services, expenses, all reminders and document expiry alerts are all included. |
| 3 | Import, CSV export, `.wheelfile` backup and restore | **Free forever on every tier.** These are never capped and never sit behind ads or the paywall. Import and restore never drop or lock data (§1.4). |
| 4 | Pro (`lifetime_full_access`) | Pro unlocks: unlimited active vehicles, the PDF vehicle history / resale report, all-time and custom-range charts, vehicle comparison, unlimited attachments and custom categories. **It also hides all ads.** |
| 5 | Remove ads (`remove_ads`) | **Hides all ads, and nothing else.** The 2-vehicle cap, 12-month charts, attachment cap and every other Pro gate stay in place. |
| 6 | Free attachment cap | **30 attachments in total**, up from the spec's GUESS of ~25. Imports and restores are never blocked by the cap (§1.3). |
| 7 | US prices | **Pro US$9.99 and Remove ads US$3.99**, both one-time. No subscription. |
| 8 | Regional prices | **The same 26-market table as Spoonfile**, copied cell for cell (§1.7), so Release & QA have one workflow. Remove ads is 38.6–41.6% of Pro in every market. Every other country auto-converts from USD. |
| 9 | Launch offers | **None** on either SKU. There is no cross-SKU credit for Remove ads buyers who later buy Pro (§1.6). |
| 10 | Banners | One anchored adaptive banner on each of **Garage, History, Documents list and Reports**. There are no ads on any other screen (§2.1). |
| 11 | Interstitial | **One unit, `wf_interstitial_after_review`.** It can fire only when the user leaves History or Reports back to Garage, after the caps. **It is suppressed for the whole session if that session saved a fill-up or a charge** (the "pump-session rule", §2.3). |
| 12 | Other formats | App-open, native and rewarded ads are **all off**. `wf_rewarded` is created as a placeholder only. Max content rating is **PG**. |
| 13 | Remote Config | **The studio schema exactly** (13 keys). The spec has two key-name drifts that must be fixed (§2.5). |
| 14 | SKUs | `lifetime_full_access` with option `lifetime-buy`, and `remove_ads` with option `remove-ads`. Both are one-time Buy options, backwards compatible, with no offers (§3.4). |
| 15 | AdMob | One new app under `pub-2007361247232604` with 6 units, all prefixed `wf_` (§3.5). **No IDs exist yet. Use Google test IDs until Ram creates the real ones.** |

---

## 1. Free vs Remove ads vs Pro: boundaries, caps and prices

### 1.1 What the market charges (verified 20 Nov 2026)

| App | Model and price | Free tier (official unless noted) | Source |
|---|---|---|---|
| **Drivvo** | Individual **US$24.90/yr**. PRO **US$49.90/yr** or **US$4.90/mo**. Fleet is separate | Free: **up to 2 active vehicles**, unlimited fuel logs, reminders, reports, ads. **Data export (CSV/Excel), Dark Theme and "No advertising" are paid** | https://www.drivvo.com/en/pricing (fetched 20 Nov 2026). Also spec §1.1 |
| **Simply Auto** | **Gold US$9.99 one-time** (no ads, up to 7 vehicles). Platinum US$9.99/yr (unlimited vehicles). The CA App Store lists GOLD at $12.99 (CAD storefront) and a Platinum one-time option at $39.99 | Free: up to 4 vehicles, **10 service and expense tasks**, ads. Abandoned on Play since 10 Oct 2024 (spec) | https://www.simplyauto.app/ (pricing table). https://apps.apple.com/ca/app/simply-auto-mileage-tracker/id893278325 |
| **aCar (Fuelly)** | Premium **US$0.99/mo or US$4.99/yr** to remove ads and add photos and PDFs. Old lifetime Pro buyers were moved to the subscription | CSV export and backup/restore are free for everyone. Photos and PDFs need Premium. Play rating 1.95★ | https://www.fuelly.com/acar/ . Spec §1.1 |
| **Fuelio** | No ads. Play IAP range US$4.99–15.99. iOS Pro US$17.99/yr or US$4.99/mo. Some widgets and weather are PRO | Free core log | Spec §1.1. Play listing fetched 20 Nov 2026, https://play.google.com/store/apps/details?id=com.kajda.fuelio&hl=en&gl=US |
| **Road Trip MPG** (iOS benchmark) | **US$6.99 one-time** (UK £6.99 in an older Auto Express review). No ads, unlimited vehicles, CSV import and export | Paid app. A separate Lite app exists | https://apps.apple.com/us/app/road-trip-mpg/id298398207 (4.9★, 636 ratings). https://www.autoexpress.co.uk/accessories-tyres/92537/fuellog-car-management-app-review |
| **CARFAX Car Care** | Free, US-centric | Not a pricing anchor | Spec §1.1 |
| **AUTOsist** | Fleet SaaS, **US$3–6 per vehicle per month** with a **US$59/month minimum** | Not a consumer comparable. It only shows that B2B is priced far higher | https://autosist.com/pricing/ |
| **Bitácar** (indie) | Pro **US$12.99 one-time** (PDF, unlimited cars) | 2 cars, no ads, free CSV export | Spec §1.1 |
| **CarStamp** (indie) | Subscription or lifetime, IAP range US$3.99–79.99 | 1 car, free import and export | Spec §1.1 |
| **Karvyn / ServiceVault** (indie) | US$14.99 / US$9.99 | — | Spec §1.1 |

**Studio ladder.** These are all one-time Pro / Remove ads prices in USD, taken from `ads-plan-2026-11.md` §2.3:
- Ghar Khata: 2.49 / 0.99
- PocketLedger: 4.99 / 1.99
- Spoonfile: 9.99 / 3.99

**Gaps:**
- I found no official India-specific (INR) price page for Drivvo, Simply Auto or Fuelio. The only India signal is the spec's Fuelio "₹1000" yearly-subscription complaint (2★, 16 Nov 2026), so India is priced with the studio's 0.64× method, not with competitor data.
- I did not verify UK, EU or DE storefront prices for these competitors in this pass. Play badges are ranges, not point prices.

### 1.2 Free vehicle cap: 2, not 1

**Recommendation: keep 2 active vehicles.** Archived and sold vehicles do not count. This is a **JUDGEMENT** based on the following:

1. **Parity with the leader.** Drivvo's free tier is "Up to 2 active vehicles" (drivvo.com/en/pricing). If our free cap were *lower* than Drivvo's, our main wedge, "the fair alternative", would fall apart on the first comparison. Bitácar also gives 2, and Simply Auto gives 4.
2. **Real households have 2.** That might be two cars, or one car plus a motorbike (VehicleType includes MOTORBIKE, and powertrains include EV and PHEV). A cap of 1 would push the most engaged early users, who are switching from Drivvo with two vehicles, to a paywall *during import*. That is exactly the moment we promised not to trap their data.
3. **The Drivvo anger is about vehicle-count pricing.** "Asking a person with one vehicle to pay the same as a customer with 10 vehicles" (1★, 18 Nov 2026, spec §0.1). If Pro depended only on the vehicle count, 1–2 car owners would see no reason to buy. So Pro has to earn its price from depth (PDF report, all-time history, unlimited receipts), not from the vehicle count alone (§1.3).
4. **Elasticity trade-off.** A cap of 1 would trigger the paywall more often. But in a market full of free clones (spec §8), a stingy free tier costs more in installs, ratings and ad impressions than it gains in Pro sales. **JUDGEMENT:** in the ads era, a retained free user is worth something (banner impressions), so conversion pressure should come from honest Pro value, not from crippling the free tier.
5. **Revisit trigger.** Look again only if, after ≥ 8 weeks and a usable sample, fewer than 5% of active users ever reach the cap *and* Pro conversion is weak. Even then, test 2 → 1 for *new installs only*, and never take a vehicle away from an existing user. (The 5% is a **GUESS** threshold.)

### 1.3 Pro feature audit (every Pro item in spec §2.8, §4.1, §5.1 and §5.4)

| Spec item | Tier in spec | My verdict | Why |
|---|---|---|---|
| Unlimited active vehicles | Pro | **Keep Pro** | This is the standard gate (Drivvo, Simply Auto, Bitácar) |
| PDF vehicle history / resale report (`PdfDocument`, service history, costs, economy chart, attachments index) | Pro | **Keep Pro. This is the flagship 1-car perk** | A service record helps resale (**JUDGEMENT**, not sourced). Bitácar sells PDF as Pro. It is valuable to a 1-car owner, so it answers the "pay the same as 10 cars" complaint |
| Charts: all-time / multi-year, vehicle comparison, custom date ranges | Pro (free gets 12 months) | **Keep Pro**, but **every core number stays free**: last and average economy, cost per km/mi, cost per month, 12-month total and the three 12-month charts | Drivvo 1★: "Removed many useful reports (fuel efficiency)". Never put economy or cost-per-distance behind the paywall |
| Attachments (receipt photos and PDFs) | Free ~25 (GUESS), Pro unlimited | **Free 30 in total**, counting receipts and documents together. **Pro unlimited** | 2 vehicles × ~5 vault documents (insurance, registration, inspection, licence, warranty) is about 10, which leaves ~20 receipts (**ESTIMATE**). aCar charges for *any* photo or PDF, and Drivvo gives 100 MB only on paid plans. So 30 free is generous, and it still gives heavy receipt-keepers a real reason to upgrade. The cap is a code constant (`Limits.FREE_ATTACHMENT_LIMIT = 30`), **not** Remote Config, so the goalposts never move silently |
| Custom expense and service categories (`CustomCategory`) | Pro | **Keep Pro**, as a minor perk | The built-in categories cover most needs. It is low-risk to gate |
| Theme System / Light / Dark | Free (spec §2.10) | **Keep free** | Drivvo sells Dark Theme in its paid plans. Giving it free is cheap goodwill |
| Reminders (date, odometer, first-of) and document expiry alerts | Free, all | **Keep free, uncapped** | These are safety and legal reminders (MOT, HU/TÜV, CT, ITV, insurance). Paywalling them would read as hostile |
| Import (all formats), CSV export, backup and restore | Free | **Keep free, never capped** | This is the wedge (spec §1.2 #1). Drivvo's "Locks your own data" 1★ (16 Nov 2026) |
| Widgets, receipt OCR, generic CSV mapping | Not in MVP (v1.1) | **Not on the paywall at launch** | Paywall copy may not promise unbuilt features. The spec's CopyRulesTest bans "coming soon" and "future pro" |

**Pro value for a 1–2 car owner**, with no vehicle-count argument needed:
- the PDF history / resale report;
- full history beyond 12 months, with custom ranges and car-vs-bike comparison;
- unlimited receipts and documents;
- custom categories;
- no ads.

### 1.4 Import and restore edge case (never block, never trap)

The spec rule (§2.7, §2.8) is right. Here it is made exact:

1. **Import and restore always bring in everything.** Every vehicle, log, reminder, document and attachment in the file is imported, whatever the tier or cap.
2. **The user chooses which vehicles stay active.** At the import mapping step on the Free or Remove ads tier, if the file would leave more than 2 active vehicles, the preview shows: "Your file has {n} vehicles. Free keeps 2 active; the rest are kept as archived, with all their history. Choose which 2 stay active." The default is the 2 with the most recent entries.
3. **What archived vehicles can do:**
   - **Can:** be viewed in full (History, Reports, Documents and attachments), appear in CSV export, PDF (if Pro) and backup, and be deleted.
   - **Cannot:** take *new* entries or new reminders.
   - **Reminders:** existing reminders on archived vehicles are **paused**, and the import result says so.
4. **Swapping.** A free user can unarchive a vehicle any time by archiving another one, so 2 stay active. There is no cooldown at launch. Log a `vehicle_swap` event, and add a cooldown only if the data shows rotation abuse (**JUDGEMENT**).
5. **Attachments over 30 on import or restore** are all kept and viewable. Only *adding a new* attachment is gated.
6. **Pro refund or revoke.** Nothing is deleted. The user picks 2 vehicles to keep active and the rest become archived. Attachments are kept, but adding new ones is gated.
7. **Copy for the import result:** "Imported 312 fill-ups, 41 services from 3 vehicles. 2 are active and 1 is archived with all its history. Upgrade to Pro to keep all 3 active." Never write "locked", "lost" or "deleted".

### 1.5 Three-tier feature matrix

| Feature | Free (with ads) | Remove ads (`remove_ads`) | Wheelfile Pro (`lifetime_full_access`) |
|---|---|---|---|
| Active vehicles | 2 (archived/sold unlimited, read-only) | **2 (same as Free)** | Unlimited |
| Fill-ups, EV charges, services, expenses | Unlimited | Unlimited | Unlimited |
| Reminders and document expiry alerts | All | All | All |
| Import (all formats), CSV export, `.wheelfile` backup and restore | Free, uncapped | Free, uncapped | Free, uncapped |
| Economy, cost per km/mi, 12-month charts | ✓ | ✓ | ✓ |
| All-time and custom-range charts, vehicle comparison | — | **—** | ✓ |
| PDF vehicle history / resale report | — | **—** | ✓ |
| Attachments (receipts and documents) | 30 total | **30 total** | Unlimited |
| Custom categories | — | **—** | ✓ |
| Theme (System/Light/Dark) | ✓ | ✓ | ✓ |
| Ads | Banners on 4 list screens, plus a capped interstitial | **None** | **None** |

**Entitlement:**
- `adsHidden = owns(remove_ads) || owns(lifetime_full_access)`
- `isPro = owns(lifetime_full_access)`

Every Pro gate reads `isPro` **only**. Remove ads never raises a cap.

### 1.6 Remove ads → Pro upgrade path

Play one-time products have no built-in cross-SKU credit. The v1 posture:
- **No credit at launch.** Building a discounted "upgrade" product would add a third SKU and an offer, which breaks the "no offers" baseline.
- **Make the gap visible before purchase.** The paywall shows both cards side by side. The Remove ads card says plainly "Ads off. Free limits stay (2 vehicles, 12-month charts, 30 attachments)." The Pro card is marked as the better value, with the Play price shown.
- **After a Remove ads purchase,** Settings and the Pro card sell **features only** ("Unlimited vehicles, PDF history…") and never resell ad removal.
- **Revisit trigger.** If more than 10% of Pro buyers already owned `remove_ads` (measured by `purchase` with `owned_remove_ads=true`), consider a discounted Pro purchase option shown only to `remove_ads` owners. That would need Ram's sign-off and a new Console option (**JUDGEMENT**).

### 1.7 US base and regional prices

**Recommendation: Pro US$9.99 and Remove ads US$3.99, both one-time. No subscription.** This is a **JUDGEMENT** based on the following:

- **Pro sits where the closest comparables cluster:**
  - Simply Auto Gold is US$9.99 one-time (official site).
  - It is above Road Trip at US$6.99 (iOS, paid upfront, no free tier).
  - It is below Bitácar at US$12.99 and Karvyn at US$14.99.
  - It is **cheaper than a single year of Drivvo Individual (US$24.90/yr)** or Fuelio iOS Pro (US$17.99/yr), with no renewal.
  - The ownership cost of a car makes US$9.99 an easy one-off (**JUDGEMENT**). The X anecdote in spec §8 ("1 lifetime sale: $9.99") shows that distribution, not price, is the bottleneck.
- **Why not US$12.99–14.99?** Wheelfile has no data yet. US$9.99 stays under the US$10 impulse line, matches Spoonfile, and lets us test *upward* later on new installs if conversion is strong.
- **Why not US$7.99?** It leaves money on the table against the US$9.99–14.99 band. A down-test can come later.
- **Remove ads at US$3.99:**
  - It is cheaper than one year of aCar Premium (US$4.99/yr) and a fraction of Drivvo's US$24.90/yr "No advertising" plan.
  - It is 40% of Pro, which keeps Pro clearly the better deal: about 2.5× the price for all features plus no ads.
- **Operational benefit.** It has the **identical** price ladder to Spoonfile, so Release & QA can copy a known-good table. The spec's §5.1 draft also proposed reusing the Spoonfile tables.

**Regional table.** All cells are copied exactly from the Spoonfile Pro table (`recipe-vault/pricing-recommendation.md` §5.2) and the Spoonfile Remove-ads row (`ads-plan-2026-11.md` §2.3).
- These are buyer-facing amounts, tax-inclusive where Play includes tax.
- The USD equivalents use the FX snapshot at https://open.er-api.com/v6/latest/USD, `time_last_update_utc` Fri, 20 Nov 2026 00:02:32 UTC (05:32 IST, 20 Nov 2026).

| # | Market | Currency | **Pro** `lifetime_full_access` | **Remove ads** `remove_ads` | RA ÷ Pro | Pro in USD (20 Nov FX) | vs US |
|---|---|---|---|---|---|---|---|
| 1 | United States | USD | **9.99** | **3.99** | 39.9% | 9.99 | 1.00× |
| 2 | United Kingdom | GBP | **8.99** | **3.49** | 38.8% | 11.79 | 1.18× |
| 3 | Eurozone core: **DE / FR / ES** / IT / NL | EUR | **9.99** | **3.99** | 39.9% | 10.97 | 1.10× |
| 4 | Finland (25.5% VAT) | EUR | **10.99** | **4.49** | 40.9% | 12.07 | 1.21× |
| 5 | India | INR | **599** | **249** | 41.6% | 6.42 | 0.64× |
| 6 | Canada | CAD | **13.99** | **5.49** | 39.2% | 9.77 | 0.98× |
| 7 | Australia | AUD | **14.99** | **5.99** | 40.0% | 10.00 | 1.00× |
| 8 | New Zealand | NZD | **18.99** | **7.49** | 39.4% | 10.43 | 1.04× |
| 9 | Japan | JPY | **1,800** | **700** | 38.9% | 11.27 | 1.13× |
| 10 | South Korea | KRW | **16,000** | **6,400** | 40.0% | 12.02 | 1.20× |
| 11 | Switzerland | CHF | **9.90** | **3.90** | 39.4% | 11.74 | 1.17× |
| 12 | Sweden | SEK | **119** | **49** | 41.2% | 11.69 | 1.17× |
| 13 | Norway | NOK | **119** | **49** | 41.2% | 12.22 | 1.22× |
| 14 | Denmark | DKK | **80** | **32** | 40.0% | 11.76 | 1.18× |
| 15 | Indonesia | IDR | **119,000** | **49,000** | 41.2% | 6.52 | 0.65× |
| 16 | Philippines | PHP | **499** | **199** | 39.9% | 7.83 | 0.78× |
| 17 | Vietnam | VND | **199,000** | **79,000** | 39.7% | 7.73 | 0.77× |
| 18 | Brazil | BRL | **49.99** | **19.99** | 40.0% | 8.48 | 0.85× |
| 19 | Mexico | MXN | **199** | **79** | 39.7% | 10.46 | 1.05× |
| 20 | Türkiye | TRY | **399.99** | **159.99** | 40.0% | 7.92 | 0.79× |
| 21 | Egypt | EGP | **399.99** | **159.99** | 40.0% | 7.43 | 0.74× |
| 22 | Pakistan | PKR | **1,999** | **799** | 40.0% | 7.23 | 0.72× |
| 23 | Nigeria | NGN | **9,999** | **3,999** | 40.0% | 7.39 | 0.74× |
| 24 | South Africa | ZAR | **189.99** | **74.99** | 39.5% | 10.91 | 1.09× |
| 25 | United Arab Emirates | AED | **37.99** | **14.99** | 39.5% | 10.34 | 1.04× |
| 26 | Saudi Arabia | SAR | **43.99** | **16.99** | 38.6% | 11.73 | 1.17× |

**Rules for Release & QA:**
- **Every Play country not listed above stays on Play auto-conversion** from the USD 9.99 / 3.99 base. That includes other euro countries; high-VAT peers may land at EUR 10.99–11.49.
- After creating each SKU, do a **read-only Console pass** for the known traps, which come from the Spoonfile and PocketLedger method:
  - emerging-market cells landing at US parity;
  - tax-inclusive USD cells that look like stray decimals;
  - leftover-precision currencies.
- Re-check tax flags on these new SKUs, especially India, Brazil, Canada, Switzerland and the Gulf.
- **Every cell keeps Remove ads strictly below Pro**, at 38.6–41.6% (checked above).

**Method.** The method is unchanged from Spoonfile §5.1:
- PocketLedger anchors are scaled ×2.002.
- India uses the 0.64× benchmark, which is still 0.64× at today's FX.
- Emerging markets use milder cuts.
- Switzerland is set to the CHF 9.90 peer price.

Between the 14 Nov and 20 Nov FX prints, no cell moved by more than about 5% in USD terms (e.g. BRL 0.89× → 0.85×). **JUDGEMENT:** that is not enough to justify diverging from Spoonfile.

**Launch offers: none on either SKU.**
- No intro price, countdown or fake discount.
- This keeps the Billing 8/9 setup to one Buy option per product and gives a clean baseline, the same as Spoonfile.

---

## 2. Ads-era placement and Remote Config plan (from Build 1)

### 2.1 Screen-by-screen map

The map covers spec §3.1 (S1–S19) and the design/README directions. "Banner" always means **one anchored adaptive banner**, placed as follows:
- **Directions 1 and 3:** stacked directly above the NavigationBar.
- **Directions 2 and 4:** at the very bottom above the 24 dp gesture inset, with the FAB 16 dp above the banner. This matches the design audit: one 360×51 slot per list screen and 0 on forms.

| # | Screen | Ads | Notes |
|---|---|---|---|
| S1 | Welcome / onboarding (incl. units step, region presets offer) | **ZERO** | No ads in onboarding |
| S2 | **Garage** (vehicle list) | **Banner** `wf_banner_garage` | **Hide the banner while Garage is in its empty state** (0 vehicles), so first-run stays clean (**JUDGEMENT**). It shows from the first vehicle onward |
| S3 | Add / edit vehicle (sheet) | **ZERO** | Form |
| S4 | Vehicle detail: **Overview** | **ZERO** | Core loop. The economy card updates here after Save |
| S4 tabs | Vehicle detail: History / Documents / Reports tabs | Same as S5 / S11 / S13 | **Catch:** the spec lists S4 with tabs "Overview · History · Documents · Reports". The banner follows the *content type*, not the route. If History is shown as a tab inside Vehicle detail, it gets `wf_banner_history`. Overview never gets a banner. Claude must not add a second banner slot |
| S5 | **History** list | **Banner** `wf_banner_history` | Leaving History is an interstitial point (§2.3) |
| S6 | Fill-up / EV Charge form | **ZERO (hard ban)** | Never on the form, and never "on Save" |
| S7 / S8 | Service form / Expense form | **ZERO (hard ban)** | |
| — | Receipt camera (`TakePicture`) / Photo Picker / attachment crop | **ZERO (hard ban)** | |
| S9 | Reminders list | **ZERO** | Kept clean (spec GUESS: low traffic). Safety-adjacent |
| S10 | Reminder editor | **ZERO (hard ban)** | |
| S11 | **Documents** list | **Banner** `wf_banner_documents` | List only |
| S12 | Document detail / attachment viewer | **ZERO** | Users show these to officials or insurers (**JUDGEMENT**) |
| S13 | **Reports / charts** | **Banner** `wf_banner_reports` | Leaving Reports is an interstitial point (§2.3) |
| S14 | Import (pick, preview, mapping, result) | **ZERO (hard ban)** | |
| S15 | Export / Backup / Restore | **ZERO (hard ban)** | No interstitial after export or backup either (§2.3) |
| S16 | PDF preview (Pro) | **ZERO** | Pro users see no ads anyway. Rule kept in case of entitlement lag |
| S17 | Paywall / upgrade sheets (Pro + Remove ads) | **ZERO (hard ban)** | |
| S18 | Settings, Privacy options, About | **ZERO (hard ban)** | |
| S19 | "How is economy calculated?" sheet | **ZERO** | Sheet |
| — | Currency and unit pickers, vehicle switcher, any dialog or bottom sheet | **ZERO** | |
| — | App open / app exit / return from background | **ZERO (hard rule)** | App-open ads are off, and there is no code path that can show one |

**Design note.** The design README top pick, Direction 2 Highway Cobalt (no bottom NavigationBar), is also the best choice for ads:
- It has 75 dp of bottom chrome, against 155 dp in Directions 1 and 3.
- The banner does not stack on the nav bar.

The runner-up, Direction 4, has the same banner geometry. Monetisation has no objection to either.

### 2.2 Formats

| Format | Launch state | RC key |
|---|---|---|
| Anchored adaptive banner | **On**, on 4 list screens | `ads_banner_enabled=true` |
| Interstitial | **On**, 1 unit, with strict caps | `ads_interstitial_enabled=true` |
| App-open | **Off** | `ads_app_open_enabled=false` |
| Native | **Off** (never inside vehicle or log lists, where it could look like a row) | `ads_native_enabled=false` |
| Rewarded | **Off**. `wf_rewarded` is created for future tests only | `ads_rewarded_enabled=false` |
| Max ad content rating | **PG** | `ads_max_content_rating="PG"` |

**Rewarded guardrail for any future test** (needs Ram's sign-off): the only acceptable offer is a one-time-ever 24-hour Pro preview, as in Spoonfile's ads plan §1.5. **Never** offer "watch an ad for a 3rd vehicle slot", "watch to hide ads for a day", or "+N attachments per ad". Each of those undercuts Pro or `remove_ads`.

### 2.3 Interstitial: trigger point, the pump-session rule and caps

**Unit:** `wf_interstitial_after_review`.
- This **renames the spec's `wf_interstitial_after_history`**, because the unit also fires from Reports.
- It matches PocketLedger's `pl_interstitial_after_review`, which is the same "finished reviewing" moment.

**The only trigger:**
- The user leaves **History (S5)** or **Reports (S13)** and arrives at **Garage (S2)**, by Back or by a Garage nav tap.
- The navigation must have finished, the Activity must be resumed, and a short settle delay must have passed, so that the next tap cannot land on the ad (the sf-01 pattern).

**Pump-session rule** (Wheelfile-specific; a hard-coded rule in `AdsPolicy`, **not** a new RC key, so the schema stays identical to the studio's):
- **If the current session has saved a fill-up or EV charge, no interstitial is shown for the rest of that session.**
- **Rationale:**
  - At a pump or charger, people log fast and pocket the phone.
  - A common pattern is: Save, then open History to check the entry, then back to Garage. Without this rule, that sequence would fire an ad at the pump.
  - Drivvo's "bloated ads everywhere" 1★ (18 Nov 2026) is the failure mode we must avoid.
  - Sessions spent browsing (checking Reports, reviewing History) still monetise.
- **Same rule for service and expense saves?** No. Those are non-urgent and done at home (**JUDGEMENT**). The interstitial still never fires *on* the save. It can only fire later, on a History or Reports → Garage exit, after caps.

**Never fire an interstitial:**
- on app open, exit or resume;
- mid-form, on Save, or right after any form;
- during or after import, export, backup, restore or PDF share (we do not tax "getting your data out");
- on or after the paywall;
- when stacked (two in a row);
- in a first session or in the first 3 install days.

**Caps** (studio standard, `ads-plan-2026-11.md` §1.1):

| Cap | Value | RC key |
|---|---|---|
| Not in the first session | true | `ads_interstitial_block_first_session` |
| Not in the first N install days | 3 | `ads_interstitial_min_install_days` |
| Min interval | 480 s (8 min) | `ads_interstitial_min_interval_sec` |
| Max per session | 1 | `ads_interstitial_max_per_session` |
| Max per day | 3 | `ads_interstitial_max_per_day` |

### 2.4 UMP consent

Follow the sf-01 pattern exactly:
- **Order of operations.** Call `requestConsentInfoUpdate` on every launch, and load and show the form if required. Call **`MobileAds.initialize` only after** that, and request ads only when `canRequestAds()` is true. No ad request is made in the EEA, UK or CH without a UMP result.
- **Messages.** The studio's GDPR message (EEA/UK/CH) and its US-state message must be **published and attached to the Wheelfile AdMob app** before any ads-containing upload. Per studio issue 00, publishing them is still open at studio level.
- **Settings row.** Settings gets a **"Privacy options"** row whenever `privacyOptionsRequirementStatus == REQUIRED`. It calls `showPrivacyOptionsForm`.
- **If the user declines personalised ads:** serve non-personalised or limited ads where policy allows, and otherwise no ad. Do not crash, and never nag.
- **Children.** Do not set the child-directed or under-age tag. The target audience is 18+.
- **Pro and Remove ads owners:** never request ads. Initialise the SDK only if it is needed for consent obligations.

### 2.5 Remote Config schema (studio keys)

These are bundled as `res/xml/remote_config_defaults.xml`, so the app behaves the same before the first fetch or if a fetch fails.

| Key | Type | Default | Role |
|---|---|---|---|
| `ads_enabled` | bool | `true` (ads-shipping build) | **Master kill switch.** `false` hides every ad, with no release |
| `ads_banner_enabled` | bool | `true` | Banner kill switch |
| `ads_interstitial_enabled` | bool | `true` | **First pull-back lever** |
| `ads_rewarded_enabled` | bool | `false` | Off |
| `ads_native_enabled` | bool | `false` | Off |
| `ads_app_open_enabled` | bool | `false` | Off (and no code path exists) |
| `ads_interstitial_block_first_session` | bool | `true` | Cap |
| `ads_interstitial_min_install_days` | int | `3` | Cap |
| `ads_interstitial_min_interval_sec` | int | `480` | Cap |
| `ads_interstitial_max_per_session` | int | `1` | Cap |
| `ads_interstitial_max_per_day` | int | `3` | Cap |
| `ads_max_content_rating` | string | `"PG"` | Content ceiling |
| `ads_rewarded_daily_limit` | int | `1` | Unused while rewarded is off |

**Spec drift to fix in spec §5.2 and the V1 issue:**
1. `ads_interstitial_min_interval_seconds` must be renamed **`ads_interstitial_min_interval_sec`**.
2. `ads_interstitial_block_first_session` is **missing** from the spec's key list and must be added. The spec's prose does say "none in the first session".

**Fetch and cache guidance:**
- Use `fetchAndActivate` at startup with the default 12 h minimum fetch interval in release, and a short interval in debug only.
- New values apply on the next launch, never mid-session.
- **Clamp to safe ranges in code:** min interval ≥ 180 s, per-session ≤ 2, per-day ≤ 5, min install days ≥ 1. A bad RC push can tighten caps but can never exceed AdMob's guidance or our ceiling (**JUDGEMENT** ceilings).

### 2.6 Analytics, KPIs and guardrails

**Events** (no PII, no free text, no plates or VINs, no amounts; enums and counts only):

| Event | Params | Source |
|---|---|---|
| `ad_impression` (automatic) | value and currency once Firebase is linked to AdMob | Studio |
| `remove_ads_paywall_view`, `remove_ads_purchase_start`, `remove_ads_purchase_success`, `remove_ads_purchase_fail` | `product_id`, `price_micros`, `currency`. **No success on restore** | Studio (sf-04) |
| `ad_paywall_entry` | `source`: `settings_row` / `paywall_card` / `interstitial_close` | Studio (optional) |
| Pro events: `pro_screen_view` (+ `trigger`), `purchase_started`, `purchase`, `purchase_restored` | `product_id`, `trigger` (`vehicle_cap`, `pdf`, `all_time_chart`, `attachment_cap`, `import_overflow`, `settings`, `soft_card`) | **Align with the template / Spoonfile event names.** The spec's `paywall_view` / `purchase_start` / `purchase_success` / `restore_success` drift from them |
| `interstitial_suppressed` | `reason`: `pump_session` / `cap` / `first_session` / `install_days` | Wheelfile. Measures how often the pump-session rule bites |
| `vehicle_cap_hit`, `import_overflow_archived` (`archived_count` bucket), `vehicle_swap` | — | Wheelfile, for cap tuning |

**KPIs:** these are the same as the studio set:
- ARPDAU (ads and IAP shown separately);
- impressions per DAU;
- Remove ads and Pro paywall → purchase conversion;
- D1/D7/D30 retention;
- rating, 1★ rate and ad-mention reviews;
- crash-free and ANR rates.

Wheelfile has no pre-ads cohort, so **freeze the internal and closed-test metrics before the wider rollout** as the baseline (sf-04 rule).

**Guardrails.** If any of these trip, pull back the same day via RC and tell Ram:
1. D1 or D7 retention is ≥ 5 pp below baseline (after ≥ 7 days and a usable sample).
2. Pro conversion is ≥ 25% below baseline (usable sample; ignore single-digit purchase counts).
3. Crash or ANR rate hits the halt threshold, or a crash cluster appears in the ads SDK.
4. An ad on open or exit, on a form, or stacked appears. That is a policy bug, so set `ads_interstitial_enabled=false` immediately.
5. **Wheelfile review-sentiment trigger.** Count 1–3★ reviews that mention ads ("ad", "ads", "advert", "Werbung", "publicité", "anuncio(s)") every week.
   - **Benchmark from the spec's own theme count:** ads appear in 52 of 328 Drivvo reviews (15.9%) and 32 of 298 aCar reviews (10.7%).
   - **Trigger:** ad mentions above 10% of our 1–3★ reviews over a rolling 4 weeks (min 20 reviews), *or* ≥ 3 ad-complaint 1–2★ reviews in one week. Either one triggers the first pull-back. (These are **JUDGEMENT** thresholds; our ad-mention share must stay below aCar's.)

**Pull-back levers:**
- First pull-back: `ads_interstitial_enabled=false`, keeping banners.
- Second: `ads_enabled=false`.
- Turning ads back on needs Ram, and then Ajith.

---

## 3. Paywall, copy, SKUs and compliance

### 3.1 Paywall trigger map

| Trigger | Type | Behaviour |
|---|---|---|
| Tap "Add vehicle" with 2 active vehicles | Hard gate (user-initiated) | Paywall sheet with trigger `vehicle_cap`. A secondary action offers "Archive a vehicle instead" |
| Tap "PDF history report" | Hard gate | Paywall sheet with trigger `pdf`. Show a static sample page preview (no user data needed) |
| Select "All time", a custom range or "Compare vehicles" in Reports | Hard gate | Paywall sheet with trigger `all_time_chart`. The 12-month charts stay usable underneath |
| Add the 31st attachment | Hard gate | Paywall sheet with trigger `attachment_cap`. The photo is not lost: the sheet offers "Save entry without photo" |
| Import or restore result with archived overflow | Soft, inline | One line in the result screen. **Never a modal during import** |
| Settings → "Wheelfile Pro" card and "Remove ads" row | Passive | Always present for non-owners |
| Reports soft card ("Turn your history into a PDF") | Soft | Only after ≥ 6 months of entries on a vehicle (**JUDGEMENT**) |

**Paywall nag caps** (hard-coded constants, not RC):
- No soft prompt in the first 3 days or the first session.
- At most **1 soft prompt per 7 days**, and stop for good after **3 dismissals**.
- **Never** show a soft prompt in a session that saved a fill-up or charge (the pump-session rule).
- Never show a soft prompt after an interstitial in the same session.
- Hard gates show only when the user taps a gated action.
- No countdowns, fake discounts or "limited time" copy.
- **No "Remove ads" link under banners** (spec §5.4 and design decision 6). Remove ads lives only in Settings and on the paywall.

### 3.2 Copy rules (Postpole, enforced by `CopyRulesTest` and `NoHardcodedPriceTest`)

1. **Global-first.**
   - Write "fuel" and "fill-up", not "gas", in global UI and store copy. Localised strings may use the local norm.
   - The powertrain picker label "Petrol/Gasoline" (spec §2.1) is fine.
   - Always cover petrol, diesel, EV and hybrid. Use metric *and* imperial, and any currency.
   - Avoid US-only terms (MPG in titles, "smog") and India-only terms in global copy.
2. **The o-word (`off\x6cine`) is never allowed,** and copy must never imply the app works without internet. Use **"Your data stays on your device."** or "Your vehicle logs are stored on your device." Both are accurate alongside the AdMob, Firebase and Billing network traffic.
   - **Catch (spec §6.2):** the spec's `CopyRulesTest` bans `stay[s]?\s+on\s+your\s+(phone|device)`. That would **fail the studio-mandated sentence** "Your data stays on your device." Remove that pattern, and assert that the studio sentence (or the spec's "stored on your device") is present instead.
3. **"No ads" or "ad-free" only on paid surfaces.**
   - Never in the Play title, short description, feature graphic, screenshots, or free-tier UI.
   - "No ads" may appear **only** as a perk inside the Pro paywall card, the Remove ads card and the Settings purchase rows, and only in builds that ship ads (all Wheelfile builds from V1).
   - Never claim the app is "ad-free".
4. **No hard-coded prices.** Use `ProductDetails.oneTimePurchaseOfferDetailsList` formatted prices only. `NoHardcodedPriceTest` must forbid 9.99, 3.99, 599, 249 and so on.
5. **No GPS, trip or tax-mileage promises.** Also never claim sync, cloud backup, family sharing or multi-device (spec regex). Phone-to-phone transfer is described only as Android's transfer.
6. **No unbuilt features** ("coming soon", widgets or OCR on the paywall).
7. Competitor names appear only in the full description, nominatively (spec D3 and D7).

**Ready-to-use copy.** EN source strings. `{price}` comes from Play.

- **Pro paywall**
  - Headline: **Wheelfile Pro**
  - Sub: *Pay once. No subscription.*
  - Bullets:
    - Unlimited vehicles
    - PDF service history for resale or your mechanic
    - Full history: all-time charts, custom ranges, compare vehicles
    - Unlimited receipts and documents
    - Your own expense and service categories
    - No ads
  - CTA: **Get Pro · {price}**
  - Badge on card: *Best value*
- **Remove ads card**
  - Title: **Remove ads**
  - Body: *Hide all ads. Free limits stay the same: 2 active vehicles, 12-month charts, 30 attachments.*
  - CTA: **Remove ads · {price}**
- **Shared footer:** *Import, export and backup stay free on every plan. Your data stays on your device.* · *Restore purchases*
- **Vehicle cap sheet**
  - Title: *Add more vehicles with Pro*
  - Body: *Free keeps 2 vehicles active. Archived vehicles keep all their history.*
  - Actions: **Get Pro · {price}** / *Archive a vehicle instead*
- **PDF sheet**
  - Title: *Your service history, as a PDF*
  - Body: *Share a clean record of services, costs and economy when you sell or visit the workshop.*
- **Attachment cap sheet**
  - Title: *You've used your 30 free attachments*
  - Body: *Pro stores unlimited receipts and documents.*
  - Actions: **Get Pro · {price}** / *Save without photo*
- **Settings card (not Pro)**
  - Title: *Wheelfile Pro*
  - Body: *Unlimited vehicles, PDF history, full charts and no ads.*
  - CTA: **{price}**
- **Settings row:** *Remove ads, {price}*
- **Settings card after `remove_ads`:**
  - Title: *Go further with Pro*
  - Body: *Unlimited vehicles, PDF history, full charts and unlimited attachments.*
  - Do not mention ads.
- **Store listing monetisation line** (full description only; ASO owns it): *Free to use with ads. One-time upgrades: Remove ads, or Wheelfile Pro for unlimited vehicles, PDF history and full charts. No subscription.* There are no prices in store copy.

### 3.3 Release order for Contains ads (Build 1 rule)

- **V0** is manual setup with no upload. **V1** adds the Mobile Ads SDK, UMP and RC (spec §7).
- **Rule: no Wheelfile AAB without the Mobile Ads SDK is ever uploaded.** The **first** Play upload, internal test included, carries the SDK and UMP, with ads RC-gated. **Contains ads = Yes on that first upload** and on every one after.
- No "no ads" listing or form ever exists. This is the same rule as Spoonfile sf-03 and ads plan §5.
- Debug and internal builds use Google demo units. Release builds use the real `wf_` units **only once they exist** in `admob-ids.md`.
- If the real units do not exist when the first *closed or production* build is cut, hold that build. Do not ship production with test IDs.

### 3.4 Play Billing 8/9 SKU and purchase-option spec (matches Spoonfile)

| Field | Pro | Remove ads |
|---|---|---|
| Product ID | `lifetime_full_access` | `remove_ads` |
| Type | One-time product | One-time product |
| Purchase option ID | `lifetime-buy` | `remove-ads` |
| Option type | Buy | Buy |
| Backwards compatible | **ON** | **ON** |
| Status | Active, all Play countries | Active, all Play countries. Wheelfile ships ads from its first build, so this can go Active with the first upload. Keep it **Draft** only if, for any reason, the uploaded build lacks ads |
| Offers | **None** | **None** |
| Price | §1.7 table (US 9.99) | §1.7 table (US 3.99) |
| Name / description (Console, EN) | "Wheelfile Pro": "Unlimited vehicles, PDF history, full charts, unlimited attachments, custom categories and no ads. One-time purchase." | "Remove ads": "Hide all ads. Free limits stay the same. One-time purchase." |

**Note on drift:** the ads plan §2 mentioned a `lifetime-full-access` option pattern. The live Spoonfile spec sent to Release & QA uses **`lifetime-buy`**, and Wheelfile follows Spoonfile.

**Prerequisites in Console.** Creating one-time products normally requires the app to exist in Console with a billing-enabled build uploaded. Release & QA should follow the Spoonfile sequence and re-verify this in Console.

**Engineering contract** (Spoonfile `PlayBillingGateway` / `BillingRepository` pattern; template uses **Play Billing 9.1.0**):
- **Library version.** PBL 8+ has been mandatory for new apps and updates since 31 Aug 2026. PBL 9's deadline is 31 Aug 2028 (https://developer.android.com/google/play/billing/deprecation-faq).
- **Product details.** Call `queryProductDetailsAsync` for both IDs as `INAPP`. Read `oneTimePurchaseOfferDetailsList` and pick the active Buy option's `offerToken`. Do not hard-code `lifetime-buy` or `remove-ads` unless Play returns several options. Pass the `offerToken` in `ProductDetailsParams`.
- **Pending purchases.** Use `enablePendingPurchases(PendingPurchasesParams…enableOneTimeProducts())`. A purchase in PENDING grants **nothing** until PURCHASED. Acknowledge within 3 days.
- **Entitlement.** `adsHidden = isPro || hasRemoveAds`, where `isPro` comes from `lifetime_full_access` only. **Restore Purchases** queries both products. A refund or revoke of one leaves the other in place. On a Pro revoke, run the archive-not-delete flow (§1.4).
- **IDs in config.** `BuildConfig.PRO_PRODUCT_ID` (default `lifetime_full_access`) and a new `REMOVE_ADS_PRODUCT_ID` (`remove_ads`).
- **Missing SKU.** If product details are missing, show the unavailable / retry state. Never fall back to a typed price.

### 3.5 AdMob app and units (publisher `pub-2007361247232604`)

| Unit name | Format | Placement |
|---|---|---|
| `wf_banner_garage` | Anchored adaptive banner | S2 Garage (not when empty) |
| `wf_banner_history` | Anchored adaptive banner | S5 History (and the History tab in S4) |
| `wf_banner_documents` | Anchored adaptive banner | S11 Documents list (and the Documents tab in S4) |
| `wf_banner_reports` | Anchored adaptive banner | S13 Reports (and the Reports tab in S4) |
| `wf_interstitial_after_review` | Interstitial | History or Reports → Garage, after caps and the pump-session rule |
| `wf_rewarded` | Rewarded | **Placeholder. RC off. No UI** |

- **AdMob app:** "Wheelfile: Car Maintenance Log", Android, linked to the Play listing once it exists, and to the Wheelfile Firebase project.
- **NEVER invent AdMob IDs.** `admob-ids.md` today lists only PocketLedger, Ghar Khata and Spoonfile. V0 and debug builds use Google's official demo units:
  - banner `ca-app-pub-3940256099942544/9214589741`
  - interstitial `ca-app-pub-3940256099942544/1033173712`
  - rewarded `ca-app-pub-3940256099942544/5224354917`
  (These come from https://developers.google.com/admob/android/test-ads, as cited in sf-01.)
- **Once real units exist,** Ram creates the app and the 6 units, then appends a `## Wheelfile` section to `/workspace/monetisation/admob-ids.md`. The manifest `APPLICATION_ID` and the release unit IDs are then taken from that file only.
- **Mediation:** none in v1.

### 3.6 Pre-V0 / Pre-V1 compliance, Data safety and privacy checklist

**app-ads.txt and developer website**
- [ ] `https://pocket-ledger-postpole.web.app/app-ads.txt` is live with `google.com, pub-2007361247232604, DIRECT, f08c47fec0942fa0` (local copy: `ads-compliance-drafts/app-ads.txt`). Same publisher, so no edit is needed.
- [ ] The Wheelfile Play developer website is set to **`https://pocket-ledger-postpole.web.app`**. AdMob crawls the developer website, not the privacy URL, and the crawl takes up to about 24 h.

**Play App content**
- [ ] Contains ads = **Yes** from the first upload (§3.3).
- [ ] Target audience is **18+ only**. "Designed for children" = No. Not enrolled in Families.
- [ ] Advertising ID declaration = **Yes**, with purposes Advertising or marketing, Analytics, and Fraud prevention, security and compliance. Tick Personalization only once personalised ads can be served after consent.

**Manifest**
- [ ] Keep `com.google.android.gms.permission.AD_ID`. **Never** add `tools:node="remove"`.
- [ ] Add `com.google.android.gms.ads.APPLICATION_ID` once the real app ID exists.
- [ ] Permissions per spec §6.1: INTERNET, AD_ID and POST_NOTIFICATIONS (in context). No CAMERA, storage, location or exact-alarm permissions.

**Data safety.** Mirror `data-safety-ads-checklists.md` (full AdMob disclosure, https://developers.google.com/admob/android/privacy/play-data-disclosure):

| Data type | Collected | Shared | Purposes | Notes |
|---|---|---|---|---|
| Location → Approximate location | Yes | **Yes** | Advertising; Analytics; Fraud prevention, security and compliance | IP-derived by the ads SDK. No location permission |
| App activity → App interactions | Yes | **Yes** | Advertising; Analytics; Fraud prevention… (plus App functionality and Analytics for GA4) | GA4 params are enums and counts only |
| App info and performance → Diagnostics | Yes | **Yes** | Advertising; Analytics; Fraud prevention… | AdMob SDK diagnostics |
| App info and performance → Crash logs | Yes | No | Analytics; App functionality | Crashlytics as service provider. No plates, VINs or notes in crash messages |
| Device or other IDs | Yes | **Yes** | Advertising; Analytics; Fraud prevention… | Advertising ID, app-set ID, Firebase installation IDs. Never call these "anonymous" |
| Financial info → Purchase history | Yes | No | App functionality; Analytics | Play Billing (Pro, Remove ads). Price and currency only |
| Photos and videos; Files and docs; Personal info; other Financial info; Messages | **No** | — | — | Vehicle logs, fuel and charge costs, receipts, documents, plates, VINs and notes **stay on the device** and are not collected or shared by Postpole. Cloud backup is off. Device-to-device transfer is between the user's own devices |

- [ ] Encrypted in transit = Yes. Users can request deletion = Yes: on-device data is deleted in-app or by uninstalling, and the Advertising ID is reset in Android settings.
- [ ] **Spec §6.1 drift:** add the "Fraud prevention, security and compliance" purpose, and mark App interactions and Diagnostics as **Shared = Yes**. The spec's draft understates sharing.

**Privacy policy**
- [ ] Template: `ads-compliance-drafts/spoonfile-privacy-ads.md`, adapted for Wheelfile:
  - vehicle logs, receipts and documents stay on the device;
  - the AdMob, UMP, Firebase (GA4, Crashlytics, Remote Config) and Play Billing clauses;
  - the US-state rights clause;
  - the children clause (not directed to under-13s);
  - contact **admin@postpole.com**.
- [ ] The hosted URL must exist **before the first Play submission**. The URL is **`https://pocket-ledger-postpole.web.app/wheelfile/privacy`** (decided 20 Nov 2026). The draft is `ads-compliance-drafts/wheelfile-privacy-ads.md`, and Ajith approves the text before it is published.
- [ ] No o-word, no "no data collected", and no "100% private" (spec §6.2).
- [ ] Set the privacy URL in `app_config.xml` and on the listing.

**Governance and sign-off**
- [x] **Ram:** signed off this pricing and ads plan (spec D5) on 20 Nov 2026.
- [ ] **Ajith:** locks the name and package (D1) and decides on the lawyer TM search (D2). He approves any spend: any AdMob, Play or tax spend, and the **Play production release**.
- [ ] **Release & QA:** create the Play SKUs per §3.4 and §1.7, and do the read-only price pass. Publish and attach the UMP messages, set the developer website, and link AdMob, Play and Firebase.
- [ ] **Ram:** creates the AdMob app and the 6 units and appends them to `admob-ids.md`. He also handles the privacy URL, the Firebase project, and the Firebase–AdMob link.
- [ ] **Claude (Android):** V1 issue = SDK, UMP, RC, `AdsPolicy` (incl. the pump-session rule) and entitlement. V3/V6/V7 issues = gates, paywall, copy tests and placements. These should mirror sf-01…sf-04 as wf-01…wf-04 when Ram asks.

---

## 4. Catches in `spec.md` and `design/README.md`

| # | Where | Issue | Fix |
|---|---|---|---|
| 1 | spec §5.2 | RC key `ads_interstitial_min_interval_seconds` | Rename to **`ads_interstitial_min_interval_sec`** (studio schema) |
| 2 | spec §5.2 | `ads_interstitial_block_first_session` is missing from the key list | Add it, default `true` |
| 3 | spec §6.2 | The CopyRulesTest regex `stay[s]?\s+on\s+your\s+(phone\|device)` blocks the studio phrase "Your data stays on your device" | Drop the pattern and assert the studio phrase instead |
| 4 | spec §3.1 / §5.3 | `wf_interstitial_after_history` also fires from Reports | Rename to **`wf_interstitial_after_review`** |
| 5 | spec §2.2 / §5.2 | Nothing stops "Save fill-up → check History → back to Garage" from firing an interstitial at the pump | Add the **pump-session rule** (§2.3) |
| 6 | spec §3.1 S4 | The Vehicle detail tabs (History/Documents/Reports) are not covered by the banner rules | The banner follows the content type, and Overview gets none (§2.1) |
| 7 | spec §5.4 / §5.1 | Attachment cap "~25 (GUESS)" | **30**, as a code constant. The trigger moves to the 31st attachment |
| 8 | spec §5.4 | Event names `paywall_view` / `purchase_start` / `purchase_success` / `restore_success` | Align with the template / Spoonfile `pro_screen_view` / `purchase_started` / `purchase` / `purchase_restored`, plus the `remove_ads_*` events |
| 9 | spec §6.1 | Data safety draft understates sharing and purposes | Use the §3.6 matrix |
| 10 | spec §5.1 | Only 4 Remove ads currencies were listed, and the Pro table was by reference | Use the full 26-market table (§1.7) |
| 11 | spec §2.7 | "Archived (read-only) with upsell" is underspecified | Use §1.4: user picks the active pair, swapping allowed, reminders paused, export, backup and PDF included, delete allowed |
| 12 | design README D1/D3 | Banner stacked above the NavigationBar (155 dp of bottom chrome) | Prefer D2 (the designer's pick) or D4. This is not a blocker |
| 13 | spec §0.3 vs §6.3 | Template has `allowBackup=false`, spec §6.3 sets `true` with rules | Not a monetisation issue. Release & QA and Claude should confirm the backup and Data safety wording matches the final XML |

---

## 5. Status of open items (after Ram's sign-off, 20 Nov 2026)

**Done or decided:**
1. Tiers, prices, ads placements and caps, the pump-session rule and the interstitial rename are **approved** (§0).
2. Spec fixes 1–7 (§4 rows 1–6 and 8–9) are **approved** and applied to `spec.md`, marked inline.
3. **Privacy policy URL:** `https://pocket-ledger-postpole.web.app/wheelfile/privacy`. **Support and contact email:** admin@postpole.com. The draft is `/workspace/monetisation/ads-compliance-drafts/wheelfile-privacy-ads.md`, with the effective date left as a placeholder until it is published.

**Gates on Release & QA's checklist:**
4. **UMP gate:** the studio GDPR message (EEA/UK/CH) and the US-state message must be published **and attached to the Wheelfile AdMob app** before the first Wheelfile upload. There is no upload without this.

**Design:**
5. Direction 2 (Highway Cobalt, no bottom NavigationBar) fits ads best. Ajith is choosing between **Direction 2 and Direction 4**. If he picks Direction 4, the anchored banner must sit **above any bottom nav and the 24 dp gesture inset, and must never overlap them**. The FAB stays 16 dp above the banner, and list content ends above the banner. (Note: `design/README.md` shows Direction 4 with top tabs and no bottom NavigationBar. The rule applies if a bottom nav is added.)

**Decided by Ajith (20 Nov 2026, 3:41 PM IST):** the US$24.90 Drivvo fixture purchase (D4) is **declined**. The Drivvo CSV importer stays marked **beta**, built from the published format.

**Pending with Ajith:**
6. Name lock for Wheelfile / `com.postpole.wheelfile` (D1).
7. Trademark search: whether a lawyer-led search is done before the first upload (D2).
8. Later: approval for the Play production release.

*End of plan. FINAL, signed off by Ram on 20 Nov 2026. No Console, AdMob, Firebase, GitHub or Gmail action was taken.*
