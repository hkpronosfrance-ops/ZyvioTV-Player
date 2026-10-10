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
        assertFalse(encoded.contains("é"))
    }

    @Test
    fun malformedOrInconsistentReferencesAreRejected() {
        assertNull(CatalogSourceRef.parse("http://provider.example/movie/1.mkv"))
        assertNull(CatalogSourceRef.parse("zyvio-store:movie:1"))
        assertNull(CatalogSourceRef.parse("zyvio-store:unknown:1:61:62"))
        assertNull(CatalogSourceRef.parse("zyvio-store:movie:x:61:62"))
        assertNull(CatalogSourceRef.parse("zyvio-store:movie:1:6:62"))
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
