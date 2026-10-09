package fr.zyviotv.player.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkAvailabilityTest {
    @Test
    fun internetCapabilityMapsToAvailability() {
        assertEquals(NetworkAvailability.Available, NetworkAvailability.fromCapabilities(true))
        assertEquals(NetworkAvailability.Unavailable, NetworkAvailability.fromCapabilities(false))
        assertEquals(NetworkAvailability.Unknown, NetworkAvailability.fromCapabilities(null))
    }

    @Test
    fun onlyConfirmedAbsenceBlocksNetworkActions() {
        assertTrue(NetworkAvailability.Available.allowsNetworkActions)
        assertTrue(NetworkAvailability.Unknown.allowsNetworkActions)
        assertFalse(NetworkAvailability.Unavailable.allowsNetworkActions)
    }
}
