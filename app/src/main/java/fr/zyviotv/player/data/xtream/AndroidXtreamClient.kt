package fr.zyviotv.player.data.xtream

import fr.zyviotv.player.data.network.NetworkDiagnostics
import fr.zyviotv.player.shared.xtream.XtreamClient
import fr.zyviotv.player.shared.xtream.XtreamConnectionResult
import fr.zyviotv.player.shared.xtream.XtreamCredentials
import fr.zyviotv.player.shared.xtream.XtreamEndpointBuilder
import fr.zyviotv.player.shared.xtream.XtreamParser
import fr.zyviotv.player.shared.xtream.XtreamValidationResult
import fr.zyviotv.player.shared.xtream.XtreamValidator
import java.net.SocketTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidXtreamClient : XtreamClient {
    private val httpClient = AndroidXtreamHttpClient()

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
                val response = httpClient.get(endpoint, operation = "xtream-auth")
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
            } catch (error: CancellationException) {
                NetworkDiagnostics.failure("xtream-auth", endpoint, error)
                throw error
            } catch (error: SocketTimeoutException) {
                NetworkDiagnostics.failure("xtream", endpoint, error)
                XtreamConnectionResult.Failure("Le serveur IPTV met trop de temps à répondre.")
            } catch (error: Exception) {
                NetworkDiagnostics.failure("xtream", endpoint, error)
                XtreamConnectionResult.Failure(
                    "Impossible de joindre le serveur IPTV. Vérifiez l’adresse et votre connexion.",
                )
            }
        }

}
