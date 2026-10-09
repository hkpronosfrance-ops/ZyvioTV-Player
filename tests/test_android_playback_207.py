"""Bloc #207: Android Live/VOD/series playback wiring contracts.

These source contracts keep the Pixel 7 regressions from coming back:
a channel tap that only selected, and a catalog-derived "offline" flag that
silently disabled every player.
"""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
UI = ROOT / "app/src/main/java/fr/zyviotv/player/ui"


def read(relative: str) -> str:
    return (UI / relative).read_text(encoding="utf-8")


class AndroidPlayback207Test(unittest.TestCase):
    def test_channel_tap_opens_player_not_only_selection(self):
        source = read("live/LiveTvScreen.kt")
        click = re.search(r"\.clickable\(onClickLabel = [^)]*\) \{(?P<body>.*?)\n\s*\}", source, re.S)
        self.assertIsNotNone(click, "channel cards must expose a tap action")
        self.assertIn("onChannelSelected(channel)", click.group("body"))
        self.assertIn("onChannelActivated(channel)", click.group("body"))
        self.assertEqual(source.count("onChannelActivated = onTuneChannel"), 2)

    def test_live_screen_never_drops_a_tune_silently(self):
        source = read("live/LiveTvScreen.kt")
        self.assertNotIn("if (!isOffline) {\n                    if (channel.isLocked)", source)

    def test_catalog_origin_is_not_an_offline_signal(self):
        session = read("catalog/ProviderCatalogSession.kt")
        self.assertNotIn("hasPlaybackSources", session)
        self.assertNotIn("isOffline", session)
        self.assertIn("isFromCache = true", session)
        library = read("library/LibrarySession.kt")
        self.assertNotIn("isOffline", library)
        self.assertNotIn("Action indisponible hors connexion", library)

    def test_every_play_action_goes_through_launch_policy(self):
        app = read("ZyvioTVPlayerApp.kt")
        self.assertIn("PlaybackLaunchPolicy.decide(\n            request = request,\n            network = networkAvailability,", app)
        # Only startPlayback assigns a request and opens the player route.
        self.assertEqual(app.count("playbackRequest = decision.request"), 1)
        self.assertEqual(len(re.findall(r"playbackRequest = PlaybackRequest\(", app)), 0)
        self.assertEqual(app.count("navController.navigate(PLAYER_ROUTE)"), 1)
        self.assertNotIn('navigate("player")', app)
        self.assertNotIn("ready.isOffline", app)
        self.assertNotIn("readyProvider.isOffline", app)

    def test_offline_state_comes_from_device_connectivity(self):
        app = read("ZyvioTVPlayerApp.kt")
        self.assertIn("rememberNetworkAvailability()", app)
        self.assertIn("networkAvailability == NetworkAvailability.Unavailable", app)
        home = read("home/HomeScreen.kt")
        self.assertIn("val offlineMode = isOffline", home)

    def test_media3_stack_handles_iptv_redirects_and_container_fallback(self):
        player = read("player/NativeVideoPlayer.kt")
        self.assertIn(".setAllowCrossProtocolRedirects(true)", player)
        self.assertIn("PlaybackMediaTypeResolver.attempts(request)", player)
        self.assertIn("PlaybackErrorClassifier.classify(error.errorCode, httpStatus)", player)
        self.assertIn("seekToDefaultPosition()", player)

    def test_playback_logs_never_include_urls(self):
        diagnostics = read("player/PlaybackErrorClassifier.kt")
        log_lines = [line for line in diagnostics.splitlines() if "Log." in line]
        self.assertTrue(log_lines)
        for line in diagnostics.split("internal object PlaybackDiagnostics", 1)[1].splitlines():
            self.assertNotIn("streamUrl}", line)
            self.assertNotIn("${request.streamUrl", line)
            self.assertNotIn("${request.title", line)


if __name__ == "__main__":
    unittest.main()
