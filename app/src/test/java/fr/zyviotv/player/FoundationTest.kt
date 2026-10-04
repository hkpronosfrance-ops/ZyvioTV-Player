package fr.zyviotv.player

import org.junit.Assert.assertEquals
import org.junit.Test

class FoundationTest {
    @Test
    fun packageIdentity_isStable() {
        assertEquals("fr.zyviotv.player", BuildConfig.APPLICATION_ID.removeSuffix(".debug"))
    }
}
