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

    private fun credentials(path: String) = XtreamCredentials(
        serverUrl = server.url(path).toString().trimEnd('/'),
        username = "user",
        password = "secret",
    )

    private fun activeProfile(): String =
        """{"user_info":{"username":"user","status":"Active"},"server_info":{"url":"localhost"}}"""
}
