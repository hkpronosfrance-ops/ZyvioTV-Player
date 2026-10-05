# Phase 12 — Android TV / Google TV / Box polish

## Goal

Adapt the Android client for remote-first 10-foot use without creating a separate Android codebase.

## Navigation

Television devices continue to be detected through Android UI mode and now receive:

- wider persistent navigation rail
- always-visible labels
- larger spacing and content margins
- D-pad-compatible focusable controls

## Focus system

A reusable TV focus effect now provides:

- red focus outline
- slight scale-up on focus
- consistent rounded focus treatment

It is applied to the main remote-interactive surfaces including:

- Home quick actions
- Live TV categories and channels
- movie cards
- series cards
- season chips
- episode rows

## Layout

TV uses larger content cards and additional screen-edge spacing to improve readability from viewing distance.

## Compatibility

The existing Android manifest already declares optional Leanback support and does not require a touchscreen, allowing the same APK architecture to support:

- Android TV
- Google TV
- Android boxes
- phones and tablets

Physical remote/controller testing remains part of the device QA phase.
