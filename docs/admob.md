# AdMob configuration (Wheelfile)

Publisher: `ca-app-pub-2007361247232604`. These are public client identifiers, not secrets.

## Release builds (production)

| Use | Name | ID |
|---|---|---|
| App ID (manifest `com.google.android.gms.ads.APPLICATION_ID`) | Wheelfile | `ca-app-pub-2007361247232604~9674014227` |
| Anchored adaptive banner, Garage | `wf_banner_garage` | `ca-app-pub-2007361247232604/7125861609` |
| Anchored adaptive banner, History tab | `wf_banner_history` | `ca-app-pub-2007361247232604/9041578502` |
| Anchored adaptive banner, Documents tab | `wf_banner_documents` | `ca-app-pub-2007361247232604/3312232966` |
| Anchored adaptive banner, Reports tab | `wf_banner_reports` | `ca-app-pub-2007361247232604/7566725638` |
| Interstitial (back to Garage after History/Reports) | `wf_interstitial_after_review` | `ca-app-pub-2007361247232604/8372987954` |
| Rewarded (defined, off by default: `ads_rewarded_enabled` = false) | `wf_rewarded` | `ca-app-pub-2007361247232604/4715320686` |

## Debug builds (always Google's official test units)

Never load the production units in debug, from a test device, or in CI/UI tests.

| Format | Test ID |
|---|---|
| Banner (adaptive) | `ca-app-pub-3940256099942544/9214589741` |
| Interstitial | `ca-app-pub-3940256099942544/1033173712` |
| Rewarded | `ca-app-pub-3940256099942544/5224354917` |

The App ID in the manifest may stay the real Wheelfile App ID in every build type (as in Spoonfile); only the unit IDs switch.

## How the IDs are wired
- Unit IDs are `BuildConfig` fields per build type (`AD_UNIT_BANNER_GARAGE`, `AD_UNIT_BANNER_HISTORY`, `AD_UNIT_BANNER_DOCUMENTS`, `AD_UNIT_BANNER_REPORTS`, `AD_UNIT_INTERSTITIAL_AFTER_REVIEW`, `AD_UNIT_REWARDED`), with the debug values above and the release values from this file. They can be overridden with Gradle properties of the same name (`-PAD_UNIT_...`) so Release & QA can rotate an ID without a code change.
- The App ID goes in via `manifestPlaceholders["admobAppId"]`, default as above.
- Remote Config controls **whether** a format shows (`ads_enabled`, `ads_banner_enabled`, `ads_interstitial_enabled`, `ads_rewarded_enabled`, caps), never the unit IDs. Defaults are bundled in `res/xml/remote_config_defaults.xml` (`spec.md` §5.2).
- `app-ads.txt` already lives at https://pocket-ledger-postpole.web.app/app-ads.txt (same publisher).
