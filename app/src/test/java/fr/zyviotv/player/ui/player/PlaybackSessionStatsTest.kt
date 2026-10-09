package fr.zyviotv.player.ui.player

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSessionStatsTest {
    private var now = 0L
    private fun stats() = PlaybackSessionStats(sessionId = "0000abcd", kind = "live", clock = { now })

    private fun field(line: String, name: String): String =
        line.split(' ').first { it.startsWith("$name=") }.substringAfter('=')

    @Test
    fun startupSeekReloadAndStallBufferingAreCountedSeparately() {
        val stats = stats()
        stats.onPrepare("transportstream")
        stats.onBuffering()
        now = 4_000L
        stats.onReady()

        stats.onSeekDiscontinuity()
        stats.onBuffering()
        now = 5_000L
        stats.onReady()

        stats.onSilentReload()
        stats.onSeekDiscontinuity() // the reload restores the position: not a viewer seek
        stats.onBuffering()
        now = 7_000L
        stats.onReady()

        stats.onBuffering() // nothing explains it: a stall
        now = 10_000L
        stats.onReady()
        stats.onBuffering()
        now = 11_000L
        stats.onReady()

        val line = stats.summary("dispose")
        assertEquals("1", field(line, "buffering_startup"))
        assertEquals("1", field(line, "buffering_seek"))
        assertEquals("1", field(line, "buffering_reload"))
        assertEquals("2", field(line, "buffering_stall"))
        assertEquals("4000", field(line, "stall_ms_total"))
        assertEquals("3000", field(line, "stall_ms_max"))
        assertEquals("5", field(line, "ready"))
        assertEquals("1", field(line, "seeks"))
        assertEquals("1", field(line, "silent_reloads"))
        assertEquals("4000", field(line, "first_ready_ms"))
    }

    @Test
    fun playingTimeFirstFrameAndBandwidthAreAggregated() {
        val stats = stats()
        now = 1_500L
        stats.onFirstFrame()
        stats.onIsPlaying(true)
        now = 61_500L
        stats.onIsPlaying(false)
        stats.onBandwidthEstimate(4_500_000L)
        stats.onBandwidthEstimate(2_000_000L)
        stats.onDroppedFrames(12)
        stats.onDroppedFrames(3)
        val line = stats.summary("dispose")
        assertEquals("1500", field(line, "first_frame_ms"))
        assertEquals("60", field(line, "playing_s"))
        assertEquals("2000", field(line, "bandwidth_kbps_last"))
        assertEquals("4500", field(line, "bandwidth_kbps_max"))
        assertEquals("15", field(line, "dropped_frames"))
    }

    @Test
    fun codecAndFormatValuesAreReducedToSafeTokens() {
        val stats = stats()
        stats.onVideoFormat("video/avc", 1024, 576)
        stats.onVideoDecoder("c2.goldfish.h264.decoder")
        stats.onAudioFormat("audio/mp4a-latm")
        stats.onAudioDecoder("c2.android.aac.decoder")
        stats.onLoadError("InvalidResponseCodeException")
        val line = stats.summary("dispose")
        assertEquals("video/avc", field(line, "video"))
        assertEquals("1024x576", field(line, "video_size"))
        assertEquals("c2.goldfish.h264.decoder", field(line, "video_decoder"))
        assertEquals("audio/mp4a-latm", field(line, "audio"))
        assertEquals("invalidresponsecodeexception:1", field(line, "load_errors"))
    }

    @Test
    fun summaryNeverCarriesUrlsCredentialsOrTitlesEvenFromHostileInput() {
        val stats = PlaybackSessionStats("0000abcd", "movie", clock = { now })
        stats.onPrepare("http://user:pass@host.invalid/live/user/pass/1.ts?username=u&password=p")
        stats.onVideoDecoder("https://provider.invalid/get.php?username=u&password=p&token=t")
        stats.onVideoFormat("video/avc; Authorization: Bearer abc", 1920, 1080)
        stats.onAudioDecoder("Authorization: Bearer secret-token")
        stats.onDiscontinuity("seek?password=x")
        stats.onLoadError("token=abc://")
        val line = stats.summary("dispose")
        DiagnosticsSafety.assertSafe(line)
    }

    @Test
    fun distinctLoadErrorKindsAreBounded() {
        val stats = stats()
        repeat(50) { stats.onLoadError("Error$it") }
        val kinds = field(stats.summary("dispose"), "load_errors").split(',')
        assertTrue(kinds.size <= 9)
        assertTrue(kinds.any { it.startsWith("other:") })
    }

    @Test
    fun sessionIdIsRandomEightHexDigits() {
        val first = PlaybackSessionStats.newSessionId(Random(1))
        val second = PlaybackSessionStats.newSessionId(Random(2))
        assertTrue(first.matches(Regex("[0-9a-f]{8}")))
        assertFalse(first == second)
    }
}
