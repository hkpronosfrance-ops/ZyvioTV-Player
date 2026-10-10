package fr.zyviotv.player.data.store

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Wraps and unwraps generation data keys. In the app the wrapping key lives in
 * the Android Keystore ([KeystoreKeyWrapper]); JVM tests use a software key.
 */
interface GenerationKeyWrapper {
    fun wrap(rawKey: ByteArray): ByteArray

    fun unwrap(wrapped: ByteArray): ByteArray
}

/**
 * Field-level encryption of stream URLs (decision #211/#212), envelope style:
 * each generation gets a random AES-256 data key, wrapped by the Keystore key
 * `zyviotv_player_catalog_url_key`. URLs are then sealed in software with the
 * data key, so a full import (~140 000 URLs) does not cross the Keystore once
 * per URL. The data key never reaches the disk unwrapped, and it disappears
 * with its generation.
 *
 * Each blob is `iv (12 bytes) | AES-GCM(url)`, with the associated data
 * `generationId|kind|id`: a blob copied onto another row does not decrypt.
 */
class CatalogUrlCipher private constructor(private val dataKey: SecretKey) {
    fun seal(generationId: Long, kind: CatalogKind, id: String, url: String): ByteArray {
        val iv = ByteArray(IV_LENGTH_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, dataKey, GCMParameterSpec(TAG_LENGTH_BITS, iv))
            updateAAD(associatedData(generationId, kind, id))
        }
        val sealed = cipher.doFinal(url.toByteArray(Charsets.UTF_8))
        return iv + sealed
    }

    /** Throws when the blob was altered or belongs to another row. */
    fun open(generationId: Long, kind: CatalogKind, id: String, blob: ByteArray): String {
        require(blob.size > IV_LENGTH_BYTES) { "Truncated catalog URL" }
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(
                Cipher.DECRYPT_MODE,
                dataKey,
                GCMParameterSpec(TAG_LENGTH_BITS, blob, 0, IV_LENGTH_BYTES),
            )
            updateAAD(associatedData(generationId, kind, id))
        }
        val clear = cipher.doFinal(blob, IV_LENGTH_BYTES, blob.size - IV_LENGTH_BYTES)
        return clear.toString(Charsets.UTF_8)
    }

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_LENGTH_BYTES = 12
        private const val TAG_LENGTH_BITS = 128
        private const val DATA_KEY_BITS = 256
        private val random = SecureRandom()

        /** New data key for a new generation; returns the cipher and the wrapped key to store. */
        fun create(wrapper: GenerationKeyWrapper): Pair<CatalogUrlCipher, ByteArray> {
            val generator = KeyGenerator.getInstance("AES").apply { init(DATA_KEY_BITS, random) }
            val key = generator.generateKey()
            val raw = key.encoded
            try {
                return CatalogUrlCipher(SecretKeySpec(raw, "AES")) to wrapper.wrap(raw)
            } finally {
                raw.fill(0)
            }
        }

        /** Cipher of an existing generation, from its wrapped key. */
        fun restore(wrapper: GenerationKeyWrapper, wrappedKey: ByteArray): CatalogUrlCipher {
            val raw = wrapper.unwrap(wrappedKey)
            try {
                return CatalogUrlCipher(SecretKeySpec(raw, "AES"))
            } finally {
                raw.fill(0)
            }
        }

        private fun associatedData(generationId: Long, kind: CatalogKind, id: String): ByteArray =
            (generationId.toString() + "|" + kind.wire + "|" + id).toByteArray(Charsets.UTF_8)
    }
}

/** AES-GCM wrapping with a fixed associated data; the key itself is supplied by the caller. */
open class AesGcmKeyWrapper(private val wrappingKey: () -> SecretKey) : GenerationKeyWrapper {
    override fun wrap(rawKey: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, wrappingKey())
            updateAAD(ASSOCIATED_DATA)
        }
        return cipher.iv + cipher.doFinal(rawKey)
    }

    override fun unwrap(wrapped: ByteArray): ByteArray {
        require(wrapped.size > IV_LENGTH_BYTES) { "Truncated generation key" }
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(
                Cipher.DECRYPT_MODE,
                wrappingKey(),
                GCMParameterSpec(TAG_LENGTH_BITS, wrapped, 0, IV_LENGTH_BYTES),
            )
            updateAAD(ASSOCIATED_DATA)
        }
        return cipher.doFinal(wrapped, IV_LENGTH_BYTES, wrapped.size - IV_LENGTH_BYTES)
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH_BYTES = 12
        const val TAG_LENGTH_BITS = 128
        val ASSOCIATED_DATA = "zyviotv-catalog-generation-key".toByteArray(Charsets.UTF_8)
    }
}

/** Wrapping key held by the Android Keystore, dedicated to catalogue URLs. */
class KeystoreKeyWrapper : AesGcmKeyWrapper(KeystoreUrlKey::get)

private object KeystoreUrlKey {
    private const val ANDROID_KEY_STORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "zyviotv_player_catalog_url_key"

    @Synchronized
    fun get(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }
}
