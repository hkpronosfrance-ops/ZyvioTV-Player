# Phase 21 — Real catalog wiring

## Goal

Normalize provider data into one shared catalog model so the UI can consume real channels, films and series regardless of provider protocol.

## Implemented

- shared catalog category/channel/movie/series models
- normalized catalog snapshot and load result
- Android Xtream loader for:
  - live categories
  - live streams
  - VOD categories
  - VOD streams
  - series categories
  - series list
- real playback URLs generated from the existing credential-safe Xtream endpoint builder
- M3U entries mapped into the same live catalog model

## Security

The loader does not log provider endpoints, usernames, passwords or stream URLs. Provider HTTP failures are converted into generic UI-safe errors.

## Remaining Phase 21 wiring

The next step is binding a selected provider/playlist to this loader and exposing the resulting CatalogSnapshot as observable UI state. Until a provider is selected, the current screens can only display their existing placeholder content.

This separation is intentional: provider-secret selection/storage must not be improvised in UI code.
