# Phase 6 — Live TV

## Goal

Introduce the first real Live TV browsing experience before native playback is connected in Phase 7.

## Android

The Live TV screen now provides:

- responsive mobile and tablet/TV layouts
- category filtering
- channel list
- selected channel state
- current and next programme presentation
- dedicated player preview area
- large-screen split layout with channels on the left and player/programme context on the right

## iPhone / iPad

A native SwiftUI Live TV screen mirrors the same structure and black/red visual direction.

## Shared core

The shared module now includes basic Live TV domain models for:

- categories
- channels
- programme metadata
- now/next programme state

## Playback boundary

The player area is deliberately a placeholder in this phase.

Actual video playback is introduced in Phase 7 with:

- Media3 / ExoPlayer on Android
- AVPlayer on iOS/iPadOS

This keeps browsing and playback responsibilities separate and easier to test.
