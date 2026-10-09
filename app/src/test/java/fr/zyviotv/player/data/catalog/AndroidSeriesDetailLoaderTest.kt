package fr.zyviotv.player.data.catalog

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidSeriesDetailLoaderTest {
    @After
    fun clearRegistry() {
        M3uSeriesDetailRegistry.clear()
    }

    @Test
    fun cachedM3uEpisodesDoNotNeedThePlaylistSecret() = runBlocking {
        val detail = SeriesDetailSource(
            title = "Série",
            year = null,
            synopsis = null,
            genres = emptyList(),
            episodes = listOf(
                SeriesEpisodeSource(
                    id = "episode-1",
                    season = 1,
                    number = 1,
                    title = "Épisode 1",
                    synopsis = null,
                    streamUrl = "https://provider.example/series/1.mp4",
                ),
            ),
        )
        M3uSeriesDetailRegistry.replace(mapOf("series-1" to detail))
        val secretCalls = AtomicInteger()

        val result = AndroidSeriesDetailLoader.loadPreferLocal("series-1") {
            secretCalls.incrementAndGet()
            Result.failure(IllegalStateException("Supabase unavailable"))
        }

        assertEquals(SeriesDetailLoadResult.Success(detail), result)
        assertEquals(0, secretCalls.get())
    }

    @Test
    fun unavailableSecretIsReportedForRemoteSeries() = runBlocking {
        val result = AndroidSeriesDetailLoader.loadPreferLocal("xtream-42") {
            Result.failure(IllegalStateException("HTTP 503"))
        }

        assertTrue(result is SeriesDetailLoadResult.Failure)
        assertEquals(
            AndroidSeriesDetailLoader.SECRET_UNAVAILABLE_MESSAGE,
            (result as SeriesDetailLoadResult.Failure).message,
        )
    }

    @Test
    fun missingSecretIsReportedForRemoteSeries() = runBlocking {
        val result = AndroidSeriesDetailLoader.loadPreferLocal("xtream-42") {
            Result.success(null)
        }

        assertEquals(
            SeriesDetailLoadResult.Failure(AndroidSeriesDetailLoader.SECRET_MISSING_MESSAGE),
            result,
        )
    }
}
