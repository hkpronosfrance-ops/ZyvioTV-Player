"""PR #217 — faster first synchronisation and session refresh (static contract)."""

import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main/java/fr/zyviotv/player"


def read(path: pathlib.Path) -> str:
    return path.read_text(encoding="utf-8")


class AndroidSync217Test(unittest.TestCase):
    def test_m3u_is_parsed_while_downloading_without_slowing_the_download(self):
        client = read(APP / "data/m3u/AndroidM3uClient.kt")
        streaming = client.split("suspend fun importStreaming(", 1)[1].split("    private fun download(", 1)[0]
        self.assertIn("onAttemptStart()", streaming)
        self.assertIn("M3uParser.parseLinesDetailed(lines, Int.MAX_VALUE, onEntry)", streaming)
        # PR #218: the socket is read by its own writer into a temporary file;
        # the parser follows that file, so it never slows the download.
        self.assertIn("val writer = async(Dispatchers.IO)", streaming)
        self.assertIn("TailingInputStream(file, progress, job)", streaming)
        self.assertNotIn("connection.inputStream).bufferedReader", streaming)
        # A truncated or cut body never becomes a success.
        self.assertIn("counted.count < expectedBytes) throw M3uTruncatedException()", streaming)
        self.assertIn("progress.failure?.let { throw it }", streaming)
        self.assertIn("if (report.danglingMetadata) throw M3uTruncatedException()", streaming)
        self.assertLess(streaming.index("progress.failure?.let { throw it }"), streaming.index("M3uStreamingResult.Success("))
        self.assertIn("file.delete()", streaming)
        self.assertIn('phase("m3u_stream"', streaming)
        self.assertIn('phase("m3u_download"', streaming)
        self.assertIn("THREAD_PRIORITY_BACKGROUND", client)

    def test_a_retried_download_starts_a_new_catalogue_builder(self):
        session = read(APP / "ui/catalog/ProviderCatalogSession.kt")
        self.assertIn("onAttemptStart = { builder = M3uCatalogMapper.builder(idIncumbents = idAliases) }", session)

    def test_home_is_not_delayed_by_the_store_write(self):
        session = read(APP / "ui/catalog/ProviderCatalogSession.kt")
        self.assertNotIn("catalogStore.writeRefreshNow(", session)
        self.assertIn("catalogStore.persistRefresh(", session)
        store = read(APP / "data/store/CatalogStore.kt")
        persist = store.split("fun persistRefresh(", 1)[1].split("    /**", 1)[0]
        self.assertIn("scope.launch", persist)
        self.assertIn("writeRefreshNow(profileId, rawCatalog, fetchedAtEpochMs, idAliases)", persist)
        self.assertIn("THREAD_PRIORITY_BACKGROUND", store)

    def test_profiles_and_parental_rules_refresh_an_expired_session(self):
        client = read(APP / "data/network/SupabaseRestClient.kt")
        self.assertIn("shouldRefreshBeforeRequest(sessionState)", client)
        self.assertIn("shouldRetryUnauthorized(response.code, refreshed)", client)
        self.assertIn("sessionRefreshMutex.withLock", client)
        for name in ("data/settings/ProfileRepository.kt", "data/settings/ParentalControlsRepository.kt"):
            self.assertIn("SupabaseRestClient(sessionStore)", read(APP / name), name)
        profiles = read(APP / "data/settings/ProfileRepository.kt")
        self.assertNotIn("HttpURLConnection", profiles)
        # One refresh at a time app-wide: the cloud sync uses the same lock.
        cloud = read(APP / "data/sync/SupabaseCloudSyncRepository.kt")
        self.assertIn("SupabaseRestClient.sessionRefreshMutex.withLock", cloud)
        self.assertNotIn("val sessionRefreshMutex = Mutex()", cloud)

    def test_rest_client_never_logs_tokens_or_bodies(self):
        client = read(APP / "data/network/SupabaseRestClient.kt")
        for line in client.splitlines():
            if "Log." in line or "println" in line:
                self.fail("direct log in SupabaseRestClient: " + line.strip())
        self.assertIn("NetworkDiagnostics::supabaseResponse", client)


if __name__ == "__main__":
    unittest.main()
