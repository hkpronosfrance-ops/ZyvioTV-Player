# Phase 1 — Authentication

## Goal

Provide one ZyvioTV identity that can later restore playlists, favorites, history and playback progress on another supported device.

## Backend

Authentication is connected to the existing **ZYVIOTV Supabase project** using the public client configuration.

This means the Player uses the same Supabase Auth identity layer as the wider ZYVIOTV ecosystem. No service-role credential is shipped in the application.

## Shared auth contract

The Kotlin Multiplatform shared core defines:

- sign in
- sign up
- password reset
- sign out
- shared credentials models
- shared validation rules
- backend-independent auth result types

## Android

The Android client includes:

- connection
- account creation
- password reset
- Supabase Auth REST adapter
- encrypted session storage using Android Keystore + AES/GCM
- automatic restoration of a stored session
- sign out

## iPhone / iPad

The SwiftUI client includes:

- connection
- account creation
- password reset
- Supabase Auth REST adapter
- Keychain session storage
- stored-session restoration
- sign out service

## Security

- only the Supabase publishable key is present client-side
- no service-role key is committed or shipped
- auth tokens are not logged
- Android tokens are encrypted with a device-held Keystore key
- Apple tokens are stored in Keychain
- network calls use HTTPS

## Acceptance criteria

Phase 1 is complete when:

- shared validation tests pass
- Android UI builds and lints
- Android sign in/sign up/reset compile against the live Supabase project
- encrypted Android session persistence compiles
- native iOS auth implementation is present
- CI is green
- end-to-end account behavior is manually verified on target devices before release
