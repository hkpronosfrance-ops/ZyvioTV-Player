# Phase 18 — Release hardening

## Release gate

Every pull request to `main` now runs a dedicated release gate in addition to the platform workflows.

The gate validates:

- shared and Android unit tests
- Android release lint
- minified Android release assembly
- Samsung Tizen JavaScript syntax
- LG webOS JavaScript syntax
- TV manifest structure
- absence of known privileged Supabase secret markers in client source

## Logging hardening

Playback/provider URLs can contain IPTV credentials in several forms.

The shared logging policy now redacts:

- query credentials such as `username`, `password`, `token`, `access_token`
- URL user-info credentials
- Xtream-style `/live/user/password/`, `/movie/user/password/` and `/series/user/password/` paths

Playback-facing shared models also override their log representation so raw stream URLs are not emitted accidentally through normal `toString()` logging.

## Publication readiness

A green release gate means the source passes automated hardening checks. It does not mean the product is ready for public store release.

Before public publication, the following still require explicit completion and verification:

- real Xtream network adapter
- production M3U network loader
- real provider data wired into Live / Movies / Series
- real EPG ingestion
- secure cross-device provider-secret storage/restoration
- playback reconnect/error/buffering UX
- physical phone/tablet/TV/box testing
- Samsung AVPlay integration and model-year QA
- LG playback integration and model-year QA
- Apple/Android/TV signing and store credentials
- privacy/store metadata and review requirements

No release should be labelled production-ready until these items are closed or intentionally scoped out.
