package fr.zyviotv.player.data.epg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EpgTimeParsingTest {
    @Test
    fun xmlTvOffsetsWithAndWithoutSpaceResolveToSameInstant() {
        val spaced = EpgTimeParsing.xmlTvEpochSeconds("20261007120000 +0200")
        val compact = EpgTimeParsing.xmlTvEpochSeconds("20261007120000+0200")

        assertEquals(spaced, compact)
    }

    @Test
    fun xmlTvUtcZIsParsed() {
        val value = EpgTimeParsing.xmlTvEpochSeconds("20261007100000Z")

        assertEquals(1_781_085_600L, value)
    }

    @Test
    fun xtreamMillisecondsAreNormalizedToSeconds() {
        assertEquals(
            1_781_085_600L,
            EpgTimeParsing.epochSeconds(1_781_085_600_000L),
        )
        assertEquals(
            1_781_085_600L,
            EpgTimeParsing.epochSeconds(1_781_085_600L),
        )
    }

    @Test
    fun invalidEpochValuesAreRejected() {
        assertNull(EpgTimeParsing.epochSeconds(0L))
        assertNull(EpgTimeParsing.epochSeconds(Long.MIN_VALUE))
    }
}
