#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
gradle_cache="${GRADLE_USER_HOME:-$HOME/.gradle}"
mkdir -p "$project_dir/.secrets"
chmod 700 "$project_dir/.secrets"
if [[ -f "$project_dir/.secrets/release-signing.p12" && ! -f "$project_dir/.secrets/release-storepass" ]]; then
    echo "Release key exists but its password file is missing. Restore the original password file." >&2
    exit 2
fi
if [[ ! -f "$project_dir/.secrets/release-storepass" ]]; then
    openssl rand -hex -out "$project_dir/.secrets/release-storepass" 32
fi
chmod 600 "$project_dir/.secrets/release-storepass"

docker run --rm \
    -e GRADLE_USER_HOME=/gradle-cache \
    -v "$project_dir:/workspace" \
    -v "$gradle_cache:/gradle-cache" \
    -v "$project_dir/tools/aapt2_qemu.sh:/usr/local/bin/aapt2:ro" \
    -w /workspace ghcr.io/cirruslabs/android-sdk:36 sh -ec '
        if [ "$(uname -m)" = aarch64 ]; then
            apt-get update -qq
            apt-get install -y -qq qemu-user libc6-amd64-cross libstdc++6-amd64-cross
            aapt_override=/usr/local/bin/aapt2
        else
            aapt_override=/opt/android-sdk-linux/build-tools/36.0.0/aapt2
        fi
        gradle_bin=$(find /gradle-cache/wrapper/dists -type f -path "*/gradle-8.13/bin/gradle" -print -quit)
        if [ -z "$gradle_bin" ]; then
            echo "Gradle 8.13 must be available in the local Gradle wrapper cache."
            exit 2
        fi
        if [ ! -f /workspace/.secrets/release-signing.p12 ]; then
            keytool -genkeypair -noprompt \
                -keystore /workspace/.secrets/release-signing.p12 -storetype PKCS12 \
                -storepass:file /workspace/.secrets/release-storepass \
                -keypass:file /workspace/.secrets/release-storepass \
                -alias gdansk-case-monitor -keyalg RSA -keysize 3072 \
                -sigalg SHA256withRSA -validity 10000 -dname "CN=GdanskCaseMonitor"
        fi
        chmod 600 /workspace/.secrets/release-signing.p12
        "$gradle_bin" :app:testReleaseUnitTest :app:lintRelease :app:assembleRelease --no-daemon --console=plain \
            -Pandroid.aapt2FromMavenOverride="$aapt_override"
        /opt/android-sdk-linux/build-tools/36.0.0/apksigner verify --verbose --print-certs \
            app/build/outputs/apk/release/app-release.apk
        chown 1000:1000 /workspace/.secrets/release-signing.p12
        chmod 600 /workspace/.secrets/release-signing.p12
    '
echo "Built: $project_dir/app/build/outputs/apk/release/app-release.apk"
