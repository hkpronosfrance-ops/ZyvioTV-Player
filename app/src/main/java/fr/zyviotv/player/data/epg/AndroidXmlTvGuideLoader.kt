package fr.zyviotv.player.data.epg

import fr.zyviotv.player.shared.catalog.CatalogLiveChannel
import fr.zyviotv.player.shared.epg.EpgWindow
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidXmlTvGuideLoader(
    private val xmlTvUrl: String,
) {
    suspend fun load(
        channels: List<CatalogLiveChannel>,
        window: EpgWindow,
        maxChannels: Int = MAX_CHANNELS,
    ): GuideLoadResult = withContext(Dispatchers.IO) {
        val bounded = channels.take(maxChannels.coerceIn(1, MAX_CHANNELS))
        if (bounded.isEmpty()) {
            return@withContext GuideLoadResult.Success(
                channels = emptyList(),
                failedChannels = 0,
            )
        }

        val epgIds = bounded
            .mapNotNull { it.epgId?.trim()?.takeIf(String::isNotBlank) }
            .toSet()

        if (epgIds.isEmpty()) {
            return@withContext GuideLoadResult.Failure(
                "Aucune chaîne M3U ne contient de tvg-id utilisable pour le guide XMLTV.",
            )
        }

        try {
            val connection = open(xmlTvUrl)
            try {
                val code = connection.responseCode
                if (code !in 200..299) {
                    return@withContext GuideLoadResult.Failure(
                        "Le serveur XMLTV a répondu avec le code $code.",
                    )
                }

                val parsed = xmlInputStream(connection).use { input ->
                    XmlTvParser.parseChannels(
                        input = input,
                        channelIds = epgIds,
                        window = window,
                    )
                }

                val loaded = bounded.map { channel ->
                    GuideChannelData(
                        channelId = channel.id,
                        programmes = channel.epgId
                            ?.trim()
                            ?.let(parsed::get)
                            .orEmpty(),
                    )
                }
                val failed = loaded.count { it.programmes.isEmpty() }

                if (failed == loaded.size) {
                    GuideLoadResult.Failure(
                        "Aucun programme XMLTV ne correspond aux tvg-id de cette playlist.",
                    )
                } else {
                    GuideLoadResult.Success(
                        channels = loaded,
                        failedChannels = failed,
                    )
                }
            } finally {
                connection.disconnect()
            }
        } catch (_: SocketTimeoutException) {
            GuideLoadResult.Failure("Le serveur XMLTV met trop de temps à répondre.")
        } catch (_: Exception) {
            GuideLoadResult.Failure(
                "Impossible de charger le guide XMLTV de cette playlist.",
            )
        }
    }

    private fun open(value: String): HttpURLConnection =
        (URL(value.trim()).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doInput = true
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/xml, text/xml, application/gzip, */*")
            setRequestProperty("Accept-Encoding", "gzip")
            setRequestProperty("User-Agent", "ZYVIOTV-Player/0.1")
        }

    private fun xmlInputStream(connection: HttpURLConnection): InputStream {
        val input = connection.inputStream
        val encodedGzip = connection.contentEncoding?.contains("gzip", ignoreCase = true) == true
        val urlGzip = connection.url.path.endsWith(".gz", ignoreCase = true)
        return if (encodedGzip || urlGzip) GZIPInputStream(input) else input
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 30_000
        const val MAX_CHANNELS = 50
    }
}
