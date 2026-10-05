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

    @Test
    fun xtreamPathCredentialsAreRedacted() {
        val value = SecretLoggingPolicy.redactUrlForLogs(
            "https://example.test/live/john/secret/12345.ts",
        )

        assertEquals(
            "https://example.test/live/***/***/12345.ts",
            value,
        )
    }

    @Test
    fun urlUserInfoCredentialsAreRedacted() {
        val value = SecretLoggingPolicy.redactUrlForLogs(
            "https://john:secret@example.test/live/1.ts",
        )

        assertEquals(
            "https://***:***@example.test/live/1.ts",
            value,
        )
    }
}
