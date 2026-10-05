# ZyvioTV Player

Cross-platform IPTV player for **iPhone, iPad, Android phones, Android tablets, Android TV / boxes and Smart TV clients**.

## Phase 0 — Multiplatform foundation

The product is designed as a multiplatform system from day one.

### Architecture

- **Shared core:** Kotlin Multiplatform
- **Android phone / tablet:** Jetpack Compose
- **Android TV / Google TV / Android boxes:** Jetpack Compose + TV-specific navigation/focus
- **iPhone / iPad:** SwiftUI shell consuming the shared Kotlin Multiplatform core
- **Playback:** Media3 on Android, AVPlayer on iOS
- **Smart TV:** dedicated clients planned for Samsung Tizen, LG webOS and other priority TV platforms, reusing the same backend contracts and account model

### Android baseline

- JDK 17
- Android minSdk 24 (Android 7.0)
- Android target SDK 35 / compile SDK 36

### Shared responsibilities

The shared module is the home for platform-independent logic such as:

- ZyvioTV account/session state
- playlist models
- Xtream Codes domain logic
- M3U/M3U8 parsing
- EPG models
- favorites
- watch history and resume state
- synchronization contracts
- validation and error models

Native player integrations remain platform-specific.

### Brand

The project uses the ZYVIOTV black/red design system. Final production logo resources will use the official **Z monogram** and **ZYVIOTV wordmark** from the flyer brand assets. Temporary launcher resources are used during Phase 0.

### Quality gates

GitHub Actions verifies the shared/Android foundation with:

- unit tests
- Android lint
- debug APK build

Signing-independent iOS CI is active; signed App Store builds still require provisioning and publication credentials.

### Project docs

- [Architecture](docs/ARCHITECTURE.md)
- [Platforms](docs/PLATFORMS.md)
- [Delivery phases](docs/PHASES.md)

## Product rule

A ZyvioTV Player account must be portable between devices. The user should not have to re-enter playlists every time they move from one supported device to another.
