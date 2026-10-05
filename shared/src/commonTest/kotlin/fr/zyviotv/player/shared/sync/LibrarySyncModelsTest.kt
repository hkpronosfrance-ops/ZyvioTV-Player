package fr.zyviotv.player.shared.sync

import kotlin.test.Test
import kotlin.test.assertEquals

class LibrarySyncModelsTest {
    @Test
    fun progressFractionIsBounded() {
        val base = SyncedWatchProgress(
            playlistId = "playlist",
            contentType = ProgressContentType.Movie,
            contentId = "movie",
            title = "Movie",
            positionMs = 50,
            durationMs = 100,
        )

        assertEquals(0.5f, base.fraction)
        assertEquals(1f, base.copy(positionMs = 150).fraction)
        assertEquals(0f, base.copy(positionMs = 0).fraction)
    }

    @Test
    fun missingDurationHasZeroFraction() {
        val progress = SyncedWatchProgress(
            playlistId = "playlist",
            contentType = ProgressContentType.Episode,
            contentId = "episode",
            title = "Episode",
            positionMs = 100,
            durationMs = null,
        )

        assertEquals(0f, progress.fraction)
    }
}
