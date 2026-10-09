package fr.zyviotv.player.data.xtream

import com.sun.net.httpserver.HttpServer
import fr.zyviotv.player.shared.xtream.XtreamConnectionResult
import fr.zyviotv.player.shared.xtream.XtreamCredentials
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AndroidXtreamClientTest {
    private lateinit var server: HttpServer

    @Before
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.start()
    }

    @After
    fun tearDown() {
        server.stop(0)
    }

    @Test
    fun http512RetriesWithCompatibilityHeadersAndStillValidatesBody() = runBlocking {
        val requests = AtomicInteger()
        server.createContext("/panel/player_api.php") { exchange ->
            requests.incrementAndGet()
            val compatible = exchange.requestHeaders.getFirst("User-Agent").startsWith("Mozilla/")
            if (compatible) {
                val body = activeProfile().toByteArray()
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            } else {
                exchange.sendResponseHeaders(512, -1)
                exchange.close()
            }
        }

        val result = AndroidXtreamClient().authenticate(credentials("/panel"))

        assertTrue(result is XtreamConnectionResult.Success)
        assertEquals(2, requests.get())
    }

    @Test
    fun redirectKeepsSameOriginQueryAndCookie() = runBlocking {
        server.createContext("/redirect/player_api.php") { exchange ->
            exchange.responseHeaders.add("Location", "/redirect/login")
            exchange.responseHeaders.add("Set-Cookie", "panel_session=ok; Path=/redirect")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
        server.createContext("/redirect/login") { exchange ->
            val hasCredentials = exchange.requestURI.rawQuery?.contains("username=user") == true &&
                exchange.requestURI.rawQuery?.contains("password=secret") == true
            val hasCookie = exchange.requestHeaders.getFirst("Cookie")?.contains("panel_session=ok") == true
            if (hasCredentials && hasCookie) {
                val body = activeProfile().toByteArray()
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            } else {
                exchange.sendResponseHeaders(400, -1)
                exchange.close()
            }
        }

        val result = AndroidXtreamClient().authenticate(credentials("/redirect"))

        assertTrue(result is XtreamConnectionResult.Success)
    }

    @Test
    fun repeated512RemainsAFailure() = runBlocking {
        val requests = AtomicInteger()
        server.createContext("/blocked/player_api.php") { exchange ->
            requests.incrementAndGet()
            exchange.sendResponseHeaders(512, -1)
            exchange.close()
        }

        val result = AndroidXtreamClient().authenticate(credentials("/blocked"))

        assertEquals(2, requests.get())
        assertEquals(
            "Le serveur IPTV a répondu avec le code 512.",
            (result as XtreamConnectionResult.Failure).message,
        )
    }

    private fun credentials(path: String) = XtreamCredentials(
        serverUrl = "http://127.0.0.1:${server.address.port}$path",
        username = "user",
        password = "secret",
    )

    private fun activeProfile(): String =
        """{"user_info":{"username":"user","status":"Active"},"server_info":{"url":"localhost"}}"""
}
