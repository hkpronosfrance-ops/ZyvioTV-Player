# Phase 1 — Authentication

## Goal

Provide one ZyvioTV Player account that can later restore playlists, favorites, history and playback progress on another supported device.

## Shared auth contract

The Kotlin Multiplatform shared core now defines:

- sign in
- sign up
- password reset
- sign out
- shared credentials models
- shared validation rules
- backend-independent auth result types

## Client UI

### Android

The Android client now contains the ZYVIOTV-themed screens for:

- connection
- account creation
- password reset

### iOS / iPadOS

A native SwiftUI auth view mirrors the same flows.

## Backend adapter

The backend adapter is intentionally not hardcoded in source control.

Before real authentication is enabled, the project needs an auth backend configuration. The recommended option is Supabase Auth because the wider ZYVIOTV ecosystem already uses Supabase.

Required values must be injected through secure build/runtime configuration and never committed:

- Supabase project URL
- public anon/publishable key

Never put a Supabase service-role key in the application.

## Acceptance criteria

Phase 1 is complete only when:

- shared validation tests pass
- Android UI builds and lints
- iOS auth shell is present
- a real auth backend is connected
- sign in/sign up/reset work end to end
- session persistence and sign out are verified
