# Wheelfile screenshot shot list

Play's adaptive app quality bar (from April 2027) expects screenshots for phone, 7-inch tablet, 10-inch tablet and a foldable. Capture each set in light theme, with sample data from `docs/aso/screenshot-captions.md` and neutral or EU/US examples (no rupee amounts, no UPI). Use a build with ads hidden or test ads, and never show a purchase price: Play shows prices itself.

| Device set | Window class | Size to capture | Notes |
|---|---|---|---|
| Phone | Compact | 1080 x 2400 | Single pane. Eight shots, one per caption |
| 7-inch tablet | Medium | 1200 x 1920 (portrait) | Garage list next to the vehicle (list-detail) |
| 10-inch tablet | Expanded | 1600 x 2560 (portrait), plus one landscape | Garage, vehicle History next to an entry |
| Foldable (unfolded) | Medium or expanded | 2208 x 1840 | Hinge-aware panes; also one folded (compact) shot |

## Shots (every device set)

1. Garage with two or three vehicles (cards with odometer, next reminder, last economy).
2. Vehicle Overview: economy card, cost card, next reminders.
3. Add a fill-up: any two of price, volume and total filled in, the third calculated.
4. History tab with filter chips.
5. Reminders: due soon and overdue.
6. Documents tab with an expiry alert.
7. Reports tab: 12-month cost chart.
8. Import preview, before anything is saved.

## Rules

- No banner may appear in a screenshot of the Overview tab, forms, import, Settings or the paywall. Banners are allowed on Garage, History, Documents and Reports only.
- The paywall screenshot, if used, shows both purchases side by side with Play's own price text, not a typed number.
- Captions are in `docs/aso/screenshot-captions.md` (40 characters or fewer).
- Localised sets (DE, FR, ES) use the in-app language of the same screens.
