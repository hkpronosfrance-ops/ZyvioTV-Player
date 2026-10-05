# Phase 10 — Series / seasons / episodes

## Goal

Add the series browsing and episode-navigation foundation on top of the shared provider and native-player layers.

## Shared core

The Kotlin Multiplatform layer now includes:

- series summary/details models
- seasons and episodes
- episode metadata and resume position
- watched state
- next-episode navigation across season boundaries
- validation rules
- unit tests

## Android

The Series screen provides:

- responsive mobile and tablet/TV layouts
- poster-style series selection
- series hero/details
- season selector
- ordered episode list
- episode resume/progress presentation
- play action ready for the native player

## iPhone / iPad

A SwiftUI series view mirrors the same hierarchy.

## Data boundary

The current UI still uses placeholder data. Real Xtream/M3U series catalogs and episode stream URLs will replace it during provider integration and sync work.
