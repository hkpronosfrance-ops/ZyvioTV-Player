# Phase 19 — Real Xtream Codes integration

## Goal

Replace the missing Android Xtream network layer with a real HTTP client that can validate a provider account against `player_api.php`.

## Implemented

- authenticated Xtream `player_api.php` URL builder
- percent-encoding for username/password/action query values
- Android HTTP client using `HttpURLConnection`
- network work dispatched to `Dispatchers.IO`
- connect/read timeouts
- JSON profile parsing through the shared Xtream parser
- inactive-account rejection
- controlled timeout/network error messages
- no credential logging
- regression test for encoded credentials

## Security

Xtream usernames/passwords are only used to construct the provider request and are not logged.

Existing shared redaction protects query/path credentials if URLs are accidentally sent to logs.

## Next within Phase 19

After the base connection client is green, wire it to an add-provider screen and fetch real categories/catalog endpoints:

- `get_live_categories`
- `get_live_streams`
- `get_vod_categories`
- `get_vod_streams`
- `get_series_categories`
- `get_series`

The current commit establishes the production network adapter needed for that wiring.
