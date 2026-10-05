package fr.zyviotv.player.shared.epg

import kotlin.test.Test
import kotlin.test.assertEquals

class EpgTimelineTest {
    @Test
    fun selectsCurrentAndNextProgramme() {
        val programmes = listOf(
            EpgProgramme("1", "Programme A", startEpochSeconds = 100, endEpochSeconds = 200),
            EpgProgramme("1", "Programme B", startEpochSeconds = 200, endEpochSeconds = 300),
        )

        val result = EpgTimeline.nowNext(programmes, nowEpochSeconds = 150)

        assertEquals("Programme A", result.now?.title)
        assertEquals("Programme B", result.next?.title)
    }

    @Test
    fun computesBoundedProgress() {
        val programme = EpgProgramme(
            channelId = "1",
            title = "Programme",
            startEpochSeconds = 100,
            endEpochSeconds = 200,
        )

        assertEquals(0.5f, EpgTimeline.progress(programme, 150))
        assertEquals(0f, EpgTimeline.progress(programme, 50))
        assertEquals(1f, EpgTimeline.progress(programme, 250))
    }
}
