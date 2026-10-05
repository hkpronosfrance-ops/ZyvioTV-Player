# Phase 17 — LG webOS client

## Goal

Introduce a dedicated LG Smart TV client while preserving the same ZyvioTV product semantics as Android, iOS and Samsung Tizen.

## Client foundation

The repository now contains `webosApp/` with:

- webOS `appinfo.json`
- a full-screen 10-foot interface
- ZYVIOTV black/red styling
- Home / TV / Films / Séries / Plus navigation
- geometry-based D-pad focus movement
- Enter activation
- LG Back key handling
- no embedded IPTV provider credentials

## CI

A dedicated webOS workflow validates:

- JavaScript syntax
- `appinfo.json` structure
- required app metadata
- required application files

## Remaining runtime integration

Still required:

- ZyvioTV account authentication
- secure playlist restoration
- real Live / Movies / Series catalogs
- EPG
- favorites/history/resume
- webOS playback integration and codec/model validation
- packaging/signing
- physical LG TV / emulator QA
