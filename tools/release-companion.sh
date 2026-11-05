#!/usr/bin/env bash
# Builds the signed Companion release APK into ../Releases/CleanYourHeadUnit-Companion-<version>.apk.
# The key stays outside the repo: SIGNING_PROPERTIES, or ../Signing/signing.properties next to the repo folder
# (storeFile, storePassword, keyAlias, keyPassword). Never commit either. Losing the key means people can't
# update in place.
set -euo pipefail
cd "$(dirname "$0")/.."
export SIGNING_PROPERTIES="${SIGNING_PROPERTIES:-$(cd .. && pwd)/Signing/signing.properties}"
[ -f "$SIGNING_PROPERTIES" ] || { echo "no signing.properties at $SIGNING_PROPERTIES" >&2; exit 1; }
VERSION=$(sed -n "s/.*versionName '\(.*\)'.*/\1/p" companion/build.gradle)
OUT="${RELEASES_DIR:-$(cd .. && pwd)/Releases}"
mkdir -p "$OUT"
if [ -n "${JAVA_HOME:-}" ]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
./gradlew :companion:clean :companion:testReleaseUnitTest :companion:lintRelease :companion:assembleRelease -q --console=plain
cp companion/build/outputs/apk/release/companion-release.apk "$OUT/CleanYourHeadUnit-Companion-$VERSION.apk"
shasum -a 256 "$OUT/CleanYourHeadUnit-Companion-$VERSION.apk"
