package fr.zyviotv.player.shared.sync

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaylistSecretTest {
    @Test
    fun xtreamToStringNeverLeaksCredentials() {
        val secret = PlaylistSecret.Xtream(
            serverUrl = "https://provider.example",
            username = "private-user",
            password = "private-password",
        )

        val rendered = secret.toString()
        assertFalse(rendered.contains("provider.example"))
        assertFalse(rendered.contains("private-user"))
        assertFalse(rendered.contains("private-password"))
        assertTrue(rendered.contains("[REDACTED]"))
    }

    @Test
    fun m3uToStringRedactsOptionalXmlTvSource() {
        val secret = PlaylistSecret.M3u(
            url = "https://example.invalid/list.m3u",
            xmlTvUrl = "https://example.invalid/guide.xml",
        )

        val rendered = secret.toString()
        assertFalse(rendered.contains("guide.xml"))
        assertFalse(rendered.contains("list.m3u"))
        assertTrue(rendered.contains("[REDACTED]"))
    }

    @Test
    fun m3uToStringNeverLeaksUrl() {
        val secret = PlaylistSecret.M3u(
            url = "https://provider.example/get.php?username=a&password=b",
        )

        val rendered = secret.toString()
        assertFalse(rendered.contains("provider.example"))
        assertFalse(rendered.contains("username"))
        assertTrue(rendered.contains("[REDACTED]"))
    }
}
