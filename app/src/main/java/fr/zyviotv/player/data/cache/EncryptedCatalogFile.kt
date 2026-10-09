package fr.zyviotv.player.data.cache

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * On-disk format of the catalog cache introduced by PR #206:
 * `MAGIC | iv length | iv | AES-GCM(gzip(CatalogCacheCodec))`, replaced
 * atomically through a temporary file and a backup.
 *
 * Extracted from OfflineContentCache so the full write/read path (including
 * encryption and compression) runs in JVM tests with a software key, while
 * the app keeps its Android Keystore key (bloc #208). The format is unchanged.
 */
internal class EncryptedCatalogFile(
    private val secretKey: () -> SecretKey,
) {
    /** Returns the stamp of the committed file (bloc #211 metadata attests it). */
    fun write(target: File, catalog: CachedCatalog): CatalogFileStamp {
        target.parentFile?.mkdirs()
        val temporary = File(target.parentFile, target.name + ".tmp")
        val backup = File(target.parentFile, target.name + ".bak")
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.ENCRYPT_MODE, secretKey())
            }
            val digest = MessageDigest.getInstance("SHA-256")
            val counter = CountingOutputStream(FileOutputStream(temporary))
            DigestOutputStream(counter, digest).use { fileOutput ->
                fileOutput.write(MAGIC)
                fileOutput.write(cipher.iv.size)
                fileOutput.write(cipher.iv)
                CipherOutputStream(fileOutput, cipher).use { encrypted ->
                    GZIPOutputStream(encrypted, STREAM_BUFFER_BYTES).use { compressed ->
                        DataOutputStream(compressed).use { output ->
                            CatalogCacheCodec.write(output, catalog)
                        }
                    }
                }
            }
            val stamp = CatalogFileStamp(counter.count, digest.digest())
            backup.delete()
            if (target.exists()) check(target.renameTo(backup)) { "Unable to back up catalog cache" }
            if (!temporary.renameTo(target)) {
                backup.renameTo(target)
                error("Unable to commit catalog cache")
            }
            backup.delete()
            return stamp
        } catch (error: Throwable) {
            temporary.delete()
            if (!target.exists()) backup.renameTo(target)
            throw error
        }
    }

    /** Null when no cache file exists; throws when one exists but cannot be decoded. */
    fun read(target: File): CachedCatalog? = readResolved(target)?.catalog

    /** Like [read], and tells whether the backup file had to be used. */
    fun readResolved(target: File): ReadResult? {
        val fromBackup = !target.exists()
        val source = target.takeIf(File::exists)
            ?: File(target.parentFile, target.name + ".bak").takeIf(File::exists)
            ?: return null
        return ReadResult(catalog = decode(source), fromBackup = fromBackup)
    }

    class ReadResult(val catalog: CachedCatalog, val fromBackup: Boolean)

    private fun decode(source: File): CachedCatalog {
        return FileInputStream(source).use { fileInput ->
            val magic = fileInput.readExactly(MAGIC.size)
            check(magic.contentEquals(MAGIC)) { "Unknown catalog cache header" }
            val ivLength = fileInput.read()
            check(ivLength == IV_LENGTH_BYTES) { "Invalid catalog cache IV" }
            val iv = fileInput.readExactly(ivLength)
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            }
            CipherInputStream(fileInput, cipher).use { decrypted ->
                GZIPInputStream(decrypted, STREAM_BUFFER_BYTES).use { decompressed ->
                    DataInputStream(decompressed).use(CatalogCacheCodec::read)
                }
            }
        }
    }

    /** Counts the bytes that reach the file, for the stamp. */
    private class CountingOutputStream(private val delegate: OutputStream) : OutputStream() {
        var count = 0L
            private set

        override fun write(value: Int) {
            delegate.write(value)
            count += 1
        }

        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            delegate.write(bytes, offset, length)
            count += length
        }

        override fun flush() = delegate.flush()

        override fun close() = delegate.close()
    }

    private fun InputStream.readExactly(count: Int): ByteArray {
        val bytes = ByteArray(count)
        var offset = 0
        while (offset < count) {
            val read = read(bytes, offset, count - offset)
            if (read < 0) throw IOException("Truncated catalog cache header")
            offset += read
        }
        return bytes
    }

    companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_LENGTH_BYTES = 12
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val STREAM_BUFFER_BYTES = 64 * 1024
        private val MAGIC = byteArrayOf('Z'.code.toByte(), 'V'.code.toByte(), 'C'.code.toByte(), 1)
    }
}
