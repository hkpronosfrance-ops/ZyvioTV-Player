package fr.zyviotv.player.data.catalog

import fr.zyviotv.player.shared.sync.PlaylistSecret
import fr.zyviotv.player.shared.xtream.XtreamCredentials

object AndroidSeriesDetailLoader {
    /**
     * M3U episodes already live in the local registry (restored from the
     * encrypted catalog cache), so they must stay playable without a
     * Supabase round-trip; only Xtream needs the playlist secret (bloc #207).
     */
    suspend fun loadPreferLocal(
        seriesId: String,
        loadSecret: suspend () -> Result<PlaylistSecret?>,
    ): SeriesDetailLoadResult {
        M3uSeriesDetailRegistry.load(seriesId)?.let { return SeriesDetailLoadResult.Success(it) }
        val secret = loadSecret().getOrElse {
            return SeriesDetailLoadResult.Failure(SECRET_UNAVAILABLE_MESSAGE)
        } ?: return SeriesDetailLoadResult.Failure(SECRET_MISSING_MESSAGE)
        return load(secret, seriesId)
    }

    const val SECRET_UNAVAILABLE_MESSAGE = "Impossible de restaurer la configuration de la playlist."
    const val SECRET_MISSING_MESSAGE = "Configuration de playlist absente."

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
