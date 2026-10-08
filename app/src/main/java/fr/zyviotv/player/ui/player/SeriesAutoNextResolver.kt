package fr.zyviotv.player.ui.player

import fr.zyviotv.player.data.catalog.AndroidSeriesDetailLoader
import fr.zyviotv.player.data.catalog.SeriesDetailLoadResult
import fr.zyviotv.player.data.catalog.SeriesEpisodeSource
import fr.zyviotv.player.data.sync.SupabaseCloudSyncRepository
import fr.zyviotv.player.shared.sync.PlaylistSecret

class SeriesAutoNextResolver(
    private val cloudRepository: SupabaseCloudSyncRepository,
) {
    suspend fun resolveNext(
        playlistId: String,
        seriesId: String,
        currentContentId: String,
        currentSeason: Int?,
        currentEpisode: Int?,
    ): SeriesEpisodeSource? {
        val secret = cloudRepository.getPlaylistSecret(playlistId).getOrNull()
            ?: return null

        val detail = (AndroidSeriesDetailLoader.load(secret, seriesId) as? SeriesDetailLoadResult.Success)?.detail
            ?: return null
        // Provider responses may return episodes in arbitrary order; auto-next must
        // follow season/episode numbering, never the raw API response order.
        val episodes = detail.episodes.sortedWith(
            compareBy<SeriesEpisodeSource> { it.season }.thenBy { it.number }
        )
        if (episodes.isEmpty()) return null

        val currentIndex = episodes.indexOfFirst { episode ->
            episode.id == currentContentId ||
                (
                    currentSeason != null &&
                        currentEpisode != null &&
                        currentSeason == episode.season &&
                        currentEpisode == episode.number
                    )
        }
        if (currentIndex < 0 || currentIndex + 1 >= episodes.size) return null
        return episodes[currentIndex + 1]
    }
}
