# Architecture — ZyvioTV Player

## Product goal

ZyvioTV Player is a multi-device product, not an Android-only app.

Supported targets are planned as:

- iPhone
- iPad
- Android phone
- Android tablet
- Android TV / Google TV / Android boxes
- Smart TV clients, beginning with priority platforms such as Samsung Tizen and LG webOS

## Architectural principle

The project uses **Kotlin Multiplatform** for business logic that should behave identically across mobile platforms.

Native presentation and playback remain platform-specific where that gives better stability and device integration.

### Shared core

The `shared` module owns platform-independent logic:

- account/session domain models
- playlist references
- provider-neutral IPTV models
- M3U/M3U8 parsing
- Xtream Codes domain logic
- EPG models
- favorites
- history
- resume/progress state
- synchronization contracts
- validation
- error taxonomy

It must not contain Android UI, SwiftUI, Media3, AVPlayer or platform secrets.

### Android client

- Kotlin
- Jetpack Compose
- adaptive phone/tablet layouts
- Android TV / Google TV / box-specific focus and D-pad UX
- Media3 for playback in the player phase

### Apple client

- SwiftUI for iPhone/iPad
- AVPlayer for playback in the player phase
- consumes the Kotlin Multiplatform shared core through the generated Apple framework

### Smart TV clients

Samsung Tizen and LG webOS do not run the Android application.

They will be separate TV clients that reuse:

- the same ZyvioTV account
- the same backend API contracts
- the same playlist/provider semantics
- the same sync model
- the same design system

Playback implementation will be native to each TV platform.

## Account portability

The user's application account is the stable identity across devices.

Provider playlists are associated with that account so that, after authentication on another device, the app can restore authorized playlist configuration without asking the user to manually type it again.

## Security baseline

- Never log IPTV credentials or full credential-bearing URLs.
- Never ship Supabase service-role credentials in any client.
- Use user-scoped authorization for cloud data.
- Encrypt sensitive provider credentials at rest on the server.
- Use secure platform storage for local tokens.
- Support device revocation.
- Keep player diagnostics free of credentials.
