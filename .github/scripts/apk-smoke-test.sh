#!/usr/bin/env bash
# Smoke test of the WellSketch Engineer debug APK on a running emulator.
# Usage: apk-smoke-test.sh <dir with the .apk> <output dir>
# Installs and launches the app, captures the intro and home screens, sends reproducible
# random UI events (monkey, fixed seed) and fails on any crash, ANR or fatal exception.
set -u
APK_DIR="$1"
OUT="$2"
PKG="com.wellsketch.engineer"
mkdir -p "$OUT"

fail() {
  echo "SMOKE TEST FAILED: $*"
  adb logcat -d > "$OUT/logcat.txt" 2>&1
  exit 1
}

APK="$(ls "$APK_DIR"/*.apk | head -n 1)"
[ -f "$APK" ] || fail "no APK in $APK_DIR"
adb install -r "$APK" || fail "install"
adb logcat -c

adb shell am start -W -n "$PKG/.MainActivity" || fail "launch"
sleep 1
adb exec-out screencap -p > "$OUT/1-intro.png"
sleep 4
adb exec-out screencap -p > "$OUT/2-home.png"
adb shell pidof "$PKG" > /dev/null || fail "app not running after launch"

adb shell monkey -p "$PKG" -s 4242 --throttle 120 --pct-syskeys 0 --pct-appswitch 5 -v 3000 > "$OUT/monkey.txt" 2>&1
if grep -E "// CRASH|// NOT RESPONDING|ANR in" "$OUT/monkey.txt"; then
  fail "monkey found a crash or ANR"
fi

adb shell am start -W -n "$PKG/.MainActivity" > /dev/null
sleep 3
adb exec-out screencap -p > "$OUT/3-after-random-use.png"
adb logcat -d > "$OUT/logcat.txt" 2>&1
if grep -E "FATAL EXCEPTION" "$OUT/logcat.txt"; then
  fail "fatal exception in logcat"
fi
echo "SMOKE TEST PASSED"
