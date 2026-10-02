#!/usr/bin/env bash
# Store screenshots for F-Droid (docs/RELEASING.md «Screenshots»).
#
#   scripts/screenshots.sh            # English and Spanish
#   scripts/screenshots.sh es         # one language
#
# Needs: the Android SDK with the emulator and its arm64 AOSP image
# (sdkmanager "emulator" "system-images;android-36;default;arm64-v8a"), a JDK 17–23 in
# JAVA_HOME, Node (npx tsx) and python3. The AVD «bw-shots» is created if missing.
# Boots the emulator if none is running, installs a debug build, loads the web
# demo dataset (scripts/demo-csv.ts) through the debug-only DemoSeedReceiver,
# then walks the screens and saves them into
# android/fastlane/metadata/android/<locale>/images/phoneScreenshots/.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
ADB="$SDK/platform-tools/adb"
PKG=com.blackwatermacros.app
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

# --- emulator --------------------------------------------------------------
# The AVD «bw-shots» (Pixel-sized, AOSP image without Google apps) is created on first use.
IMAGE="system-images/android-36/default/arm64-v8a"
if ! ANDROID_SDK_ROOT="$SDK" "$SDK/emulator/emulator" -list-avds | grep -qx bw-shots; then
  [ -d "$SDK/$IMAGE" ] || { echo "Install the image first: sdkmanager \"emulator\" \"${IMAGE//\//;}\"" >&2; exit 1; }
  mkdir -p "$HOME/.android/avd/bw-shots.avd"
  printf 'avd.ini.encoding=UTF-8\npath=%s\ntarget=android-36\n' "$HOME/.android/avd/bw-shots.avd" >"$HOME/.android/avd/bw-shots.ini"
  cat >"$HOME/.android/avd/bw-shots.avd/config.ini" <<CFG
AvdId=bw-shots
avd.ini.displayname=bw-shots
abi.type=arm64-v8a
hw.cpu.arch=arm64
image.sysdir.1=$IMAGE/
tag.id=default
target=android-36
hw.lcd.width=1080
hw.lcd.height=2400
hw.lcd.density=420
hw.ramSize=4096
disk.dataPartition.size=6G
hw.keyboard=yes
hw.gpu.enabled=yes
hw.gpu.mode=swiftshader_indirect
showDeviceFrame=no
CFG
fi
if ! "$ADB" get-state >/dev/null 2>&1; then
  echo "Booting the emulator (bw-shots)…"
  ANDROID_SDK_ROOT="$SDK" nohup "$SDK/emulator/emulator" -avd bw-shots -no-window -no-audio \
    -no-boot-anim -no-snapshot -gpu swiftshader_indirect >"$WORK/emulator.log" 2>&1 &
  "$ADB" wait-for-device
  until [ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do sleep 3; done
fi

# Clean status bar: 12:00, full battery and signal, no notifications.
"$ADB" shell settings put global sysui_demo_allowed 1
demo() { "$ADB" shell am broadcast -a com.android.systemui.demo -e command "$@" >/dev/null; }
demo enter
demo clock -e hhmm 1200
demo battery -e level 100 -e plugged false
demo network -e wifi show -e level 4 -e mobile hide
demo notifications -e visible false

# --- app ---------------------------------------------------------------------
(cd "$ROOT/android" && ./gradlew -q :app:assembleDebug)
"$ADB" install -r "$ROOT/android/app/build/outputs/apk/debug/app-debug.apk" >/dev/null

# Label of a text in the app's language: web dictionaries first, then Android's strings.xml.
label() { # lang key   (key = json.path or android_name)
  python3 - "$ROOT" "$1" "$2" <<'PY'
import json, re, sys
root, lang, key = sys.argv[1:]
if "." in key:
    node = json.load(open(f"{root}/src/i18n/{lang}.json"))
    for part in key.split("."):
        node = node[part]
    print(node)
else:
    res = "values" if lang == "en" else f"values-{lang}"
    xml = open(f"{root}/android/app/src/main/res/{res}/strings.xml", encoding="utf-8").read()
    print(re.search(rf'<string name="{key}">(.*?)</string>', xml).group(1).replace("\\'", "'"))
PY
}

# Taps the first element whose text or content description is exactly $1.
tap() {
  "$ADB" shell uiautomator dump /sdcard/ui.xml >/dev/null
  local xy
  xy="$("$ADB" shell cat /sdcard/ui.xml | python3 -c '
import re, sys, html
target = sys.argv[1]
for m in re.finditer(r"<node [^>]*>", sys.stdin.read()):
    node = m.group(0)
    texts = [html.unescape(v) for v in re.findall(r"(?:text|content-desc)=\"([^\"]*)\"", node)]
    if target in texts:
        x1, y1, x2, y2 = map(int, re.search(r"bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"", node).groups())
        print((x1 + x2) // 2, (y1 + y2) // 2)
        break
' "$1")"
  [ -n "$xy" ] || { echo "Not on screen: «$1»" >&2; exit 1; }
  "$ADB" shell input tap $xy
  sleep 2
}

shot() { # dir name
  sleep 1
  "$ADB" exec-out screencap -p >"$1/$2.png"
  echo "  $2"
}

back() { "$ADB" shell input keyevent KEYCODE_BACK; sleep 1.5; }

run() { # lang locale-folder
  local lang=$1 out="$ROOT/android/fastlane/metadata/android/$2/images/phoneScreenshots"
  echo "Screenshots in $lang → ${out#"$ROOT"/}"
  rm -rf "$out" && mkdir -p "$out"

  "$ADB" shell pm clear "$PKG" >/dev/null
  "$ADB" shell cmd locale set-app-locales "$PKG" --locales "$lang"
  (cd "$ROOT" && npx tsx scripts/demo-csv.ts "$WORK/seed-$lang" "$lang" >/dev/null)
  "$ADB" push "$WORK/seed-$lang/meals.csv" "$WORK/seed-$lang/weights.csv" /data/local/tmp/ >/dev/null
  "$ADB" shell "run-as $PKG mkdir -p files/seed && run-as $PKG cp /data/local/tmp/meals.csv /data/local/tmp/weights.csv files/seed/"
  "$ADB" shell am broadcast -n "$PKG/.DemoSeedReceiver" >/dev/null
  sleep 3
  "$ADB" shell am start -W -n "$PKG/.MainActivity" >/dev/null
  sleep 4

  shot "$out" 1_meals
  tap "$(label "$lang" meal_add)";               shot "$out" 2_add_food
  tap "$(label "$lang" photo.title)";            shot "$out" 3_photo_or_text
  back
  tap "$(label "$lang" meal.edit)";              shot "$out" 4_meal_form
  back
  tap "$(label "$lang" nav.progreso)";           shot "$out" 5_progress
  tap "$(label "$lang" nav.coach)";              shot "$out" 6_coach
  tap "$(label "$lang" nav.ajustes)"
  tap "$(label "$lang" perfil.title)";           shot "$out" 7_profile
}

langs=("${@:-en es}")
for lang in ${langs[@]}; do
  case $lang in
    en) run en en-US ;;
    es) run es es-ES ;;
    *) echo "Unknown language: $lang (en, es)" >&2; exit 1 ;;
  esac
done
demo exit
echo "Done. Review the images before committing them."
