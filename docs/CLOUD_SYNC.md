# Phase 2 — Cloud sync

## Goal

Associate ZyvioTV Player data with the authenticated user so the same account can restore its device and playlist context on another supported client.

## Database

The existing ZYVIOTV Supabase project now contains:

- `player_devices`
- `player_playlists`

Both tables use strict RLS tied to `auth.uid()`.

### player_devices

Stores only app/device metadata needed for account management:

- stable per-install/device identifier
- display name
- platform
- app version
- last seen time

### player_playlists

Stores playlist metadata only at this phase:

- name
- provider type
- server host or non-sensitive URL hint
- enabled state
- sync timestamps
- secret configuration status

## Credential rule

**Raw M3U URLs, Xtream usernames and Xtream passwords are not stored in these tables.**

Credential portability is required for the final product, but it will be implemented with a dedicated encrypted secret mechanism in the provider phases. We will not put IPTV credentials in plain Postgres columns.

## Android

After authentication, the client automatically registers/upserts the current Android phone, tablet or TV/box in `player_devices`.

The sync repository can also read the authenticated user's playlist metadata.

## RLS

All user policies use `(select auth.uid())` to preserve row isolation and avoid per-row auth-function re-evaluation.

## Next

Phase 3 adds the Xtream provider core and the secure credential path required to restore an Xtream playlist on another device without retyping it.
