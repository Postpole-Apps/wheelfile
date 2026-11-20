#!/usr/bin/env bash
# Usage: scripts/new-app.sh [--dry-run|-n] <applicationId> "<App Name>"
# Renames the template package/namespace/applicationId, moves source dirs, sets app_name,
# resets versionCode/versionName and prints a setup checklist. Run from a fresh copy of the template.
# --dry-run prints what would change and writes nothing.
set -euo pipefail

DRY=0
ARGS=()
for a in "$@"; do
  case "$a" in
    --dry-run|-n) DRY=1 ;;
    *) ARGS+=("$a") ;;
  esac
done
if [ ${#ARGS[@]} -ne 2 ]; then
  echo "Usage: $0 [--dry-run|-n] <applicationId> \"<App Name>\"" >&2
  exit 1
fi

NEW_ID="${ARGS[0]}"
APP_NAME="${ARGS[1]}"
OLD_ID="com.postpole.template"

if ! [[ "$NEW_ID" =~ ^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$ ]]; then
  echo "Invalid applicationId '$NEW_ID' (expected e.g. com.postpole.myapp, lowercase)." >&2
  exit 1
fi
# Reject Kotlin/Java keywords as package segments (they would not compile).
KEYWORDS=" abstract as assert boolean break byte case catch char class const continue default do double else enum extends false final finally float for fun goto if implements import in instanceof int interface is long native new null object package private protected public return short static strictfp super switch synchronized this throw throws transient true try typealias typeof val var void volatile when while "
IFS='.' read -r -a SEGMENTS <<< "$NEW_ID"
for seg in "${SEGMENTS[@]}"; do
  if [[ "$KEYWORDS" == *" $seg "* ]]; then
    echo "Invalid applicationId '$NEW_ID': '$seg' is a reserved keyword and cannot be a package segment." >&2
    exit 1
  fi
done
if [[ "$APP_NAME" == *[\&\<\>\"\\\|]* ]]; then
  echo "App name must not contain & < > \" \\ or |." >&2
  exit 1
fi

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

if [ "$NEW_ID" = "$OLD_ID" ]; then
  echo "applicationId is already $OLD_ID; nothing to rename." >&2
  exit 1
fi
if [ ! -d "app/src/main/kotlin/${OLD_ID//./\/}" ]; then
  echo "Template package $OLD_ID not found; was this script already run?" >&2
  exit 1
fi

OLD_PATH="${OLD_ID//./\/}"
NEW_PATH="${NEW_ID//./\/}"

# Android string resource escaping: \' for apostrophes, \@ / \? for a leading @ or ?.
XML_NAME="${APP_NAME//\'/\\\'}"
case "$XML_NAME" in
  @*|\?*) XML_NAME="\\$XML_NAME" ;;
esac
# Backslashes must be doubled inside the sed replacement.
SED_NAME="${XML_NAME//\\/\\\\}"

if [ "$DRY" -eq 1 ]; then
  echo "[dry-run] nothing will be written."
else
  echo "Renaming $OLD_ID -> $NEW_ID ..."
fi

# 1. Move source directories (main, test, androidTest if present).
for set in main test androidTest; do
  old_dir="app/src/$set/kotlin/$OLD_PATH"
  [ -d "$old_dir" ] || continue
  new_dir="app/src/$set/kotlin/$NEW_PATH"
  if [ "$DRY" -eq 1 ]; then
    echo "[dry-run] move $old_dir -> $new_dir"
    continue
  fi
  mkdir -p "$new_dir"
  cp -R "$old_dir"/. "$new_dir"/
  rm -rf "$old_dir"
  # Remove now-empty old parent dirs (up to the kotlin root).
  dir="$(dirname "$old_dir")"
  while [ "$dir" != "app/src/$set/kotlin" ] && rmdir "$dir" 2>/dev/null; do
    dir="$(dirname "$dir")"
  done
done

# 2. Replace the package id everywhere it appears (sources, gradle, manifest).
while IFS= read -r f; do
  if [ "$DRY" -eq 1 ]; then
    echo "[dry-run] update $OLD_ID in $f"
  else
    sed -i.bak "s|$OLD_ID|$NEW_ID|g" "$f" && rm -f "$f.bak"
  fi
done < <(grep -rIl --exclude-dir=.git --exclude-dir=build --exclude-dir=.gradle --exclude-dir=scripts \
  --exclude-dir=ci-templates --exclude-dir=docs --exclude=README.md --exclude=CLAUDE.md \
  -F "$OLD_ID" . || true)

# Room schema folders are named after the database class' fully qualified name
# (e.g. app/schemas/com.postpole.template.data.AppDatabase): rename every one.
if [ -d app/schemas ]; then
  for d in app/schemas/"$OLD_ID".*; do
    [ -d "$d" ] || continue
    target="app/schemas/$NEW_ID.${d#app/schemas/"$OLD_ID".}"
    if [ "$DRY" -eq 1 ]; then
      echo "[dry-run] move $d -> $target"
    else
      mv "$d" "$target"
    fi
  done
fi

# 3. App name.
if [ "$DRY" -eq 1 ]; then
  echo "[dry-run] set app_name to \"$APP_NAME\" in app/src/main/res/values/strings.xml (written as: $XML_NAME)"
else
  sed -i.bak "s|<string name=\"app_name\">.*</string>|<string name=\"app_name\">${SED_NAME}</string>|" \
    app/src/main/res/values/strings.xml && rm -f app/src/main/res/values/strings.xml.bak
fi

# 4. Reset version.
if [ "$DRY" -eq 1 ]; then
  echo "[dry-run] set versionCode 1 / versionName \"1.0.0\" in app/build.gradle.kts"
  echo "[dry-run] done; re-run without --dry-run to apply."
  exit 0
fi
sed -i.bak -E 's/^([[:space:]]*versionCode = ).*/\11/; s/^([[:space:]]*versionName = ).*/\1"1.0.0"/' \
  app/build.gradle.kts && rm -f app/build.gradle.kts.bak

cat <<EOF

Done: $OLD_ID -> $NEW_ID, app name "$APP_NAME", versionCode 1 / versionName 1.0.0.

Checklist (see docs/NEW_APP.md):
  [ ] Play Console: create the app and store listing (title, descriptions, screenshots, icon, feature graphic)
  [ ] Privacy policy URL: set privacy_policy_url (and support_email) in app/src/main/res/values/app_config.xml
  [ ] Pro product id: create the one-time in-app product in Play Console and match it
      (default 'lifetime_full_access', or pass -PproProductId=...)
  [ ] Firebase: create a project, register $NEW_ID, enable GA4 + Crashlytics,
      download google-services.json into app/ (git-ignored)
  [ ] Repo secrets: KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD,
      PLAY_SERVICE_ACCOUNT_JSON, GOOGLE_SERVICES_JSON (contents of google-services.json)
  [ ] Copy ci-templates/*.yml to .github/workflows/
  [ ] Replace the launcher icon and the Home/Item placeholders
  [ ] ./gradlew assembleDebug testDebugUnitTest lint
EOF
