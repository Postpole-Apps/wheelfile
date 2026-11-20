# Ram's design decisions (20 Nov 2026, IST)

These answer the App Designer's open questions in `README.md` ("Open questions for Ram") and **override the design pack wherever they differ**. Where a PNG shows something else (for example top-level History/Docs/Reports tabs, or a banner on the empty Garage), follow this file.

1. **Navigation (IA).** Garage is the **only** top-level home. There are no top-level History / Documents / Reports tabs. History, Documents and Reports are **per-vehicle tabs** inside Vehicle detail: Overview · History · Documents · Reports (`s04-vehicle-overview` shows the tab row). Reuse the `s05-history`, `s11-documents` and `s13-reports` layouts as the tab content, filtered to the selected vehicle.
2. **Overview tab is ad-free.** No banner on Vehicle detail › Overview.
3. **Banners** (anchored adaptive, bottom, above the gesture inset) appear **only** on Garage and the History, Documents and Reports tabs. Nowhere else.
4. **Banner slot behaviour.** The 50 dp + 1 dp slot is reserved while the ad is loading (nothing jumps, `st-loading`). If the ad **fails to fill**, the slot **collapses** to 0 and the FAB moves down to 16 dp above the gesture inset. The slot is also 0 when `adsHidden` (Remove ads or Pro, `s02-garage-ads-hidden`).
5. **Empty first-run Garage is ad-free.** No banner and no ad request until at least one vehicle exists (`st-empty-garage` shows a slot; ignore it).
6. **Reminders entry points.** A bell icon button in the **Vehicle detail top app bar** opens Reminders for that vehicle (badge with the due-soon count is allowed), plus Settings › Reminders for all vehicles. No Reminders tab.
7. **Roboto Flex is bundled** as `res/font/roboto_flex.ttf` (from `design/tokens/fonts/`). No downloadable fonts.
8. **Paywall.** Pro may stay highlighted as drawn; both options stay one-time and show prices only from Google Play.
9. **Interstitial trigger with the new IA.** `wf_interstitial_after_review` may show only when the user navigates **back from Vehicle detail to Garage after viewing the History or Reports tab**, subject to every cap and the pump-session rule in `spec.md` §5.2.
