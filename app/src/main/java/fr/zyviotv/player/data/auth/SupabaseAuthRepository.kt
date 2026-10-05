package fr.zyviotv.player.data.auth

import fr.zyviotv.player.BuildConfig
import fr.zyviotv.player.shared.auth.AuthCredentials
import fr.zyviotv.player.shared.auth.AuthRepository
import fr.zyviotv.player.shared.auth.AuthResult
import fr.zyviotv.player.shared.auth.RegistrationCredentials
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class SupabaseAuthRepository(
    private val sessionStore: SecureSessionStore,
) : AuthRepository {

    override suspend fun signIn(credentials: AuthCredentials): AuthResult =
        withContext(Dispatchers.IO) {
            val response = request(
                path = "/auth/v1/token?grant_type=password",
                method = "POST",
                body = JSONObject()
                    .put("email", credentials.email.trim())
                    .put("password", credentials.password)
                    .toString(),
            )

            if (response.code in 200..299) {
                saveSessionFromResponse(response.body)
                AuthResult.Success
            } else {
                AuthResult.Failure(readErrorMessage(response.body))
            }
        }

    override suspend fun signUp(credentials: RegistrationCredentials): AuthResult =
        withContext(Dispatchers.IO) {
            val response = request(
                path = "/auth/v1/signup",
                method = "POST",
                body = JSONObject()
                    .put("email", credentials.email.trim())
                    .put("password", credentials.password)
                    .toString(),
            )

            if (response.code in 200..299) {
                runCatching { saveSessionFromResponse(response.body) }
                AuthResult.Success
            } else {
                AuthResult.Failure(readErrorMessage(response.body))
            }
        }

    override suspend fun requestPasswordReset(email: String): AuthResult =
        withContext(Dispatchers.IO) {
            val response = request(
                path = "/auth/v1/recover",
                method = "POST",
                body = JSONObject()
                    .put("email", email.trim())
                    .toString(),
            )

            if (response.code in 200..299) {
                AuthResult.Success
            } else {
                AuthResult.Failure(readErrorMessage(response.body))
            }
        }

    override suspend fun signOut(): AuthResult =
        withContext(Dispatchers.IO) {
            val stored = sessionStore.load()
            if (stored == null) {
                sessionStore.clear()
                return@withContext AuthResult.Success
            }

            val response = request(
                path = "/auth/v1/logout",
                method = "POST",
                body = "{}",
                bearerToken = stored.accessToken,
            )

            sessionStore.clear()

            if (response.code in 200..299 || response.code == 401) {
                AuthResult.Success
            } else {
                AuthResult.Failure(readErrorMessage(response.body))
            }
        }

    fun hasStoredSession(): Boolean = sessionStore.load() != null

    private fun saveSessionFromResponse(body: String) {
        val json = JSONObject(body)
        val accessToken = json.optString("access_token")
        val refreshToken = json.optString("refresh_token")
        if (accessToken.isBlank() || refreshToken.isBlank()) return

        val expiresIn = json.optLong("expires_in", 3600L)
        val expiresAt = (System.currentTimeMillis() / 1000L) + expiresIn

        sessionStore.save(
            SecureSessionStore.StoredSession(
                accessToken = accessToken,
                refreshToken = refreshToken,
                expiresAtEpochSeconds = expiresAt,
            ),
        )
    }

    private fun request(
        path: String,
        method: String,
        body: String,
        bearerToken: String? = null,
    ): HttpResponse {
        val connection = (URL(BuildConfig.SUPABASE_URL + path).openConnection() as HttpURLConnection)
        try {
            connection.requestMethod = method
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.doInput = true
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            connection.setRequestProperty(
                "Authorization",
                "Bearer " + (bearerToken ?: BuildConfig.SUPABASE_PUBLISHABLE_KEY),
            )

            connection.outputStream.bufferedWriter(StandardCharsets.UTF_8).use {
                it.write(body)
            }

            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val responseBody = stream?.bufferedReader()?.use { it.readText() }.orEmpty()

            return HttpResponse(code = code, body = responseBody)
        } finally {
            connection.disconnect()
        }
    }

    private fun readErrorMessage(body: String): String {
        val json = runCatching { JSONObject(body) }.getOrNull()
        return json?.optString("msg")?.takeIf { it.isNotBlank() }
            ?: json?.optString("message")?.takeIf { it.isNotBlank() }
            ?: json?.optString("error_description")?.takeIf { it.isNotBlank() }
            ?: "Une erreur est survenue. Réessayez."
    }

    private data class HttpResponse(
        val code: Int,
        val body: String,
    )
}
