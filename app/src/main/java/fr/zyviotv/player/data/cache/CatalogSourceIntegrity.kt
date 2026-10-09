package fr.zyviotv.player.data.cache

import fr.zyviotv.player.data.catalog.SeriesDetailSource
import fr.zyviotv.player.shared.catalog.CatalogSnapshot

/**
 * Counts how many catalog items still carry a playback source. Only counts
 * are exposed: the report is meant for `ZyvioCatalog` logs and for deciding
 * whether a restored catalog can be offered for playback (bloc #208).
 */
data class CatalogSourceReport(
    val liveTotal: Int,
    val liveWithSource: Int,
    val moviesTotal: Int,
    val moviesWithSource: Int,
    val episodesTotal: Int,
    val episodesWithSource: Int,
    /** Series listed in the catalog that have no episode index at all. */
    val seriesWithoutEpisodes: Int,
) {
    val missingSources: Int
        get() = (liveTotal - liveWithSource) +
            (moviesTotal - moviesWithSource) +
            (episodesTotal - episodesWithSource)

    /**
     * A catalog is playable from cache only when every channel, film and
     * indexed episode kept its source. Series without an episode index are
     * tolerated (Xtream loads them on demand) but reported.
     */
    val isPlayable: Boolean
        get() = missingSources == 0

    fun logFields(): String =
        "live=$liveWithSource/$liveTotal movies=$moviesWithSource/$moviesTotal " +
            "episodes=$episodesWithSource/$episodesTotal series_without_episodes=$seriesWithoutEpisodes"

    companion object {
        fun of(
            snapshot: CatalogSnapshot,
            seriesDetails: Map<String, SeriesDetailSource>,
        ): CatalogSourceReport {
            var episodesTotal = 0
            var episodesWithSource = 0
            seriesDetails.values.forEach { detail ->
                detail.episodes.forEach { episode ->
                    episodesTotal += 1
                    if (episode.streamUrl.isNotBlank()) episodesWithSource += 1
                }
            }
            return CatalogSourceReport(
                liveTotal = snapshot.liveChannels.size,
                liveWithSource = snapshot.liveChannels.count { it.streamUrl.isNotBlank() },
                moviesTotal = snapshot.movies.size,
                moviesWithSource = snapshot.movies.count { it.streamUrl.isNotBlank() },
                episodesTotal = episodesTotal,
                episodesWithSource = episodesWithSource,
                seriesWithoutEpisodes = snapshot.series.count { series ->
                    seriesDetails[series.id]?.episodes.isNullOrEmpty()
                },
            )
        }
    }
}
