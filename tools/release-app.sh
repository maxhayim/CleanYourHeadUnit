#!/usr/bin/env bash
# Builds the signed head-unit app release APK into ../Releases/CleanYourHeadUnit-<version>.apk.
# The key stays outside the repo: SIGNING_PROPERTIES, or ../Signing/headunit.properties next to the repo folder
# (storeFile, storePassword, keyAlias, keyPassword). Never commit either. Losing the key means people can't
# update in place.
set -euo pipefail
cd "$(dirname "$0")/.."
export SIGNING_PROPERTIES="${SIGNING_PROPERTIES:-$(cd .. && pwd)/Signing/headunit.properties}"
[ -f "$SIGNING_PROPERTIES" ] || { echo "no signing.properties at $SIGNING_PROPERTIES" >&2; exit 1; }
VERSION=$(sed -n "s/.*versionName '\(.*\)'.*/\1/p" app/build.gradle)
OUT="${RELEASES_DIR:-$(cd .. && pwd)/Releases}"
mkdir -p "$OUT"
if [ -n "${JAVA_HOME:-}" ]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
./gradlew :app:clean :app:testDebugUnitTest :app:lintRelease :app:assembleRelease -q --console=plain
cp app/build/outputs/apk/release/app-release.apk "$OUT/CleanYourHeadUnit-$VERSION.apk"
shasum -a 256 "$OUT/CleanYourHeadUnit-$VERSION.apk"
