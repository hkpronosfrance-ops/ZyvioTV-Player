# Phase 22 — Production playback

## Goal

Harden the native playback layer so real provider streams can be handed to Media3/ExoPlayer and AVPlayer with predictable lifecycle, resume and error behavior.

## Android

- detects HLS, MPEG-TS and progressive provider URLs
- explicitly sets HLS / MPEG-TS media MIME types when appropriate
- pauses when the app moves to the background
- resumes when the app returns if playback has not ended
- reports the current position before backgrounding/disposal
- exposes buffering/ready/ended/error states
- exposes a user-safe playback error
- keeps the screen awake while video is visible
- reacts to audio-becoming-noisy events

## iOS / iPadOS

- AVPlayer configured for media playback audio
- automatic stall minimization
- bounded forward-buffer hint
- resume seeking
- periodic playback-position reporting
- final position reporting when leaving the player
- end/failure observation with a user-safe error

## Security

Playback errors do not include provider URLs, usernames, passwords or tokens. Existing PlaybackRequest string rendering remains credential-redacted.

## Validation limitation

CI validates compilation and deterministic playback planning. A physical-device matrix with real provider streams is still required before claiming codec/provider compatibility across all target devices.
