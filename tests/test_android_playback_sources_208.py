"""Bloc #208: M3U playback sources from the playlist line to Media3.

Source contracts guarding the Pixel 7 regression where every play action
logged `ZyvioPlayback blocked reason=missingsource`: a pre-#206 JSON cache
(which never stored stream URLs) was restored as a playable catalog.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main/java/fr/zyviotv/player"
SHARED = ROOT / "shared/src/commonMain/kotlin/fr/zyviotv/player/shared"


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


class AndroidPlaybackSources208Test(unittest.TestCase):
    def test_legacy_cache_is_marked_as_sourceless(self):
        cache = read(APP / "data/cache/OfflineContentCache.kt")
        legacy = cache.split("private fun loadLegacyCatalog", 1)[1].split("fun saveLibrary", 1)[0]
        self.assertIn("origin = CatalogCacheOrigin.LegacyWithoutSources", legacy)
        self.assertIn("origin == CatalogCacheOrigin.Encrypted && sourceReport.isPlayable", cache)

    def test_cache_is_replaced_only_by_a_validated_catalog(self):
        cache = read(APP / "data/cache/OfflineContentCache.kt")
        save = cache.split("fun saveCatalog", 1)[1].split("fun loadCatalog", 1)[0]
        self.assertLess(save.index("report.isPlayable"), save.index("encryptedFile.write"))
        self.assertIn("catalog_persist_failed", save)
        self.assertIn("catalog_cache_unreadable", cache)
        # The legacy JSON writer is gone: nothing may persist a sourceless catalog.
        self.assertNotIn("CatalogSnapshot.toJson", cache)

    def test_restored_catalog_carries_its_source_state(self):
        session = read(APP / "ui/catalog/ProviderCatalogSession.kt")
        self.assertEqual(session.count("sourcesPending = !cached.isPlayable"), 2)
        self.assertIn("!raw.sourceReport.isPlayable", session)
        self.assertIn("sourcesPending = sourcesPending,", session)
        app = read(APP / "ui/ZyvioTVPlayerApp.kt")
        self.assertIn("catalogSourcesPending = (providerState as? ProviderCatalogState.Ready)?.sourcesPending == true", app)

    def test_movie_detail_plays_the_current_snapshot_source(self):
        app = read(APP / "ui/ZyvioTVPlayerApp.kt")
        self.assertIn("?.firstOrNull { it.id == movie.id }", app)
        self.assertIn("streamUrl = playable.streamUrl", app)

    def test_media3_receives_url_and_provider_headers_separately(self):
        player = read(APP / "ui/player/NativeVideoPlayer.kt")
        self.assertIn(".setUri(PlaybackSource.parse(request.streamUrl).url)", player)
        self.assertIn("providerHttpDataSourceFactory(PlaybackSource.parse(request.streamUrl).headers)", player)
        self.assertNotIn(".setUri(request.streamUrl)", player)

    def test_parser_keeps_playlist_access_headers(self):
        parser = read(SHARED / "m3u/M3uParser.kt")
        self.assertIn("#EXTVLCOPT:", parser)
        self.assertIn("PlaybackSource.compose(streamUrl.trim(), headers)", parser)

    def test_source_diagnostics_never_log_urls(self):
        integrity = read(APP / "data/cache/CatalogSourceIntegrity.kt")
        self.assertNotIn("streamUrl}", integrity)
        cache = read(APP / "data/cache/OfflineContentCache.kt")
        for line in cache.splitlines():
            if "CatalogPerformanceDiagnostics.event" in line or "fields =" in line:
                self.assertNotIn("streamUrl", line)
                self.assertNotIn("playlistName", line)


if __name__ == "__main__":
    unittest.main()
