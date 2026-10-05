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
