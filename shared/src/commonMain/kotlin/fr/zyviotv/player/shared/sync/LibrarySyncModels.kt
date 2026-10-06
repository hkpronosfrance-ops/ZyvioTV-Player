package fr.zyviotv.player.shared.sync

enum class FavoriteContentType(val wireValue: String) {
    Live("live"),
    Movie("movie"),
    Series("series"),
}

enum class ProgressContentType(val wireValue: String) {
    Movie("movie"),
    Episode("episode"),
}

data class SyncedFavorite(
    val profileId: String = "",
    val playlistId: String,
    val contentType: FavoriteContentType,
    val contentId: String,
    val title: String,
    val artworkUrl: String? = null,
)

data class SyncedWatchProgress(
    val profileId: String = "",
    val playlistId: String,
    val contentType: ProgressContentType,
    val contentId: String,
    val title: String,
    val seriesId: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val artworkUrl: String? = null,
    val positionMs: Long,
    val durationMs: Long? = null,
    val completed: Boolean = false,
) {
    val fraction: Float
        get() {
            val duration = durationMs ?: return 0f
            if (duration <= 0L) return 0f
            return (positionMs.toDouble() / duration.toDouble())
                .coerceIn(0.0, 1.0)
                .toFloat()
        }
}

interface CloudLibraryRepository {
    suspend fun listFavorites(profileId: String): Result<List<SyncedFavorite>>
    suspend fun upsertFavorite(favorite: SyncedFavorite): SyncResult
    suspend fun removeFavorite(
        profileId: String,
        playlistId: String,
        contentType: FavoriteContentType,
        contentId: String,
    ): SyncResult

    suspend fun listWatchProgress(profileId: String, limit: Int = 50): Result<List<SyncedWatchProgress>>
    suspend fun upsertWatchProgress(progress: SyncedWatchProgress): SyncResult
    suspend fun removeWatchProgress(
        profileId: String,
        playlistId: String,
        contentType: ProgressContentType,
        contentId: String,
    ): SyncResult
}
