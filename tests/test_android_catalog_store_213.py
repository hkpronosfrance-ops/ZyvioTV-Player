"""PR #213 (bloc Room, PR A): generational catalogue store, filled in the background.

Pixel 7 measurements (09/10/2026): reading the V1 file cache takes 35 to 77 s
because the whole AES-GCM(gzip) file must be decoded before any screen. PR A
adds a Room store (generations, field-encrypted stream URLs) and fills it
from the V1 cache and from validated refreshes. The app still reads the V1
cache (PR B switches the screens), and the V1 cache is never deleted here.
These contracts keep those guarantees in place.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main/java/fr/zyviotv/player"
STORE = APP / "data/store"


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


class AndroidCatalogStore213Test(unittest.TestCase):
    def test_room_is_wired_with_exported_schemas_and_no_large_heap(self):
        gradle = read(ROOT / "app/build.gradle.kts")
        self.assertIn('id("com.google.devtools.ksp")', gradle)
        self.assertIn('id("androidx.room")', gradle)
        self.assertIn('ksp("androidx.room:room-compiler:$roomVersion")', gradle)
        self.assertIn('schemaDirectory("$projectDir/schemas")', gradle)
        manifest = read(ROOT / "app/src/main/AndroidManifest.xml")
        self.assertNotIn("largeHeap", manifest)
        self.assertIn('android:allowBackup="false"', manifest)

    def test_store_never_runs_queries_on_the_main_thread(self):
        for path in STORE.glob("*.kt"):
            self.assertNotIn("allowMainThreadQueries", read(path), path.name)
        store = read(STORE / "CatalogStore.kt")
        self.assertIn("Dispatchers.IO", store)
        self.assertIn("playback.awaitIdle()", store)

    def test_stream_urls_are_only_stored_encrypted(self):
        entities = read(STORE / "CatalogStoreEntities.kt")
        for forbidden in ("stream_url", "streamUrl"):
            self.assertNotIn(forbidden, entities)
        self.assertEqual(entities.count("val urlBlob: ByteArray"), 3)
        cipher = read(STORE / "CatalogUrlCipher.kt")
        self.assertIn('"zyviotv_player_catalog_url_key"', cipher)
        self.assertIn("AES/GCM/NoPadding", cipher)
        self.assertIn("updateAAD(associatedData(generationId, kind, id))", cipher)

    def test_generation_is_activated_in_one_transaction_after_verification(self):
        writer = read(STORE / "CatalogGenerationWriter.kt")
        verify = writer.index("val mismatch = verify(")
        switch = writer.index("retired = dao.retireActive(source.profileKey)")
        self.assertLess(verify, switch)
        self.assertIn("check(dao.markActive(generationId) == 1)", writer)
        self.assertIn("if (!catalog.isPlayable)", writer)
        self.assertIn("runCatching { deleteGeneration(generationId) }", writer)

    def test_v1_cache_is_never_deleted_by_pr_a(self):
        for path in STORE.glob("*.kt"):
            text = read(path)
            self.assertNotIn("catalogFile(", text, path.name)
            self.assertNotIn("offline-catalogs", text, path.name)
        cache = read(APP / "data/cache/OfflineContentCache.kt")
        self.assertNotIn("CatalogStore", cache)

    def test_session_mirrors_raw_refreshes_and_the_restored_cache(self):
        session = read(APP / "ui/catalog/ProviderCatalogSession.kt")
        self.assertIn("catalogStore.mirrorRestoredCache(profileId, restored)", session)
        mirror = session.split("catalogStore.mirrorRefresh(", 1)[1][:600]
        self.assertIn("snapshot = loaded.snapshot", mirror)
        self.assertIn("seriesDetails = allDetails", mirror)
        # The V1 cache keeps the profile-filtered catalogue, unchanged.
        self.assertIn("snapshot = filtered,", session)

    def test_store_logs_carry_counts_only(self):
        store = read(STORE / "CatalogStore.kt")
        for line in store.splitlines():
            if "CatalogPerformanceDiagnostics.event(" in line or "fields =" in line:
                for forbidden in ("Url", "url", "title", "playlistName", "profileId"):
                    self.assertNotIn(forbidden, line)

    def test_ci_runs_these_contracts(self):
        workflow = read(ROOT / ".github/workflows/android-ci.yml")
        self.assertIn("test_android_catalog_store_213.py", workflow)


if __name__ == "__main__":
    unittest.main()
