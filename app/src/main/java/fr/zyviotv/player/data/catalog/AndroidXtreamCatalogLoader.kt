package fr.zyviotv.player.data.catalog

import fr.zyviotv.player.shared.catalog.CatalogCategory
import fr.zyviotv.player.shared.catalog.CatalogLiveChannel
import fr.zyviotv.player.shared.catalog.CatalogLoadResult
import fr.zyviotv.player.shared.catalog.CatalogMovie
import fr.zyviotv.player.shared.catalog.CatalogSeries
import fr.zyviotv.player.shared.catalog.CatalogSnapshot
import fr.zyviotv.player.shared.xtream.XtreamCredentials
import fr.zyviotv.player.shared.xtream.XtreamEndpointBuilder
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class AndroidXtreamCatalogLoader(
    private val credentials: XtreamCredentials,
) {
    suspend fun load(): CatalogLoadResult = withContext(Dispatchers.IO) {
        try {
            val liveCategories = getArray("get_live_categories").mapCategories()
            val liveChannels = getArray("get_live_streams").mapLiveChannels()
            val movieCategories = getArray("get_vod_categories").mapCategories()
            val movies = getArray("get_vod_streams").mapMovies()
            val seriesCategories = getArray("get_series_categories").mapCategories()
            val series = getArray("get_series").mapSeries()

            CatalogLoadResult.Success(
                CatalogSnapshot(
                    liveCategories = liveCategories,
                    liveChannels = liveChannels,
                    movieCategories = movieCategories,
                    movies = movies,
                    seriesCategories = seriesCategories,
                    series = series,
                ),
            )
        } catch (_: SocketTimeoutException) {
            CatalogLoadResult.Failure("Le catalogue IPTV met trop de temps à répondre.")
        } catch (_: Exception) {
            CatalogLoadResult.Failure(
                "Impossible de charger le catalogue IPTV. Vérifiez votre connexion et votre fournisseur.",
            )
        }
    }

    private fun getArray(action: String): JSONArray {
        val endpoint = XtreamEndpointBuilder.authenticatedPlayerApi(
            credentials = credentials,
            action = action,
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
                throw IllegalStateException("Provider HTTP error")
            }

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            return JSONArray(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun JSONArray.mapCategories(): List<CatalogCategory> =
        buildList(length()) {
            for (index in 0 until length()) {
                val item = optJSONObject(index) ?: continue
                val id = item.optString("category_id").takeIf { it.isNotBlank() } ?: continue
                val name = item.optString("category_name").takeIf { it.isNotBlank() } ?: continue
                add(CatalogCategory(id = id, name = name))
            }
        }

    private fun JSONArray.mapLiveChannels(): List<CatalogLiveChannel> =
        buildList(length()) {
            for (index in 0 until length()) {
                val item = optJSONObject(index) ?: continue
                val id = item.stringId("stream_id") ?: continue
                val name = item.optString("name").takeIf { it.isNotBlank() } ?: continue
                add(
                    CatalogLiveChannel(
                        id = id,
                        name = name,
                        categoryId = item.optString("category_id").takeIf { it.isNotBlank() },
                        logoUrl = item.optString("stream_icon").takeIf { it.isNotBlank() },
                        streamUrl = XtreamEndpointBuilder.liveStream(credentials, id),
                    ),
                )
            }
        }

    private fun JSONArray.mapMovies(): List<CatalogMovie> =
        buildList(length()) {
            for (index in 0 until length()) {
                val item = optJSONObject(index) ?: continue
                val id = item.stringId("stream_id") ?: continue
                val title = item.optString("name").takeIf { it.isNotBlank() } ?: continue
                val extension = item.optString("container_extension")
                    .takeIf { it.isNotBlank() }
                    ?: "mp4"
                add(
                    CatalogMovie(
                        id = id,
                        title = title,
                        categoryId = item.optString("category_id").takeIf { it.isNotBlank() },
                        posterUrl = item.optString("stream_icon").takeIf { it.isNotBlank() },
                        streamUrl = XtreamEndpointBuilder.movieStream(credentials, id, extension),
                        containerExtension = extension,
                        addedAtEpochSeconds = item.epochSeconds("added"),
                    ),
                )
            }
        }

    private fun JSONArray.mapSeries(): List<CatalogSeries> =
        buildList(length()) {
            for (index in 0 until length()) {
                val item = optJSONObject(index) ?: continue
                val id = item.stringId("series_id") ?: continue
                val title = item.optString("name").takeIf { it.isNotBlank() } ?: continue
                add(
                    CatalogSeries(
                        id = id,
                        title = title,
                        categoryId = item.optString("category_id").takeIf { it.isNotBlank() },
                        posterUrl = item.optString("cover").takeIf { it.isNotBlank() },
                        addedAtEpochSeconds = item.epochSeconds("added"),
                    ),
                )
            }
        }

    private fun JSONObject.stringId(key: String): String? {
        val value = opt(key) ?: return null
        return value.toString().takeIf { it.isNotBlank() && it != "null" }
    }

    private fun JSONObject.epochSeconds(key: String): Long? {
        val raw = opt(key)?.toString()?.trim().orEmpty()
        val value = raw.toLongOrNull()?.takeIf { it > 0L } ?: return null
        return if (value > 9_999_999_999L) value / 1_000L else value
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 30_000
    }
}
