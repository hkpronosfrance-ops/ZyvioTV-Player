package fr.zyviotv.player.data.cache

import fr.zyviotv.player.data.catalog.SeriesDetailSource
import fr.zyviotv.player.data.catalog.SeriesEpisodeSource
import fr.zyviotv.player.shared.catalog.CatalogCategory
import fr.zyviotv.player.shared.catalog.CatalogLiveChannel
import fr.zyviotv.player.shared.catalog.CatalogMovie
import fr.zyviotv.player.shared.catalog.CatalogSeries
import fr.zyviotv.player.shared.catalog.CatalogSnapshot
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogCacheCodecTest {
    @Test
    fun roundTripsLargeCatalogWithoutDroppingPlaybackUrlsOrEpisodes() {
        val liveCount = 40_000
        val movieCount = 10_000
        val catalog = CachedCatalog(
            playlistId = "playlist-large",
            playlistName = "Catalogue volumineux",
            snapshot = CatalogSnapshot(
                liveCategories = listOf(CatalogCategory("live", "TV")),
                liveChannels = List(liveCount) { index ->
                    CatalogLiveChannel(
                        id = "live-$index",
                        name = "Chaîne $index",
                        categoryId = "live",
                        logoUrl = "https://images.invalid/$index.png",
                        streamUrl = "http://iptv.invalid/live/user/redacted/$index.ts",
                        epgId = "epg-$index",
                    )
                },
                movieCategories = listOf(CatalogCategory("movies", "Films")),
                movies = List(movieCount) { index ->
                    CatalogMovie(
                        id = "movie-$index",
                        title = "Film $index",
                        categoryId = "movies",
                        posterUrl = null,
                        streamUrl = "http://iptv.invalid/movie/user/redacted/$index.mkv",
                        containerExtension = "mkv",
                    )
                },
                seriesCategories = listOf(CatalogCategory("series", "Séries")),
                series = listOf(CatalogSeries("series-1", "Série", "series", null)),
            ),
            seriesDetails = mapOf(
                "series-1" to SeriesDetailSource(
                    title = "Série",
                    year = "2026",
                    synopsis = "Synopsis",
                    genres = listOf("Drame"),
                    episodes = listOf(
                        SeriesEpisodeSource(
                            id = "episode-1",
                            season = 1,
                            number = 1,
                            title = "Pilote",
                            synopsis = null,
                            streamUrl = "http://iptv.invalid/series/user/redacted/1.mkv",
                        ),
                    ),
                ),
            ),
        )

        val bytes = ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { CatalogCacheCodec.write(it, catalog) }
            bytes.toByteArray()
        }
        val restored = DataInputStream(ByteArrayInputStream(bytes)).use(CatalogCacheCodec::read)

        assertEquals(liveCount, restored.snapshot.liveChannels.size)
        assertEquals(movieCount, restored.snapshot.movies.size)
        assertEquals(catalog.snapshot.liveChannels.last().streamUrl, restored.snapshot.liveChannels.last().streamUrl)
        assertEquals(catalog.snapshot.movies.last().streamUrl, restored.snapshot.movies.last().streamUrl)
        assertEquals(
            catalog.seriesDetails.getValue("series-1").episodes.single().streamUrl,
            restored.seriesDetails.getValue("series-1").episodes.single().streamUrl,
        )
    }
}
