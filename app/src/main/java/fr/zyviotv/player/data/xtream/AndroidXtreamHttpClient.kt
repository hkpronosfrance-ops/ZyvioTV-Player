package fr.zyviotv.player.data.xtream

import fr.zyviotv.player.data.network.NetworkDiagnostics
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.HttpURLConnection
import java.net.ProtocolException
import java.net.URL

internal data class XtreamHttpResponse(
    val code: Int,
    val body: String,
)

internal class AndroidXtreamHttpClient(
    private val connectTimeoutMs: Int = CONNECT_TIMEOUT_MS,
    private val readTimeoutMs: Int = READ_TIMEOUT_MS,
) {
    fun get(url: String, operation: String): XtreamHttpResponse {
        val cookies = CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER)
        val primary = execute(url, operation, HeaderProfile.Primary, cookies, attempt = 1)
        return if (primary.code == COMPATIBILITY_RETRY_STATUS) {
            execute(url, operation, HeaderProfile.Compatibility, cookies, attempt = 2)
        } else {
            primary
        }
    }

    private fun execute(
        initialUrl: String,
        operation: String,
        profile: HeaderProfile,
        cookies: CookieManager,
        attempt: Int,
    ): XtreamHttpResponse {
        var current = URL(initialUrl)
        repeat(MAX_REDIRECTS + 1) { redirectCount ->
            val connection = current.openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = connectTimeoutMs
                connection.readTimeout = readTimeoutMs
                connection.doInput = true
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("Accept", "application/json, text/plain, */*")
                connection.setRequestProperty("User-Agent", profile.userAgent)
                cookies.get(current.toURI(), emptyMap()).forEach { (name, values) ->
                    if (name.isNotBlank() && values.isNotEmpty()) {
                        connection.setRequestProperty(name, values.joinToString("; "))
                    }
                }

                val code = connection.responseCode
                NetworkDiagnostics.response(operation, current.toExternalForm(), code, attempt)
                cookies.put(
                    current.toURI(),
                    buildMap {
                        connection.headerFields.forEach { (name, values) ->
                            if (name != null) put(name, values)
                        }
                    },
                )

                if (code in REDIRECT_CODES) {
                    if (redirectCount >= MAX_REDIRECTS) throw ProtocolException("Too many redirects")
                    val location = connection.getHeaderField("Location")
                        ?: throw ProtocolException("Redirect without Location")
                    current = redirectedUrl(current, location)
                    return@repeat
                }

                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                return XtreamHttpResponse(
                    code = code,
                    body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty(),
                )
            } finally {
                connection.disconnect()
            }
        }
        throw ProtocolException("Too many redirects")
    }

    private fun redirectedUrl(current: URL, location: String): URL {
        var next = URL(current, location)
        if (next.protocol != "http" && next.protocol != "https") {
            throw ProtocolException("Unsupported redirect protocol")
        }
        if (
            next.query == null && current.query != null &&
            next.host.equals(current.host, ignoreCase = true) && next.port == current.port
        ) {
            next = URL(next.toExternalForm() + "?" + current.query)
        }
        return next
    }

    private enum class HeaderProfile(val userAgent: String) {
        Primary("ZYVIOTV-Player/0.1 (Android)"),
        Compatibility("Mozilla/5.0 (Linux; Android 15; Mobile) AppleWebKit/537.36 Safari/537.36"),
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 30_000
        const val MAX_REDIRECTS = 5
        const val COMPATIBILITY_RETRY_STATUS = 512
        val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
    }
}
