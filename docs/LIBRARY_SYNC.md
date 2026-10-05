# Phase 11 — Favorites / history / resume sync

## Goal

Synchronize user library state across ZyvioTV Player devices without exposing provider credentials.

## Supabase

Two RLS-protected tables are added:

- player_favorites
- player_watch_progress

Rows are isolated by auth.uid() and playlist context.

Favorites support live channels, movies and series. Watch progress supports movies and episodes with position, optional duration, completed state, last-watched time and series context.

History is derived from watch-progress rows ordered by last_watched_at.

## Shared core

The KMP layer provides platform-neutral favorite/watch-progress models and a cloud repository contract.

## Android

The authenticated Supabase repository can list/add/remove favorites, list recent history, upsert resume progress and remove progress. Only the publishable Supabase key and authenticated user token are used.

## Next integration

Favorites and Continue Watching can replace placeholders once real provider catalog IDs are wired into the UI.
