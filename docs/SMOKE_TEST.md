# Phase 18.5 — Real-device smoke test

## Objective

Validate the current application on a real Android device before starting the real Xtream/M3U integration phases.

This smoke test is intentionally honest about the current product state: it verifies the app shell, account flow, navigation, responsive layouts and native player surface. It does **not** claim a complete end-to-end IPTV provider flow yet, because the production Xtream network adapter and M3U loader are still pending.

## Test build

The `Android Smoke APK` workflow:

1. runs Android/shared unit tests
2. builds the debug APK
3. generates a SHA-256 checksum
4. uploads both files as the `zyviotv-player-smoke-apk` GitHub Actions artifact

The artifact is retained for 14 days.

## Real-device smoke checklist

Test on at least one Android phone first.

### Launch and account

- app installs successfully
- splash launches without crash
- registration screen opens
- sign-in screen opens
- invalid credentials produce a controlled error
- valid ZyvioTV account can sign in
- logout returns to authentication

### Navigation

- Accueil opens
- TV opens
- Films opens
- Séries opens
- Plus / account opens
- back navigation behaves normally
- no frozen screen after repeated tab switching

### Layout

- portrait phone layout is readable
- landscape does not overlap critical controls
- scrolling remains smooth
- no clipped buttons/text on a narrow screen

### Playback surface

- player screen/surface can be opened from any currently wired demo path
- background/foreground does not crash the app
- rotating the device does not permanently break navigation

### Security check

While testing, do not paste provider credentials into screenshots, issue descriptions or logs.

If a URL containing IPTV credentials appears in application logs, stop the test and report the exact screen/action without sharing the secret itself.

## Current expected limitation

A real Xtream provider cannot yet be validated end-to-end because the Android Xtream HTTP adapter is not implemented and the Live / Films / Séries screens are not yet fully connected to provider data.

That becomes the next engineering milestone after this smoke test: **Phase 19 — real Xtream Codes integration**.
