package fr.zyviotv.player.shared.m3u

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class M3uCoreTest {
    @Test
    fun validSourcePassesValidation() {
        assertIs<M3uValidationResult.Valid>(
            M3uValidator.validate(M3uSource("https://example.com/list.m3u")),
        )
    }

    @Test
    fun parserReadsMetadataAndStreams() {
        val entries = M3uParser.parse(
            """
            #EXTM3U
            #EXTINF:-1 tvg-id="tf1.fr" tvg-name="TF1" tvg-logo="https://img.example/tf1.png" group-title="France",TF1
            https://stream.example/live/1.ts
            #EXTINF:-1 group-title="Films",Film Demo
            https://stream.example/movie/2.mp4
            """.trimIndent(),
        )

        assertEquals(2, entries.size)
        assertEquals("TF1", entries[0].name)
        assertEquals("tf1.fr", entries[0].tvgId)
        assertEquals("France", entries[0].groupTitle)
        assertEquals("Film Demo", entries[1].name)
    }

    @Test
    fun malformedPlaylistReturnsNoEntries() {
        assertTrue(M3uParser.parse("not a playlist").isEmpty())
    }

    @Test
    fun sensitiveQueryValuesAreRedacted() {
        val redacted = M3uLogging.redactUrl(
            "https://example.com/get.php?username=john&password=secret&type=m3u",
        )

        assertTrue("john" !in redacted)
        assertTrue("secret" !in redacted)
        assertTrue("type=m3u" in redacted)
    }
}
