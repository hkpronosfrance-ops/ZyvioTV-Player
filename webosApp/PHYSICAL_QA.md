# LG webOS physical QA checklist

This checklist is required before declaring the LG webOS build production-ready or ready for Seller Lounge submission.

## Prerequisites

- LG webOS TV with Developer Mode enabled
- TV and development machine on the same network
- current webOS CLI installed with `npm install -g @webos-tools/cli`
- TV configured with `ares-setup-device`
- Developer Mode session active
- no private key, certificate, provider credential or PIN stored in the repository

## Package and install

Run:

```bash
bash webosApp/package-ipk.sh "<device-name>"
```

Expected:
- `appinfo.json` passes `ares-package --check`
- an `.ipk` is generated under `webosApp/dist/`
- package metadata is readable with `ares-package --info`
- package installs successfully with `ares-install`
- `fr.zyviotv.player` launches with `ares-launch`
- no missing icon/app asset error occurs

## Launcher and app metadata

Verify:
- app title is ZYVIOTV Player
- small icon is sharp and correctly padded
- large icon is sharp and correctly padded
- no transparent/blank launcher tile
- app launches into the expected auth/profile flow
- Back behavior follows the app flow and exits only from root
- `requiredACG` is accepted by the installed CLI

## Remote and focus

Verify with the physical LG remote:
- D-pad movement is predictable
- focus state is white, never red
- OK activates cards/buttons
- Back closes PIN/device/system dialogs first
- Back stops playback before leaving the app
- Back from secondary panels returns toward Home
- Back from root exits the app
- red key toggles favorites outside Devices
- red key disconnects a non-current device inside Devices
- Play/Pause and Stop work during playback

## Playback

Verify at least:
- one Xtream live stream
- one movie
- one series episode
- current EPG display for live
- movie/episode resume
- stop and reopen
- channel switch
- app background/foreground
- network interruption and recovery
- unsupported/failed stream handling
- paused time does not count toward Child screen time
- buffering time does not count toward Child screen time

## Account and profiles

Verify:
- cold-start session restore
- manual sign-in
- sign-out
- primary/profile picker
- remembered profile
- profile-scoped favorites/history/progress
- Child profile cannot exit without PIN

## Parental controls

Verify:
- adult categories/content never appear on Child
- explicit category/content locks are enforced
- weekday limit
- weekend limit
- schedule window
- overnight schedule window
- 30-minute PIN exception
- exception ends when content stops/changes
- temporary network loss preserves conservative usage
- simultaneous Child playback does not double-count server usage

## Devices and system states

Verify:
- current LG TV registers once as platform=webos
- last-seen refreshes
- current TV is labelled as current
- rename works
- another device can be disconnected
- current TV cannot disconnect itself
- account suspension blocks the shell
- blocking maintenance blocks the shell
- planned maintenance can be dismissed and continued

## Home

Verify canonical order:
1. Continue Watching
2. Next Episodes
3. Recent Channels
4. Favorites
5. Recent Movies
6. Recent Series
7. Same category as last watched

Also verify:
- empty shelves are hidden
- parental filtering happens before rendering
- max 20 items per shelf
- See All appears only above 20
- focus restores after returning from content
- M3U does not show unsupported Movies/Series shelves

## Resolution and Seller Lounge

LG recommends separate 1920x1080 and 1280x720 packages for broad model coverage. Before submission:
- validate the 1920x1080 package on a UHD model/simulator
- validate the 1280x720 variant on an FHD/compatible model
- capture required Seller Lounge screenshots/assets
- upload the separate 400x400 store icon
- verify privacy/support URLs and store metadata
- complete LG content/playback policy checks

## Exit criteria

LG webOS can move to store submission only when:
- all CI checks are green on the exact release commit
- the generated IPK installs successfully
- physical LG playback/focus/back behavior passes
- no blocking issue remains on representative hardware
- no provider credential, PIN, access token or private signing/device material appears in source, logs, UI, or artifacts
