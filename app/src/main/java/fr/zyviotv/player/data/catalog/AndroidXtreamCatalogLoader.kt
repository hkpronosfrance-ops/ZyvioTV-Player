package fr.zyviotv.player.data.catalog

import fr.zyviotv.player.data.xtream.AndroidXtreamHttpClient
import fr.zyviotv.player.shared.catalog.CatalogCategory
import fr.zyviotv.player.shared.catalog.CatalogLiveChannel
import fr.zyviotv.player.shared.catalog.CatalogLoadResult
import fr.zyviotv.player.shared.catalog.CatalogMovie
import fr.zyviotv.player.shared.catalog.CatalogSeries
import fr.zyviotv.player.shared.catalog.CatalogSnapshot
import fr.zyviotv.player.shared.xtream.XtreamCredentials
import fr.zyviotv.player.shared.xtream.XtreamEndpointBuilder
import java.net.SocketTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class AndroidXtreamCatalogLoader(
    private val credentials: XtreamCredentials,
) {
    private val httpClient = AndroidXtreamHttpClient()

    /** A list rejected by [XtreamCatalogValidation]: the refresh fails as a whole. */
    private class RejectedList(val list: XtreamCatalogList, val outcome: XtreamListOutcome) : Exception()

    /**
     * @param previous the validated catalogue of the same playlist, if any:
     *   an empty list is suspicious only when the same list was not empty.
     */
    suspend fun load(previous: CatalogSnapshot? = null): CatalogLoadResult = withContext(Dispatchers.IO) {
        try {
            val liveCategories = getList(XtreamCatalogList.LiveCategories, previous?.liveCategories?.size) {
                it.mapCategories()
            }
            val liveChannels = getList(XtreamCatalogList.LiveStreams, previous?.liveChannels?.size) {
                it.mapLiveChannels()
            }
            val movieCategories = getList(XtreamCatalogList.MovieCategories, previous?.movieCategories?.size) {
                it.mapCategories()
            }
            val movies = getList(XtreamCatalogList.Movies, previous?.movies?.size) { it.mapMovies() }
            val seriesCategories = getList(XtreamCatalogList.SeriesCategories, previous?.seriesCategories?.size) {
                it.mapCategories()
            }
            val series = getList(XtreamCatalogList.Series, previous?.series?.size) { it.mapSeries() }

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
        } catch (error: CancellationException) {
            throw error
        } catch (rejected: RejectedList) {
            CatalogLoadResult.Failure(XtreamCatalogValidation.message(rejected.list, rejected.outcome))
        } catch (_: SocketTimeoutException) {
            CatalogLoadResult.Failure("Le catalogue IPTV met trop de temps à répondre.")
        } catch (_: Exception) {
            CatalogLoadResult.Failure(
                "Impossible de charger le catalogue IPTV. Vérifiez votre connexion et votre fournisseur.",
            )
        }
    }

    private fun <T> getList(
        list: XtreamCatalogList,
        previousCount: Int?,
        map: (JSONArray) -> List<T>,
    ): List<T> {
        val endpoint = XtreamEndpointBuilder.authenticatedPlayerApi(
            credentials = credentials,
            action = list.action,
        )
        val response = try {
            httpClient.get(endpoint, operation = "xtream-catalog-" + list.logName)
        } catch (error: Exception) {
            CatalogPerformanceDiagnostics.event(
                name = "xtream_list",
                fields = "list=${list.logName} outcome=network_error failure=" + error.javaClass.simpleName,
                warning = true,
            )
            throw error
        }
        if (response.code !in 200..299) {
            CatalogPerformanceDiagnostics.event(
                name = "xtream_list",
                fields = "list=${list.logName} outcome=http_error http=${response.code}",
                warning = true,
            )
            throw IllegalStateException("Provider HTTP error")
        }
        val bodyKind = XtreamBodyKind.of(response.body)
        val array = when (bodyKind) {
            XtreamBodyKind.JsonArray -> runCatching { JSONArray(response.body) }.getOrNull()
            else -> null
        }
        val items = array?.let(map).orEmpty()
        val outcome = if (bodyKind == XtreamBodyKind.JsonArray && array == null) {
            XtreamListOutcome.InvalidBody
        } else {
            XtreamCatalogValidation.check(
                bodyKind = bodyKind,
                rawCount = array?.length() ?: 0,
                usableCount = items.size,
                previousCount = previousCount,
            )
        }
        CatalogPerformanceDiagnostics.event(
            name = "xtream_list",
            fields = "list=${list.logName} outcome=${outcome.logName} body=${bodyKind.logName} " +
                "items=${items.size} raw=${array?.length() ?: 0}",
            warning = !outcome.accepted,
        )
        if (!outcome.accepted) throw RejectedList(list, outcome)
        return items
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

}
