package fr.zyviotv.player.shared.series

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SeriesModelsTest {
    @Test
    fun nextEpisodeMovesAcrossSeasonBoundary() {
        val seasons = listOf(
            SeriesSeason(
                number = 1,
                episodes = listOf(
                    SeriesEpisode("s1e1", 1, 1, "Episode 1", streamUrl = "https://example.com/1"),
                    SeriesEpisode("s1e2", 1, 2, "Episode 2", streamUrl = "https://example.com/2"),
                ),
            ),
            SeriesSeason(
                number = 2,
                episodes = listOf(
                    SeriesEpisode("s2e1", 2, 1, "Episode 1", streamUrl = "https://example.com/3"),
                ),
            ),
        )

        assertEquals("s2e1", SeriesNavigator.nextEpisode(seasons, "s1e2")?.id)
    }

    @Test
    fun validEpisodePassesValidation() {
        assertIs<EpisodeValidationResult.Valid>(
            EpisodeValidator.validate(
                SeriesEpisode(
                    id = "e1",
                    seasonNumber = 1,
                    episodeNumber = 1,
                    title = "Episode 1",
                    streamUrl = "https://stream.example/e1.m3u8",
                ),
            ),
        )
    }
}
