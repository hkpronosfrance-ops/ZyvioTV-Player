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

    fun response(client: String, url: String, statusCode: Int, attempt: Int = 1) {
        Log.i(TAG, "$client response=$statusCode transport=${safeEndpoint(url)} attempt=$attempt")
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
        Log.i(
            TAG,
            "supabase operation=$operation response=$statusCode session=$sessionState " +
                "authError=${supabaseErrorKind(responseBody)} retried=$retried",
        )
    }

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
            "missing" in normalized && "authorization" in normalized -> "missing-authorization"
            "missing" in normalized && "credential" in normalized -> "missing-credentials"
            "permission denied" in normalized -> "permission-denied"
            "timeout" in normalized || "timed out" in normalized -> "upstream-timeout"
            responseBody.isBlank() -> "empty"
            else -> "unclassified"
        }
    }
}
