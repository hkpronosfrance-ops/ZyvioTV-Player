# D7 cross-platform visual evidence pack — PR #183

This release QA process expands [the D7 acceptance matrix](claude-d7-crossplatform-acceptance.md). CI checks on source **do not** prove actual screenshots match Claude Design, or that physical devices can play every stream.

## Automated source verification

Run from repository root:

```sh
python3 scripts/d7_source_audit.py
python3 scripts/d7_source_audit.py --json
python3 -m pytest tests/test_d7_crossplatform_source_audit.py
```

The audit checks visible TV route shells (authentication, profiles, PIN, player, catalog and account), TV focus/overscan/motion rules, iOS adaptive poster layouts and key Android Compose screens. A missing route/style fails with a nonzero exit code. This is a **source-level** check, not a pixel comparison.

## Screenshot capture plan

For every route below, capture **reference** (Claude D6/D7 screen), **implementation**, and **diff notes** at the same device, orientation, pixel ratio and localization.

| Screen | 390x844 phone | 768x1024 tablet | 1920x1080 TV | 3840x2160 TV |
|---|---|---|---|---|
| Splash/auth | Reference + app | Reference + app | Reference + app | Reference + app |
| Profile chooser/PIN | Reference + app | Reference + app | Reference + app | Reference + app |
| Home/hero/shelves | Reference + app | Reference + app | Reference + app | Reference + app |
| Global search/filter | Reference + app | Reference + app | Reference + app | Reference + app |
| Live TV/player/zapping | Reference + app | Reference + app | Reference + app | Reference + app |
| EPG/program details | Reference + app | Reference + app | Reference + app | Reference + app |
| Films/film detail | Reference + app | Reference + app | Reference + app | Reference + app |
| Series/season/episodes | Reference + app | Reference + app | Reference + app | Reference + app |
| Play/resume/tracks/subtitles | Reference + app | Reference + app | Reference + app | Reference + app |
| Settings/playlists/devices | Reference + app | Reference + app | Reference + app | Reference + app |

**Evidence naming:** `<platform>_<device>_<screen>_<locale>_<reference|implementation>.png`. Attach links to the QA ticket, not copyrighted or account-specific user data. Record build SHA, date, physical device model, OS version, resolution, active playlist type, test stream format, accessibility settings and PASS/FAIL.

## Validation outcomes

- **Source gate**: automatic (this PR).
- **Rendered screenshot parity**: BLOCKED until actual captures are supplied and compared.
- **Real device codecs, remote focus, zapping, overscan and long-catalog memory**: BLOCKED until hardware QA.
- **M3U and Xtream**: test Live, films and real series episodes independently for all platforms; don't manufacture episode data.
- **Release sign-off**: requires every outstanding blocker in D7 acceptance matrix to be addressed with evidence.

No release should be described as "pixel-perfect" based solely on successful CI.
