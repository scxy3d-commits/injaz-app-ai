#!/usr/bin/env bash
# Installs the APK on the emulator, opens each main screen and saves screenshots and logs.
set -x
PKG=com.injaz.app
OUT=ci-out
mkdir -p "$OUT"
APK=$(ls app/build/outputs/apk/release/*.apk | head -1)
adb install -r "$APK" > "$OUT/install.txt" 2>&1
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS
adb logcat -c
shot() { sleep "${2:-4}"; adb exec-out screencap -p > "$OUT/$1.png"; }
open() { adb shell am start -W -n $PKG/.MainActivity --es route "$1" > /dev/null; }

adb shell am start -W -n $PKG/.MainActivity > "$OUT/start.txt" 2>&1
shot 01-first-launch 9
open "ci/skip-onboarding"; shot 02-home-ar 4
open "course";             shot 03-course-ar 3
open "section/1";          shot 04-section-ar 3
open "lesson/11";          shot 05-lesson-ar 3
open "tools";              shot 06-tools-ar 3
open "focus";              shot 07-focus-ar 3
open "matrix";             shot 08-matrix-ar 3
open "settings";           shot 09-settings-ar 3
open "ci/lang-en";         shot 10-home-en 4
open "lesson/11";          shot 11-lesson-en 3
open "course";             shot 12-course-en 3
open "ci/theme-dark";      shot 13-dark-en 4
open "lesson/60";          shot 14-lesson-dark 3
open "ci/lang-ar";         shot 15-dark-ar 4

# Notifications
adb shell am start -W -n $PKG/.MainActivity --es ci notify > /dev/null
sleep 3
adb shell am start -W -n $PKG/.MainActivity --es ci focus > /dev/null
sleep 3
adb shell dumpsys notification --noredact > "$OUT/notification-dump.txt" 2>&1
grep -c "pkg=$PKG" "$OUT/notification-dump.txt" > "$OUT/notification-count.txt"
adb shell cmd statusbar expand-notifications
shot 16-notifications 3
adb shell cmd statusbar collapse

# Widget
adb shell dumpsys appwidget > "$OUT/appwidget-dump.txt" 2>&1
adb shell am start -W -n $PKG/.WidgetPreviewActivity --es lang ar > /dev/null
shot 17-widget-ar 3
adb shell am start -W -n $PKG/.WidgetPreviewActivity --es lang en > /dev/null
shot 18-widget-en 3
adb shell am broadcast -a android.appwidget.action.APPWIDGET_UPDATE -n $PKG/.InjazWidget > "$OUT/widget-broadcast.txt" 2>&1

# Alarms and crash check
adb shell dumpsys alarm | grep -A6 "$PKG" > "$OUT/alarms.txt" 2>&1
adb logcat -d > "$OUT/logcat-full.txt" 2>&1
grep -E "AndroidRuntime|FATAL EXCEPTION|chromium.*(Uncaught|Error)|INJAZ" "$OUT/logcat-full.txt" > "$OUT/logcat.txt" || true
grep -c "FATAL EXCEPTION" "$OUT/logcat-full.txt" > "$OUT/crash-count.txt" || true
rm -f "$OUT/logcat-full.txt"
adb shell dumpsys package $PKG | grep -E "versionName|targetSdk|minSdk|permission" > "$OUT/package.txt" 2>&1
exit 0
