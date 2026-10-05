# Phase 4 — M3U / M3U8 shared provider core

## Goal

Add a platform-independent M3U/M3U8 foundation to the shared Kotlin Multiplatform module.

## Shared core

The shared module now contains:

- M3U source model
- source validation
- EXTINF parser
- support for tvg-id, tvg-name, tvg-logo and group-title metadata
- HTTP/HTTPS stream filtering
- credential-safe URL logging helper
- unit tests

## Performance rule

The shared parser is deliberately lightweight. Large production playlists must later be processed incrementally and cached locally instead of keeping the entire catalog and artwork in memory.

## Security rule

Full credential-bearing playlist URLs must never be written to logs. Sensitive query parameters are redacted before diagnostics.

## Next

After this core is validated, the following phases connect the provider cores to the app flow and build the Home experience.
