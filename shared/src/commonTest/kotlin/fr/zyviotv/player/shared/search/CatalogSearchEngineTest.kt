package fr.zyviotv.player.shared.search

import fr.zyviotv.player.shared.catalog.CatalogCategory
import fr.zyviotv.player.shared.catalog.CatalogLiveChannel
import fr.zyviotv.player.shared.catalog.CatalogMovie
import fr.zyviotv.player.shared.catalog.CatalogSeries
import fr.zyviotv.player.shared.catalog.CatalogSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CatalogSearchEngineTest {
    private val snapshot = CatalogSnapshot(
        liveCategories = listOf(CatalogCategory("live-fr", "France")),
        liveChannels = listOf(
            CatalogLiveChannel("1", "12 - Télé Évasion", "live-fr", null, "https://example/live/1"),
        ),
        movieCategories = listOf(CatalogCategory("movie-action", "Action")),
        movies = listOf(
            CatalogMovie("2", "L'Été rouge", "movie-action", null, "https://example/movie/2", "mp4"),
        ),
        seriesCategories = listOf(CatalogCategory("series-drama", "Drame")),
        series = listOf(
            CatalogSeries("3", "Élite", "series-drama", null),
        ),
    )

    @Test
    fun ignoresQueriesShorterThanTwoCharacters() {
        assertTrue(CatalogSearchEngine.search(snapshot, "e").isEmpty())
    }

    @Test
    fun searchIsCaseAndAccentInsensitive() {
        val result = CatalogSearchEngine.search(snapshot, "elite")
        assertEquals(listOf("Élite"), result.map { it.title })
    }

    @Test
    fun channelNumberCanBeSearched() {
        val result = CatalogSearchEngine.search(snapshot, "12")
        assertEquals(SearchKind.Live, result.single().kind)
        assertEquals("12", result.single().channelNumber)
    }

    @Test
    fun categoryCanBeSearched() {
        val result = CatalogSearchEngine.search(snapshot, "action")
        assertEquals(listOf("L'Été rouge"), result.map { it.title })
    }
}
