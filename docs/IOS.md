# Phase 13 — iPhone / iPad polish and Apple QA

## Goal

Turn the existing SwiftUI source shell into a real, continuously build-validated Apple application.

## Xcode project

The repository now contains a native Xcode project and a shared scheme:

- iosApp/ZyvioTVPlayer.xcodeproj
- ZyvioTVPlayer scheme
- iPhone + iPad target
- iOS 17 minimum deployment target
- generated Info.plist
- no signing material committed

## Navigation

Authentication now routes into the real Apple application shell.

The main tab bar contains:

- Accueil
- TV
- Films
- Séries
- Plus

The Plus tab includes account sign-out.

## Apple CI

A dedicated GitHub Actions workflow runs on macOS and builds the iOS Simulator target with signing disabled.

This is the first phase where Apple source changes are validated by Xcode instead of being source-only.

## Remaining Apple QA

Physical iPhone/iPad testing, signing, provisioning and App Store distribution remain release activities. Kotlin Multiplatform framework linkage into the Xcode target is also still separate from the initial native build-validation milestone.
