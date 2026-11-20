# Wheelfile launcher icon — Hub Tab (locked 20 Nov 2026)

Concept 3 from the icon round, exported byte-for-byte unchanged from `icons/src/geom.py` → `concept3()` (re-exported by `icon/src/build_final_icon.py`).

| File | What |
|---|---|
| `svg/background.svg` | 108×108dp full-bleed `#FFDBCA` |
| `svg/foreground.svg` | folder card `#99460C` (radii 11/2.2/2.2/11/11), dog-ear fold `#D9824A`, 6-spoke wheel `#FFF8F2` |
| `svg/monochrome.svg` | 4.5dp outline of the card + 6-spoke wheel, single colour (Android 13+ themed icons) |
| `drawable/ic_launcher_{background,foreground,monochrome}.xml` | the same three layers as VectorDrawables (viewport 108) |
| `mipmap-anydpi-v26/ic_launcher.xml`, `ic_launcher_round.xml` | `<adaptive-icon>` with background, foreground **and `<monochrome>`** |
| `play-512.svg` / `play-512.png` | Play Store icon, 512×512 **RGBA**, full square (alpha 255 everywhere), no mask — Play applies its own corner radius |
| `preview.png` | masks, layers with 72dp/66dp guides, sizes, themed icons, Play crop, home screens (default + dark themed) |
| `safe-zone-check.json` | 66dp safe zone (circle r33 around 54,54): foreground **0 px** outside (max r 31.465dp), monochrome **0 px** outside (max r 29.698dp) |

## Handoff notes for Claude (build)
1. Copy `drawable/ic_launcher_background.xml`, `ic_launcher_foreground.xml`, `ic_launcher_monochrome.xml` into `app/src/main/res/drawable/`.
2. Copy `mipmap-anydpi-v26/ic_launcher.xml` and `ic_launcher_round.xml` into `app/src/main/res/mipmap-anydpi-v26/` (minSdk ≥ 26 means no PNG mipmaps are needed; delete the template `mipmap-*/ic_launcher*.webp` files so they do not shadow these).
3. In `AndroidManifest.xml` on `<application>`: `android:icon="@mipmap/ic_launcher"` and `android:roundIcon="@mipmap/ic_launcher_round"`.
4. Upload `play-512.png` as the Play Console app icon (32-bit PNG, 512×512).
5. Do not edit path data by hand; regenerate with `python3 icon/src/build_final_icon.py` (from `design/final`) if anything changes. The audit recomputes the safe zone from these SVGs.
