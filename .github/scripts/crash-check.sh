#!/usr/bin/env bash
# Installs KYE, opens it and taps the main places; prints any crash. Used by crash-check.yml.
set -u
PKG=com.kye.app
mkdir -p out
adb install -r apk/*.apk
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS || true
adb logcat -c

# Tap the centre of the first on-screen element whose content-desc or text matches $1.
tap() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb pull /sdcard/ui.xml out/ui.xml >/dev/null 2>&1
  local b
  b=$(grep -o "<node[^>]*\(content-desc\|text\)=\"$1\"[^>]*>" out/ui.xml | head -1 | grep -o 'bounds="[^"]*"' | grep -o '[0-9]\+' | tr '\n' ' ')
  if [ -z "$b" ]; then echo "STEP: '$1' not found on screen"; return 1; fi
  set -- $b
  adb shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))
}

shot() { adb exec-out screencap -p > "out/$1.png"; }

alive() {
  if adb shell pidof $PKG >/dev/null; then echo "STEP: $1 - app running"; else echo "STEP: $1 - APP NOT RUNNING"; fi
}

adb shell am start -W -n $PKG/.MainActivity
sleep 8; shot 1-home; alive "opened"

tap "Settings"; sleep 6; shot 2-settings; alive "Settings icon"
tap "Categories" && { sleep 4; alive "Categories"; adb shell input keyevent KEYCODE_BACK; sleep 3; }
for i in 1 2 3 4; do adb shell input swipe 540 1800 540 600 300; sleep 1; done
shot 2b-settings-bottom
tap "Ocean" && { sleep 3; alive "Ocean colours"; }
tap "Wallpaper" && { sleep 3; alive "Wallpaper colours"; }
tap "Dark" && { sleep 3; alive "Dark mode"; shot 2c-dark; }
tap "Violet" && { sleep 3; alive "Violet colours"; }
tap "System" && { sleep 2; }
adb shell input keyevent KEYCODE_BACK; sleep 3; alive "back from Settings"

tap "History"; sleep 4; shot 3-history; alive "History tab"
tap "Insights"; sleep 4; shot 4-insights; alive "Insights tab"
tap "Recurring"; sleep 4; alive "Recurring tab"
tap "Home"; sleep 4; alive "Home tab"
tap "Add manually" || tap "Add entry"; sleep 4; shot 5-add; alive "+ form"
adb shell input keyevent KEYCODE_BACK; sleep 2
adb shell input keyevent KEYCODE_BACK; sleep 3; alive "back from form"

adb logcat -d > out/logcat.txt
echo "===== CRASHES ====="
grep -n -A40 "FATAL EXCEPTION" out/logcat.txt | head -150 || echo "no FATAL EXCEPTION in the log"
exit 0
