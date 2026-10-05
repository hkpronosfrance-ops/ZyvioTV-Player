# Phase 24 — Secure cross-device synchronization

## Goal

Allow a signed-in ZYVIOTV user to restore a configured IPTV provider on another device without exposing provider credentials as ordinary rows or client-side service-role data.

## Existing synchronized data

- registered devices
- playlist metadata
- favorites
- watch progress / resume history

All existing tables remain protected by own-user RLS.

## Provider secrets

Phase 24 stores provider configurations through Supabase Vault:

- Xtream: server URL, username and password
- M3U: playlist URL
- the client calls authenticated RPCs only
- direct client access to the secret-mapping table is revoked
- direct access to Vault is never granted
- RPCs verify `auth.uid()` owns the playlist before every set/get/delete
- secret payloads are capped at 16 KiB
- Vault encrypts secret values at rest
- client models override `toString()` so credentials cannot be emitted accidentally
- no service-role key is shipped in any app

The ordinary `player_playlists` table stores only safe metadata/hints and a `secret_status`.

## Restore flow

1. user signs into ZYVIOTV
2. device metadata is registered
3. playlist metadata is loaded under RLS
4. for an enabled playlist with `secret_status=configured`, the authenticated client requests that playlist secret through the ownership-checking RPC
5. the provider configuration exists only in process memory while needed and may then be placed in platform secure storage

## Security boundary

Cross-device recovery is deliberately server-mediated. Provider credentials are never synchronized through a public table and are never included in diagnostics or UI-safe errors.
