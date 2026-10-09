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
    fun parserAcceptsUtf8BomAndCaseInsensitiveHeader() {
        val entries = M3uParser.parse(
            "\uFEFF#extm3u\n#EXTINF:-1 tvg-id='tf1.fr' group-title=' France  Généralistes ',TF1\nhttps://stream.example/live/1.ts",
        )

        assertEquals(1, entries.size)
        assertEquals("tf1.fr", entries.single().tvgId)
        assertEquals("France  Généralistes", entries.single().groupTitle)
    }

    @Test
    fun parserFallsBackToTvgNameWhenDisplayNameIsMissing() {
        val entries = M3uParser.parse(
            """
            #EXTM3U
            #EXTINF:-1 tvg-name="France 2" group-title=France,
            https://stream.example/live/2.ts
            """.trimIndent(),
        )

        assertEquals(1, entries.size)
        assertEquals("France 2", entries.single().name)
        assertEquals("France", entries.single().groupTitle)
    }

    @Test
    fun malformedPlaylistReturnsNoEntries() {
        assertTrue(M3uParser.parse("not a playlist").isEmpty())
    }

    @Test
    fun parserCanStreamEntriesWithoutBuildingAnIntermediateLineList() {
        val lines = sequence {
            yield("#EXTM3U")
            repeat(5_000) { index ->
                yield("#EXTINF:-1 group-title=\"Large\",Channel $index")
                yield("https://stream.example/live/$index.ts")
            }
        }

        var seen = 0
        val emitted = M3uParser.parseLines(lines) {
            seen += 1
        }

        assertEquals(5_000, emitted)
        assertEquals(5_000, seen)
    }

    @Test
    fun parserHandlesVeryLargeCatalogWithPrecompiledAttributeScanner() {
        val entryCount = 50_000
        val lines = sequence {
            yield("#EXTM3U")
            repeat(entryCount) { index ->
                yield(
                    "#EXTINF:-1 tvg-id=\"id-$index\" tvg-name='Channel $index' " +
                        "tvg-logo=https://img.example/$index.png group-title=Group-$index,Channel $index",
                )
                yield("https://stream.example/live/$index.ts")
            }
        }

        var last: M3uEntry? = null
        val report = M3uParser.parseLinesDetailed(lines) { last = it }

        assertEquals(entryCount, report.emitted)
        assertTrue(report.headerSeen)
        assertTrue(!report.danglingMetadata)
        assertEquals("id-49999", last?.tvgId)
        assertEquals("Group-49999", last?.groupTitle)
    }

    @Test
    fun detailedParserReportsDanglingMetadataAtEndOfStream() {
        val report = M3uParser.parseLinesDetailed(
            sequenceOf(
                "#EXTM3U",
                "#EXTINF:-1,Complete",
                "https://stream.example/live/1.ts",
                "#EXTINF:-1,Interrupted",
            ),
        ) { }

        assertEquals(1, report.emitted)
        assertTrue(report.danglingMetadata)
    }

    @Test
    fun parserCanStopAtConfiguredEntryLimit() {
        val content = buildString {
            appendLine("#EXTM3U")
            repeat(1_000) { index ->
                appendLine("#EXTINF:-1,Channel $index")
                appendLine("https://stream.example/live/$index.ts")
            }
        }

        val entries = M3uParser.parse(content, maxEntries = 120)

        assertEquals(120, entries.size)
        assertEquals("Channel 119", entries.last().name)
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
