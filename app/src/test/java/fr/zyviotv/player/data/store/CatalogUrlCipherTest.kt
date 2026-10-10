package fr.zyviotv.player.data.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.fail
import org.junit.Test

class CatalogUrlCipherTest {
    private val wrapper = CatalogStoreFixtures.softwareKeyWrapper()
    private val url = "http://provider.example/movie/1.mkv|User-Agent=Test"

    @Test
    fun sealedUrlOpensWithTheRestoredGenerationKey() {
        val (cipher, wrappedKey) = CatalogUrlCipher.create(wrapper)
        val blob = cipher.seal(7L, CatalogKind.Movie, "movie-1", url)

        val restored = CatalogUrlCipher.restore(wrapper, wrappedKey)

        assertEquals(url, restored.open(7L, CatalogKind.Movie, "movie-1", blob))
        assertFalse(String(blob, Charsets.ISO_8859_1).contains("provider.example"))
    }

    @Test
    fun eachSealUsesAFreshIv() {
        val (cipher, _) = CatalogUrlCipher.create(wrapper)
        assertNotEquals(
            cipher.seal(1L, CatalogKind.Live, "a", url).toList(),
            cipher.seal(1L, CatalogKind.Live, "a", url).toList(),
        )
    }

    @Test
    fun aBlobCopiedOntoAnotherRowDoesNotOpen() {
        val (cipher, _) = CatalogUrlCipher.create(wrapper)
        val blob = cipher.seal(7L, CatalogKind.Movie, "movie-1", url)

        assertOpenFails { cipher.open(7L, CatalogKind.Movie, "movie-2", blob) }
        assertOpenFails { cipher.open(8L, CatalogKind.Movie, "movie-1", blob) }
        assertOpenFails { cipher.open(7L, CatalogKind.Live, "movie-1", blob) }
    }

    @Test
    fun anotherGenerationKeyCannotOpenTheBlob() {
        val (first, _) = CatalogUrlCipher.create(wrapper)
        val (second, _) = CatalogUrlCipher.create(wrapper)
        val blob = first.seal(7L, CatalogKind.Movie, "movie-1", url)

        assertOpenFails { second.open(7L, CatalogKind.Movie, "movie-1", blob) }
    }

    @Test
    fun aTamperedWrappedKeyIsRejected() {
        val (_, wrappedKey) = CatalogUrlCipher.create(wrapper)
        wrappedKey[wrappedKey.size - 1] = (wrappedKey.last() + 1).toByte()

        assertOpenFails { CatalogUrlCipher.restore(wrapper, wrappedKey) }
    }

    private fun assertOpenFails(block: () -> Unit) {
        try {
            block()
            fail("Decryption must fail")
        } catch (expected: java.security.GeneralSecurityException) {
            // AEADBadTagException: authentication failed.
        }
    }
}
