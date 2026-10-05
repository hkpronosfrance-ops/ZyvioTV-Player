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
