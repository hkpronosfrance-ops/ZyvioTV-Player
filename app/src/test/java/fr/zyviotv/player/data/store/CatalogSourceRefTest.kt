package fr.zyviotv.player.data.store

import fr.zyviotv.player.data.catalog.M3uSeriesDetailRegistry
import fr.zyviotv.player.data.catalog.SeriesDetailSource
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogSourceRefTest {
    @After
    fun tearDown() {
        M3uSeriesDetailRegistry.clear()
    }

    @Test
    fun referencesRoundTripIncludingSeparatorsAndAccents() {
        val ref = CatalogSourceRef(CatalogKind.Episode, 42L, "playlist:é/1", "ep:1|x", "série:2")

        val encoded = ref.encode()

        assertEquals(ref, CatalogSourceRef.parse(encoded))
        assertTrue(CatalogSourceRef.isRef(encoded))
        // No host, path or credential-looking text inside a reference.
        assertFalse(encoded.contains("://"))
    }

    @Test
    fun escapingIsReversibleForEveryTrickySequence() {
        for (value in listOf("", "plain-1", "a:b", "100%", "%3A", "%253A", "::%%", "m3u-0123abcd", "id:%3A:%25")) {
            assertEquals(value, CatalogSourceRef.unescape(CatalogSourceRef.escape(value)))
            assertFalse(CatalogSourceRef.escape(value).contains(':'))
        }
        // The common case allocates nothing new.
        val plain = "m3u-0123abcd"
        assertTrue(CatalogSourceRef.escape(plain) === plain)
    }

    @Test
    fun precomputedPrefixGivesTheSameReferenceAsEncode() {
        val prefix = CatalogSourceRef.prefix(CatalogKind.Movie, 9L, "playlist:1")
        val ref = CatalogSourceRef(CatalogKind.Movie, 9L, "playlist:1", "movie:7")

        assertEquals(ref.encode(), CatalogSourceRef.ofItem(prefix, "movie:7"))
        assertEquals(ref, CatalogSourceRef.parse(CatalogSourceRef.ofItem(prefix, "movie:7")))
    }

    @Test
    fun encodingARealSizedCatalogueIsFarCheaperThanThePrBHexEncoding() {
        // JVM micro-measure (not a device figure): 18 681 references, the
        // live channels + films of the recetted playlist.
        val playlist = "4f6c2a1e-9b0d-4c3e-8f2a-7d5e6b1c0a99"
        val ids = List(18_681) { index -> "m3u-" + (index * 7919L).toString(16).padStart(16, '0') }
        fun legacyHex(value: String) = value.toByteArray(Charsets.UTF_8).joinToString("") { "%02x".format(it) }
        fun timeMs(block: () -> Int): Long {
            repeat(2) { block() } // warm-up
            val start = System.nanoTime()
            block()
            return (System.nanoTime() - start) / 1_000_000
        }

        val legacyMs = timeMs { ids.sumOf { ("zyvio-store:movie:1:" + legacyHex(playlist) + ":" + legacyHex(it)).length } }
        val prefix = CatalogSourceRef.prefix(CatalogKind.Movie, 1L, playlist)
        val newMs = timeMs { ids.sumOf { CatalogSourceRef.ofItem(prefix, it).length } }

        println("catalog_source_ref_jvm items=18681 legacy_hex_ms=$legacyMs escaped_prefix_ms=$newMs")
        assertTrue("escaped=$newMs ms legacy=$legacyMs ms", newMs * 10 <= legacyMs.coerceAtLeast(10))
    }

    @Test
    fun malformedOrInconsistentReferencesAreRejected() {
        assertNull(CatalogSourceRef.parse("http://provider.example/movie/1.mkv"))
        assertNull(CatalogSourceRef.parse("zyvio-store:movie:1"))
        assertNull(CatalogSourceRef.parse("zyvio-store:unknown:1:61:62"))
        assertNull(CatalogSourceRef.parse("zyvio-store:movie:x:61:62"))
        // An episode needs its series, a film must not have one.
        assertNull(CatalogSourceRef.parse("zyvio-store:episode:1:61:62"))
        assertNull(CatalogSourceRef.parse("zyvio-store:movie:1:61:62:63"))
    }

    @Test
    fun registryFallsBackToTheStoreUntilTheNextImport() = runBlocking {
        val stored = SeriesDetailSource("Série", null, null, emptyList(), emptyList())
        M3uSeriesDetailRegistry.useStored { seriesId -> stored.takeIf { seriesId == "series-1" } }

        assertEquals(stored, M3uSeriesDetailRegistry.loadLocal("series-1"))
        assertNull(M3uSeriesDetailRegistry.loadLocal("series-2"))

        M3uSeriesDetailRegistry.replace(emptyMap())
        assertNull(M3uSeriesDetailRegistry.loadLocal("series-1"))
    }
}
