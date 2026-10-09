package fr.zyviotv.player.shared.xtream

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class XtreamCoreTest {
    @Test
    fun validCredentialsPassValidation() {
        val result = XtreamValidator.validate(
            XtreamCredentials(
                serverUrl = "https://provider.example",
                username = "user",
                password = "secret",
            ),
        )

        assertIs<XtreamValidationResult.Valid>(result)
    }

    @Test
    fun authenticatedPlayerApiEncodesCredentialsSafely() {
        val url = XtreamEndpointBuilder.authenticatedPlayerApi(
            XtreamCredentials(
                serverUrl = "https://provider.example/",
                username = "john+tv@example.com",
                password = "s ecret&42",
            ),
        )

        assertEquals(
            "https://provider.example/player_api.php?username=john%2Btv%40example.com&password=s%20ecret%2642",
            url,
        )
    }

    @Test
    fun authenticatedPlayerApiEncodesExtraParameters() {
        val url = XtreamEndpointBuilder.authenticatedPlayerApi(
            credentials = XtreamCredentials(
                serverUrl = "https://provider.example",
                username = "user",
                password = "secret",
            ),
            action = "get_short_epg",
            extraParams = mapOf(
                "stream_id" to "42",
                "note" to "a b&c",
            ),
        )

        assertEquals(
            "https://provider.example/player_api.php?username=user&password=secret&action=get_short_epg&note=a%20b%26c&stream_id=42",
            url,
        )
    }

    @Test
    fun playerApiDoesNotEmbedCredentialsInPath() {
        val url = XtreamEndpointBuilder.playerApi(
            XtreamCredentials(
                serverUrl = "https://provider.example/",
                username = "user",
                password = "secret",
            ),
        )

        assertEquals("https://provider.example/player_api.php", url)
    }

    @Test
    fun endpointBuilderPreservesPortAndProviderPath() {
        val url = XtreamEndpointBuilder.authenticatedPlayerApi(
            XtreamCredentials(
                serverUrl = "http://provider.example:8080/server-path/",
                username = "user",
                password = "secret",
            ),
        )

        assertEquals(
            "http://provider.example:8080/server-path/player_api.php?username=user&password=secret",
            url,
        )
    }

    @Test
    fun endpointBuilderDoesNotDuplicateKnownApiFilename() {
        val credentials = XtreamCredentials(
            serverUrl = "https://provider.example/panel/player_api.php?old=value",
            username = "user",
            password = "secret",
        )

        assertEquals(
            "https://provider.example/panel/player_api.php?username=user&password=secret",
            XtreamEndpointBuilder.authenticatedPlayerApi(credentials),
        )
        assertEquals(
            "https://provider.example/panel/live/user/secret/42.ts",
            XtreamEndpointBuilder.liveStream(credentials, "42"),
        )
    }

    @Test
    fun validatorAcceptsCaseInsensitiveHttpScheme() {
        assertIs<XtreamValidationResult.Valid>(
            XtreamValidator.validate(
                XtreamCredentials("HTTP://provider.example:8080/path", "user", "secret"),
            ),
        )
    }

    @Test
    fun streamUrlCanBeRedactedForLogs() {
        val url = XtreamEndpointBuilder.liveStream(
            credentials = XtreamCredentials(
                serverUrl = "https://provider.example",
                username = "john",
                password = "secret",
            ),
            streamId = "42",
        )

        val redacted = XtreamEndpointBuilder.redactedForLogs(url)
        assertTrue("***" in redacted)
        assertTrue("secret" !in redacted)
        assertTrue("john" !in redacted)
    }

    @Test
    fun profileParserReadsAccountAndServerInfo() {
        val profile = XtreamParser.parseProfile(
            """
            {
              "user_info": {
                "username": "john",
                "status": "Active",
                "is_trial": "0",
                "active_cons": "1",
                "max_connections": "2",
                "created_at": "1700000000",
                "exp_date": "1800000000"
              },
              "server_info": {
                "url": "provider.example",
                "port": "80",
                "https_port": "443",
                "server_protocol": "https",
                "timezone": "Europe/Paris"
              }
            }
            """.trimIndent(),
        )

        assertNotNull(profile)
        assertEquals("john", profile.account.username)
        assertEquals("Active", profile.account.status)
        assertEquals(2, profile.account.maxConnections)
        assertEquals(443, profile.server.httpsPort)
    }
}
