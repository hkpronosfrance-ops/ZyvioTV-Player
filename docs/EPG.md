# Phase 8 — EPG

## Goal

Add the programme-guide domain layer and the first now/next presentation for Live TV.

## Shared core

The shared Kotlin Multiplatform module now provides:

- programme model
- current/next programme selection
- progress calculation
- validation against invalid time ranges
- unit tests

Times are kept as epoch seconds in the shared domain layer so platform UIs can display them in the device's local timezone.

## Live TV UX

The Live TV screen presents:

- current programme
- next programme
- programme progress
- empty-state wording when EPG data is unavailable

Real provider EPG ingestion is connected later to Xtream/XMLTV data. The UI/domain contract is ready now and does not depend on a specific provider.
