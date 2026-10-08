# LG webOS client

Phase 17 introduces the dedicated LG webOS Smart TV client foundation.

## Included

- webOS `appinfo.json`
- full-screen 10-foot HTML shell
- ZYVIOTV black/red design
- Home / TV / Films / Séries / Plus navigation
- geometry-based remote/D-pad focus
- Enter activation
- LG Back key support (keyCode 461)
- no provider credentials embedded in source

## Current scope

Phase 25 adds the functional provider/playback runtime for live TV:
- Xtream live categories/streams loading
- M3U loading/parsing
- HTML5 video playback surface
- in-memory provider configuration only
- provider URLs/credentials are never written to UI status or logs
- CI regression tests for provider parsing and URL redaction

This still does not claim Store readiness or physical model-year certification.

Remaining integration includes:

- ZyvioTV account authentication
- secure account-based playlist restoration into the TV runtime
- real Movies / Series catalog UI wiring
- EPG UI wiring
- favorites/history/resume UI wiring
- webOS codec/model playback validation
- model-year compatibility testing
- packaging/signing for LG Seller Lounge
- physical LG TV/emulator QA


## Account + secure provider restoration

The LG webOS client now also supports:
- Supabase email/password sign-in
- persistent app-local Supabase session with refresh
- cold-start session restoration
- sign-out
- restoration of the highest-priority enabled configured playlist
- provider-secret retrieval only through `player_get_playlist_secret`
- Xtream/M3U provider configuration kept in memory only
- real Live TV grid backed by the restored account playlist
- direct live playback from remote-focusable channel cards
- LG Back behavior that stops playback before leaving the app
- media Play/Pause/Stop handling

Provider credentials and stream URLs are never written to UI status or local account storage.

No database migration is required.


## Catalog + EPG parity

The LG webOS client now also supports:
- Xtream movie categories and VOD streams
- Xtream series categories and series listings
- series detail loading with normalized seasons/episodes
- direct movie playback
- direct episode playback
- Xtream short EPG lookup for live channels
- current-program display when live playback starts
- remote-focusable dynamic movie/series/episode grids

M3U remains Live-only for this phase.
No database migration is required.


## Profiles + synced library home

The LG webOS client now also supports:
- account profile creation/restore through the existing primary-profile RPC
- profile picker and device-local remembered profile selection
- profile-scoped favorites
- movie/episode watch progress
- resume from saved position
- profile-scoped live history
- Home shelves for Continue Watching, Recent Channels, Favorites and Recently Watched
- direct playback from synced Home cards
- red-key favorite toggle for movies, series and live channels
- 30-second progress synchronization while playing
- collision-safe library matching using playlist + content type + content id

No database migration is required; existing profile/library tables and RLS policies are reused.

Parental enforcement remains a dedicated follow-up webOS phase.


## Child profile parental enforcement

The LG webOS client now also supports:
- account parental settings and profile lock retrieval
- secure parental PIN verification through the existing backend RPC
- adult-labelled categories/content hidden on Child profiles
- explicit locked content hidden on Child profiles
- explicit locked categories hidden on Child profiles
- Child-safe filtering for Live, Movies, Series, Favorites, Continue Watching and Recently Watched
- PIN-gated exit from a Child profile
- PIN-gated access to Profiles and More
- PIN-gated sign-out from a Child profile
- library actions and provider reloads reapply Child filtering
- profile switches clear cached catalogs to prevent cross-profile leakage

No database migration is required.

Screen-time limits, schedules and temporary PIN exceptions remain a dedicated follow-up webOS phase.


## Advanced parental runtime

The LG webOS client now also supports:
- server-backed Child screen-time runtime state
- synchronized 30-second parental heartbeat
- weekday daily limits
- separate weekend limits
- allowed viewing schedules, including overnight windows
- temporary PIN exception for up to 30 minutes or until the content ends
- server-time anchored runtime cache with restart fallback
- local conservative runtime fallback when the backend is temporarily unavailable
- playback blocked before starting when schedule/time limits apply
- runtime blocking while content is already playing
- explicit exception termination when playback stops or content changes
- stable per-TV runtime device UID
- true HTML5 playback-state reporting: paused, ended and buffering time do not count as screen time

No database migration is required; the existing parental runtime RPCs are reused.


## Devices + system states

The LG webOS client now also supports:
- stable per-TV device registration with platform=webos
- device list with current-TV marker
- remote-friendly device rename flow
- disconnecting another registered device with the red key
- protection against disconnecting the current TV from itself
- account-suspended blocking state
- blocking maintenance state
- dismissible planned-maintenance notice
- periodic system-state refresh while signed in
- device registration refresh on sign-in/session restore

No database migration is required; existing device/account/service-state tables and RLS policies are reused.


## Canonical Home parity

The LG webOS Home now follows the shared canonical shelf order:
1. Continue Watching
2. Next Episodes
3. Recent Channels
4. Favorites
5. Recent Movies
6. Recent Series
7. Same category as the most recently watched title

Additional behavior:
- parental filtering is applied before Home rendering
- empty shelves are hidden
- each shelf is capped at 20 items
- See All appears only when a shelf has more than 20 items
- recent Movies/Series are sorted by Xtream added/last_modified timestamps
- Next Episodes are resolved from real provider episode lists and watch progress
- same-category recommendations exclude the last watched title
- Home card actions open/play the correct content
- focus is restored when returning to Home
- M3U remains limited to compatible Home shelves

No database migration is required.
