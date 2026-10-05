# Phase 23 — Real EPG

## Goal

Replace synthetic programme data with provider EPG data while keeping memory and network use bounded.

## Xtream

- uses `get_short_epg`
- sends stream id and a bounded listing limit
- decodes title/description when providers return Base64
- converts results to the shared EPG model
- filters to the active time window
- returns generic errors without provider credentials or URLs

## XMLTV

- streaming pull parser
- channel-specific filtering
- bounded programme count
- programme title/description extraction
- XMLTV timezone-aware timestamp parsing
- shared EPG window filtering

## Window strategy

The default window is ±3 hours around now. UI consumers can request another bounded window without loading a complete multi-day guide.

## Shared timeline

Existing `EpgTimeline` continues to compute:
- current programme
- next programme
- live progress

## Validation limitation

Real provider feeds vary in XMLTV formatting and Xtream payloads. CI can validate deterministic parsing/model behavior, but provider/device QA remains required.
