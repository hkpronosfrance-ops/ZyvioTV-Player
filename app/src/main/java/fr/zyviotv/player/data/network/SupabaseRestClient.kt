package fr.zyviotv.player.data.network

import fr.zyviotv.player.BuildConfig
import fr.zyviotv.player.data.auth.SecureSessionStore
import fr.zyviotv.player.data.auth.SupabaseAuthRepository
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * PR #217: authenticated Supabase REST calls with the session refresh of
 * PR #204, shared by the repositories that used the stored access token as is.
 *
 * A first synchronisation can outlast the access token: the profile list then
 * answered 401 with no refresh attempt (seen on the Pixel 7 emulator, perf
 * variant). The token is refreshed before the request when it is expired or
 * about to expire, and once more after a 401. One `ZyvioNetwork` line per
 * response: operation, status and session state, never a token or a body.
 */
class SupabaseRestClient internal constructor(
    private val loadSession: () -> SecureSessionStore.StoredSession?,
    private val refreshSession: suspend () -> Boolean,
    private val baseUrl: String = BuildConfig.SUPABASE_URL,
    private val apiKey: String = BuildConfig.SUPABASE_PUBLISHABLE_KEY,
    private val log: (operation: String, statusCode: Int, sessionState: String, body: String, retried: Boolean) -> Unit =
        NetworkDiagnostics::supabaseResponse,
) {
    constructor(sessionStore: SecureSessionStore) : this(
        loadSession = sessionStore::load,
        refreshSession = {
            SupabaseAuthRepository(sessionStore).refreshSession() ==
                SupabaseAuthRepository.SessionRestoreResult.Valid
        },
    )

    data class Response(val code: Int, val body: String)

    /** Blocking network call: run it on an IO dispatcher. 401 when no session is stored. */
    suspend fun request(
        path: String,
        method: String,
        body: String? = null,
        extraHeaders: Map<String, String> = emptyMap(),
    ): Response {
        var session = loadSession() ?: return Response(UNAUTHORIZED, "")
        var token = session.accessToken
        var sessionState = sessionState(session)
        var refreshed = false

        if (SupabaseSessionDiagnostics.shouldRefreshBeforeRequest(sessionState)) {
            val renewed = refreshAccessToken(previousAccessToken = token)
            if (renewed != null && renewed != token) {
                token = renewed
                refreshed = true
            }
            session = loadSession() ?: session
            sessionState = sessionState(session)
        }

        val operation = SupabaseSessionDiagnostics.operation(path)
        var response = rawRequest(path, method, body, token, extraHeaders)
        log(operation, response.code, sessionState, response.body, false)

        if (SupabaseSessionDiagnostics.shouldRetryUnauthorized(response.code, refreshed)) {
            val renewed = refreshAccessToken(previousAccessToken = token)
            if (renewed != null) {
                response = rawRequest(path, method, body, renewed, extraHeaders)
                log(operation, response.code, sessionState(loadSession()), response.body, true)
            }
        }
        return response
    }

    /**
     * A new access token, or null when the session cannot be renewed. Refreshes
     * are serialised app-wide: a refresh token must not be spent twice.
     */
    suspend fun refreshAccessToken(previousAccessToken: String): String? =
        sessionRefreshMutex.withLock {
            val latest = loadSession() ?: return@withLock null
            // Another caller already renewed it while this one waited.
            if (latest.accessToken != previousAccessToken && sessionState(latest) == "fresh") {
                return@withLock latest.accessToken
            }
            if (refreshSession()) loadSession()?.accessToken else null
        }

    private fun rawRequest(
        path: String,
        method: String,
        body: String?,
        accessToken: String,
        extraHeaders: Map<String, String>,
    ): Response {
        val connection = URL(baseUrl + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.doInput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("apikey", apiKey)
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            extraHeaders.forEach(connection::setRequestProperty)

            if (body != null) {
                connection.doOutput = true
                connection.outputStream.bufferedWriter(StandardCharsets.UTF_8).use { it.write(body) }
            }

            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            return Response(code, stream?.bufferedReader()?.use { it.readText() }.orEmpty())
        } finally {
            connection.disconnect()
        }
    }

    private fun sessionState(session: SecureSessionStore.StoredSession?): String =
        SupabaseSessionDiagnostics.state(
            accessToken = session?.accessToken,
            expiresAtEpochSeconds = session?.expiresAtEpochSeconds,
        )

    companion object {
        private const val UNAUTHORIZED = 401
        private const val TIMEOUT_MS = 15_000

        /** Shared by every client instance and by [fr.zyviotv.player.data.sync.SupabaseCloudSyncRepository]. */
        internal val sessionRefreshMutex = Mutex()
    }
}
