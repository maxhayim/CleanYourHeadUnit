#!/usr/bin/env bash
# Checks the Android 11+ Wireless debugging code (pairing, TLS) against Google's own adb.
# Runs a stand-in unit (shared/test-java/.../FakeWirelessAdbd.java) on 127.0.0.1, then:
#   1. this project's client pairs with it and runs a shell command (and a wrong code is refused);
#   2. the real `adb pair`, `adb connect` and `adb shell` do the same against it.
# If Google's adb accepts the stand-in, the SPAKE2, key derivation, encryption and TLS framing
# match adbd's bit for bit (the client and the stand-in share that code).
# Needs JDK 25+ (TLS keying-material export) as JAVA27_HOME or on PATH, and adb (platform-tools).
# Touches nothing but 127.0.0.1; disconnects at the end.
set -euo pipefail
cd "$(dirname "$0")/.."
JAVA_BIN="${JAVA27_HOME:-/opt/homebrew/opt/openjdk@27}/bin"
ADB="${ADB:-$(command -v adb || echo /opt/homebrew/share/android-commandlinetools/platform-tools/adb)}"
CODE="${CODE:-$(printf '%06d' $((RANDOM % 1000000)))}"
OUT="$(mktemp -d)"
trap 'kill ${PID:-0} 2>/dev/null || true; rm -rf "$OUT"' EXIT
"$JAVA_BIN/javac" -d "$OUT/classes" -nowarn \
  shared/java/io/github/maxhayim/cleanyourheadunit/adb/*.java \
  shared/test-java/io/github/maxhayim/cleanyourheadunit/adb/FakeAdbd.java \
  shared/test-java/io/github/maxhayim/cleanyourheadunit/adb/FakeWirelessAdbd.java
MAIN=io.github.maxhayim.cleanyourheadunit.adb.FakeWirelessAdbd
echo "== 1. this project's client"
"$JAVA_BIN/java" -cp "$OUT/classes" $MAIN selftest "$CODE"
echo "== 2. Google's adb ($("$ADB" version | sed -n 2p))"
"$JAVA_BIN/java" -cp "$OUT/classes" $MAIN serve "$CODE" "$OUT/ports" > "$OUT/server.log" 2>&1 &
PID=$!
for _ in $(seq 50); do [ -s "$OUT/ports" ] && break; sleep 0.1; done
read -r PAIR CONN < "$OUT/ports"
"$ADB" pair "127.0.0.1:$PAIR" "$CODE"
"$ADB" connect "127.0.0.1:$CONN"
sleep 1
REPLY=$("$ADB" -s "127.0.0.1:$CONN" shell echo adb-ok | tr -d '\r')
"$ADB" disconnect "127.0.0.1:$CONN" >/dev/null
echo "adb shell said: $REPLY"
cat "$OUT/server.log"
[ "$REPLY" = "adb-ok" ] && echo "PASS: Google's adb paired and connected over TLS" || { echo "FAIL"; exit 1; }
