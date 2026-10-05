# Phase 15 — Device QA matrix

## Goal

Track compatibility coverage explicitly instead of treating a successful compile as proof that every device class behaves correctly.

## Automated coverage

| Area | Coverage | Status |
| --- | --- | --- |
| Shared Kotlin tests | Domain/parsing/paging/sync contracts | Automated |
| Android unit tests | Phone/tablet/TV device-profile classification | Automated |
| Android lint | Static Android checks | Automated |
| Android APK build | API 36 compile, debug packaging | Automated |
| iOS Xcode build | SwiftUI iPhone/iPad target on simulator SDK | Automated |

## Physical / emulator QA matrix

| Device class | Minimum scenarios | Current status |
| --- | --- | --- |
| Android phone, narrow ~360–390dp | auth, Home, Live, Films, Séries, player rotation/background | Pending device run |
| Android phone, large | same + large catalog scrolling | Pending device run |
| Android tablet >=600dp | rail layout, grids, player, split layouts | Pending device run |
| Android TV / Google TV | D-pad focus, rail, Live channel navigation, playback | Pending remote run |
| Generic Android box | remote keys, decoder/playback stability, reconnect | Pending hardware run |
| iPhone | auth, tabs, AVPlayer, background/foreground | Pending device/simulator scenario run |
| iPad | adaptive layout, tabs, AVPlayer, rotation | Pending device/simulator scenario run |

## Network scenarios

Every supported platform should be exercised against:

- normal broadband
- high latency
- temporary disconnect
- stream failure
- app background / foreground
- provider timeout

Expected behavior is controlled failure or recovery without freezing the whole app and without leaking provider credentials into logs.

## Large-catalog scenarios

QA should include:

- 5,000+ M3U entries
- large Xtream channel/movie/series catalogs
- repeated category switching
- long scrolling sessions
- leaving and returning to the app
- memory-pressure observation

## Release rule

A green CI run means the source/build checks passed. It does not replace physical-device QA. Phase 15 remains partially manual until the target hardware matrix has actually been exercised.
