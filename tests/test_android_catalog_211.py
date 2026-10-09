"""PR #211: catalogue telemetry, useless work, stability, integrity and UX.

Pixel 7 audit (09/10/2026): a full catalogue resynchronisation started right
after a valid cache had been restored, the offline check decoded the whole
cache on the main thread, the Films/Séries/Live states were built on the
main thread, grids showed no poster, and the Fit/Fill control looked like
the fullscreen control. These contracts keep the fixes in place.
"""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main/java/fr/zyviotv/player"


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


class AndroidCatalog211Test(unittest.TestCase):
    def test_refresh_is_decided_by_the_policy_and_waits_for_playback(self):
        session = read(APP / "ui/catalog/ProviderCatalogSession.kt")
        decide = session.index("CatalogRefreshPolicy.decide(")
        idle = session.index("PlaybackActivity.tracker.awaitIdle()")
        refresh = session.index("refreshCatalog(", decide)
        self.assertLess(decide, idle)
        self.assertLess(idle, refresh)
        policy = read(APP / "data/catalog/CatalogRefreshPolicy.kt")
        self.assertIn("MAX_AUTOMATIC_AGE_MS: Long = 12L * 60L * 60L * 1_000L", policy)

    def test_metadata_is_written_only_after_the_catalogue_file(self):
        cache = read(APP / "data/cache/OfflineContentCache.kt")
        save = cache.split("fun saveCatalog(", 1)[1].split("\n    fun ", 1)[0]
        self.assertLess(save.index("encryptedFile.write("), save.index("writeMetadata("))

    def test_offline_check_never_runs_on_the_main_thread(self):
        splash = read(APP / "ui/startup/StartupSplashScreen.kt")
        block = splash.split("canUseOffline = {", 1)[1][:400]
        self.assertIn("withContext(Dispatchers.IO)", block)

    def test_catalogue_screen_states_are_built_off_the_main_thread(self):
        app = read(APP / "ui/ZyvioTVPlayerApp.kt")
        for call in ("toLiveState()", "toMoviesState(", "toSeriesState("):
            self.assertNotIn(call, app)
        cache = read(APP / "ui/catalog/CatalogUiStateCache.kt")
        self.assertIn("withContext(dispatcher)", cache)
        self.assertIn("Dispatchers.Default", cache)

    def test_grids_show_posters_without_requests_for_blank_urls(self):
        for screen in ("ui/movies/MoviesScreen.kt", "ui/series/SeriesScreen.kt"):
            self.assertIn("CatalogPosterImage(", read(APP / screen))
        poster = read(APP / "ui/catalog/CatalogPosterImage.kt")
        self.assertIn("takeIf { it.isNotEmpty() }", poster)
        self.assertIn("if (posterUrl != null)", poster)

    def test_scale_control_cannot_be_mistaken_for_fullscreen(self):
        screen = read(APP / "ui/player/PlayerScreen.kt")
        self.assertNotIn("ZoomOutMap", screen)
        self.assertIn("Mode d'image : ajuster", screen)
        self.assertIn("Mode d'image : remplir", screen)
        self.assertIn('"Plein écran"', screen)

    def test_no_buffer_tuning_before_the_new_telemetry(self):
        for path in (APP / "ui/player").glob("*.kt"):
            self.assertNotIn("LoadControl", read(path), path.name)

    def test_playback_summary_is_logged_once_per_playback(self):
        player = read(APP / "ui/player/NativeVideoPlayer.kt")
        self.assertEqual(1, player.count("PlaybackDiagnostics.session("))
        monitor = read(APP / "ui/diagnostics/FrameStatsMonitor.kt")
        for periodic in ("postDelayed", "Timer(", "scheduleAtFixedRate", "while (true)"):
            self.assertNotIn(periodic, monitor)

    def test_new_diagnostics_never_log_raw_text(self):
        stats = read(APP / "ui/player/PlaybackSessionStats.kt")
        self.assertIn("SENSITIVE_FRAGMENTS", stats)
        network = read(APP / "data/network/NetworkDiagnostics.kt")
        xtream = network.split("internal fun xtreamLine(", 1)[1].split("internal fun contentCategory", 1)[0]
        self.assertNotIn("$url", xtream)
        self.assertIn("safeEndpoint(url)", xtream)
        line = network.split("internal fun supabaseLine(", 1)[1].split("internal fun supabaseErrorCode", 1)[0]
        self.assertNotRegex(line, re.compile(r"\$\{?responseBody\b"))


if __name__ == "__main__":
    unittest.main()
