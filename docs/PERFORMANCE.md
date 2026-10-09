# Phase 14 — Performance and large-playlist optimization

## Goal

Keep the player responsive with very large IPTV catalogs while limiting avoidable memory growth.

## M3U parsing

The shared parser no longer materializes every playlist line into an intermediate list before parsing.

It now supports:

- incremental Sequence-based parsing
- callback-based entry emission
- an optional maximum-entry bound
- precompiled metadata regexes reused across entries
- compatibility with the existing List-returning parse API

This reduces temporary allocations and lets platform adapters consume entries progressively.

## Catalog pagination

A shared bounded paging helper now:

- validates negative/out-of-range offsets
- caps page size at 200 items
- reports whether more items remain

This provides a common contract for catalog screens and provider adapters so large channel/movie/series lists do not need to render thousands of items at once.

## Tests

Coverage includes:

- a 5,000-entry streamed M3U parse
- early stopping at a configured limit
- bounded pagination
- end-of-list behavior

## Remaining work

Network fetches still need to feed the parser incrementally instead of downloading a whole M3U response into one String. Local persistent catalog caching and real provider pagination remain part of the data-integration hardening work.

## Pixel 7 measurements to address (reported 2026-10-09, after #209)

Recorded for the dedicated performance phase; not changed by PR #210.

| Step | Measured |
|---|---|
| Encrypted cache load | 46 971 ms (`playable=true`) |
| Sources restored | live 6 074/6 074, films 12 607/12 607, episodes 117 989/117 989 |
| M3U download | 27 401 ms |
| Parsing | 65 313 ms |
| Catalogue save | 45 611 ms |
| UI | very frequent GC, `Skipped 297 frames`, several `Davey` > 2 s, one > 5 s |

- The whole catalogue is resynchronised shortly after a usable cache was loaded: audit why.
- Supabase `player_devices` still answers HTTP 403 (separate authorization audit).
