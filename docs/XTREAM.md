# Phase 3 — Xtream Codes core

## Goal

Add a provider-neutral, testable Xtream Codes foundation to the shared Kotlin Multiplatform module.

## Shared core

The shared module now contains:

- Xtream credentials model
- validation rules
- account/server profile models
- authentication result types
- endpoint builder for live, movie and series playback
- player API profile parser
- log redaction for credential-bearing URLs
- unit tests

## Security rules

Xtream credentials must never be printed to logs.

Generated stream URLs contain credentials because that is how Xtream-style endpoints work. Any URL used for diagnostics must be passed through the redaction helper first.

The cloud database still stores only non-sensitive playlist metadata.

## Next implementation step

The Android and Apple provider adapters will call the Xtream player API, parse the returned account/server data through the shared core, then securely persist provider secret material.

The secure cross-device secret path will be introduced before playlist credentials are synchronized between devices.
