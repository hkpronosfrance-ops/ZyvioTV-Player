package fr.zyviotv.player.shared.security

import kotlin.test.Test
import kotlin.test.assertEquals

class SecretLoggingPolicyTest {
    @Test
    fun credentialsAreRedactedFromUrls() {
        val value = SecretLoggingPolicy.redactUrlForLogs(
            "https://example.test/player_api.php?username=john&password=secret&type=m3u",
        )

        assertEquals(
            "https://example.test/player_api.php?username=***&password=***&type=m3u",
            value,
        )
    }
}
