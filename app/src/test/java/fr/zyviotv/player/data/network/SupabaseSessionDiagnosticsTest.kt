package fr.zyviotv.player.data.network

import org.junit.Assert.assertEquals
import org.junit.Test

class SupabaseSessionDiagnosticsTest {
    private val jwtShape = "header.payload.signature"

    @Test
    fun sessionStateDistinguishesMissingMalformedExpiredAndFreshTokens() {
        assertEquals("missing", SupabaseSessionDiagnostics.state(null, null, 1_000L))
        assertEquals("non-jwt", SupabaseSessionDiagnostics.state("sb_publishable_key", 2_000L, 1_000L))
        assertEquals("expired", SupabaseSessionDiagnostics.state(jwtShape, 999L, 1_000L))
        assertEquals("expiring", SupabaseSessionDiagnostics.state(jwtShape, 1_030L, 1_000L))
        assertEquals("fresh", SupabaseSessionDiagnostics.state(jwtShape, 2_000L, 1_000L))
    }

    @Test
    fun operationRemovesQueryValuesFromDiagnostics() {
        assertEquals(
            "player_playlists",
            SupabaseSessionDiagnostics.operation(
                "/rest/v1/player_playlists?select=id&token=private-value",
            ),
        )
    }

    @Test
    fun refreshAndRetryPolicyIsBounded() {
        assertEquals(true, SupabaseSessionDiagnostics.shouldRefreshBeforeRequest("expired"))
        assertEquals(true, SupabaseSessionDiagnostics.shouldRefreshBeforeRequest("expiring"))
        assertEquals(false, SupabaseSessionDiagnostics.shouldRefreshBeforeRequest("fresh"))
        assertEquals(true, SupabaseSessionDiagnostics.shouldRetryUnauthorized(401, false))
        assertEquals(false, SupabaseSessionDiagnostics.shouldRetryUnauthorized(401, true))
        assertEquals(false, SupabaseSessionDiagnostics.shouldRetryUnauthorized(403, false))
    }
}
