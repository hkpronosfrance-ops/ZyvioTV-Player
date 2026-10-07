package fr.zyviotv.player.data.epg

import android.util.Base64
import fr.zyviotv.player.shared.epg.EpgLoadResult
import fr.zyviotv.player.shared.epg.EpgProgramme
import fr.zyviotv.player.shared.epg.EpgRepository
import fr.zyviotv.player.shared.epg.EpgWindow
import fr.zyviotv.player.shared.xtream.XtreamCredentials
import fr.zyviotv.player.shared.xtream.XtreamEndpointBuilder
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class AndroidXtreamEpgRepository(
    private val credentials: XtreamCredentials,
) : EpgRepository {
    override suspend fun load(
        channelId: String,
        window: EpgWindow,
    ): EpgLoadResult = withContext(Dispatchers.IO) {
        try {
            val endpoint = XtreamEndpointBuilder.authenticatedPlayerApi(
                credentials = credentials,
                action = "get_short_epg",
                extraParams = mapOf(
                    "stream_id" to channelId,
                    "limit" to MAX_PROGRAMMES.toString(),
                ),
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
                    return@withContext EpgLoadResult.Failure(
                        "Impossible de charger le guide TV pour cette chaîne.",
                    )
                }

                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(body)
                val array = root.optJSONArray("epg_listings")
                    ?: return@withContext EpgLoadResult.Success(emptyList())

                val programmes = buildList(array.length()) {
                    for (index in 0 until array.length()) {
                        val item = array.optJSONObject(index) ?: continue
                        val start = EpgTimeParsing.epochSeconds(
                            item.optLong("start_timestamp", Long.MIN_VALUE),
                        )
                        val end = EpgTimeParsing.epochSeconds(
                            item.optLong("stop_timestamp", Long.MIN_VALUE),
                        )
                        if (start == null || end == null || end <= start) continue

                        val programme = EpgProgramme(
                            channelId = channelId,
                            title = decodeMaybeBase64(item.optString("title"))
                                .ifBlank { "Programme TV" },
                            description = decodeMaybeBase64(item.optString("description"))
                                .takeIf(String::isNotBlank),
                            startEpochSeconds = start,
                            endEpochSeconds = end,
                        )

                        if (window.contains(programme)) add(programme)
                    }
                }
                    .distinctBy {
                        Triple(it.startEpochSeconds, it.endEpochSeconds, it.title)
                    }
                    .sortedBy { it.startEpochSeconds }

                EpgLoadResult.Success(programmes)
            } finally {
                connection.disconnect()
            }
        } catch (_: SocketTimeoutException) {
            EpgLoadResult.Failure("Le guide TV met trop de temps à répondre.")
        } catch (_: Exception) {
            EpgLoadResult.Failure("Impossible de charger le guide TV.")
        }
    }

    private fun decodeMaybeBase64(value: String): String {
        if (value.isBlank()) return ""
        return try {
            String(Base64.decode(value, Base64.DEFAULT), Charsets.UTF_8)
                .takeIf { decoded -> decoded.all { it == '\n' || it == '\r' || it == '\t' || it.code >= 32 } }
                ?: value
        } catch (_: Exception) {
            value
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 20_000
        const val MAX_PROGRAMMES = 100
    }
}
