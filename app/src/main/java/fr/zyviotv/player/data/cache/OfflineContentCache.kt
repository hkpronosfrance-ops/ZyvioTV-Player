package fr.zyviotv.player.data.cache

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import fr.zyviotv.player.data.catalog.CatalogPerformanceDiagnostics
import fr.zyviotv.player.data.catalog.M3uSeriesDetailRegistry
import fr.zyviotv.player.data.catalog.SeriesDetailSource
import fr.zyviotv.player.data.sync.SyncedLiveHistory
import fr.zyviotv.player.shared.catalog.CatalogCategory
import fr.zyviotv.player.shared.catalog.CatalogLiveChannel
import fr.zyviotv.player.shared.catalog.CatalogMovie
import fr.zyviotv.player.shared.catalog.CatalogSeries
import fr.zyviotv.player.shared.catalog.CatalogSnapshot
import fr.zyviotv.player.shared.sync.FavoriteContentType
import fr.zyviotv.player.shared.sync.ProgressContentType
import fr.zyviotv.player.shared.sync.SyncedFavorite
import fr.zyviotv.player.shared.sync.SyncedWatchProgress
import org.json.JSONArray
import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

data class CachedCatalog(
    val playlistId: String,
    val playlistName: String,
    val snapshot: CatalogSnapshot,
    val seriesDetails: Map<String, SeriesDetailSource> = emptyMap(),
)

data class CachedLibrary(
    val profileId: String,
    val favorites: List<SyncedFavorite>,
    val progress: List<SyncedWatchProgress>,
    val liveHistory: List<SyncedLiveHistory>,
)

class OfflineContentCache(context: Context) {
    private val applicationContext = context.applicationContext
    private val preferences =
        applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val catalogDirectory = File(applicationContext.filesDir, CATALOG_DIRECTORY)

    fun saveCatalog(profileId: String, catalog: CachedCatalog) {
        val startedAt = CatalogPerformanceDiagnostics.startedAt()
        catalogDirectory.mkdirs()
        val target = catalogFile(profileId)
        val temporary = File(target.parentFile, target.name + ".tmp")
        val backup = File(target.parentFile, target.name + ".bak")
        runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.ENCRYPT_MODE, secretKey())
            }
            FileOutputStream(temporary).use { fileOutput ->
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
            backup.delete()
            if (target.exists()) check(target.renameTo(backup))
            if (!temporary.renameTo(target)) {
                backup.renameTo(target)
                error("Unable to commit catalog cache")
            }
            backup.delete()
            preferences.edit().remove(catalogKey(profileId)).apply()
            CatalogPerformanceDiagnostics.phase(
                name = "catalog_persist",
                startedAtMs = startedAt,
                itemCount = catalog.snapshot.itemCount(),
            )
        }.onFailure {
            temporary.delete()
            if (!target.exists()) backup.renameTo(target)
        }
    }

    fun loadCatalog(profileId: String): CachedCatalog? {
        val startedAt = CatalogPerformanceDiagnostics.startedAt()
        val stored = loadEncryptedCatalog(profileId) ?: loadLegacyCatalog(profileId)
        if (stored != null) {
            M3uSeriesDetailRegistry.replace(stored.seriesDetails)
            CatalogPerformanceDiagnostics.phase(
                name = "catalog_cache_load",
                startedAtMs = startedAt,
                itemCount = stored.snapshot.itemCount(),
            )
        }
        return stored
    }

    private fun loadEncryptedCatalog(profileId: String): CachedCatalog? = runCatching {
        val target = catalogFile(profileId).let { file ->
            if (file.exists()) file else File(file.parentFile, file.name + ".bak")
        }
        if (!target.exists()) return null
        FileInputStream(target).use { fileInput ->
            val magic = ByteArray(MAGIC.size)
            check(fileInput.read(magic) == magic.size && magic.contentEquals(MAGIC))
            val ivLength = fileInput.read()
            check(ivLength == IV_LENGTH_BYTES)
            val iv = ByteArray(ivLength)
            check(fileInput.read(iv) == ivLength)
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            }
            CipherInputStream(fileInput, cipher).use { decrypted ->
                GZIPInputStream(decrypted, STREAM_BUFFER_BYTES).use { decompressed ->
                    DataInputStream(decompressed).use(CatalogCacheCodec::read)
                }
            }
        }
    }.getOrNull()

    private fun loadLegacyCatalog(profileId: String): CachedCatalog? = runCatching {
        val raw = preferences.getString(catalogKey(profileId), null) ?: return null
        val root = JSONObject(raw)
        CachedCatalog(
            playlistId = root.getString("playlist_id"),
            playlistName = root.optString("playlist_name"),
            snapshot = root.getJSONObject("snapshot").toCatalogSnapshot(),
        )
    }.getOrNull()

    fun saveLibrary(library: CachedLibrary) {
        val root = JSONObject()
            .put("profile_id", library.profileId)
            .put("favorites", JSONArray().apply {
                library.favorites.forEach { put(it.toJson()) }
            })
            .put("progress", JSONArray().apply {
                library.progress.forEach { put(it.toJson()) }
            })
            .put("live_history", JSONArray().apply {
                library.liveHistory.forEach { put(it.toJson()) }
            })
        preferences.edit()
            .putString(libraryKey(library.profileId), root.toString())
            .apply()
    }

    fun loadLibrary(profileId: String): CachedLibrary? = runCatching {
        val raw = preferences.getString(libraryKey(profileId), null) ?: return null
        val root = JSONObject(raw)
        CachedLibrary(
            profileId = root.getString("profile_id"),
            favorites = root.getJSONArray("favorites").mapObjects { it.toFavorite() },
            progress = root.getJSONArray("progress").mapObjects { it.toProgress() },
            liveHistory = root.getJSONArray("live_history").mapObjects { it.toLiveHistory() },
        )
    }.getOrNull()

    fun hasUsableOfflineData(profileId: String?): Boolean {
        if (profileId.isNullOrBlank()) return false
        return loadCatalog(profileId) != null && loadLibrary(profileId) != null
    }

    private fun catalogKey(profileId: String) = "catalog_" + profileId
    private fun libraryKey(profileId: String) = "library_" + profileId

    private fun CatalogSnapshot.toJson() = JSONObject()
        .put("live_categories", categoriesToJson(liveCategories))
        .put("live_channels", JSONArray().apply {
            liveChannels.forEach { channel ->
                put(
                    JSONObject()
                        .put("id", channel.id)
                        .put("name", channel.name)
                        .putNullable("category_id", channel.categoryId)
                        .putNullable("logo_url", channel.logoUrl)
                        .putNullable("epg_id", channel.epgId),
                )
            }
        })
        .put("movie_categories", categoriesToJson(movieCategories))
        .put("movies", JSONArray().apply {
            movies.forEach { movie ->
                put(
                    JSONObject()
                        .put("id", movie.id)
                        .put("title", movie.title)
                        .putNullable("category_id", movie.categoryId)
                        .putNullable("poster_url", movie.posterUrl)
                        .put("container_extension", movie.containerExtension)
                        .putNullable("added_at", movie.addedAtEpochSeconds),
                )
            }
        })
        .put("series_categories", categoriesToJson(seriesCategories))
        .put("series", JSONArray().apply {
            series.forEach { series ->
                put(
                    JSONObject()
                        .put("id", series.id)
                        .put("title", series.title)
                        .putNullable("category_id", series.categoryId)
                        .putNullable("poster_url", series.posterUrl)
                        .putNullable("added_at", series.addedAtEpochSeconds),
                )
            }
        })

    private fun JSONObject.toCatalogSnapshot() = CatalogSnapshot(
        liveCategories = getJSONArray("live_categories").mapObjects { it.toCategory() },
        liveChannels = getJSONArray("live_channels").mapObjects {
            CatalogLiveChannel(
                id = it.getString("id"),
                name = it.getString("name"),
                categoryId = it.optNullableString("category_id"),
                logoUrl = it.optNullableString("logo_url"),
                streamUrl = "",
                epgId = it.optNullableString("epg_id"),
            )
        },
        movieCategories = getJSONArray("movie_categories").mapObjects { it.toCategory() },
        movies = getJSONArray("movies").mapObjects {
            CatalogMovie(
                id = it.getString("id"),
                title = it.getString("title"),
                categoryId = it.optNullableString("category_id"),
                posterUrl = it.optNullableString("poster_url"),
                streamUrl = "",
                containerExtension = it.optString("container_extension", "mp4"),
                addedAtEpochSeconds = it.optNullableLong("added_at"),
            )
        },
        seriesCategories = getJSONArray("series_categories").mapObjects { it.toCategory() },
        series = getJSONArray("series").mapObjects {
            CatalogSeries(
                id = it.getString("id"),
                title = it.getString("title"),
                categoryId = it.optNullableString("category_id"),
                posterUrl = it.optNullableString("poster_url"),
                addedAtEpochSeconds = it.optNullableLong("added_at"),
            )
        },
    )

    private fun SyncedFavorite.toJson() = JSONObject()
        .put("profile_id", profileId)
        .put("playlist_id", playlistId)
        .put("content_type", contentType.wireValue)
        .put("content_id", contentId)
        .put("title", title)
        .putNullable("artwork_url", artworkUrl)

    private fun JSONObject.toFavorite() = SyncedFavorite(
        profileId = getString("profile_id"),
        playlistId = getString("playlist_id"),
        contentType = FavoriteContentType.entries.first { it.wireValue == getString("content_type") },
        contentId = getString("content_id"),
        title = getString("title"),
        artworkUrl = optNullableString("artwork_url"),
    )

    private fun SyncedWatchProgress.toJson() = JSONObject()
        .put("profile_id", profileId)
        .put("playlist_id", playlistId)
        .put("content_type", contentType.wireValue)
        .put("content_id", contentId)
        .put("title", title)
        .putNullable("series_id", seriesId)
        .putNullable("season_number", seasonNumber)
        .putNullable("episode_number", episodeNumber)
        .putNullable("artwork_url", artworkUrl)
        .put("position_ms", positionMs)
        .putNullable("duration_ms", durationMs)
        .put("completed", completed)

    private fun JSONObject.toProgress() = SyncedWatchProgress(
        profileId = getString("profile_id"),
        playlistId = getString("playlist_id"),
        contentType = ProgressContentType.entries.first { it.wireValue == getString("content_type") },
        contentId = getString("content_id"),
        title = getString("title"),
        seriesId = optNullableString("series_id"),
        seasonNumber = optNullableInt("season_number"),
        episodeNumber = optNullableInt("episode_number"),
        artworkUrl = optNullableString("artwork_url"),
        positionMs = getLong("position_ms"),
        durationMs = optNullableLong("duration_ms"),
        completed = optBoolean("completed", false),
    )

    private fun SyncedLiveHistory.toJson() = JSONObject()
        .put("profile_id", profileId)
        .put("playlist_id", playlistId)
        .put("channel_id", channelId)
        .put("channel_name", channelName)
        .putNullable("logo_url", logoUrl)

    private fun JSONObject.toLiveHistory() = SyncedLiveHistory(
        profileId = getString("profile_id"),
        playlistId = getString("playlist_id"),
        channelId = getString("channel_id"),
        channelName = getString("channel_name"),
        logoUrl = optNullableString("logo_url"),
    )

    private fun categoriesToJson(items: List<CatalogCategory>) = JSONArray().apply {
        items.forEach { put(JSONObject().put("id", it.id).put("name", it.name)) }
    }

    private fun JSONObject.toCategory() = CatalogCategory(
        id = getString("id"),
        name = getString("name"),
    )

    private fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> =
        buildList {
            for (index in 0 until length()) {
                add(transform(getJSONObject(index)))
            }
        }

    private fun JSONObject.putNullable(key: String, value: Any?): JSONObject =
        put(key, value ?: JSONObject.NULL)

    private fun JSONObject.optNullableString(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    private fun JSONObject.optNullableLong(key: String): Long? =
        if (isNull(key) || !has(key)) null else optLong(key)

    private fun JSONObject.optNullableInt(key: String): Int? =
        if (isNull(key) || !has(key)) null else optInt(key)

    private fun catalogFile(profileId: String): File {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(profileId.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
        return File(catalogDirectory, "$digest.catalog")
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        val existing = keyStore.getKey(CATALOG_KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEY_STORE,
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                CATALOG_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }

    private fun CatalogSnapshot.itemCount(): Int =
        liveChannels.size + movies.size + series.size

    private companion object {
        const val PREFS_NAME = "zyviotv_offline_content_cache"
        const val CATALOG_DIRECTORY = "offline-catalogs"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val CATALOG_KEY_ALIAS = "zyviotv_player_catalog_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH_BYTES = 12
        const val GCM_TAG_LENGTH_BITS = 128
        const val STREAM_BUFFER_BYTES = 64 * 1024
        val MAGIC = byteArrayOf('Z'.code.toByte(), 'V'.code.toByte(), 'C'.code.toByte(), 1)
    }
}
