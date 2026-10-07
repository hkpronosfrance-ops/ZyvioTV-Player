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


## Episodes + library sync

The Samsung client now also supports:
- resolving Xtream episode streams from series details
- direct episode playback
- primary-profile restoration for profile-scoped library data
- synced movie/series favorites
- TV remote favorite toggle with the red color key
- profile-scoped watch progress for movies and episodes
- resume playback from saved position
- periodic progress sync while playing
- completion detection near the end of content

Provider credentials and stream URLs are never written to library rows.


## Profiles + actionable home library

The Samsung client now also supports:
- account profile picker after sign-in when multiple profiles exist
- device-local remembered profile selection
- explicit Profiles navigation entry for switching profile
- profile-scoped Favorites / Continue Watching / Recent history shelves
- actionable movie and episode resume cards
- actionable movie/series favorites
- no empty synced shelves
- up to 20 items per home shelf
- fixed catalog-card activation for remote/Enter clicks

Child-profile parental enforcement remains a dedicated follow-up phase; this phase only establishes correct profile selection and scoped library data.


## Child profile parental enforcement

The Samsung client now also supports:
- loading account parental settings
- loading profile-scoped content locks
- hiding explicitly locked live/movie/series content for Child profiles
- always hiding adult-labelled categories on Child profiles when parental controls are enabled
- parental PIN verification through the existing secure RPC
- PIN-gated exit from a Child profile to the profile picker
- PIN-gated access to the account/More area from a Child profile
- PIN-gated sign-out from a Child profile
- lockout/attempt feedback returned by the backend

Screen-time heartbeat, schedules and temporary 30-minute runtime exceptions remain a dedicated follow-up phase.


## Parental runtime enforcement

The Samsung client now also supports:
- server-anchored parental runtime state
- a stable per-TV device UID for multi-device heartbeat reconciliation
- screen-time heartbeat v2 every 30 seconds
- weekday/weekend limit enforcement
- schedule-window enforcement using trusted server time
- local cached consumed-time high-water mark for temporary network loss
- fail-safe monotonic clock handling that never advances time from a backwards/reset local clock
- PIN-granted 30-minute exception scoped to the current content
- exception termination when playback ends/leaves the current content
- live, movie and episode runtime enforcement
- adult-labelled categories always hidden on Child profiles
- Child-profile exits always PIN protected

No database migration is required; the existing hardened parental RPCs are reused.


## Live library parity

The Samsung client now also supports:
- profile-scoped live favorites using the existing favorites table
- adding/removing the currently playing live channel with the red remote key
- profile-scoped recent-channel history
- automatic history recording after successful live playback
- a real Recent Channels shelf on Home
- direct playback from Recent Channels
- direct playback of live favorites
- Child-profile live favorites/history remain hidden unless the channel resolves through the already filtered live catalog

No database migration is required; the existing live history and favorites tables/RLS are reused.


## Devices + system state parity

The Samsung client now also supports:
- Tizen device registration/upsert with stable device UID, model name and app version
- profile-independent device list in Plus
- rename any registered device
- disconnect another device with the red remote key
- prevent disconnecting the current TV from itself
- account suspension gate
- blocking maintenance gate
- planned maintenance notice with Continue
- the same system-state checks after restored sessions and manual sign-in
- blocking states remain isolated from the application shell

No database migration is required; existing player_devices, player_account_status and player_service_state RLS-backed tables are reused.
