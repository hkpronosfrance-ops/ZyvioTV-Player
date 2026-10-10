"""Bloc Room PR B: the screens read the catalogue from the Room store.

Pixel 7 measurements (09-10/10/2026): reading the V1 file cache took 35 to
77 s before any screen. PR B restores titles, ids and artwork from Room,
keeps stream URLs encrypted until playback, reads episodes per series and
deletes the V1 file cache only once a complete generation is active.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main/java/fr/zyviotv/player"
STORE = APP / "data/store"


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


class AndroidCatalogRoomReads214Test(unittest.TestCase):
    def test_startup_reads_the_store_before_the_v1_file(self):
        session = read(APP / "ui/catalog/ProviderCatalogSession.kt")
        restore = session.split("private fun restoreCatalogForSession(", 1)[1].split("\nprivate fun ", 1)[0]
        self.assertLess(restore.index("catalogStore.restore(profileId)"), restore.index("offlineCache.restoreCatalog(profileId)"))
        self.assertIn('name = "catalog_ready"', restore)
        self.assertEqual(session.count("restoreCatalogForSession(profileId, catalogStore, offlineCache)"), 2)

    def test_v1_cache_is_deleted_only_after_an_active_generation(self):
        session = read(APP / "ui/catalog/ProviderCatalogSession.kt")
        restore = session.split("private fun restoreCatalogForSession(", 1)[1].split("\nprivate fun ", 1)[0]
        self.assertLess(restore.index("val stored = catalogStore.restore(profileId)"), restore.index("offlineCache.deleteCatalogFiles(profileId)"))
        # PR #217: written in the background by CatalogStore.persistRefresh.
        refresh = session.split("catalogStore.persistRefresh(", 1)[1][:1600]
        self.assertLess(refresh.index("if (stored) {"), refresh.index("offlineCache.deleteCatalogFiles(profileId)"))
        self.assertLess(refresh.index("} else {"), refresh.index("offlineCache.saveCatalog("))
        self.assertEqual(session.count("offlineCache.deleteCatalogFiles(profileId)"), 2)

    def test_startup_read_never_loads_encrypted_urls_or_episodes(self):
        dao = read(STORE / "CatalogStoreDao.kt")
        for query in ("fun liveRows", "fun movieRows", "fun seriesRows", "fun episodeRows"):
            block = dao.split(query, 1)[0].rsplit("@Query(", 1)[1]
            self.assertNotIn("url_blob", block, query)
            self.assertNotIn("SELECT *", block, query)
        reader = read(STORE / "CatalogStoreReader.kt")
        latest = reader.split("private fun load(", 1)[1].split("    /** Episodes of one series", 1)[0]
        self.assertNotIn("episodeRows", latest)
        self.assertNotIn("UrlBlob", latest)

    def test_playback_resolves_references_before_the_policy(self):
        app = read(APP / "ui/ZyvioTVPlayerApp.kt")
        start = app.split("    fun startPlayback(", 1)[1].split("\n    fun ", 1)[0]
        self.assertIn("CatalogSourceRef.isRef(request.streamUrl)", start)
        self.assertLess(start.index("catalogStore.resolveSource(request.streamUrl)"), start.index("launchResolvedPlayback(request.copy(streamUrl = resolved)"))
        self.assertIn("PlaybackLaunchPolicy.decide(", app.split("fun launchResolvedPlayback(", 1)[1][:400])
        zap = app.split("fun zapLiveChannel(", 1)[1][:900]
        self.assertIn("it.id == currentLiveChannelId", zap)

    def test_episodes_come_from_the_local_store_first(self):
        loader = read(APP / "data/catalog/AndroidSeriesDetailLoader.kt")
        self.assertEqual(loader.count("M3uSeriesDetailRegistry.loadLocal(seriesId)"), 2)
        self.assertNotIn("M3uSeriesDetailRegistry.load(seriesId)", loader)
        app = read(APP / "ui/ZyvioTVPlayerApp.kt")
        self.assertIn("M3uSeriesDetailRegistry.loadLocal(series.id)", app)
        registry = read(APP / "data/catalog/M3uSeriesDetailRegistry.kt")
        replace = registry.split("fun replace(", 1)[1][:200]
        self.assertIn("storedLookup = null", replace)

    def test_offline_start_accepts_an_active_generation(self):
        splash = read(APP / "ui/startup/StartupSplashScreen.kt")
        block = splash.split("canUseOffline = {", 1)[1][:900]
        self.assertIn("withContext(Dispatchers.IO)", block)
        self.assertIn("hasActiveGeneration(profileId)", block)
        self.assertIn("offlineCache.hasLibrary(profileId)", block)

    def test_source_references_carry_no_url(self):
        ref = read(STORE / "CatalogSourceRef.kt")
        self.assertIn('PREFIX = "zyvio-store:"', ref)
        self.assertNotIn("import java.util.Base64", ref)

    def test_ci_runs_these_contracts(self):
        workflow = read(ROOT / ".github/workflows/android-ci.yml")
        self.assertIn("test_android_catalog_room_reads_214.py", workflow)


if __name__ == "__main__":
    unittest.main()
