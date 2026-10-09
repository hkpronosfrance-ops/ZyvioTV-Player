package fr.zyviotv.player.data.m3u

import fr.zyviotv.player.data.network.NetworkDiagnostics
import fr.zyviotv.player.shared.m3u.M3uClient
import fr.zyviotv.player.shared.m3u.M3uEntry
import fr.zyviotv.player.shared.m3u.M3uImportResult
import fr.zyviotv.player.shared.m3u.M3uParser
import fr.zyviotv.player.shared.m3u.M3uSource
import fr.zyviotv.player.shared.m3u.M3uValidationResult
import fr.zyviotv.player.shared.m3u.M3uValidator
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidM3uClient : M3uClient {
    override suspend fun import(
        source: M3uSource,
        maxEntries: Int,
    ): M3uImportResult = withContext(Dispatchers.IO) {
        when (val validation = M3uValidator.validate(source)) {
            is M3uValidationResult.Invalid -> {
                return@withContext M3uImportResult.Failure(validation.message)
            }
            M3uValidationResult.Valid -> Unit
        }

        if (maxEntries <= 0) {
            return@withContext M3uImportResult.Success(emptyList(), totalParsed = 0)
        }

        try {
            val connection = open(source.url.trim())
            try {
                val code = connection.responseCode
                NetworkDiagnostics.response("m3u", source.url, code)
                if (code !in 200..299) {
                    return@withContext M3uImportResult.Failure(
                        "Le serveur M3U a répondu avec le code $code.",
                    )
                }

                val entries = ArrayList<M3uEntry>(minOf(maxEntries, 256))
                val total = connection.inputStream
                    .bufferedReader()
                    .use { reader ->
                        M3uParser.parseLines(
                            lines = reader.lineSequence(),
                            maxEntries = maxEntries,
                        ) { entry -> entries += entry }
                    }

                if (total == 0) {
                    return@withContext M3uImportResult.Failure(
                        "La playlist M3U est vide ou invalide.",
                    )
                }

                M3uImportResult.Success(
                    entries = entries,
                    totalParsed = total,
                )
            } finally {
                connection.disconnect()
            }
        } catch (error: SocketTimeoutException) {
            NetworkDiagnostics.failure("m3u", source.url, error)
            M3uImportResult.Failure("Le serveur M3U met trop de temps à répondre.")
        } catch (error: Exception) {
            NetworkDiagnostics.failure("m3u", source.url, error)
            M3uImportResult.Failure(
                "Impossible de télécharger la playlist M3U. Vérifiez l’adresse et votre connexion.",
            )
        }
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doInput = true
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/x-mpegURL, audio/x-mpegurl, text/plain, */*")
            setRequestProperty("User-Agent", "ZYVIOTV-Player/0.1")
        }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 30_000
    }
}
