# Architecture — ZyvioTV Player

## Goals

- One Android codebase for phone, tablet and Android TV / Android boxes.
- Native Kotlin + Jetpack Compose UI.
- TV-friendly navigation and focus behavior.
- IPTV engine isolated from UI code.
- Cloud account synchronization isolated from provider credentials.
- Local-first cache for large playlists.
- Media3-based playback layer in a later phase.

## Planned module boundaries

The first foundation intentionally starts with one Android module to keep build complexity low.
As features grow, code will be split by responsibility:

- `core:ui`
- `core:network`
- `core:database`
- `core:player`
- `data:account`
- `data:xtream`
- `data:m3u`
- `data:epg`
- `feature:auth`
- `feature:onboarding`
- `feature:home`
- `feature:live`
- `feature:movies`
- `feature:series`
- `feature:favorites`
- `feature:settings`

## Device strategy

- Mobile: bottom navigation and touch-first controls.
- Tablet: navigation rail and larger content surfaces.
- Television: D-pad/focus-first UI with a 10-foot layout.

## Security baseline

- Never log IPTV credentials or full credential-bearing URLs.
- Never ship Supabase service-role credentials in the app.
- Use user-scoped authorization for cloud data.
- Store sensitive local values with Android secure storage mechanisms.
