package fr.zyviotv.player.data.epg

import android.util.Base64
import fr.zyviotv.player.data.xtream.AndroidXtreamHttpClient
import fr.zyviotv.player.shared.epg.EpgLoadResult
import fr.zyviotv.player.shared.epg.EpgProgramme
import fr.zyviotv.player.shared.epg.EpgRepository
import fr.zyviotv.player.shared.epg.EpgWindow
import fr.zyviotv.player.shared.xtream.XtreamCredentials
import fr.zyviotv.player.shared.xtream.XtreamEndpointBuilder
import java.net.SocketTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class AndroidXtreamEpgRepository(
    private val credentials: XtreamCredentials,
) : EpgRepository {
    private val httpClient = AndroidXtreamHttpClient()

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

            val response = httpClient.get(endpoint, operation = "xtream-epg")
            if (response.code !in 200..299) {
                return@withContext EpgLoadResult.Failure(
                    "Impossible de charger le guide TV pour cette chaîne (HTTP ${response.code}).",
                )
            }

                val root = JSONObject(response.body)
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
        } catch (error: CancellationException) {
            throw error
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
        const val MAX_PROGRAMMES = 100
    }
}
