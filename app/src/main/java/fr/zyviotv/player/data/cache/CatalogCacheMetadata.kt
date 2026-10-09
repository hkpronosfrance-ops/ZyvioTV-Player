package fr.zyviotv.player.data.cache

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Exact identity of a catalog cache file: its size and SHA-256 digest. */
class CatalogFileStamp(val length: Long, sha256: ByteArray) {
    private val digest = sha256.copyOf()

    val sha256: ByteArray
        get() = digest.copyOf()

    override fun equals(other: Any?): Boolean =
        other is CatalogFileStamp && other.length == length && other.digest.contentEquals(digest)

    override fun hashCode(): Int = 31 * length.hashCode() + digest.contentHashCode()

    // Never print the digest: it is not secret, but logs only carry counts.
    override fun toString(): String = "CatalogFileStamp(length=$length)"

    companion object {
        /** Streams the file once; never loads it in memory. */
        fun of(file: File): CatalogFileStamp {
            val digest = MessageDigest.getInstance("SHA-256")
            var length = 0L
            FileInputStream(file).use { input ->
                val buffer = ByteArray(STREAM_BUFFER_BYTES)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                    length += read
                }
            }
            return CatalogFileStamp(length, digest.digest())
        }

        private const val STREAM_BUFFER_BYTES = 64 * 1024
    }
}

/**
 * Bloc #211 metadata written next to the catalog cache, only after the catalog
 * itself was validated (every source present) and committed. It attests one
 * exact catalog file through [stamp]; a metadata file that does not match the
 * catalog on disk proves nothing and is ignored.
 *
 * @param fetchedAtEpochMs when the provider catalogue was downloaded, or null
 *   when unknown (cache written before #211).
 * @param idAliases M3U legacy id to the 64-bit fingerprint of the entry that
 *   holds it, only for ids shared by several entries (see M3uIdResolver).
 */
data class CatalogCacheMetadata(
    val fetchedAtEpochMs: Long?,
    val stamp: CatalogFileStamp,
    val liveCount: Int,
    val movieCount: Int,
    val seriesCount: Int,
    val episodeCount: Int,
    val idAliases: Map<String, Long> = emptyMap(),
) {
    /** True when this metadata describes exactly [actual]. */
    fun attests(actual: CatalogFileStamp?): Boolean = actual != null && actual == stamp
}

/**
 * Small authenticated file: `MAGIC | iv length | iv | AES-GCM(payload)`.
 * AES-GCM authenticates the whole payload, so the fetch date cannot be edited
 * without the decryption failing. Written atomically (temporary file + rename).
 */
internal class EncryptedCatalogMetadataFile(
    private val secretKey: () -> SecretKey,
) {
    fun write(target: File, metadata: CatalogCacheMetadata) {
        target.parentFile?.mkdirs()
        val cipher = Cipher.getInstance(EncryptedCatalogFile.TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, secretKey())
            updateAAD(ASSOCIATED_DATA)
        }
        val encrypted = cipher.doFinal(encode(metadata))
        val temporary = File(target.parentFile, target.name + ".tmp")
        try {
            FileOutputStream(temporary).use { output ->
                output.write(MAGIC)
                output.write(cipher.iv.size)
                output.write(cipher.iv)
                output.write(encrypted)
                output.fd.sync()
            }
            if (!temporary.renameTo(target)) {
                target.delete()
                check(temporary.renameTo(target)) { "Unable to commit catalog metadata" }
            }
        } finally {
            temporary.delete()
        }
    }

    /** Null when absent; throws when present but unreadable or not authentic. */
    fun read(target: File): CatalogCacheMetadata? {
        if (!target.exists()) return null
        val bytes = target.readBytes()
        if (bytes.size > MAX_FILE_BYTES) throw IOException("Catalog metadata too large")
        check(bytes.size > MAGIC.size + 1) { "Truncated catalog metadata" }
        check(bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) { "Unknown catalog metadata header" }
        val ivLength = bytes[MAGIC.size].toInt()
        check(ivLength == IV_LENGTH_BYTES) { "Invalid catalog metadata IV" }
        val ivStart = MAGIC.size + 1
        check(bytes.size > ivStart + ivLength) { "Truncated catalog metadata" }
        val iv = bytes.copyOfRange(ivStart, ivStart + ivLength)
        val cipher = Cipher.getInstance(EncryptedCatalogFile.TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            updateAAD(ASSOCIATED_DATA)
        }
        val payload = cipher.doFinal(bytes, ivStart + ivLength, bytes.size - ivStart - ivLength)
        return decode(payload)
    }

    companion object {
        private const val PAYLOAD_VERSION = 2
        private const val IV_LENGTH_BYTES = 12
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val SHA256_BYTES = 32
        private const val MAX_FILE_BYTES = 1024 * 1024
        private const val MAX_ALIASES = 100_000
        private val MAGIC = byteArrayOf('Z'.code.toByte(), 'V'.code.toByte(), 'M'.code.toByte(), 2)
        private val ASSOCIATED_DATA = "zyviotv-catalog-metadata".toByteArray(Charsets.UTF_8)

        internal fun encode(metadata: CatalogCacheMetadata): ByteArray {
            val bytes = ByteArrayOutputStream()
            DataOutputStream(bytes).use { output ->
                output.writeInt(PAYLOAD_VERSION)
                output.writeLong(metadata.fetchedAtEpochMs ?: 0L)
                output.writeLong(metadata.stamp.length)
                output.write(metadata.stamp.sha256)
                output.writeInt(metadata.liveCount)
                output.writeInt(metadata.movieCount)
                output.writeInt(metadata.seriesCount)
                output.writeInt(metadata.episodeCount)
                output.writeInt(metadata.idAliases.size)
                metadata.idAliases.forEach { (id, fingerprint) ->
                    output.writeUTF(id)
                    output.writeLong(fingerprint)
                }
            }
            return bytes.toByteArray()
        }

        internal fun decode(payload: ByteArray): CatalogCacheMetadata =
            DataInputStream(ByteArrayInputStream(payload)).use { input ->
                val version = input.readInt()
                check(version == PAYLOAD_VERSION) { "Unsupported catalog metadata version" }
                val fetchedAt = input.readLong().takeIf { it > 0L }
                val length = input.readLong()
                val digest = ByteArray(SHA256_BYTES).also(input::readFully)
                val live = input.readInt()
                val movies = input.readInt()
                val series = input.readInt()
                val episodes = input.readInt()
                val aliasCount = input.readInt()
                check(aliasCount in 0..MAX_ALIASES) { "Invalid catalog metadata aliases" }
                val aliases = LinkedHashMap<String, Long>(aliasCount)
                repeat(aliasCount) { aliases[input.readUTF()] = input.readLong() }
                CatalogCacheMetadata(
                    fetchedAtEpochMs = fetchedAt,
                    stamp = CatalogFileStamp(length, digest),
                    liveCount = live,
                    movieCount = movies,
                    seriesCount = series,
                    episodeCount = episodes,
                    idAliases = aliases,
                )
            }
    }
}
