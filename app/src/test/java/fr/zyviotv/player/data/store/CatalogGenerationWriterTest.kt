package fr.zyviotv.player.data.store

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import fr.zyviotv.player.data.store.CatalogStoreFixtures.PROFILE_KEY
import fr.zyviotv.player.data.store.CatalogStoreFixtures.catalog
import fr.zyviotv.player.data.store.CatalogStoreFixtures.source
import fr.zyviotv.player.shared.catalog.CatalogMovie
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CatalogGenerationWriterTest {
    private lateinit var database: CatalogStoreDatabase
    private lateinit var keyWrapper: GenerationKeyWrapper
    private lateinit var writer: CatalogGenerationWriter
    private val dao get() = database.dao()

    @Before
    fun setUp() {
        database = CatalogStoreFixtures.inMemoryDatabase()
        keyWrapper = CatalogStoreFixtures.softwareKeyWrapper()
        writer = CatalogGenerationWriter(database, keyWrapper, clock = { 42L }, batchSize = 2)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun writesVerifiesAndActivatesACompleteGeneration() {
        val result = writer.write(
            catalog(live = 5, movies = 3, series = 2, episodesPerSeries = 4),
            source(aliases = mapOf("legacy-1" to 7L)),
        ) as GenerationWriteResult.Activated

        assertEquals(5, result.liveCount)
        assertEquals(3, result.movieCount)
        assertEquals(2, result.seriesCount)
        assertEquals(8, result.episodeCount)
        val active = requireNotNull(dao.activeGeneration(PROFILE_KEY))
        assertEquals(result.generationId, active.id)
        assertEquals("raw", active.scope)
        assertEquals("refresh", active.origin)
        assertEquals(1_000L, active.fetchedAtEpochMs)
        assertEquals(4, dao.countCategories(active.id))
        assertEquals(2, dao.countSeriesDetails(active.id))
        assertEquals(listOf(IdAliasEntity(active.id, "legacy-1", 7L)), dao.idAliases(active.id))

        val cipher = CatalogUrlCipher.restore(keyWrapper, active.wrappedKey)
        val channel = requireNotNull(dao.liveChannelAt(active.id, 3))
        assertEquals("live-3", channel.id)
        assertEquals("4 - chaine 3 4 sport", channel.searchKey)
        assertEquals(
            CatalogStoreFixtures.liveUrl(3),
            cipher.open(active.id, CatalogKind.Live, channel.id, channel.urlBlob),
        )
        val movie = requireNotNull(dao.movieById(active.id, "movie-2"))
        assertEquals("ete 2", movie.sortTitle)
        assertEquals(
            CatalogStoreFixtures.movieUrl(2),
            cipher.open(active.id, CatalogKind.Movie, movie.id, movie.urlBlob),
        )
        val episodes = dao.episodesOf(active.id, "series-1")
        assertEquals(listOf("ep-0", "ep-1", "ep-2", "ep-3"), episodes.map { it.id })
        assertEquals(
            CatalogStoreFixtures.episodeUrl(1, 2),
            cipher.open(
                active.id,
                CatalogKind.Episode,
                CatalogGenerationWriter.episodeAadId("series-1", "ep-2"),
                episodes[2].urlBlob,
            ),
        )
    }

    @Test
    fun aNewGenerationReplacesThePreviousOneAndDeletesItsRows() {
        val first = writer.write(catalog(live = 4), source()) as GenerationWriteResult.Activated
        val second = writer.write(catalog(live = 2), source(fetchedAtEpochMs = 2_000L)) as GenerationWriteResult.Activated

        assertEquals(1, second.retiredGenerations)
        assertEquals(second.generationId, dao.activeGeneration(PROFILE_KEY)?.id)
        assertNull(dao.generation(first.generationId))
        assertEquals(0, dao.countLiveChannels(first.generationId))
        assertEquals(0, dao.countEpisodes(first.generationId))
        assertEquals(2, dao.countLiveChannels(second.generationId))
    }

    @Test
    fun generationsOfAnotherProfileAreNeverRetired() {
        val other = writer.write(catalog(), source(profileKey = "other-profile")) as GenerationWriteResult.Activated
        writer.write(catalog(), source())

        assertEquals(other.generationId, dao.activeGeneration("other-profile")?.id)
    }

    @Test
    fun anInterruptedWriteKeepsThePreviousActiveGenerationIntact() {
        val previous = writer.write(catalog(live = 3), source()) as GenerationWriteResult.Activated
        var batches = 0
        try {
            writer.write(catalog(live = 9, movies = 9), source(fetchedAtEpochMs = 3_000L)) {
                batches += 1
                if (batches == 5) throw IllegalStateException("cancelled")
            }
            fail("The interrupted write must not complete")
        } catch (expected: IllegalStateException) {
            // Expected: the probe abandons the generation.
        }

        val active = requireNotNull(dao.activeGeneration(PROFILE_KEY))
        assertEquals(previous.generationId, active.id)
        assertEquals(3, dao.countLiveChannels(active.id))
        assertEquals(emptyList<Long>(), dao.inactiveGenerationIds())
    }

    @Test
    fun aCatalogueWithMissingSourcesIsRejectedWithoutTouchingTheStore() {
        val previous = writer.write(catalog(), source()) as GenerationWriteResult.Activated
        val broken = catalog().let { valid ->
            valid.copy(
                snapshot = valid.snapshot.copy(
                    movies = valid.snapshot.movies + CatalogMovie("movie-x", "Sans source", "mc1", null, "", "mkv"),
                ),
            )
        }

        val result = writer.write(broken, source(fetchedAtEpochMs = 9_000L))

        assertEquals(GenerationWriteResult.Rejected("missing_sources"), result)
        assertEquals(previous.generationId, dao.activeGeneration(PROFILE_KEY)?.id)
        assertEquals(emptyList<Long>(), dao.inactiveGenerationIds())
    }

    @Test
    fun buildingGenerationsLeftByAKilledProcessAreDiscarded() {
        val previous = writer.write(catalog(), source()) as GenerationWriteResult.Activated
        val orphan = dao.insertGeneration(
            dao.generation(previous.generationId)!!.copy(id = 0, state = GenerationState.Building.wire),
        )
        dao.insertLiveChannels(
            listOf(LiveChannelEntity(orphan, 0, "x", "x", null, null, null, "x", byteArrayOf(1))),
        )

        assertEquals(1, writer.discardInactive())
        assertNull(dao.generation(orphan))
        assertEquals(0, dao.countLiveChannels(orphan))
        assertEquals(previous.generationId, dao.activeGeneration(PROFILE_KEY)?.id)
    }

    @Test
    fun duplicatedProviderIdsDoNotBreakTheImport() {
        val valid = catalog(live = 2)
        val duplicated = valid.copy(
            snapshot = valid.snapshot.copy(liveChannels = valid.snapshot.liveChannels + valid.snapshot.liveChannels),
        )

        val result = writer.write(duplicated, source()) as GenerationWriteResult.Activated

        assertEquals(4, dao.countLiveChannels(result.generationId))
    }

    @Test
    fun streamUrlsNeverReachTheDatabaseFilesInClear() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "catalog-store-clear-text-test.db"
        context.deleteDatabase(name)
        val fileDatabase = Room.databaseBuilder(context, CatalogStoreDatabase::class.java, name)
            .allowMainThreadQueries()
            .build()
        try {
            CatalogGenerationWriter(fileDatabase, keyWrapper).write(catalog(live = 20, movies = 20), source())
            val path = context.getDatabasePath(name).path
            val bytes = listOf(File(path), File("$path-wal"))
                .filter(File::exists)
                .fold(ByteArray(0)) { all, file -> all + file.readBytes() }
            assertTrue(bytes.isNotEmpty())
            val text = String(bytes, Charsets.ISO_8859_1)
            assertFalse(text.contains("provider.example"))
            // Titles stay queryable in clear (decision #211/#212).
            assertTrue(String(bytes, Charsets.UTF_8).contains("Chaîne 7"))
        } finally {
            fileDatabase.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun volumeOfARealSizedCatalogueIsStoredCompletely() {
        // Synthetic sizes of the recetted playlist (6 074 / 12 607 / 4 160 /
        // 117 989). JVM + Robolectric timing only: not a device measurement.
        val volume = catalog(live = 6_074, movies = 12_607, series = 4_160, episodesPerSeries = 28)
        val bulkWriter = CatalogGenerationWriter(database, keyWrapper)
        val startedAt = System.nanoTime()

        val result = bulkWriter.write(volume, source()) as GenerationWriteResult.Activated

        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
        println("catalog_store_volume_jvm live=6074 movies=12607 series=4160 episodes=116480 duration_ms=$elapsedMs")
        assertEquals(6_074, dao.countLiveChannels(result.generationId))
        assertEquals(12_607, dao.countMovies(result.generationId))
        assertEquals(4_160, dao.countSeries(result.generationId))
        assertEquals(116_480, dao.countEpisodes(result.generationId))
    }
}
