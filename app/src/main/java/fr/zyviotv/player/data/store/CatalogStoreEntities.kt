package fr.zyviotv.player.data.store

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * Bloc #213 (PR A): generational catalogue store. Each provider refresh (or the
 * one-time import of the V1 file cache) is written into its own generation,
 * which becomes `active` in a single transaction once complete and verified.
 *
 * Stream URLs (with their Kodi headers) are never stored in clear: `url_blob`
 * holds `iv | AES-GCM(url)` sealed with the generation key, itself wrapped by
 * an Android Keystore key (see CatalogUrlCipher). Titles, categories, artwork
 * and ids stay queryable in the app-private database (decision #211/#212).
 *
 * Rows are keyed by their provider order (`ordinal`) rather than by provider
 * id: a duplicated provider id can never make a whole import fail.
 */

@Entity(
    tableName = "generation",
    indices = [Index(value = ["profile_key", "playlist_id", "state"])],
)
data class GenerationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** SHA-256 of the profile id, like the V1 cache file name. */
    @ColumnInfo(name = "profile_key") val profileKey: String,
    @ColumnInfo(name = "playlist_id") val playlistId: String,
    @ColumnInfo(name = "playlist_name") val playlistName: String,
    /** [GenerationState] wire value. */
    val state: String,
    /** [GenerationScope] wire value: raw provider catalogue or profile-filtered V1 copy. */
    val scope: String,
    /** [GenerationOrigin] wire value. */
    val origin: String,
    @ColumnInfo(name = "fetched_at_ms") val fetchedAtEpochMs: Long?,
    @ColumnInfo(name = "created_at_ms") val createdAtEpochMs: Long,
    @ColumnInfo(name = "live_count") val liveCount: Int,
    @ColumnInfo(name = "movie_count") val movieCount: Int,
    @ColumnInfo(name = "series_count") val seriesCount: Int,
    @ColumnInfo(name = "episode_count") val episodeCount: Int,
    /** Size and SHA-256 (hex) of the V1 cache file this generation mirrors, if any. */
    @ColumnInfo(name = "v1_length") val v1Length: Long?,
    @ColumnInfo(name = "v1_sha256") val v1Sha256: String?,
    /** Generation data key wrapped by the Keystore key: `iv | AES-GCM(key)`. */
    @ColumnInfo(name = "wrapped_key", typeAffinity = ColumnInfo.BLOB) val wrappedKey: ByteArray,
)

enum class GenerationState(val wire: String) {
    Building("building"),
    Active("active"),
    Retired("retired"),
}

enum class GenerationScope(val wire: String) {
    /** Complete provider catalogue; parental rules are applied when reading (PR B). */
    Raw("raw"),

    /** Copy of a V1 cache, which was saved after the profile's parental filtering. */
    ProfileFiltered("profile_filtered"),
}

enum class GenerationOrigin(val wire: String) {
    V1Import("v1_import"),
    Refresh("refresh"),
}

@Entity(
    tableName = "category",
    primaryKeys = ["generation_id", "kind", "ordinal"],
    indices = [Index(value = ["generation_id", "kind", "id"])],
)
data class CategoryEntity(
    @ColumnInfo(name = "generation_id") val generationId: Long,
    /** [CatalogKind] wire value. */
    val kind: String,
    val ordinal: Int,
    val id: String,
    val name: String,
)

enum class CatalogKind(val wire: String) {
    Live("live"),
    Movie("movie"),
    Series("series"),
    Episode("episode"),
}

@Entity(
    tableName = "live_channel",
    primaryKeys = ["generation_id", "ordinal"],
    indices = [
        Index(value = ["generation_id", "id"]),
        Index(value = ["generation_id", "category_id", "ordinal"]),
    ],
)
data class LiveChannelEntity(
    @ColumnInfo(name = "generation_id") val generationId: Long,
    val ordinal: Int,
    val id: String,
    val name: String,
    @ColumnInfo(name = "category_id") val categoryId: String?,
    @ColumnInfo(name = "logo_url") val logoUrl: String?,
    @ColumnInfo(name = "epg_id") val epgId: String?,
    /** Normalised "name number category", as matched by CatalogSearchEngine. */
    @ColumnInfo(name = "search_key") val searchKey: String,
    @ColumnInfo(name = "url_blob", typeAffinity = ColumnInfo.BLOB) val urlBlob: ByteArray,
)

@Entity(
    tableName = "movie",
    primaryKeys = ["generation_id", "ordinal"],
    indices = [
        Index(value = ["generation_id", "id"]),
        Index(value = ["generation_id", "category_id", "sort_title"]),
        Index(value = ["generation_id", "added_at"]),
    ],
)
data class MovieEntity(
    @ColumnInfo(name = "generation_id") val generationId: Long,
    val ordinal: Int,
    val id: String,
    val title: String,
    @ColumnInfo(name = "sort_title") val sortTitle: String,
    @ColumnInfo(name = "category_id") val categoryId: String?,
    @ColumnInfo(name = "poster_url") val posterUrl: String?,
    @ColumnInfo(name = "container_extension") val containerExtension: String,
    @ColumnInfo(name = "added_at") val addedAtEpochSeconds: Long?,
    @ColumnInfo(name = "search_key") val searchKey: String,
    @ColumnInfo(name = "url_blob", typeAffinity = ColumnInfo.BLOB) val urlBlob: ByteArray,
)

@Entity(
    tableName = "series",
    primaryKeys = ["generation_id", "ordinal"],
    indices = [
        Index(value = ["generation_id", "id"]),
        Index(value = ["generation_id", "category_id", "sort_title"]),
        Index(value = ["generation_id", "added_at"]),
    ],
)
data class SeriesEntity(
    @ColumnInfo(name = "generation_id") val generationId: Long,
    val ordinal: Int,
    val id: String,
    val title: String,
    @ColumnInfo(name = "sort_title") val sortTitle: String,
    @ColumnInfo(name = "category_id") val categoryId: String?,
    @ColumnInfo(name = "poster_url") val posterUrl: String?,
    @ColumnInfo(name = "added_at") val addedAtEpochSeconds: Long?,
    @ColumnInfo(name = "search_key") val searchKey: String,
)

@Entity(
    tableName = "series_detail",
    primaryKeys = ["generation_id", "ordinal"],
    indices = [Index(value = ["generation_id", "series_id"])],
)
data class SeriesDetailEntity(
    @ColumnInfo(name = "generation_id") val generationId: Long,
    val ordinal: Int,
    @ColumnInfo(name = "series_id") val seriesId: String,
    val title: String?,
    val year: String?,
    val synopsis: String?,
    /** Genres joined with [GENRE_SEPARATOR]. */
    val genres: String,
)

internal const val GENRE_SEPARATOR = '\u001F'

@Entity(
    tableName = "episode",
    primaryKeys = ["generation_id", "series_id", "ordinal"],
    indices = [Index(value = ["generation_id", "series_id", "season", "number"])],
)
data class EpisodeEntity(
    @ColumnInfo(name = "generation_id") val generationId: Long,
    @ColumnInfo(name = "series_id") val seriesId: String,
    /** Order of the episode inside its series detail. */
    val ordinal: Int,
    val id: String,
    val season: Int,
    val number: Int,
    val title: String,
    val synopsis: String?,
    @ColumnInfo(name = "url_blob", typeAffinity = ColumnInfo.BLOB) val urlBlob: ByteArray,
)

/** M3U shared-id aliases (bloc #211), kept per generation. */
@Entity(
    tableName = "id_alias",
    primaryKeys = ["generation_id", "alias"],
)
data class IdAliasEntity(
    @ColumnInfo(name = "generation_id") val generationId: Long,
    val alias: String,
    val fingerprint: Long,
)
