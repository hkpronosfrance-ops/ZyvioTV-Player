package fr.zyviotv.player.data.network

import fr.zyviotv.player.data.auth.SecureSessionStore
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class SupabaseRestClientTest {
    private lateinit var server: MockWebServer
    private var session: SecureSessionStore.StoredSession? = null
    private var refreshes = 0
    private val logged = mutableListOf<String>()

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
    fun anUnauthorizedAnswerRefreshesTheSessionOnceAndRetries() = runBlocking {
        session = session(token = jwt("old"), expiresInSeconds = 3_600)
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))

        val response = client(refreshTo = jwt("new")).request("/rest/v1/player_profiles?select=id", "GET")

        assertEquals(200, response.code)
        assertEquals(1, refreshes)
        assertEquals("Bearer ${jwt("old")}", server.takeRequest().getHeader("Authorization"))
        assertEquals("Bearer ${jwt("new")}", server.takeRequest().getHeader("Authorization"))
        assertEquals(listOf("player_profiles 401 false", "player_profiles 200 true"), logged)
    }

    @Test
    fun anExpiredTokenIsRefreshedBeforeTheRequest() = runBlocking {
        session = session(token = jwt("old"), expiresInSeconds = -10)
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))

        val response = client(refreshTo = jwt("new")).request("/rest/v1/player_profiles", "GET")

        assertEquals(200, response.code)
        assertEquals(1, refreshes)
        assertEquals(1, server.requestCount)
        assertEquals("Bearer ${jwt("new")}", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun aSessionThatCannotBeRenewedKeepsTheUnauthorizedAnswer() = runBlocking {
        session = session(token = jwt("old"), expiresInSeconds = 3_600)
        server.enqueue(MockResponse().setResponseCode(401))

        val response = client(refreshTo = null).request("/rest/v1/player_profiles", "GET")

        assertEquals(401, response.code)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun noStoredSessionMeansNoRequest() = runBlocking {
        session = null

        val response = client(refreshTo = jwt("new")).request("/rest/v1/player_profiles", "GET")

        assertEquals(401, response.code)
        assertEquals(0, server.requestCount)
    }

    private fun client(refreshTo: String?) = SupabaseRestClient(
        loadSession = { session },
        refreshSession = {
            refreshes += 1
            if (refreshTo != null) {
                session = session(token = refreshTo, expiresInSeconds = 3_600)
                true
            } else {
                false
            }
        },
        baseUrl = server.url("/").toString().trimEnd('/'),
        apiKey = "publishable-test",
        log = { operation, status, _, _, retried -> logged += "$operation $status $retried" },
    )

    private fun session(token: String, expiresInSeconds: Long) = SecureSessionStore.StoredSession(
        accessToken = token,
        refreshToken = "refresh-test",
        expiresAtEpochSeconds = System.currentTimeMillis() / 1000L + expiresInSeconds,
    )

    // Shape of a JWT only (three dot-separated parts); no real credential.
    private fun jwt(tag: String) = "header.$tag.signature"
}
