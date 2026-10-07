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
