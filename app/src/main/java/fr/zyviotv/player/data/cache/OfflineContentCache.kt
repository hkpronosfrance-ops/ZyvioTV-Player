package fr.zyviotv.player.data.cache

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import fr.zyviotv.player.data.catalog.CatalogPerformanceDiagnostics
import fr.zyviotv.player.data.catalog.CatalogRefreshPolicy
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
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/** Where a restored catalog came from. */
enum class CatalogCacheOrigin {
    /** Encrypted, compressed cache (PR #206): stream URLs are stored. */
    Encrypted,

    /**
     * SharedPreferences JSON written before PR #206. It never stored stream
     * URLs, so it can be browsed but never played (bloc #208).
     */
    LegacyWithoutSources,
}

data class CachedCatalog(
    val playlistId: String,
    val playlistName: String,
    val snapshot: CatalogSnapshot,
    val seriesDetails: Map<String, SeriesDetailSource> = emptyMap(),
    val origin: CatalogCacheOrigin = CatalogCacheOrigin.Encrypted,
) {
    val sourceReport: CatalogSourceReport by lazy {
        CatalogSourceReport.of(snapshot, seriesDetails)
    }

    /** Restored data that may be offered for playback without a refresh. */
    val isPlayable: Boolean
        get() = origin == CatalogCacheOrigin.Encrypted && sourceReport.isPlayable
}

/**
 * A restored catalog with its freshness (bloc #211). [fetchedAtEpochMs] is
 * only set when authenticated metadata attests this exact cache file.
 */
class RestoredCatalog(
    val catalog: CachedCatalog,
    val fetchedAtEpochMs: Long?,
    val idAliases: Map<String, Long>,
    /** Size and SHA-256 of the decoded cache file; null when read from the backup or legacy JSON. */
    val fileStamp: CatalogFileStamp? = null,
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
    private val encryptedFile = EncryptedCatalogFile(::secretKey)
    private val metadataFile = EncryptedCatalogMetadataFile(::secretKey)

    /**
     * Replaces the cached catalog only with a validated one: every channel,
     * film and indexed episode must carry its source. Returns the stamp of the
     * committed file, or null (keeping the previous cache) otherwise or when
     * the write fails.
     */
    fun saveCatalog(
        profileId: String,
        catalog: CachedCatalog,
        fetchedAtEpochMs: Long,
        idAliases: Map<String, Long> = emptyMap(),
    ): CatalogFileStamp? {
        val startedAt = CatalogPerformanceDiagnostics.startedAt()
        val report = catalog.sourceReport
        if (catalog.origin != CatalogCacheOrigin.Encrypted || !report.isPlayable) {
            CatalogPerformanceDiagnostics.event(
                name = "catalog_persist_rejected",
                fields = "reason=missing_sources " + report.logFields(),
                warning = true,
            )
            return null
        }
        return try {
            val stamp = encryptedFile.write(catalogFile(profileId), catalog)
            // The metadata is written only now, after the validated catalog was
            // committed: it attests this exact file and its fetch date.
            writeMetadata(profileId, catalog, stamp, fetchedAtEpochMs, idAliases)
            preferences.edit().remove(catalogKey(profileId)).apply()
            CatalogPerformanceDiagnostics.phase(
                name = "catalog_persist",
                startedAtMs = startedAt,
                itemCount = catalog.snapshot.itemCount(),
            )
            stamp
        } catch (error: Exception) {
            CatalogPerformanceDiagnostics.event(
                name = "catalog_persist_failed",
                fields = "failure=" + error.javaClass.simpleName,
                warning = true,
            )
            null
        }
    }

    fun loadCatalog(profileId: String): CachedCatalog? = restoreCatalog(profileId)?.catalog

    /**
     * Decodes the whole catalog cache (several seconds on large catalogs: call
     * off the main thread) and resolves its authenticated freshness.
     */
    fun restoreCatalog(profileId: String): RestoredCatalog? {
        val startedAt = CatalogPerformanceDiagnostics.startedAt()
        val read = try {
            encryptedFile.readResolved(catalogFile(profileId))
        } catch (error: Exception) {
            // Unreadable (key reset, truncation): never hide it behind the
            // legacy fallback silently. The next validated refresh rewrites it.
            CatalogPerformanceDiagnostics.event(
                name = "catalog_cache_unreadable",
                fields = "failure=" + error.javaClass.simpleName,
                warning = true,
            )
            null
        }
        val encrypted = read?.catalog
        if (encrypted != null && preferences.contains(catalogKey(profileId))) {
            // A pre-#206 JSON copy next to a valid encrypted cache is dead
            // weight loaded with every SharedPreferences access.
            preferences.edit().remove(catalogKey(profileId)).apply()
            CatalogPerformanceDiagnostics.event(name = "legacy_catalog_removed")
        }
        val stored = encrypted ?: loadLegacyCatalog(profileId)
        if (stored != null) {
            M3uSeriesDetailRegistry.replace(stored.seriesDetails)
            CatalogPerformanceDiagnostics.phase(
                name = "catalog_cache_load",
                startedAtMs = startedAt,
                itemCount = stored.snapshot.itemCount(),
            )
            CatalogPerformanceDiagnostics.event(
                name = "catalog_cache_sources",
                fields = "origin=" + stored.origin.name.lowercase() +
                    " playable=" + stored.isPlayable + " " + stored.sourceReport.logFields(),
                warning = !stored.isPlayable,
            )
        }
        stored ?: return null
        if (read == null) {
            return RestoredCatalog(stored, fetchedAtEpochMs = null, idAliases = emptyMap())
        }
        return resolveFreshness(profileId, stored, fromBackup = read.fromBackup)
    }

    private fun resolveFreshness(
        profileId: String,
        catalog: CachedCatalog,
        fromBackup: Boolean,
    ): RestoredCatalog {
        val metadata = readMetadata(profileId)
        val aliases = metadata?.idAliases.orEmpty()
        if (fromBackup) {
            CatalogPerformanceDiagnostics.event("catalog_cache_freshness", "status=backup")
            return RestoredCatalog(catalog, fetchedAtEpochMs = null, idAliases = aliases)
        }
        val stamp = runCatching { CatalogFileStamp.of(catalogFile(profileId)) }.getOrNull()
        if (metadata != null && metadata.attests(stamp)) {
            val ageMinutes = CatalogRefreshPolicy.ageMinutes(metadata.fetchedAtEpochMs, System.currentTimeMillis())
            CatalogPerformanceDiagnostics.event(
                "catalog_cache_freshness",
                "status=valid age_min=" + (ageMinutes?.toString() ?: "unknown"),
            )
            return RestoredCatalog(catalog, metadata.fetchedAtEpochMs, aliases, fileStamp = stamp)
        }
        CatalogPerformanceDiagnostics.event(
            "catalog_cache_freshness",
            "status=" + if (metadata == null) "missing" else "mismatch",
        )
        // Cache from before #211 (or interrupted save): the decoded file is
        // valid but its date is unknown. Attest it now with an unknown date so
        // later offline checks do not need a full decode. Never deleted.
        if (stamp != null && catalog.isPlayable) {
            writeMetadata(profileId, catalog, stamp, fetchedAtEpochMs = null, idAliases = aliases)
        }
        return RestoredCatalog(catalog, fetchedAtEpochMs = null, idAliases = aliases, fileStamp = stamp)
    }

    /**
     * Bloc #213 (PR B): removes the V1 file cache of a profile (catalogue,
     * backup, temporary file, metadata and pre-#206 JSON copy). Only call once
     * a complete Room generation of that profile is active. True when nothing
     * is left.
     */
    fun deleteCatalogFiles(profileId: String): Boolean {
        val catalog = catalogFile(profileId)
        val files = listOf(
            catalog,
            File(catalog.parentFile, catalog.name + ".bak"),
            File(catalog.parentFile, catalog.name + ".tmp"),
            metadataFile(profileId),
            File(catalogDirectory, metadataFile(profileId).name + ".tmp"),
        )
        val present = files.filter(File::exists)
        val hadLegacy = preferences.contains(catalogKey(profileId))
        if (present.isEmpty() && !hadLegacy) return true
        present.forEach(File::delete)
        if (hadLegacy) preferences.edit().remove(catalogKey(profileId)).apply()
        val removed = files.none(File::exists)
        CatalogPerformanceDiagnostics.event(
            name = "legacy_cache_deleted",
            fields = "files=${present.size} legacy_json=$hadLegacy complete=$removed",
            warning = !removed,
        )
        return removed
    }

    /** True when the profile has a cached library (favourites, progress, history). */
    fun hasLibrary(profileId: String?): Boolean =
        !profileId.isNullOrBlank() && loadLibrary(profileId) != null

    /** Authentic id aliases of the last saved catalog, even if that file changed since. */
    fun loadIdAliases(profileId: String): Map<String, Long> =
        readMetadata(profileId)?.idAliases.orEmpty()

    private fun readMetadata(profileId: String): CatalogCacheMetadata? = try {
        metadataFile.read(metadataFile(profileId))
    } catch (error: Exception) {
        CatalogPerformanceDiagnostics.event(
            name = "catalog_metadata_unreadable",
            fields = "failure=" + error.javaClass.simpleName,
            warning = true,
        )
        null
    }

    private fun writeMetadata(
        profileId: String,
        catalog: CachedCatalog,
        stamp: CatalogFileStamp,
        fetchedAtEpochMs: Long?,
        idAliases: Map<String, Long>,
    ) {
        val report = catalog.sourceReport
        try {
            metadataFile.write(
                metadataFile(profileId),
                CatalogCacheMetadata(
                    fetchedAtEpochMs = fetchedAtEpochMs,
                    stamp = stamp,
                    liveCount = report.liveTotal,
                    movieCount = report.moviesTotal,
                    seriesCount = catalog.snapshot.series.size,
                    episodeCount = report.episodesTotal,
                    idAliases = idAliases,
                ),
            )
        } catch (error: Exception) {
            // The catalog stays valid; its date is simply unknown next time.
            CatalogPerformanceDiagnostics.event(
                name = "catalog_metadata_failed",
                fields = "failure=" + error.javaClass.simpleName,
                warning = true,
            )
        }
    }

    private fun loadLegacyCatalog(profileId: String): CachedCatalog? = runCatching {
        val raw = preferences.getString(catalogKey(profileId), null) ?: return null
        val root = JSONObject(raw)
        CachedCatalog(
            playlistId = root.getString("playlist_id"),
            playlistName = root.optString("playlist_name"),
            snapshot = root.getJSONObject("snapshot").toCatalogSnapshot(),
            origin = CatalogCacheOrigin.LegacyWithoutSources,
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

    /**
     * Offline start check (bloc #211). Blocking I/O: call off the main thread.
     * The fast path never trusts a file's mere existence: the authenticated
     * metadata must attest the exact size and SHA-256 of the catalog file, so
     * a truncated, corrupted or replaced cache is never declared usable.
     * Without such metadata, the catalog is fully decoded once instead.
     */
    fun hasUsableOfflineData(profileId: String?): Boolean {
        if (profileId.isNullOrBlank()) return false
        if (loadLibrary(profileId) == null) return false
        val file = catalogFile(profileId)
        val metadata = if (file.exists()) readMetadata(profileId) else null
        if (metadata != null) {
            val stamp = runCatching { CatalogFileStamp.of(file) }.getOrNull()
            if (metadata.attests(stamp)) {
                CatalogPerformanceDiagnostics.event("offline_check", "method=metadata usable=true")
                return true
            }
        }
        val usable = restoreCatalog(profileId) != null
        CatalogPerformanceDiagnostics.event("offline_check", "method=full_decode usable=$usable")
        return usable
    }

    private fun catalogKey(profileId: String) = "catalog_" + profileId
    private fun libraryKey(profileId: String) = "library_" + profileId

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

    private fun metadataFile(profileId: String): File =
        File(catalogDirectory, catalogFile(profileId).nameWithoutExtension + ".meta")

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
    }
}
