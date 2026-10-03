#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
if [[ "$(uname -s)" != Darwin ]]; then
    echo "This project must be built on macOS with Xcode. No IPA was generated." >&2
    exit 2
fi
command -v xcodebuild >/dev/null || { echo "Install Xcode and select it with xcode-select." >&2; exit 2; }
command -v pod >/dev/null || { echo "Install CocoaPods, then run this script again." >&2; exit 2; }
cd "$project_dir"
mode="${1:-simulator}"
team_id="${2:-}"
case "$mode" in
    simulator|test) ;;
    device|archive)
        if [[ ! "$team_id" =~ ^[A-Z0-9]{10}$ ]]; then
            echo "Usage: bash tools/build_macos.sh $mode APPLE_TEAM_ID (10 characters). Sign in to Xcode first." >&2
            exit 2
        fi ;;
    *) echo "Usage: bash tools/build_macos.sh [simulator|test|device|archive] [APPLE_TEAM_ID]" >&2; exit 2 ;;
esac
if [[ "$mode" == test && -z "${SIMULATOR_ID:-}" ]]; then
    echo "Set SIMULATOR_ID to an available iPhone simulator UUID (xcrun simctl list devices available)." >&2
    exit 2
fi
pod install
common=(-workspace GdanskCaseMonitor.xcworkspace -scheme GdanskCaseMonitor -derivedDataPath build/DerivedData)
case "$mode" in
    simulator)
        xcodebuild "${common[@]}" -configuration Debug -sdk iphonesimulator \
            -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build ;;
    test)
        xcodebuild "${common[@]}" -configuration Debug -sdk iphonesimulator \
            -destination "platform=iOS Simulator,id=$SIMULATOR_ID" CODE_SIGNING_ALLOWED=NO test ;;
    device)
        xcodebuild "${common[@]}" -configuration Debug -destination 'generic/platform=iOS' \
            CODE_SIGN_STYLE=Automatic "DEVELOPMENT_TEAM=$team_id" -allowProvisioningUpdates build ;;
    archive)
        xcodebuild "${common[@]}" -configuration Release -destination 'generic/platform=iOS' \
            -archivePath build/GdanskCaseMonitor.xcarchive CODE_SIGN_STYLE=Automatic \
            "DEVELOPMENT_TEAM=$team_id" -allowProvisioningUpdates archive ;;
esac
echo "Completed: $mode. Archive/export and device installation still depend on your Apple signing team."
