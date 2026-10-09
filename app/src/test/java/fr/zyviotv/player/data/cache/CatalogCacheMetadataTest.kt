package fr.zyviotv.player.data.cache

import fr.zyviotv.player.shared.catalog.CatalogLiveChannel
import fr.zyviotv.player.shared.catalog.CatalogSnapshot
import java.io.File
import java.nio.file.Files
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CatalogCacheMetadataTest {
    private val key: SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val directory: File = Files.createTempDirectory("catalog-metadata").toFile()

    private fun catalog(channels: Int = 3) = CachedCatalog(
        playlistId = "playlist",
        playlistName = "Playlist",
        snapshot = CatalogSnapshot(
            liveCategories = emptyList(),
            liveChannels = List(channels) { index ->
                CatalogLiveChannel(
                    id = "live-$index",
                    name = "Chaîne $index",
                    categoryId = null,
                    logoUrl = null,
                    streamUrl = "https://media.invalid/live/$index.ts",
                )
            },
            movieCategories = emptyList(),
            movies = emptyList(),
            seriesCategories = emptyList(),
            series = emptyList(),
        ),
    )

    private fun metadata(stamp: CatalogFileStamp, fetchedAt: Long? = 1_800_000_000_000L) = CatalogCacheMetadata(
        fetchedAtEpochMs = fetchedAt,
        stamp = stamp,
        liveCount = 3,
        movieCount = 0,
        seriesCount = 0,
        episodeCount = 0,
        idAliases = mapOf("m3u-1a2b3c" to -42L),
    )

    @Test
    fun committedCatalogStampMatchesTheFileOnDisk() {
        val file = File(directory, "profile.catalog")
        val stamp = EncryptedCatalogFile { key }.write(file, catalog())
        assertEquals(CatalogFileStamp.of(file), stamp)
        assertEquals(file.length(), stamp.length)
    }

    @Test
    fun metadataRoundTripsThroughAuthenticatedEncryption() {
        val catalogFile = File(directory, "profile.catalog")
        val stamp = EncryptedCatalogFile { key }.write(catalogFile, catalog())
        val target = File(directory, "profile.meta")
        val store = EncryptedCatalogMetadataFile { key }
        store.write(target, metadata(stamp))

        val restored = store.read(target)!!
        assertEquals(1_800_000_000_000L, restored.fetchedAtEpochMs)
        assertEquals(mapOf("m3u-1a2b3c" to -42L), restored.idAliases)
        assertTrue(restored.attests(CatalogFileStamp.of(catalogFile)))
    }

    @Test
    fun editedFetchDateIsRejectedByGcm() {
        val target = File(directory, "tampered.meta")
        val store = EncryptedCatalogMetadataFile { key }
        store.write(target, metadata(CatalogFileStamp(10L, ByteArray(32))))
        val bytes = target.readBytes()
        bytes[bytes.size - 20] = (bytes[bytes.size - 20].toInt() xor 0x01).toByte()
        target.writeBytes(bytes)
        try {
            store.read(target)
            fail("A modified metadata file must not be accepted")
        } catch (_: Exception) {
            // expected: authentication failure
        }
    }

    @Test
    fun metadataFromAnotherKeyIsRejected() {
        val target = File(directory, "other-key.meta")
        EncryptedCatalogMetadataFile { key }.write(target, metadata(CatalogFileStamp(10L, ByteArray(32))))
        val otherKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        try {
            EncryptedCatalogMetadataFile { otherKey }.read(target)
            fail("Metadata encrypted with another key must not be accepted")
        } catch (_: Exception) {
        }
    }

    @Test
    fun corruptedOrReplacedCatalogIsNotAttested() {
        val catalogFile = File(directory, "corrupted.catalog")
        val stamp = EncryptedCatalogFile { key }.write(catalogFile, catalog())
        val meta = metadata(stamp)

        val bytes = catalogFile.readBytes()
        bytes[bytes.size / 2] = (bytes[bytes.size / 2].toInt() xor 0x40).toByte()
        catalogFile.writeBytes(bytes)
        assertFalse(meta.attests(CatalogFileStamp.of(catalogFile)))

        EncryptedCatalogFile { key }.write(catalogFile, catalog(channels = 4))
        assertFalse(meta.attests(CatalogFileStamp.of(catalogFile)))

        catalogFile.writeBytes(catalogFile.readBytes().copyOf(100))
        assertFalse(meta.attests(CatalogFileStamp.of(catalogFile)))
        assertFalse(meta.attests(null))
    }

    @Test
    fun unknownFetchDateStaysUnknown() {
        val target = File(directory, "unknown.meta")
        val store = EncryptedCatalogMetadataFile { key }
        store.write(target, metadata(CatalogFileStamp(1L, ByteArray(32)), fetchedAt = null))
        assertNull(store.read(target)!!.fetchedAtEpochMs)
    }

    @Test
    fun missingMetadataReadsAsNull() {
        assertNull(EncryptedCatalogMetadataFile { key }.read(File(directory, "absent.meta")))
    }

    @Test
    fun legacyV1CatalogStillReadsAfterMetadataWasAdded() {
        val catalogFile = File(directory, "v1.catalog")
        EncryptedCatalogFile { key }.write(catalogFile, catalog())
        EncryptedCatalogMetadataFile { key }.write(
            File(directory, "v1.meta"),
            metadata(CatalogFileStamp.of(catalogFile)),
        )
        val restored = EncryptedCatalogFile { key }.readResolved(catalogFile)!!
        assertFalse(restored.fromBackup)
        assertEquals(3, restored.catalog.snapshot.liveChannels.size)
        assertEquals("https://media.invalid/live/2.ts", restored.catalog.snapshot.liveChannels[2].streamUrl)
    }
}
