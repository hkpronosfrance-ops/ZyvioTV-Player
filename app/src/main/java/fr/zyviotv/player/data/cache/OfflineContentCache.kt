package fr.zyviotv.player.data.cache

import android.content.Context
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

data class CachedCatalog(
    val playlistId: String,
    val playlistName: String,
    val snapshot: CatalogSnapshot,
)

data class CachedLibrary(
    val profileId: String,
    val favorites: List<SyncedFavorite>,
    val progress: List<SyncedWatchProgress>,
    val liveHistory: List<SyncedLiveHistory>,
)

class OfflineContentCache(context: Context) {
    private val preferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveCatalog(profileId: String, catalog: CachedCatalog) {
        val root = JSONObject()
            .put("playlist_id", catalog.playlistId)
            .put("playlist_name", catalog.playlistName)
            .put("snapshot", catalog.snapshot.toJson())
        preferences.edit()
            .putString(catalogKey(profileId), root.toString())
            .apply()
    }

    fun loadCatalog(profileId: String): CachedCatalog? = runCatching {
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

    private companion object {
        const val PREFS_NAME = "zyviotv_offline_content_cache"
    }
}
