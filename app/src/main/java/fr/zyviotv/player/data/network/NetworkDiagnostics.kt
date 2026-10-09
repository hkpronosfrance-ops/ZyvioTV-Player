package fr.zyviotv.player.data.network

import android.util.Log
import java.net.ConnectException
import java.net.MalformedURLException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import javax.net.ssl.SSLException

internal object NetworkDiagnostics {
    private const val TAG = "ZyvioNetwork"

    fun response(client: String, url: String, statusCode: Int) {
        Log.i(TAG, "$client response=$statusCode endpoint=${safeEndpoint(url)}")
    }

    fun failure(client: String, url: String, error: Throwable) {
        // Never log Throwable/message: URLConnection exceptions can include the
        // complete URL, including Xtream credentials or M3U query parameters.
        Log.w(TAG, "$client failure=${failureKind(error)} endpoint=${safeEndpoint(url)}")
    }

    internal fun safeEndpoint(value: String): String = try {
        val url = URL(value)
        val scheme = url.protocol.lowercase()
        val host = url.host.takeIf { it.isNotBlank() } ?: return "invalid-url"
        val port = url.port.takeIf { it >= 0 && it != url.defaultPort }
        buildString {
            append(scheme)
            append("://")
            append(host)
            if (port != null) {
                append(':')
                append(port)
            }
        }
    } catch (_: Exception) {
        "invalid-url"
    }

    internal fun failureKind(error: Throwable): String = when (error) {
        is SocketTimeoutException -> "timeout"
        is UnknownHostException -> "dns"
        is ConnectException -> "connection"
        is SSLException -> "tls"
        is MalformedURLException -> "invalid-url"
        is SecurityException -> "security-policy"
        else -> "io"
    }
}
