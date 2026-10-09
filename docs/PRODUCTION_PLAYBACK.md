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

## Bloc #207 — Android playback wiring (October 2026)

Pixel 7 symptoms after PR #206: tapping a channel only selected it, and screens showed "hors connexion" while network requests succeeded.

Root causes found in the code:

- Live channel cards only called `onChannelSelected`; playback required a separate "Regarder" button.
- "Offline" was derived from data, not connectivity: `ProviderCatalogState.Ready.isOffline` was `true` whenever the restored catalog had no stream URLs (legacy pre-#206 cache), and `LibrarySnapshot.isOffline` was `true` whenever one Supabase library request failed. Every play action (`tuneLiveChannel`, movie, episode, guide, favorites, resume) then returned silently.

Behaviour now:

- Tapping (or pressing OK on) a channel card opens the player; D-pad focus still only selects.
- Every play action goes through `PlaybackLaunchPolicy`: it refuses only a missing/invalid source or a device with no network (`NetworkAvailability` from `ConnectivityManager`), and each refusal shows a French message.
- `isFromCache` (catalog and library) describes data origin only; it never disables playback. The Home banner shows "Mode hors connexion" only for real device offline, and "Synchronisation incomplète" when an account sync failed while online.
- M3U series episodes are read from the local registry before any Supabase secret lookup, so cached episodes stay playable.
- Media3 uses a provider HTTP stack (`ZYVIOTV-Player/0.1 (Android)` user agent, cross-protocol redirects, bounded timeouts), retries an unparseable extensionless/`.ts` stream once as HLS, recovers `BEHIND_LIVE_WINDOW`, and maps errors (HTTP 401/403/404, network, codec, cleartext) to explicit messages.
- Logcat `tag:ZyvioPlayback` records launch/blocked/attempt/failure events without URL, title or credentials.

Not yet verified: real provider streams on Pixel 7 emulator, physical phone, tablet and TV; codec coverage; Xtream HTTP 512 provider issue.

## Bloc #208 — M3U playback sources (October 2026)

- A blank `streamUrl` can only come from the pre-#206 SharedPreferences JSON cache, which never stored stream URLs. It is now restored as `CatalogCacheOrigin.LegacyWithoutSources`: browsable, never playable (`Ready.sourcesPending`, French `SourcesPending` message) until a refresh succeeds.
- `OfflineContentCache.saveCatalog` replaces the encrypted cache only with a catalog whose every channel, film and indexed episode has a source (`CatalogSourceReport`). Read and write failures are logged under `ZyvioCatalog` with counts and exception types only.
- The on-disk format of #206 is unchanged; it moved to `EncryptedCatalogFile` so the full parse → encrypt/gzip → restore → play path runs in JVM tests (`M3uPlaybackSourcePipelineTest`).
- Provider access headers from M3U playlists (`url|User-Agent=…&Referer=…`, `#EXTVLCOPT:http-user-agent`/`http-referrer`) are kept in the stream line (`PlaybackSource`) and sent by Media3 as HTTP headers. Only User-Agent, Referer and Origin are honoured; header values are never logged.
- Logcat: `tag:ZyvioCatalog` shows `catalog event=catalog_cache_sources origin=… playable=… live=a/b movies=c/d episodes=e/f`.
- Not verified yet: real playback on the Pixel 7 with the provider.

## Phase #209 — Android player reliability (October 2026)

- Back: the top Retour button and the system back share `PlayerBackPolicy`
  (close panel → cancel channel number → save progress and exit). The player
  route pops once, guarded by `PlayerExitNavigation.shouldPop`.
- Media3 lifecycle: one `ExoPlayer` per stream, released exactly once in
  `DisposableEffect(player)`; the `PlayerView` detaches in `onRelease`;
  commands are token-gated (`PlayerCommandGate`) so a new player never replays
  the previous command; ON_START resumes only what was playing
  (`PlayerResumePolicy`).
- Controls: `safeDrawing` insets, single-line title, icon-only actions below
  600 dp, horizontally scrollable action row, elapsed/total time, no "Suivant"
  for films. Phones/tablets get a real full screen (hidden system bars,
  sensor landscape, restored on exit) and Fit/Zoom picture modes; video is
  never stretched.
- Tracks: explicit selection by Media3 track id (`TrackSelectionOverride`).
- HEVC: decoder fallback enabled; decoder failures log
  `mime=… codec=… emulator=…` and show an emulator-specific French message.
  An emulator HEVC failure is not, by itself, an app defect: confirm on a
  physical device.
- Tests: `PlayerLifecycleTest` (JVM) and `tests/test_android_player_209.py`.
  Not verified on Pixel 7, physical phone, TV or with a real provider.

