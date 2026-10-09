package fr.zyviotv.player.data.network

import android.util.Log
import java.io.EOFException
import java.net.ConnectException
import java.net.MalformedURLException
import java.net.ProtocolException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import kotlinx.coroutines.CancellationException

internal object NetworkDiagnostics {
    private const val TAG = "ZyvioNetwork"
    private val ERROR_CODE = Regex("\"code\"\\s*:\\s*\"([^\"]{1,16})\"")
    private val SAFE_ERROR_CODE = Regex("[0-9A-Z]{5}|PGRST[0-9]{3}")
    private val SAFE_NAME = Regex("[a-z0-9_-]{1,48}")

    fun response(client: String, url: String, statusCode: Int, attempt: Int = 1) {
        Log.i(TAG, "$client response=$statusCode transport=${safeEndpoint(url)} attempt=$attempt")
    }

    /**
     * Bloc #211: one line per Xtream HTTP hop, to tell a provider refusal
     * (e.g. HTTP 512) from a redirect, a cookie wall or an HTML page. Only
     * categories are logged: never the URL, host, path, query, cookie value
     * or redirect target.
     */
    fun xtreamResponse(
        operation: String,
        url: String,
        statusCode: Int,
        attempt: Int,
        profile: String,
        hop: Int,
        redirect: String,
        contentType: String?,
        hasSetCookie: Boolean,
    ) {
        Log.i(
            TAG,
            xtreamLine(operation, url, statusCode, attempt, profile, hop, redirect, contentType, hasSetCookie),
        )
    }

    internal fun xtreamLine(
        operation: String,
        url: String,
        statusCode: Int,
        attempt: Int,
        profile: String,
        hop: Int,
        redirect: String,
        contentType: String?,
        hasSetCookie: Boolean,
    ): String =
        "${safeName(operation)} response=$statusCode transport=${safeEndpoint(url)} attempt=$attempt " +
            "profile=${safeName(profile)} hop=$hop redirect=${safeName(redirect)} " +
            "content=${contentCategory(contentType)} set_cookie=$hasSetCookie"

    internal fun contentCategory(contentType: String?): String {
        val type = contentType?.substringBefore(';')?.trim()?.lowercase().orEmpty()
        return when {
            type.isEmpty() -> "none"
            type == "application/json" || type.endsWith("+json") -> "json"
            type == "text/html" -> "html"
            type.startsWith("text/") -> "text"
            else -> "other"
        }
    }

    fun failure(client: String, url: String, error: Throwable, terminal: Boolean = true) {
        // Never log Throwable/message: URLConnection exceptions can include the
        // complete URL, including Xtream credentials or M3U query parameters.
        Log.w(
            TAG,
            "$client failure=${failureKind(error)} transport=${safeEndpoint(url)} terminal=$terminal",
        )
    }

    fun supabaseResponse(
        operation: String,
        statusCode: Int,
        sessionState: String,
        responseBody: String,
        retried: Boolean,
    ) {
        Log.i(TAG, supabaseLine(operation, statusCode, sessionState, responseBody, retried))
    }

    /**
     * Bloc #211: the line keeps `authError=` for comparison with earlier
     * recordings and adds the PostgREST/Postgres error code (`sqlstate=`, a
     * short allow-listed code) and its category (`pg=`), e.g. a missing table
     * privilege versus a row-level security refusal, both SQLSTATE 42501.
     * The body itself, the request, the JWT and any identifier are never logged.
     */
    internal fun supabaseLine(
        operation: String,
        statusCode: Int,
        sessionState: String,
        responseBody: String,
        retried: Boolean,
    ): String =
        "supabase operation=${safeName(operation)} response=$statusCode session=${safeName(sessionState)} " +
            "authError=${supabaseErrorKind(responseBody)} sqlstate=${supabaseErrorCode(responseBody)} " +
            "pg=${postgresErrorCategory(responseBody)} retried=$retried"

    /** The `code` field of a PostgREST error body, when it is a known code shape. */
    internal fun supabaseErrorCode(responseBody: String): String {
        val code = ERROR_CODE.find(responseBody)?.groupValues?.get(1) ?: return "none"
        return if (SAFE_ERROR_CODE.matches(code)) code else "other"
    }

    internal fun postgresErrorCategory(responseBody: String): String {
        val normalized = responseBody.lowercase()
        return when {
            "row-level security" in normalized || "row level security" in normalized -> "rls-violation"
            "permission denied for table" in normalized ||
                "permission denied for view" in normalized ||
                "permission denied for sequence" in normalized -> "missing-table-grant"
            "permission denied for function" in normalized -> "missing-function-grant"
            "permission denied for schema" in normalized -> "missing-schema-usage"
            "permission denied" in normalized -> "permission-denied-other"
            else -> "none"
        }
    }

    private fun safeName(value: String): String =
        if (SAFE_NAME.matches(value)) value else "other"

    internal fun safeEndpoint(value: String): String {
        return try {
            val url = URL(value)
            val scheme = url.protocol.lowercase()
            if (url.host.isBlank()) "invalid-url"
            else if (scheme == "http" || scheme == "https") scheme else "other"
        } catch (_: Exception) {
            "invalid-url"
        }
    }

    internal fun failureKind(error: Throwable): String = when (error) {
        is CancellationException -> "cancelled"
        is SocketTimeoutException -> "timeout"
        is EOFException -> "truncated"
        is ProtocolException -> "protocol"
        is ConnectException -> "connection"
        is SocketException -> "connection-interrupted"
        is UnknownHostException -> "dns"
        is SSLException -> "tls"
        is MalformedURLException -> "invalid-url"
        is SecurityException -> "security-policy"
        else -> "io"
    }


    internal fun supabaseErrorKind(responseBody: String): String {
        val normalized = responseBody.lowercase()
        return when {
            "invalid jwt" in normalized || "invalid_jwt" in normalized -> "invalid-jwt"
            "jwt expired" in normalized || "token is expired" in normalized -> "expired-jwt"
            "missing" in normalized && "authorization" in normalized -> "missing-auth-header"
            "missing" in normalized && "credential" in normalized -> "missing-credentials"
            "permission denied" in normalized -> "permission-denied"
            "timeout" in normalized || "timed out" in normalized -> "upstream-timeout"
            responseBody.isBlank() -> "empty"
            else -> "unclassified"
        }
    }
}
