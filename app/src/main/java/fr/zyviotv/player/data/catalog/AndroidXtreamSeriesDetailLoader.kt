package fr.zyviotv.player.data.catalog

import fr.zyviotv.player.shared.xtream.XtreamCredentials
import fr.zyviotv.player.shared.xtream.XtreamEndpointBuilder
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class SeriesEpisodeSource(
    val id: String,
    val season: Int,
    val number: Int,
    val title: String,
    val synopsis: String?,
    val streamUrl: String,
)

data class SeriesDetailSource(
    val title: String?,
    val year: String?,
    val synopsis: String?,
    val genres: List<String>,
    val episodes: List<SeriesEpisodeSource>,
)

sealed interface SeriesDetailLoadResult {
    data class Success(val detail: SeriesDetailSource) : SeriesDetailLoadResult
    data class Failure(val message: String) : SeriesDetailLoadResult
}

class AndroidXtreamSeriesDetailLoader(
    private val credentials: XtreamCredentials,
) {
    suspend fun load(seriesId: String): SeriesDetailLoadResult = withContext(Dispatchers.IO) {
        try {
            val endpoint = XtreamEndpointBuilder.authenticatedPlayerApi(
                credentials = credentials,
                action = "get_series_info",
                extraParams = mapOf("series_id" to seriesId),
            )
            val connection = URL(endpoint).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = CONNECT_TIMEOUT_MS
                connection.readTimeout = READ_TIMEOUT_MS
                connection.doInput = true
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("User-Agent", "ZYVIOTV-Player/0.1")

                val code = connection.responseCode
                if (code !in 200..299) {
                    return@withContext SeriesDetailLoadResult.Failure(
                        "Le fournisseur n’a pas pu charger les épisodes.",
                    )
                }

                val root = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                val info = root.optJSONObject("info")
                val episodesObject = root.optJSONObject("episodes") ?: JSONObject()

                val episodes = buildList {
                    val seasonKeys = episodesObject.keys()
                    while (seasonKeys.hasNext()) {
                        val seasonKey = seasonKeys.next()
                        val season = seasonKey.toIntOrNull() ?: continue
                        val array = episodesObject.optJSONArray(seasonKey) ?: JSONArray()

                        for (index in 0 until array.length()) {
                            val item = array.optJSONObject(index) ?: continue
                            val episodeId = item.stringId("id") ?: item.stringId("stream_id") ?: continue
                            val episodeNumber = item.optInt("episode_num", index + 1)
                            val title = item.optString("title")
                                .takeIf { it.isNotBlank() }
                                ?: "Épisode $episodeNumber"
                            val extension = item.optString("container_extension")
                                .takeIf { it.isNotBlank() }
                                ?: "mp4"
                            val episodeInfo = item.optJSONObject("info")
                            add(
                                SeriesEpisodeSource(
                                    id = episodeId,
                                    season = season,
                                    number = episodeNumber,
                                    title = title,
                                    synopsis = episodeInfo
                                        ?.optString("plot")
                                        ?.takeIf { it.isNotBlank() },
                                    streamUrl = XtreamEndpointBuilder.seriesStream(
                                        credentials = credentials,
                                        streamId = episodeId,
                                        extension = extension,
                                    ),
                                ),
                            )
                        }
                    }
                }.sortedWith(compareBy<SeriesEpisodeSource> { it.season }.thenBy { it.number })

                SeriesDetailLoadResult.Success(
                    SeriesDetailSource(
                        title = info?.optString("name")?.takeIf { it.isNotBlank() },
                        year = info?.optString("releaseDate")
                            ?.takeIf { it.length >= 4 }
                            ?.take(4),
                        synopsis = info?.optString("plot")?.takeIf { it.isNotBlank() },
                        genres = info
                            ?.optString("genre")
                            ?.split(',')
                            ?.map { it.trim() }
                            ?.filter { it.isNotBlank() }
                            .orEmpty(),
                        episodes = episodes,
                    ),
                )
            } finally {
                connection.disconnect()
            }
        } catch (_: SocketTimeoutException) {
            SeriesDetailLoadResult.Failure("Le fournisseur met trop de temps à répondre.")
        } catch (_: Exception) {
            SeriesDetailLoadResult.Failure(
                "Impossible de charger les saisons et épisodes de cette série.",
            )
        }
    }

    private fun JSONObject.stringId(key: String): String? {
        val value = opt(key) ?: return null
        return value.toString().takeIf { it.isNotBlank() && it != "null" }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 30_000
    }
}
