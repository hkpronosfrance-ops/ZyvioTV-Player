package fr.zyviotv.player.data.xtream

import fr.zyviotv.player.shared.xtream.XtreamClient
import fr.zyviotv.player.shared.xtream.XtreamConnectionResult
import fr.zyviotv.player.shared.xtream.XtreamCredentials
import fr.zyviotv.player.shared.xtream.XtreamEndpointBuilder
import fr.zyviotv.player.shared.xtream.XtreamParser
import fr.zyviotv.player.shared.xtream.XtreamValidationResult
import fr.zyviotv.player.shared.xtream.XtreamValidator
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidXtreamClient : XtreamClient {
    override suspend fun authenticate(credentials: XtreamCredentials): XtreamConnectionResult =
        withContext(Dispatchers.IO) {
            when (val validation = XtreamValidator.validate(credentials)) {
                is XtreamValidationResult.Invalid -> {
                    return@withContext XtreamConnectionResult.Failure(validation.message)
                }
                XtreamValidationResult.Valid -> Unit
            }

            val endpoint = XtreamEndpointBuilder.authenticatedPlayerApi(credentials)

            try {
                val response = get(endpoint)
                if (response.code !in 200..299) {
                    return@withContext XtreamConnectionResult.Failure(
                        "Le serveur IPTV a répondu avec le code ${response.code}.",
                    )
                }

                val profile = XtreamParser.parseProfile(response.body)
                    ?: return@withContext XtreamConnectionResult.Failure(
                        "La réponse du serveur IPTV est invalide.",
                    )

                if (!profile.account.status.equals("Active", ignoreCase = true)) {
                    return@withContext XtreamConnectionResult.Failure(
                        "Ce compte IPTV n’est pas actif.",
                    )
                }

                XtreamConnectionResult.Success(profile)
            } catch (_: SocketTimeoutException) {
                XtreamConnectionResult.Failure("Le serveur IPTV met trop de temps à répondre.")
            } catch (_: Exception) {
                XtreamConnectionResult.Failure(
                    "Impossible de joindre le serveur IPTV. Vérifiez l’adresse et votre connexion.",
                )
            }
        }

    private fun get(url: String): HttpResponse {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.doInput = true
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "ZYVIOTV-Player/0.1")

            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            return HttpResponse(code, body)
        } finally {
            connection.disconnect()
        }
    }

    private data class HttpResponse(
        val code: Int,
        val body: String,
    )

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 20_000
    }
}
