# Phase 7 — Native playback

## Goal

Introduce platform-native video playback primitives without coupling the shared domain layer to a specific media engine.

## Android

Android playback now uses AndroidX Media3 / ExoPlayer.

The app includes:

- ExoPlayer-backed native player view
- HLS support
- playback controls through Media3 PlayerView
- live, movie and episode playback request model
- resume position support
- player lifecycle cleanup when the Compose view leaves the screen

Media3 is pinned to 1.11.1, the current stable release selected for this phase.

## iPhone / iPad

Apple playback uses AVPlayer through SwiftUI VideoPlayer.

The implementation supports:

- remote HTTP/HTTPS streams
- resume position
- automatic start
- cleanup when leaving the view

## Shared contract

The Kotlin Multiplatform core owns only platform-neutral playback metadata and validation.

It does not instantiate or depend on ExoPlayer or AVPlayer.

## Integration boundary

The Live TV, Movies and Series screens will pass real provider stream URLs into these native players as their provider/catalog phases are connected.

Until real playlist data is wired into a screen, no placeholder network stream is started automatically.
