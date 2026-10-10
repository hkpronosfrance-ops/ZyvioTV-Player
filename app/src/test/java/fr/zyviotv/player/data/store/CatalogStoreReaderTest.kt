package fr.zyviotv.player.data.store

import fr.zyviotv.player.data.store.CatalogStoreFixtures.PROFILE_KEY
import fr.zyviotv.player.data.store.CatalogStoreFixtures.catalog
import fr.zyviotv.player.data.store.CatalogStoreFixtures.source
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CatalogStoreReaderTest {
    private lateinit var database: CatalogStoreDatabase
    private lateinit var keyWrapper: GenerationKeyWrapper
    private lateinit var writer: CatalogGenerationWriter
    private lateinit var reader: CatalogStoreReader

    @Before
    fun setUp() {
        database = CatalogStoreFixtures.inMemoryDatabase()
        keyWrapper = CatalogStoreFixtures.softwareKeyWrapper()
        writer = CatalogGenerationWriter(database, keyWrapper)
        reader = CatalogStoreReader(database.dao(), keyWrapper)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun restoredSnapshotMatchesTheCatalogueExceptForItsSources() {
        val original = catalog(live = 4, movies = 3, series = 2)
        writer.write(original, source(aliases = mapOf("legacy-1" to 5L)))

        val stored = requireNotNull(reader.latest(PROFILE_KEY))
        val snapshot = stored.catalog.snapshot

        assertEquals(original.snapshot.liveCategories, snapshot.liveCategories)
        assertEquals(original.snapshot.movieCategories, snapshot.movieCategories)
        assertEquals(original.snapshot.seriesCategories, snapshot.seriesCategories)
        assertEquals(original.snapshot.series, snapshot.series)
        assertEquals(
            original.snapshot.liveChannels.map { it.copy(streamUrl = "") },
            snapshot.liveChannels.map { it.copy(streamUrl = "") },
        )
        assertEquals(
            original.snapshot.movies.map { it.copy(streamUrl = "") },
            snapshot.movies.map { it.copy(streamUrl = "") },
        )
        assertTrue(snapshot.liveChannels.all { CatalogSourceRef.isRef(it.streamUrl) })
        assertTrue(snapshot.movies.all { CatalogSourceRef.isRef(it.streamUrl) })
        // No URL in memory, no episode index loaded at startup.
        assertFalse(snapshot.toString().contains("provider.example"))
        assertTrue(stored.catalog.seriesDetails.isEmpty())
        assertTrue(stored.catalog.isPlayable)
        assertEquals(mapOf("legacy-1" to 5L), stored.idAliases)
        assertEquals("Playlist test", stored.catalog.playlistName)
    }

    @Test
    fun episodesAreReadPerSeriesWithTheirDetail() {
        writer.write(catalog(series = 2, episodesPerSeries = 3), source())
        val stored = requireNotNull(reader.latest(PROFILE_KEY))

        val detail = requireNotNull(reader.seriesDetail(stored.generation.id, "playlist-1", "series-1"))

        assertEquals(listOf("ep-0", "ep-1", "ep-2"), detail.episodes.map { it.id })
        assertEquals(listOf("Drame", "Comédie"), detail.genres)
        assertEquals("2026", detail.year)
        assertEquals(
            CatalogStoreFixtures.episodeUrl(1, 2),
            reader.resolve(requireNotNull(CatalogSourceRef.parse(detail.episodes[2].streamUrl))),
        )
        assertNull(reader.seriesDetail(stored.generation.id, "playlist-1", "series-unknown"))
    }

    @Test
    fun aReferenceFromAReplacedGenerationResolvesInTheActiveOne() {
        writer.write(catalog(movies = 3), source())
        val old = requireNotNull(reader.latest(PROFILE_KEY))
        val ref = requireNotNull(CatalogSourceRef.parse(old.catalog.snapshot.movies[2].streamUrl))

        writer.write(catalog(movies = 3), source(fetchedAtEpochMs = 2_000L))

        assertEquals(CatalogStoreFixtures.movieUrl(2), reader.resolve(ref))
        assertNull(reader.resolve(ref.copy(id = "movie-gone")))
        assertNull(reader.resolve(ref.copy(playlistId = "other-playlist", generationId = 999L)))
    }

    @Test
    fun noActiveGenerationMeansNothingToRestore() {
        assertNull(reader.latest(PROFILE_KEY))
        assertFalse(reader.hasActiveGeneration(PROFILE_KEY))
    }
}
