# Samsung Tizen client

Phase 16 introduces the dedicated Samsung Smart TV web client shell.

## Files

- `config.xml` — Tizen TV application manifest
- `index.html` — 10-foot UI shell
- `styles.css` — black/red ZYVIOTV TV design
- `app.js` — D-pad / remote focus navigation

## Current scope

The client provides:

- Samsung TV profile metadata
- landscape/maximized layout
- remote-first navigation
- Home / TV / Films / Séries / Plus destinations
- focus outline and scale feedback
- optional registration of Samsung media remote keys
- Supabase origin allow-list

Phase 25 adds the functional provider/playback runtime:
- Xtream live categories/streams loading
- M3U loading/parsing
- Samsung AVPlay playback when available, with HTML5 video fallback
- in-memory provider configuration only
- provider URLs/credentials are never written to UI status or logs
- CI regression tests for provider parsing and URL redaction

Authentication UI, secure account-based provider restoration into this runtime, production Samsung signing and physical model-year certification are still pending.

## Security

Provider credentials must not be embedded in source, query logs or static assets. Cross-device account/provider restoration must use the same secure backend contracts as the mobile clients.

## Validation

The repository CI validates JavaScript syntax and the Tizen XML manifest structure. A real Samsung TV or Tizen emulator is still required for runtime certification.


## Account + secure provider restoration

The Samsung client now also provides:
- Supabase email/password sign-in
- persistent app-local Supabase session with refresh
- session validation on startup
- sign-out
- restoration of the highest-priority enabled configured playlist
- provider-secret retrieval only through `player_get_playlist_secret`
- Xtream/M3U provider configuration kept in memory only
- no IPTV credentials written to the DOM, status messages, logs or static assets
- dynamic provider network access required for arbitrary IPTV hosts

The remaining Samsung work is catalog/UI parity beyond Live TV, EPG, favorites/history/resume, system/account gates, packaging/signing and physical Samsung certification.


## Catalog + EPG parity

The Samsung client now also supports:
- Xtream movie categories and VOD streams
- Xtream series categories and series listings
- series detail loading with seasons/episodes metadata
- direct movie playback through the existing player adapter
- Xtream short EPG lookup for live channels
- current-program display when live playback starts
- remote-focusable dynamic movie/series grids

M3U remains Live-only. Series episode playback, favorites/history/resume and richer EPG UI remain follow-up work.
