#!/usr/bin/env bash
set -euo pipefail
if ! command -v gradle >/dev/null 2>&1; then
  echo "Gradle not found. Install Gradle 8.13 or open the project in Android Studio."
  exit 2
fi
if [[ -z "${ANDROID_HOME:-}" && -z "${ANDROID_SDK_ROOT:-}" ]]; then
  echo "ANDROID_HOME/ANDROID_SDK_ROOT is not set."
  exit 2
fi
gradle :app:assembleDebug --stacktrace
APK="app/build/outputs/apk/debug/app-debug.apk"
[[ -f "$APK" ]] || { echo "APK was not produced"; exit 3; }
echo "Built: $APK"
