# Platform strategy

## Tier 1 — Mobile and tablets

### Android

- Android phone
- Android tablet
- Jetpack Compose
- Media3 player
- minimum Android 7.0 during Phase 0

### Apple

- iPhone
- iPad
- SwiftUI
- AVPlayer
- Kotlin Multiplatform shared core

## Tier 2 — Android television

- Android TV
- Google TV
- Android boxes
- D-pad navigation
- focus-first 10-foot UI
- Media3 playback

## Tier 3 — Smart TV

Smart TVs are not one platform.

### Samsung

Samsung televisions use Tizen. A dedicated Tizen client is required.

### LG

LG televisions use webOS. A dedicated webOS client is required.

### Other televisions

Other TV platforms will be prioritized from real user/device demand.

## Shared product contract

Every client must use the same:

- ZyvioTV account identity
- device registration model
- playlist references
- sync semantics
- watch-history semantics
- favorites semantics
- EPG semantics
- security rules

This keeps the experience consistent even though playback and UI are implemented natively per platform.
