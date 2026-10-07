# Samsung TV physical QA checklist

This checklist is required before declaring the Tizen build production-ready.

## Prerequisites

- Samsung TV in Developer Mode
- TV and development machine on the same network
- Tizen Studio / CLI installed
- Samsung certificate profile configured locally
- TV registered in Tizen Device Manager
- No certificate/private key stored in the repository

## Build and install

```bash
bash tizenApp/package-wgt.sh "<security-profile>" "<device-name>"
```

Expected:
- web build completes
- signed `.wgt` is produced under `tizenApp/.buildResult/`
- package installs on the selected TV
- application launches without a certificate error

## Remote and focus

Verify with the physical Samsung remote:
- D-pad moves focus predictably
- focused state is white
- Enter activates cards/buttons
- Back closes dialogs first
- Back exits playback before exiting the app
- Back from secondary panels returns toward Home
- Back from the root exits the app
- Red key toggles favorites outside Devices
- Red key disconnects a non-current device in Devices
- Play/Pause works during playback
- Stop exits playback
- Fast-forward / rewind seek VOD and episodes
- seek keys do not attempt to seek live TV

## Playback / AVPlay

Verify at least:
- one Xtream live stream
- one movie
- one series episode
- resume from saved progress
- stop and reopen
- app background/foreground
- stream failure handling
- channel switch
- short EPG current-program display

## Account and profiles

Verify:
- cold-start session restore
- manual sign-in
- sign-out
- profile picker
- remembered profile
- Child profile cannot exit without PIN
- account suspension blocks the shell
- blocking maintenance blocks the shell
- planned maintenance can be continued past

## Parental controls

Verify:
- adult categories never appear on Child profile
- explicit category/content locks are enforced
- weekday/weekend limit
- schedule window
- 30-minute PIN exception
- exception is scoped to current content
- simultaneous playback does not double-count screen time
- temporary network loss keeps local high-water usage

## Devices

Verify:
- current Samsung TV registers once
- last-seen refreshes
- current TV is labelled as current
- rename works
- another device can be disconnected
- current TV cannot disconnect itself

## Library

Verify:
- movie favorite
- series favorite
- live favorite
- Continue Watching
- recent movie/episode history
- Recent Channels
- profile isolation

## Exit criteria

Tizen can move to store-submission preparation only when:
- all CI checks are green on the exact release commit
- the signed WGT installs successfully
- no blocking issue remains on physical Samsung hardware
- no provider credential, PIN or signing secret appears in logs/source/UI
