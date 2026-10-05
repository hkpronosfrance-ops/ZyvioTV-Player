package fr.zyviotv.player.shared.security

import fr.zyviotv.player.shared.live.LiveChannel
import fr.zyviotv.player.shared.movies.MovieDetails
import fr.zyviotv.player.shared.playback.PlaybackKind
import fr.zyviotv.player.shared.playback.PlaybackRequest
import fr.zyviotv.player.shared.series.SeriesEpisode
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SecureModelLoggingTest {
    private val sensitiveUrl = "https://provider.test/live/john/secret/123.ts?token=abcdef"

    @Test
    fun playbackModelsDoNotExposeRawCredentialsInToString() {
        val values = listOf(
            PlaybackRequest("Live", sensitiveUrl, PlaybackKind.Live).toString(),
            LiveChannel("1", "Channel", null, null, sensitiveUrl).toString(),
            MovieDetails(id = "2", title = "Movie", streamUrl = sensitiveUrl).toString(),
            SeriesEpisode(
                id = "3",
                seasonNumber = 1,
                episodeNumber = 1,
                title = "Episode",
                streamUrl = sensitiveUrl,
            ).toString(),
        )

        values.forEach { value ->
            assertFalse("john" in value)
            assertFalse("secret" in value)
            assertFalse("abcdef" in value)
            assertTrue("***" in value)
        }
    }
}
