# Phase 16 — Samsung Tizen client

## Goal

Introduce a dedicated Samsung Smart TV client while keeping ZyvioTV account and catalog semantics aligned with the Android/iOS applications.

## Client foundation

The repository now contains `tizenApp/` with:

- a Samsung TV Tizen manifest
- a full-screen 10-foot HTML shell
- ZYVIOTV black/red styling
- Home / TV / Films / Séries / Plus navigation
- geometry-based D-pad focus movement
- Enter activation
- Back/Escape handling
- optional Samsung media-key registration
- a restricted Supabase network origin in the manifest

## CI

A dedicated Tizen workflow validates:

- JavaScript syntax
- XML manifest structure
- Samsung TV profile declaration
- required application metadata/files

## Security boundary

No IPTV username/password, playlist URL with credentials or service-role key is embedded in the Tizen client.

Real account authentication and provider-secret restoration must reuse secure backend contracts rather than shipping secrets in static files.

## Remaining runtime integration

The current client is a native Tizen Web application foundation, not yet a complete production Samsung release.

Still required:

- real ZyvioTV account login/session flow
- cloud playlist restoration
- real channel/movie/series catalogs
- EPG
- Samsung AVPlay/native streaming integration
- favorites/history/resume wiring
- model-year compatibility tests
- Tizen certificate/signing and Samsung Seller Office packaging
- physical Samsung TV / emulator QA
