# iOS client

This folder contains the native SwiftUI iPhone/iPad application and its Xcode project.

## Project

Open:

`iosApp/ZyvioTVPlayer.xcodeproj`

Shared scheme:

`ZyvioTVPlayer`

The target supports both iPhone and iPad and currently uses iOS 17 as its minimum deployment target.

## Current native stack

- SwiftUI interface
- AVPlayer / VideoPlayer playback
- Keychain session storage
- Supabase Auth and library sync over HTTPS
- Home, Live TV, Movies, Series and Account tabs

No Apple signing certificate, provisioning profile or private signing material belongs in the repository.

## CI

The `iOS CI` GitHub Actions workflow builds the application on a macOS runner against the iOS Simulator with code signing disabled.

The Kotlin Multiplatform shared module remains the cross-platform domain source for Android. Direct framework linkage into the Apple target is a separate integration step; the current Apple client is buildable independently so Swift/iOS regressions can be caught immediately.
