# Phase 20 — Real M3U import

## Goal

Download and parse real M3U/M3U8 playlists on Android without first loading the whole response into one giant String.

## Implemented

- shared M3U client/import contract
- Android HTTP downloader
- parser fed directly from a buffered network reader
- incremental line parsing
- configurable maximum-entry bound
- connect/read timeouts
- controlled HTTP/network/timeout errors
- empty/invalid playlist detection
- no playlist URL logging

## Memory behavior

The network response is consumed line-by-line through `BufferedReader.lineSequence()`. This removes the previous need for a platform adapter to materialize the complete playlist body before parsing.

The returned entries are still collected because the current UI/catalog layer consumes a list. Persistent local catalog storage and incremental database writes remain a later data-layer optimization.

## Security

M3U URLs may contain provider credentials. The Android client does not log the source URL, and existing URL-redaction helpers must be used anywhere diagnostic URLs are required.
