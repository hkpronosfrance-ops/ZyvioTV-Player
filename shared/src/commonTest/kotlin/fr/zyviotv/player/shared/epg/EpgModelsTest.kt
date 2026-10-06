package fr.zyviotv.player.shared.epg

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EpgModelsTest {
    @Test
    fun windowAroundNowUsesThreeHoursBeforeAndSixHoursAfterByDefault() {
        val window = EpgWindow.around(10_000)
        assertEquals(-800, window.fromEpochSeconds)
        assertEquals(31_600, window.toEpochSeconds)
    }

    @Test
    fun windowContainsOverlappingProgramme() {
        val window = EpgWindow(100, 200)
        val programme = EpgProgramme(
            channelId = "1",
            title = "Programme",
            startEpochSeconds = 150,
            endEpochSeconds = 250,
        )

        assertTrue(window.contains(programme))
    }

    @Test
    fun windowRejectsProgrammeOutsideRange() {
        val window = EpgWindow(100, 200)
        val programme = EpgProgramme(
            channelId = "1",
            title = "Programme",
            startEpochSeconds = 200,
            endEpochSeconds = 250,
        )

        assertFalse(window.contains(programme))
    }
}
