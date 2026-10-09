package fr.zyviotv.player.ui.player

import fr.zyviotv.player.ui.diagnostics.FrameStatsAggregator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FrameStatsAggregatorTest {
    private fun ms(value: Long) = value * 1_000_000L

    @Test
    fun oneSummaryPerScreenWithSlowAndFrozenFrames() {
        val stats = FrameStatsAggregator()
        assertNull(stats.switchTo("home"))
        repeat(90) { stats.onFrame(ms(12)) }
        repeat(8) { stats.onFrame(ms(80)) }
        stats.onFrame(ms(750))
        stats.onFrame(ms(7_507))

        val summary = stats.switchTo("player")
        assertEquals(
            "frames screen=home total=100 slow=10 frozen=2 max_ms=7507 p95_ms_le=100",
            summary,
        )
        // The new screen starts empty: nothing is reported without frames.
        assertNull(stats.switchTo("live"))
    }

    @Test
    fun screenNamesAreReducedToRouteConstants() {
        assertEquals("movie-detail", FrameStatsAggregator.screenName("movie-detail"))
        assertEquals("series", FrameStatsAggregator.screenName("series/{id}?x=1"))
        assertEquals("unknown", FrameStatsAggregator.screenName("https://host.invalid/path"))
        assertEquals("unknown", FrameStatsAggregator.screenName(null))
    }

    @Test
    fun summaryIsSafe() {
        val stats = FrameStatsAggregator()
        stats.switchTo("http://user:password@host.invalid/?token=1")
        stats.onFrame(ms(20))
        DiagnosticsSafety.assertSafe(stats.summaryOrNull()!!)
    }
}
