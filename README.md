# ZyvioTV Player

Native Android IPTV player for **mobile, tablet and Android TV / Android boxes**.

## Phase 0 — Foundation

This branch establishes the Android foundation before any IPTV provider logic is added.

### Stack

- Kotlin
- Jetpack Compose
- Material 3
- Android Navigation Compose
- JDK 17
- Android minSdk 24 (Android 7.0)
- Android target / compile SDK 35

### Device strategy

- Mobile: touch-first UI with bottom navigation
- Tablet: adaptive navigation rail
- Android TV / boxes: TV detection and D-pad-ready navigation foundation

### Brand

The app uses the ZYVIOTV black/red design tokens. Final production logo resources will use the official ZYVIOTV monogram and wordmark assets from the brand kit; temporary launcher resources are used during Phase 0.

### Quality gates

GitHub Actions verifies:

- unit tests
- Android lint
- debug APK build

### Project docs

- [Architecture](docs/ARCHITECTURE.md)
- [Delivery phases](docs/PHASES.md)

## Roadmap

Phase 1 will add ZyvioTV Player account authentication. IPTV credentials and playlists will be linked to the user's app account in later phases so the same account can restore them on another device.
