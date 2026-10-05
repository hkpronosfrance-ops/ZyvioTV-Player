# Phase 26 — Final QA matrix

This matrix is the release acceptance checklist. Automated CI can verify source-level guarantees, but rows marked **physical** require a real device before public release.

| Platform | Target | Automated | Physical | Required checks |
| --- | --- | --- | --- | --- |
| Android phone | API 24, 28, 33, 35/36 | ✅ | ☐ | auth, provider restore, large catalog, rotation, playback, background/foreground, resume |
| Android tablet | 8–13 inch portrait/landscape | ✅ build | ☐ | adaptive layout, split views, playback, EPG, keyboard/back |
| Android TV / Google TV | 1080p + 4K | ✅ build | ☐ | D-pad focus, numeric channel jump, live playback, EPG, overscan/safe area |
| Android box | common Amlogic/Realtek-class boxes | ✅ build | ☐ | remote keys, decoder fallback, Ethernet/Wi-Fi, HLS/TS |
| iPhone | current + oldest supported iOS target | ✅ compile | ☐ | auth, safe-area, background audio/video lifecycle, resume |
| iPad | portrait + landscape | ✅ compile | ☐ | adaptive layout, multitasking-safe layout, playback |
| Samsung Tizen | supported model-year range | ✅ static/runtime-core tests | ☐ | D-pad, AVPlay, HLS/TS, Back, suspend/resume, network loss |
| LG webOS | supported model-year range | ✅ static/runtime-core tests | ☐ | D-pad, HTML5 media pipeline, Back, suspend/resume, network loss |

## Provider test set

Before release, validate with provider accounts/playlists the owner is authorized to use:

- Xtream account with live + movies + series
- Xtream account with HLS live output
- Xtream account with MPEG-TS live output
- M3U playlist with logos and groups
- large playlist (5,000+ entries)
- provider with missing logos / missing EPG
- expired or disabled account
- slow provider response
- temporary HTTP 5xx
- malformed M3U and malformed EPG payload

Never commit or paste provider credentials into issues, screenshots, CI variables, logs or test fixtures.

## Network matrix

Physical QA should cover:

- fast Wi-Fi
- Ethernet where applicable
- throttled connection
- packet loss / temporary disconnect
- app launched while offline
- network lost during playback
- network restored after failure
- provider timeout
- DNS / unreachable host

## Playback acceptance

For every target family:

- first frame starts without UI deadlock
- buffering is visible and recoverable
- unsupported/broken stream returns a safe user-facing error
- credentials never appear in UI or logs
- pause/resume lifecycle is correct
- movie/episode position is persisted
- completed content does not incorrectly resume near the end
- live playback never writes VOD resume progress
- screen/remote controls remain responsive during buffering

## Large-catalog acceptance

- category switching remains responsive
- no whole-file M3U materialization before parsing
- pagination/bounds remain enforced
- search handles long names and accents
- images failing to load do not break navigation
- focus/scroll position is preserved where specified by the UX
- EPG uses bounded windows rather than loading multiple complete days

## Release blockers

Any of the following blocks a production-ready label:

- failing required CI
- provider credential leakage
- missing RLS/ownership check for user data
- crash during basic auth/navigation/playback flow
- untested signing package
- failed physical-device playback on a platform advertised as supported
- unresolved store review/signing requirement
