package fr.zyviotv.player.ui.player

import fr.zyviotv.player.data.catalog.AndroidXtreamSeriesDetailLoader
import fr.zyviotv.player.data.catalog.SeriesDetailLoadResult
import fr.zyviotv.player.data.catalog.SeriesEpisodeSource
import fr.zyviotv.player.data.sync.SupabaseCloudSyncRepository
import fr.zyviotv.player.shared.sync.PlaylistSecret
import fr.zyviotv.player.shared.xtream.XtreamCredentials

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
            as? PlaylistSecret.Xtream
            ?: return null

        val loader = AndroidXtreamSeriesDetailLoader(
            XtreamCredentials(
                serverUrl = secret.serverUrl,
                username = secret.username,
                password = secret.password,
            ),
        )
        val detail = (loader.load(seriesId) as? SeriesDetailLoadResult.Success)?.detail
            ?: return null
        val episodes = detail.episodes
        if (episodes.isEmpty()) return null

        val currentIndex = episodes.indexOfFirst { episode ->
            episode.id == currentContentId ||
                (
                    currentSeason == episode.season &&
                        currentEpisode == episode.number
                    )
        }
        if (currentIndex < 0 || currentIndex + 1 >= episodes.size) return null
        return episodes[currentIndex + 1]
    }
}
