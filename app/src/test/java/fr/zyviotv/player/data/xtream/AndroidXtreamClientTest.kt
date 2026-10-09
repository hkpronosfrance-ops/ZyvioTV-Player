package fr.zyviotv.player.data.xtream

import fr.zyviotv.player.shared.xtream.XtreamConnectionResult
import fr.zyviotv.player.shared.xtream.XtreamCredentials
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AndroidXtreamClientTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun http512RetriesWithCompatibilityHeadersAndStillValidatesBody() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.getHeader("User-Agent")?.startsWith("Mozilla/") == true) {
                    MockResponse().setResponseCode(200).setBody(activeProfile())
                } else {
                    MockResponse().setResponseCode(512)
                }
        }

        val result = AndroidXtreamClient().authenticate(credentials("/panel"))

        assertTrue(result is XtreamConnectionResult.Success)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun redirectKeepsSameOriginQueryAndCookie() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.requestUrl?.encodedPath) {
                "/redirect/player_api.php" -> MockResponse()
                    .setResponseCode(302)
                    .setHeader("Location", "/redirect/login")
                    .setHeader("Set-Cookie", "panel_session=ok; Path=/redirect")
                "/redirect/login" -> {
                    val hasCredentials = request.requestUrl?.queryParameter("username") == "user" &&
                        request.requestUrl?.queryParameter("password") == "secret"
                    val hasCookie = request.getHeader("Cookie")?.contains("panel_session=ok") == true
                    if (hasCredentials && hasCookie) {
                        MockResponse().setResponseCode(200).setBody(activeProfile())
                    } else {
                        MockResponse().setResponseCode(400)
                    }
                }
                else -> MockResponse().setResponseCode(404)
            }
        }

        val result = AndroidXtreamClient().authenticate(credentials("/redirect"))

        assertTrue(result is XtreamConnectionResult.Success)
    }

    @Test
    fun repeated512RemainsAFailure() = runBlocking {
        repeat(2) { server.enqueue(MockResponse().setResponseCode(512)) }

        val result = AndroidXtreamClient().authenticate(credentials("/blocked"))

        assertEquals(2, server.requestCount)
        assertEquals(
            "Le serveur IPTV a répondu avec le code 512.",
            (result as XtreamConnectionResult.Failure).message,
        )
    }

    @Test
    fun cookieSetWithA512IsReplayedOnTheCompatibilityAttempt() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.getHeader("Cookie")?.contains("wall=passed") == true) {
                    MockResponse().setResponseCode(200).setBody(activeProfile())
                } else {
                    MockResponse().setResponseCode(512).setHeader("Set-Cookie", "wall=passed; Path=/")
                }
        }

        val result = AndroidXtreamClient().authenticate(credentials("/wall"))

        assertTrue(result is XtreamConnectionResult.Success)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun crossHostRedirectDoesNotCarryTheCredentialsQuery() {
        val other = MockWebServer()
        other.start()
        try {
            other.enqueue(MockResponse().setResponseCode(200).setBody("[]"))
            server.enqueue(
                MockResponse()
                    .setResponseCode(302)
                    .setHeader("Location", other.url("/elsewhere").toString().replace("localhost", "127.0.0.1")),
            )
            val response = AndroidXtreamHttpClient().get(
                server.url("/panel/player_api.php?username=user&password=secret").toString(),
                operation = "xtream-auth",
            )
            assertEquals(200, response.code)
            val forwarded = other.takeRequest()
            assertEquals(null, forwarded.requestUrl?.queryParameter("password"))
        } finally {
            other.shutdown()
        }
    }

    @Test
    fun tooManyRedirectsFailWithoutLooping() {
        repeat(6) {
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "/loop"))
        }
        val failure = runCatching {
            AndroidXtreamHttpClient().get(server.url("/loop").toString(), operation = "xtream-auth")
        }.exceptionOrNull()
        assertTrue(failure is java.net.ProtocolException)
        assertEquals(6, server.requestCount)
    }

    @Test
    fun hopDiagnosticLineHoldsCategoriesOnly() {
        val line = fr.zyviotv.player.data.network.NetworkDiagnostics.xtreamLine(
            operation = "xtream-catalog-movies",
            url = "http://provider.example:8080/player_api.php?username=user&password=secret&action=get_vod_streams",
            statusCode = 512,
            attempt = 2,
            profile = "compatibility",
            hop = 1,
            redirect = "cross-host",
            contentType = "text/html; charset=UTF-8",
            hasSetCookie = true,
        )
        assertEquals(
            "xtream-catalog-movies response=512 transport=http attempt=2 profile=compatibility hop=1 " +
                "redirect=cross-host content=html set_cookie=true",
            line,
        )
        fr.zyviotv.player.ui.player.DiagnosticsSafety.assertSafe(line)
        assertTrue(!line.contains("provider.example") && !line.contains("user") && !line.contains("8080"))
    }

    private fun credentials(path: String) = XtreamCredentials(
        serverUrl = server.url(path).toString().trimEnd('/'),
        username = "user",
        password = "secret",
    )

    private fun activeProfile(): String =
        """{"user_info":{"username":"user","status":"Active"},"server_info":{"url":"localhost"}}"""
}
