package fr.zyviotv.player.data.store

import fr.zyviotv.player.data.cache.CatalogFileStamp
import fr.zyviotv.player.data.cache.RestoredCatalog
import fr.zyviotv.player.data.catalog.PlaybackActivityTracker
import fr.zyviotv.player.data.store.CatalogStore.Companion.v1ImportSkipReason
import fr.zyviotv.player.data.store.CatalogStoreFixtures.catalog
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CatalogStoreTest {
    private lateinit var database: CatalogStoreDatabase
    private lateinit var playback: PlaybackActivityTracker
    private lateinit var job: Job
    private lateinit var store: CatalogStore
    private val dao get() = database.dao()

    @Before
    fun setUp() {
        database = CatalogStoreFixtures.inMemoryDatabase()
        playback = PlaybackActivityTracker()
        job = Job()
        val keyWrapper = CatalogStoreFixtures.softwareKeyWrapper()
        store = CatalogStore(
            writer = CatalogGenerationWriter(database, keyWrapper),
            reader = CatalogStoreReader(dao, keyWrapper),
            dao = dao,
            databaseFile = null,
            playback = playback,
            scope = CoroutineScope(job + Dispatchers.IO),
        )
    }

    @After
    fun tearDown() {
        job.cancel()
        database.close()
    }

    @Test
    fun profileKeyIsTheSameDigestAsTheV1CacheFileName() {
        val expected = MessageDigest.getInstance("SHA-256")
            .digest("profile-1".toByteArray())
            .joinToString("") { "%02x".format(it) }
        assertEquals(expected, CatalogStore.profileKey("profile-1"))
    }

    @Test
    fun restoredV1CacheIsImportedOnceThenSkipped() = runBlocking {
        val restored = restored(stamp(1))

        store.mirrorRestoredCache("profile-1", restored)
        awaitJobs()
        val first = requireNotNull(dao.activeGeneration(CatalogStore.profileKey("profile-1"), "playlist-1"))
        assertEquals("v1_import", first.origin)
        assertEquals("profile_filtered", first.scope)

        store.mirrorRestoredCache("profile-1", restored)
        awaitJobs()
        assertEquals(first.id, dao.activeGeneration(CatalogStore.profileKey("profile-1"), "playlist-1")?.id)
    }

    @Test
    fun nothingStartsWhilePlaybackIsActive() = runBlocking {
        val player = playback.begin()
        store.mirrorRestoredCache("profile-1", restored(stamp(1)))
        delay(300)
        assertNull(dao.activeGeneration(CatalogStore.profileKey("profile-1"), "playlist-1"))

        player.close()
        awaitJobs()
        assertEquals("v1_import", dao.activeGeneration(CatalogStore.profileKey("profile-1"), "playlist-1")?.origin)
    }

    @Test
    fun aRefreshWrittenNowSupersedesAPendingV1Import() = runBlocking {
        val player = playback.begin()
        store.mirrorRestoredCache("profile-1", restored(stamp(1)))
        val written = store.writeRefreshNow("profile-1", catalog(live = 7), 6_000L, emptyMap())
        player.close()
        awaitJobs()

        assertEquals(true, written)

        val active = requireNotNull(dao.activeGeneration(CatalogStore.profileKey("profile-1"), "playlist-1"))
        assertEquals("refresh", active.origin)
        assertEquals(6_000L, active.fetchedAtEpochMs)
        assertEquals(7, dao.countLiveChannels(active.id))
        assertEquals(emptyList<Long>(), dao.inactiveGenerationIds())
    }

    @Test
    fun v1ImportPolicyNeverReplacesANewerOrIdenticalGeneration() {
        val v1 = source(GenerationOrigin.V1Import, fetchedAt = 2_000L, stamp = stamp(1))
        assertNull(v1ImportSkipReason(active = null, source = v1))
        assertEquals("already_mirrored", v1ImportSkipReason(generation("refresh", 2_000L, stamp(1)), v1))
        assertNull(v1ImportSkipReason(generation("v1_import", 1_000L, stamp(9)), v1))
        assertEquals("store_newer", v1ImportSkipReason(generation("refresh", 3_000L, null), v1))
        assertEquals("store_newer", v1ImportSkipReason(generation("refresh", 3_000L, null), v1.copy(fetchedAtEpochMs = null)))
        // A V1 file written by a later refresh whose store write failed.
        assertNull(v1ImportSkipReason(generation("refresh", 1_000L, null), v1))
    }

    @Test
    fun restoredCatalogueCarriesReferencesThatResolveToTheStoredUrls() = runBlocking {
        store.writeRefreshNow("profile-1", catalog(live = 3, movies = 2, series = 1, episodesPerSeries = 2), 7_000L, emptyMap())

        val restored = requireNotNull(store.restore("profile-1"))
        val channel = restored.catalog.snapshot.liveChannels[1]
        assertEquals(true, CatalogSourceRef.isRef(channel.streamUrl))
        assertEquals(CatalogStoreFixtures.liveUrl(1), store.resolveSource(channel.streamUrl))
        assertEquals(CatalogStoreFixtures.movieUrl(1), store.resolveSource(restored.catalog.snapshot.movies[1].streamUrl))
        val detail = requireNotNull(store.seriesDetail(restored.generation.id, "playlist-1", "series-0"))
        assertEquals(CatalogStoreFixtures.episodeUrl(0, 1), store.resolveSource(detail.episodes[1].streamUrl))
        // A plain URL (fresh refresh in memory) is returned unchanged.
        assertEquals(CatalogStoreFixtures.movieUrl(9), store.resolveSource(CatalogStoreFixtures.movieUrl(9)))
        assertNull(store.resolveSource("zyvio-store:broken"))
        assertEquals(7_000L, restored.generation.fetchedAtEpochMs)
        assertEquals(true, store.hasActiveGeneration("profile-1"))
        assertEquals(false, store.hasActiveGeneration("profile-2"))
    }

    private suspend fun awaitJobs() {
        job.children.toList().forEach { it.join() }
    }

    private fun restored(stamp: CatalogFileStamp) = RestoredCatalog(
        catalog = catalog(),
        fetchedAtEpochMs = 1_000L,
        idAliases = emptyMap(),
        fileStamp = stamp,
    )

    private fun stamp(seed: Int) = CatalogFileStamp(100L + seed, ByteArray(32) { seed.toByte() })

    private fun source(origin: GenerationOrigin, fetchedAt: Long?, stamp: CatalogFileStamp?) =
        CatalogStoreFixtures.source(origin = origin, fetchedAtEpochMs = fetchedAt).copy(v1Stamp = stamp)

    private fun generation(origin: String, fetchedAt: Long?, stamp: CatalogFileStamp?) = GenerationEntity(
        id = 1,
        profileKey = CatalogStoreFixtures.PROFILE_KEY,
        playlistId = "playlist-1",
        playlistName = "Playlist test",
        state = GenerationState.Active.wire,
        scope = GenerationScope.Raw.wire,
        origin = origin,
        fetchedAtEpochMs = fetchedAt,
        createdAtEpochMs = 0L,
        liveCount = 0,
        movieCount = 0,
        seriesCount = 0,
        episodeCount = 0,
        v1Length = stamp?.length,
        v1Sha256 = stamp?.sha256?.joinToString("") { "%02x".format(it) },
        wrappedKey = ByteArray(0),
    )
}
