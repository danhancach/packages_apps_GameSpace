#!/usr/bin/env bash
# Keo jar platform tu out/ (ROM tree) vao system_libs/ + tao SDK Gradle
# (android.jar SDK + class @hide tu framework.jar).
# Mac dinh: OUT = <android_root>/out ; SDK = $ANDROID_HOME hoac ~/Android/Sdk
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
OUT_DIR="${1:-$ROOT/../../../out}"
DEST="$ROOT/system_libs"
INTER="$OUT_DIR/soong/.intermediates"
SDK_SRC="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
LOCAL_SDK="$ROOT/.gradle-sdk"
COMPILE_SDK=36

if [[ ! -d "$INTER" ]]; then
  echo "Loi: khong tim thay $INTER" >&2
  echo "Chay: $0 /path/to/out" >&2
  exit 1
fi

# name|relative-path-under-intermediates (uu tien jar day du class)
declare -a JARS=(
  "framework.jar|frameworks/base/framework-minus-apex/android_common/combined/framework.jar"
  "SettingsLib.jar|frameworks/base/packages/SettingsLib/SettingsLib/android_common/javac/SettingsLib.jar"
  "SettingsLibSettingsTheme.jar|frameworks/base/packages/SettingsLib/SettingsTheme/SettingsLibSettingsTheme/android_common/kotlin/SettingsLibSettingsTheme.jar"
  "SettingsLibCollapsingToolbarBaseActivity.jar|frameworks/base/packages/SettingsLib/CollapsingToolbarBaseActivity/SettingsLibCollapsingToolbarBaseActivity/android_common/javac/SettingsLibCollapsingToolbarBaseActivity.jar"
  "SettingsLibDisplayUtils.jar|frameworks/base/packages/SettingsLib/DisplayUtils/SettingsLibDisplayUtils/android_common/javac/SettingsLibDisplayUtils.jar"
  "SettingsLibMainSwitchPreference.jar|frameworks/base/packages/SettingsLib/MainSwitchPreference/SettingsLibMainSwitchPreference/android_common/javac/SettingsLibMainSwitchPreference.jar"
  "SettingsLibLayoutPreference.jar|frameworks/base/packages/SettingsLib/LayoutPreference/SettingsLibLayoutPreference/android_common/javac/SettingsLibLayoutPreference.jar"
  "SettingsLibSliderPreference.jar|frameworks/base/packages/SettingsLib/SliderPreference/SettingsLibSliderPreference/android_common/javac/SettingsLibSliderPreference.jar"
  "LineagePreferenceLib.jar|lineage-sdk/packages/LineagePreferenceLib/LineagePreferenceLib/android_common/javac/LineagePreferenceLib.jar"
  "org.lineageos.platform.internal.jar|lineage-sdk/org.lineageos.platform.internal/android_common/javac/org.lineageos.platform.internal.jar"
  "ax_platform_hooks.jar|vendor/extras/sdk/ax_platform_hooks/ax_platform_hooks/android_common/javac/ax_platform_hooks.jar"
  "ax_compose.jar|vendor/extras/sdk/ax_compose/ax_compose/android_common/kotlin/ax_compose.jar"
  "PlatformComposeCore.jar|frameworks/base/packages/SystemUI/compose/core/PlatformComposeCore/android_common/kotlin/PlatformComposeCore.jar"
  "PlatformComposeSceneTransitionLayout.jar|frameworks/base/packages/SystemUI/compose/scene/PlatformComposeSceneTransitionLayout/android_common/kotlin/PlatformComposeSceneTransitionLayout.jar"
)

mkdir -p "$DEST"
missing=0
for entry in "${JARS[@]}"; do
  name="${entry%%|*}"
  rel="${entry#*|}"
  src="$INTER/$rel"
  if [[ ! -f "$src" ]]; then
    echo "THIEU: $name ($src)" >&2
    missing=1
    continue
  fi
  install -m 0644 "$src" "$DEST/$name"
  echo "OK  $name ($(du -h "$DEST/$name" | cut -f1))"
done

if (( missing )); then
  echo "Mot so jar thieu — build ROM/module lien quan roi chay lai." >&2
  exit 1
fi

echo "Xong: $DEST ($(ls -1 "$DEST" | wc -l) jar)"

# --- SDK Gradle: merge @hide class vao android.jar, giu resources.arsc ---
if [[ ! -d "$SDK_SRC/platforms/android-$COMPILE_SDK" ]]; then
  echo "Bo qua SDK Gradle: khong co $SDK_SRC/platforms/android-$COMPILE_SDK" >&2
  exit 0
fi

echo "Tao .gradle-sdk (merge framework.jar -> android.jar)..."
rm -rf "$LOCAL_SDK"
mkdir -p "$LOCAL_SDK/platforms"
for name in build-tools platform-tools cmdline-tools ndk ndk-bundle tools licenses system-images emulator; do
  [[ -e "$SDK_SRC/$name" ]] && ln -sfn "$SDK_SRC/$name" "$LOCAL_SDK/$name"
done
for d in "$SDK_SRC"/platforms/*; do
  [[ -d "$d" ]] || continue
  name=$(basename "$d")
  if [[ "$name" == "android-$COMPILE_SDK" ]]; then
    cp -a "$d" "$LOCAL_SDK/platforms/$name"
  else
    ln -sfn "$d" "$LOCAL_SDK/platforms/$name"
  fi
done

PLATFORM_JAR="$LOCAL_SDK/platforms/android-$COMPILE_SDK/android.jar"
FW="$DEST/framework.jar"
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
(cd "$TMP" && jar xf "$FW")
(cd "$TMP" && find . -name '*.class' -print0 | xargs -0 jar uf "$PLATFORM_JAR")
printf 'sdk.dir=%s\n' "$LOCAL_SDK" > "$ROOT/local.properties"
echo "OK  merged android.jar ($(du -h "$PLATFORM_JAR" | cut -f1))"
echo "OK  local.properties -> $LOCAL_SDK"
echo "Tiep: ./gradlew :app:assembleDebug"
