#!/usr/bin/env bash
# For each release: install, open Settings (a long list that exists without any entries) and
# Home, scroll a few times, and print the frame-time summary. Used by speed-check.yml.
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

summary() {
  adb shell dumpsys gfxinfo $PKG | grep -E "Total frames rendered|Janky frames|50th percentile|90th percentile|95th percentile|99th percentile" | sed 's/^/  /'
}

for t in $1; do
  echo "===== $t ====="
  adb uninstall $PKG >/dev/null 2>&1
  adb install -r apk/$t/*.apk >/dev/null
  adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS || true
  adb shell am start -W -n $PKG/.MainActivity >/dev/null
  sleep 8
  # Warm up once, then measure.
  scroll
  echo "Home:"
  adb shell dumpsys gfxinfo $PKG reset >/dev/null
  scroll
  summary
  tap "Settings"; sleep 4
  scroll
  echo "Settings:"
  adb shell dumpsys gfxinfo $PKG reset >/dev/null
  scroll
  summary
  adb shell am force-stop $PKG
done
exit 0
