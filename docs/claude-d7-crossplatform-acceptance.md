# ZYVIOTV — D7 acceptance matrix (Claude Design source audit)

Reference bundle: `ZYVIOTV D3 M3UXtream (5).zip`, supplied by the project owner.
Sources: `D6_Final_Design_Handoff/ZYVIOTV D6 Mapping Integration.md`,
`D7_Final_QA_Release_Readiness/ZYVIOTV D7 Registre Anomalies.md`,
`ZYVIOTV D6 QA Checklist.md` and `ZYVIOTV D7 Matrice Recette Multiplateforme.md`.

**Status: visual parity is NOT certified.** The bundle is a design prototype, not a
recording of a compiled application. CI success cannot validate real TV remote
navigation, codecs, overscan, frame pacing, or rendered pixel parity.

## Cross-platform sign-off matrix

| Scenario | Android phone/tablet | Android TV | iPhone/iPad | Samsung Tizen | LG webOS |
|---|---|---|---|---|---|
| Auth / session persistence / relaunch | Device QA | Device QA | Device QA | Device QA | Device QA |
| Playlist add M3U / Xtream, full Live+Movies+Series | Device QA | Device QA | Device QA | Device QA | Device QA |
| Film details / poster fallback / play / resume | Screenshot + playback | Remote + playback | Screenshot + playback | Remote + playback | Remote + playback |
| Series seasons / genuine episodes / next episode | Screenshot + playback | Remote + playback | Screenshot + playback | Remote + playback | Remote + playback |
| EPG now / timezone / programme details | Device QA | Remote QA | Device QA | Remote QA | Remote QA |
| Search / filters / locked content / favorites | Device QA | Remote QA | Device QA | Remote QA | Remote QA |
| Profile-scope and playlist-scope watch progress | Backend + device QA | Backend + remote | Backend + device QA | Backend + remote | Backend + remote |
| Reduced motion / long titles / 130% text scale | Device QA | Device QA | Device QA | Device QA | Device QA |
| Focus 4px white + 4px offset, back-navigation | N/A | Remote QA | N/A | Remote QA | Remote QA |
| 720p / 1080p / 4K safe-area visibility | N/A | Screenshot QA | N/A | Screenshot QA | Screenshot QA |

## D7 open design anomalies — tracked, not silently marked fixed

- A-D6-01 selected chips across frozen D5 demo vs D6 reference.
- A-D6-02 fifth tab inconsistency (Search vs Favorites): D6 wins.
- A-D6-03 inactive tab color mismatch in frozen D5.
- A-D6-04 roughly 218 hard-coded French strings in historical demos:
  production localization parity needs audit.
- A-D7-06 relative i18n asset paths in design demo need app mapping.

## Physical-platform blockers (D7 register)

- R-01 native MPEG-TS handling on iOS AVPlayer.
- R-02 external subtitles on selected Tizen models.
- R-03 DTS / E-AC-3 codec compatibility on Samsung and LG.
- R-04 TV memory with 10,000+ catalog items.
- R-05 system reduce-motion preferences on Tizen / webOS.
- R-06 real TV overscan / safe area on hardware.

## Acceptance procedure

1. Capture matching routes on Claude D6 mobile/tablet/TV prototype and
   compiled application in matching dimensions; annotate notable differences.
2. Run navigation and access tests on physical devices (not only browser emulation).
3. Check network input variants: M3U Live/Movie/Series, Xtream Live/Movie/Series;
   series episodes must actually occur in the input data.
4. Test every row above, record device model, OS version, date, screenshots,
   expected and observed results and a linked GitHub issue for every failure.
5. Pass only after user-reviewed visual comparisons and platform QA.
   Do not claim pixel-perfect parity from source-level CI tests.
