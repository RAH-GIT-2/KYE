#!/usr/bin/env bash
# For each version: install its benchmark APK, load ~1,500 sample entries, then scroll Home,
# History, Insights and Settings and print the frame-time summary. Used by speed-check.yml.
set -u
PKG=com.kye.app
read -r W H < <(adb shell wm size | grep -o '[0-9]\+x[0-9]\+' | tail -1 | tr 'x' ' ')
X=$((W / 2)); LOW=$((H * 80 / 100)); HIGH=$((H * 25 / 100))

tap() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb pull /sdcard/ui.xml /tmp/ui.xml >/dev/null 2>&1
  local b
  b=$(grep -o "<node[^>]*\(content-desc\|text\)=\"$1\"[^>]*>" /tmp/ui.xml | head -1 | grep -o 'bounds="[^"]*"' | grep -o '[0-9]\+' | tr '\n' ' ')
  [ -z "$b" ] && { echo "  '$1' not found"; return 1; }
  set -- $b
  adb shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))
}

scroll() {
  for i in 1 2 3 4 5 6; do adb shell input swipe $X $LOW $X $HIGH 250; sleep 0.6; adb shell input swipe $X $HIGH $X $LOW 250; sleep 0.6; done
}

measure() {
  scroll # warm up
  adb shell dumpsys gfxinfo $PKG reset >/dev/null
  scroll
  echo "$1:"
  adb shell dumpsys gfxinfo $PKG | grep -E "Total frames rendered|Janky frames:|50th percentile|90th percentile|99th percentile" | sed 's/^/  /'
}

for r in $1; do
  n=$(echo "$r" | tr '/' '_')
  echo "===== $r ====="
  adb uninstall $PKG >/dev/null 2>&1
  adb install -r apk/$n/*.apk >/dev/null
  adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS || true
  adb logcat -c
  adb shell am start -W -n $PKG/.SeedActivity >/dev/null
  for i in $(seq 1 60); do adb logcat -d | grep -q "KYE-SEED" && break; sleep 1; done
  adb logcat -d | grep "KYE-SEED" | sed 's/.*KYE-SEED: /  /'
  adb shell am start -W -n $PKG/.MainActivity >/dev/null
  sleep 8
  measure "Home"
  tap "History"; sleep 4; measure "History"
  tap "Insights"; sleep 4; measure "Insights"
  tap "Home"; sleep 3; tap "Settings"; sleep 4; measure "Settings"
  adb shell am force-stop $PKG
done
exit 0
