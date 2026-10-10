package fr.zyviotv.player.data.store

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

/**
 * Blocking DAO: every call runs on the store's background dispatcher, never on
 * the main thread (Room enforces it outside tests).
 */
@Dao
interface CatalogStoreDao {
    @Insert
    fun insertGeneration(generation: GenerationEntity): Long

    @Insert
    fun insertCategories(rows: List<CategoryEntity>)

    @Insert
    fun insertLiveChannels(rows: List<LiveChannelEntity>)

    @Insert
    fun insertMovies(rows: List<MovieEntity>)

    @Insert
    fun insertSeries(rows: List<SeriesEntity>)

    @Insert
    fun insertSeriesDetails(rows: List<SeriesDetailEntity>)

    @Insert
    fun insertEpisodes(rows: List<EpisodeEntity>)

    @Insert
    fun insertIdAliases(rows: List<IdAliasEntity>)

    @Query("SELECT * FROM generation WHERE id = :generationId")
    fun generation(generationId: Long): GenerationEntity?

    /** One active generation per profile and playlist (multi-playlist ready). */
    @Query(
        "SELECT * FROM generation WHERE profile_key = :profileKey AND playlist_id = :playlistId " +
            "AND state = 'active' LIMIT 1",
    )
    fun activeGeneration(profileKey: String, playlistId: String): GenerationEntity?

    @Query("SELECT * FROM generation WHERE profile_key = :profileKey AND state = 'active' ORDER BY id")
    fun activeGenerations(profileKey: String): List<GenerationEntity>

    @Query("SELECT id FROM generation WHERE state != 'active'")
    fun inactiveGenerationIds(): List<Long>

    @Query(
        "UPDATE generation SET state = 'retired' WHERE profile_key = :profileKey " +
            "AND playlist_id = :playlistId AND state = 'active'",
    )
    fun retireActive(profileKey: String, playlistId: String): Int

    @Query("UPDATE generation SET state = 'active' WHERE id = :generationId AND state = 'building'")
    fun markActive(generationId: Long): Int

    @Query("SELECT COUNT(*) FROM category WHERE generation_id = :generationId")
    fun countCategories(generationId: Long): Int

    @Query("SELECT COUNT(*) FROM live_channel WHERE generation_id = :generationId")
    fun countLiveChannels(generationId: Long): Int

    @Query("SELECT COUNT(*) FROM movie WHERE generation_id = :generationId")
    fun countMovies(generationId: Long): Int

    @Query("SELECT COUNT(*) FROM series WHERE generation_id = :generationId")
    fun countSeries(generationId: Long): Int

    @Query("SELECT COUNT(*) FROM series_detail WHERE generation_id = :generationId")
    fun countSeriesDetails(generationId: Long): Int

    @Query("SELECT COUNT(*) FROM episode WHERE generation_id = :generationId")
    fun countEpisodes(generationId: Long): Int

    @Query("SELECT COUNT(*) FROM id_alias WHERE generation_id = :generationId")
    fun countIdAliases(generationId: Long): Int

    @Query("SELECT * FROM live_channel WHERE generation_id = :generationId AND ordinal = :ordinal")
    fun liveChannelAt(generationId: Long, ordinal: Int): LiveChannelEntity?

    @Query("SELECT * FROM movie WHERE generation_id = :generationId AND id = :id ORDER BY ordinal LIMIT 1")
    fun movieById(generationId: Long, id: String): MovieEntity?

    @Query("SELECT * FROM series WHERE generation_id = :generationId AND id = :id ORDER BY ordinal LIMIT 1")
    fun seriesById(generationId: Long, id: String): SeriesEntity?

    @Query(
        "SELECT * FROM episode WHERE generation_id = :generationId AND series_id = :seriesId " +
            "ORDER BY ordinal",
    )
    fun episodesOf(generationId: Long, seriesId: String): List<EpisodeEntity>

    @Query("SELECT * FROM id_alias WHERE generation_id = :generationId")
    fun idAliases(generationId: Long): List<IdAliasEntity>

    // --- Reads for the screens (PR B): no encrypted column is loaded here. ---

    @Query(
        "SELECT * FROM generation WHERE playlist_id = :playlistId AND state = 'active' " +
            "ORDER BY id DESC LIMIT 1",
    )
    fun activeGenerationOfPlaylist(playlistId: String): GenerationEntity?

    @Query("SELECT id, name FROM category WHERE generation_id = :generationId AND kind = :kind ORDER BY ordinal")
    fun categoryRows(generationId: Long, kind: String): List<CategoryRow>

    @Query(
        "SELECT id, name, category_id, logo_url, epg_id FROM live_channel " +
            "WHERE generation_id = :generationId ORDER BY ordinal",
    )
    fun liveRows(generationId: Long): List<LiveRow>

    @Query(
        "SELECT id, title, category_id, poster_url, container_extension, added_at FROM movie " +
            "WHERE generation_id = :generationId ORDER BY ordinal",
    )
    fun movieRows(generationId: Long): List<MovieRow>

    @Query(
        "SELECT id, title, category_id, poster_url, added_at FROM series " +
            "WHERE generation_id = :generationId ORDER BY ordinal",
    )
    fun seriesRows(generationId: Long): List<SeriesRow>

    @Query("SELECT * FROM series_detail WHERE generation_id = :generationId AND series_id = :seriesId LIMIT 1")
    fun seriesDetail(generationId: Long, seriesId: String): SeriesDetailEntity?

    @Query(
        "SELECT id, season, number, title, synopsis FROM episode " +
            "WHERE generation_id = :generationId AND series_id = :seriesId ORDER BY ordinal",
    )
    fun episodeRows(generationId: Long, seriesId: String): List<EpisodeRow>

    @Query("SELECT url_blob FROM live_channel WHERE generation_id = :generationId AND id = :id ORDER BY ordinal LIMIT 1")
    fun liveUrlBlob(generationId: Long, id: String): ByteArray?

    @Query("SELECT url_blob FROM movie WHERE generation_id = :generationId AND id = :id ORDER BY ordinal LIMIT 1")
    fun movieUrlBlob(generationId: Long, id: String): ByteArray?

    @Query(
        "SELECT url_blob FROM episode WHERE generation_id = :generationId AND series_id = :seriesId " +
            "AND id = :id ORDER BY ordinal LIMIT 1",
    )
    fun episodeUrlBlob(generationId: Long, seriesId: String, id: String): ByteArray?

    @Query("DELETE FROM category WHERE generation_id = :generationId")
    fun deleteCategories(generationId: Long)

    @Query("DELETE FROM live_channel WHERE generation_id = :generationId")
    fun deleteLiveChannels(generationId: Long)

    @Query("DELETE FROM movie WHERE generation_id = :generationId")
    fun deleteMovies(generationId: Long)

    @Query("DELETE FROM series WHERE generation_id = :generationId")
    fun deleteSeries(generationId: Long)

    @Query("DELETE FROM series_detail WHERE generation_id = :generationId")
    fun deleteSeriesDetails(generationId: Long)

    @Query("DELETE FROM episode WHERE generation_id = :generationId")
    fun deleteEpisodes(generationId: Long)

    @Query("DELETE FROM id_alias WHERE generation_id = :generationId")
    fun deleteIdAliases(generationId: Long)

    @Query("DELETE FROM generation WHERE id = :generationId")
    fun deleteGenerationRow(generationId: Long)
}
