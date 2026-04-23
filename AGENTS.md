# AGENTS.md

## Project shape
- Single-module Android app (`:app`) built with Gradle 9.1.0, AGP 8.2.0, Kotlin 1.9.22.
- App entry point is `app/src/main/java/com/beckersuite/box/MainActivity.kt`.
- Runtime flow is WebView-first: `MainActivity` loads `WEB_APP_URL` and injects `BleBridge` as `window.AndroidBle`.

## Important code paths
- `MainActivity` enables JavaScript + DOM storage and keeps navigation inside the WebView.
- `BleBridge` is the native BLE layer; it scans for `SERVICE_UUID` and writes to `CHAR_UUID`.
- JavaScript callbacks are hardcoded as `__bleOnConnected`, `__bleOnDisconnected`, and `__bleOnError`.

## Build / test commands
- `./gradlew assembleDebug` builds the app.
- `./gradlew test` runs local JVM tests.
- `./gradlew connectedAndroidTest` runs device/emulator tests.

## Android conventions in this repo
- `compileSdk` / `targetSdk` are 35; `minSdk` is 26; Java/Kotlin bytecode target is 11.
- Permissions are requested at startup for `BLUETOOTH_SCAN` and `BLUETOOTH_CONNECT`; manifest also declares `INTERNET`, `usesCleartextTraffic=true`, and `android.hardware.bluetooth_le`.
- The app is portrait-only and uses a no-action-bar launcher activity.

## Repo-specific gotchas
- `MainActivity` expects `R.id.webview`, so `app/src/main/res/layout/activity_main.xml` must provide that view or the activity must be updated with the layout.
- `app/src/main/res/values/themes.xml` defines `Theme.BeckerBoxRemote`, but the manifest currently points at `@style/Theme.AppCompat.NoActionBar`.
- The default instrumented test still asserts `com.beckersuite.box`; update it if package naming changes.

## Editing guidance
- Treat BLE UUIDs and the Web app URL as integration points: change them together with the paired server/peripheral.
- Prefer small, coordinated changes across `MainActivity`, `BleBridge`, manifest, and layout when altering startup or BLE behavior.
- There is no repo README or existing agent guidance file in the current tree, so this document is the source of truth for AI contributors.

