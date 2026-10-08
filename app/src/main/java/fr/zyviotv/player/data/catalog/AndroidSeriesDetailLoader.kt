package fr.zyviotv.player.data.catalog

import fr.zyviotv.player.shared.sync.PlaylistSecret
import fr.zyviotv.player.shared.xtream.XtreamCredentials

object AndroidSeriesDetailLoader {
    suspend fun load(
        secret: PlaylistSecret,
        seriesId: String,
    ): SeriesDetailLoadResult {
        return when (secret) {
            is PlaylistSecret.Xtream -> AndroidXtreamSeriesDetailLoader(
                credentials = XtreamCredentials(
                    serverUrl = secret.serverUrl,
                    username = secret.username,
                    password = secret.password,
                ),
            ).load(seriesId)

            is PlaylistSecret.M3u -> {
                val detail = M3uSeriesDetailRegistry.load(seriesId)
                if (detail != null) {
                    SeriesDetailLoadResult.Success(detail)
                } else {
                    SeriesDetailLoadResult.Failure(
                        "Impossible de charger les saisons et épisodes de cette série M3U.",
                    )
                }
            }
        }
    }
}
