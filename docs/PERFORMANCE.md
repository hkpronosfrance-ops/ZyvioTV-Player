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

## PR #211 — Telemetry, useless work, stability and integrity (October 2026)

Scope agreed for #211: measure, remove useless work and protect integrity.
The paged/generational store (Room) and the < 2 s start goal belong to #212;
#211 does **not** promise a faster cold start.

Useless work removed:

- **No resync after a valid cache.** `CatalogRefreshPolicy`: an automatic
  trigger (start-up, profile change) reuses a playable cache younger than
  12 h; manual triggers (Retry, playlist change, parental change) always
  refresh. Log: `catalog event=refresh_decision trigger= decision= age_min=
  playback_active=`.
- **Authenticated freshness.** The fetch date is never read from a plain
  header: `<digest>.meta` is AES-GCM (Keystore key, AAD
  `zyviotv-catalog-metadata`) and binds the date to the SHA-256 and length
  of the catalogue file. It is written only after the catalogue file is
  committed. A replaced, truncated or corrupted catalogue no longer matches
  its metadata (`catalog_cache_freshness status=mismatch`) and is treated as
  of unknown age. The V1 catalogue format is unchanged and still read; no
  cache is deleted.
- **Offline check off the main thread.** The start-up offline check runs on
  `Dispatchers.IO`, at most once per restore, and uses the authenticated
  metadata (`offline_check method=metadata`) before falling back to a full
  decode (`method=full_decode`).
- **No automatic sync during playback.** Automatic refreshes wait for the
  end of playback (`refresh_deferred_for_playback`); a running parser is
  never suspended. Manual refreshes run, with a warning in the log.
- **Screen states off the main thread.** Live/Films/Séries states are built
  on `Dispatchers.Default` and kept at the app root
  (`CatalogUiStateCache`, phases `ui_map_live|ui_map_movies|ui_map_series`).
  The home screen computes catalogue-wide sets and lists once per snapshot.
- `CatalogSingleFlight` releases a finished refresh even when every caller
  was cancelled (a whole decoded catalogue stayed reachable).

Telemetry added (all anonymous, bounded, no periodic logging):

- `ZyvioCatalog` phase lines: `native_heap_mb`, `gc_count`, `gc_time_ms`
  (process counters: the difference between two lines gives a phase).
- `ZyvioUi`: one `frames screen= total= slow= frozen= max_ms= p95_ms_le=`
  line per screen visit (FrameMetrics; slow > 50 ms, frozen ≥ 700 ms).
- `ZyvioPlayback`: one `summary` line per playback, at release (see
  `PRODUCTION_PLAYBACK.md`).

Integrity:

- M3U ids: unique legacy 32-bit ids are unchanged; on collision one entry
  keeps the id (the one recorded in the alias table, otherwise the smallest
  64-bit fingerprint, independent of playlist order) and the others get a
  deterministic `m3u-<16 hex>` id. Aliases are stored in the authenticated
  metadata so favourites, history and resume points stay attached. Log:
  `m3u_shared_ids groups=`.
- Xtream: each of the six lists is validated (`xtream_list list= outcome=
  body= items= raw=`). An HTML/JSON-object/unreadable body, a list with no
  usable entry, or an empty list where the previous catalogue of the same
  playlist was not empty fails the refresh; the validated catalogue and its
  cache are kept.

Tests: JVM tests (`CatalogRefreshPolicyTest`, `CatalogCacheMetadataTest`,
`M3uIdResolverTest` up to 150k shared-id groups and 13k films + 120k
episodes, `XtreamCatalogValidationTest`, `CatalogSingleFlightTest`), shared
`DisplayTitleTest`, and `tests/test_android_catalog_211.py`. Not measured
on Pixel 7 yet.
