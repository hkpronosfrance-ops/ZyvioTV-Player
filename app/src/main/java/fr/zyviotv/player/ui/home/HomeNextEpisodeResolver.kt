package fr.zyviotv.player.ui.home

import fr.zyviotv.player.data.catalog.AndroidXtreamSeriesDetailLoader
import fr.zyviotv.player.data.catalog.SeriesDetailLoadResult
import fr.zyviotv.player.data.catalog.SeriesEpisodeSource
import fr.zyviotv.player.data.sync.SupabaseCloudSyncRepository
import fr.zyviotv.player.shared.catalog.CatalogSeries
import fr.zyviotv.player.shared.sync.PlaylistSecret
import fr.zyviotv.player.shared.sync.ProgressContentType
import fr.zyviotv.player.shared.sync.SyncedWatchProgress
import fr.zyviotv.player.shared.xtream.XtreamCredentials

data class HomeNextEpisode(
    val playlistId: String,
    val seriesId: String,
    val seriesTitle: String,
    val artworkUrl: String?,
    val episode: SeriesEpisodeSource,
)

class HomeNextEpisodeResolver(
    private val cloudRepository: SupabaseCloudSyncRepository,
) {
    suspend fun resolve(
        playlistId: String,
        visibleSeries: List<CatalogSeries>,
        progress: List<SyncedWatchProgress>,
    ): List<HomeNextEpisode> {
        val visibleById = visibleSeries.associateBy { it.id }
        val episodeProgress = progress.filter {
            it.playlistId == playlistId &&
                it.contentType == ProgressContentType.Episode &&
                !it.seriesId.isNullOrBlank() &&
                it.seriesId in visibleById
        }
        if (episodeProgress.isEmpty()) return emptyList()

        val secret = cloudRepository.getPlaylistSecret(playlistId).getOrNull()
            as? PlaylistSecret.Xtream
            ?: return emptyList()

        val loader = AndroidXtreamSeriesDetailLoader(
            XtreamCredentials(
                serverUrl = secret.serverUrl,
                username = secret.username,
                password = secret.password,
            ),
        )

        val candidateSeriesIds = episodeProgress
            .mapNotNull { it.seriesId }
            .distinct()
            .take(MAX_SERIES_LOOKUPS)

        return buildList {
            for (seriesId in candidateSeriesIds) {
                val latestForSeries = episodeProgress.firstOrNull { it.seriesId == seriesId }
                    ?: continue

                // Do not suggest skipping an episode that is still in progress.
                if (!latestForSeries.completed) continue

                val result = loader.load(seriesId)
                val detail = (result as? SeriesDetailLoadResult.Success)?.detail ?: continue
                val episodes = detail.episodes
                if (episodes.isEmpty()) continue

                val currentIndex = episodes.indexOfFirst { episode ->
                    episode.id == latestForSeries.contentId ||
                        (
                            latestForSeries.seasonNumber == episode.season &&
                                latestForSeries.episodeNumber == episode.number
                            )
                }
                if (currentIndex < 0 || currentIndex + 1 >= episodes.size) continue

                val next = episodes[currentIndex + 1]
                val series = visibleById[seriesId] ?: continue
                add(
                    HomeNextEpisode(
                        playlistId = playlistId,
                        seriesId = seriesId,
                        seriesTitle = series.title,
                        artworkUrl = series.posterUrl,
                        episode = next,
                    ),
                )
                if (size >= MAX_HOME_NEXT_EPISODES) break
            }
        }
    }

    private companion object {
        const val MAX_SERIES_LOOKUPS = 8
        const val MAX_HOME_NEXT_EPISODES = 20
    }
}
