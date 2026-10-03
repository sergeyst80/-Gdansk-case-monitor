# Gdańsk Case Monitor

[English](README.md) · [Русский](README_RU.md)

An application for checking case status for multiple accounts on
`https://klient.gdansk.uw.gov.pl/`.

This repository contains two versions:

- **Android 8.0+** — source project in the repository root and a signed
  **1.2.1** APK in [release/](release/).
- **iPhone / iPad, iOS 17+** — a separate source project in [ios/](ios/).
  Prepared for building on a Mac; compilation has not yet been verified,
  and no installable IPA is available.

## Android: installation

A standalone Android application supporting multiple portal accounts.

Current release: **1.2.1**, requiring Android 8.0 or later. Download:
[release/GdanskCaseMonitor-1.2.1.apk](release/GdanskCaseMonitor-1.2.1.apk).
Checksums: [release/SHA256SUMS](release/SHA256SUMS).
Install over the previous release without uninstalling the application:
the signing key and encrypted data format have been preserved.

## iPhone / iOS: project and build

The [ios/](ios/) folder contains an Xcode project, CocoaPods configuration,
on-device ML Kit translation, 12 interface languages, Keychain storage,
isolated WebView sessions, tests, and Mac build commands.
Required tools, build instructions, and the user guide (currently in Russian):
[ios/README.md](ios/README.md).

Nine structural checks pass on Linux. Swift code has not yet been compiled,
XCTest has not been run, and no signed IPA is available. Actions includes
a manual **Check iPhone project (unsigned simulator)** workflow for macOS.
It does not use Apple signing keys or export an IPA. To install on an iPhone,
select your signing team in Xcode; no Apple Account password is stored in the project.

The following sections cover guides, implementation, and builds for the
**Android version**. iOS-specific details are documented in its own README.

## Android: user guides

- [English — illustrated guide for version 1.2.1](docs/USER_GUIDE_EN.md)
- [Russian — illustrated guide for version 1.2.1](docs/USER_GUIDE_RU.md)
- PDF: [English](docs/USER_GUIDE_EN.pdf) · [Russian](docs/USER_GUIDE_RU.pdf)

Re-exporting the PDFs requires Chromium and `markdown-it` 14.1.0:
`node tools/export_guides.cjs /path/to/node_modules/markdown-it`.
Export runs locally without connecting to the portal or using secrets.

## Android: features

- Add, edit, and delete multiple users.
- View all users and their latest case details on one screen.
- Refresh all users or check an individual user.
- Logins, passwords, and stored details are encrypted with AES-256-GCM;
  the key is held in Android Keystore.
- WebView cookies are cleared between accounts.
- Periodic background checks use AndroidX WorkManager rather than a
  continuously running foreground service.
- System notifications report changes in case data.
- Checks run only when a network connection is available.
- CAPTCHA and MFA are not bypassed.

## Android: why WorkManager

On Android 15+, a `dataSync` foreground service is limited to a total of
six hours per 24 hours. WorkManager is used for periodic monitoring instead.
The default interval is approximately 30 minutes; actual timing may shift
because of Android scheduling and power-saving policies.

## Android: build

- Android Gradle Plugin: 8.13.2
- Gradle: 8.13
- compileSdk: 36
- minSdk: 26
- WorkManager: 2.12.0
- Java: 17

Open the repository root in Android Studio and select `Build > Build APK(s)`.

From the command line, with Android SDK and Gradle installed:

```bash
gradle :app:assembleDebug
```

Output:

`app/build/outputs/apk/debug/app-debug.apk`

The debug APK build was verified on October 2, 2026, using the
`ghcr.io/cirruslabs/android-sdk:36` Docker image on ARM64. The x86-64 AAPT2
binary ran through `qemu-x86_64`, with `libc6-amd64-cross` and
`libstdc++6-amd64-cross` installed inside the container. The wrapper must
be named `aapt2` and supplied using
`-Pandroid.aapt2FromMavenOverride=/usr/local/bin/aapt2`.
The APK signature was checked with `apksigner verify`; this verification
did not include running the application on a physical phone.

## Android: login and data checks

On October 2, 2026, `app/src/main/assets/portal_adapter.js` was tested
against the live portal using local Chromium: login succeeded and six
non-empty fields were retrieved. Twelve browser checks also passed for
HTML/Vaadin, loading waits, rejected login, MFA, and Shadow DOM extraction.
These are Chromium adapter checks, not Android WebView tests on a phone.

Local login testing uses `tools/test_portal_login.py` with Chromium,
Python, and `websocket-client`. The test reads `.secrets/portal-test.env`,
sends credentials only to the HTTPS host `klient.gdansk.uw.gov.pl`, and
prints only processing stages and field counts. The temporary browser
profile is deleted after testing. The `.secrets/` directory is excluded
from Git; do not include it in published archives.

## Changelog

Android release history and iOS project preparation are documented in
[CHANGELOG.md](CHANGELOG.md) (currently in Russian).

Other platform projects are stored separately in the local sibling folder
`../gdansk_case_monitor_apps/`; they are not included in this repository.
