package fr.zyviotv.player.data.m3u

import fr.zyviotv.player.shared.m3u.M3uImportResult
import fr.zyviotv.player.shared.m3u.M3uSource
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AndroidM3uClientTest {
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
    fun interruptedBodyIsRetriedAndNeverReturnedAsSuccess() = runBlocking {
        val body = buildString {
            appendLine("#EXTM3U")
            repeat(200) { index ->
                appendLine("#EXTINF:-1,Channel $index")
                appendLine("http://stream.example/$index.ts")
            }
        }
        repeat(2) {
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(body)
                    .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY),
            )
        }

        val result = AndroidM3uClient(readTimeoutMs = 2_000).import(
            M3uSource(server.url("/interrupted").toString()),
            maxEntries = Int.MAX_VALUE,
        )

        assertTrue(result is M3uImportResult.Failure)
        assertEquals(2, server.requestCount)
        val message = (result as M3uImportResult.Failure).message
        assertTrue("fin" in message || "interrompue" in message)
    }

    @Test
    fun transientHttpFailureIsRetriedOnlyOnce() = runBlocking {
        repeat(2) { server.enqueue(MockResponse().setResponseCode(503)) }

        val result = AndroidM3uClient().import(
            M3uSource(server.url("/unavailable").toString()),
            maxEntries = Int.MAX_VALUE,
        )

        assertEquals(2, server.requestCount)
        assertEquals(
            "Le serveur M3U a répondu avec le code 503.",
            (result as M3uImportResult.Failure).message,
        )
    }

    @Test
    fun samplingValidationMayStopEarlyWithoutClaimingFullSync() = runBlocking {
        val body = buildString {
            appendLine("#EXTM3U")
            repeat(20) { index ->
                appendLine("#EXTINF:-1,Channel $index")
                appendLine("http://stream.example/$index.ts")
            }
        }
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))

        val result = AndroidM3uClient().import(
            M3uSource(server.url("/large").toString()),
            maxEntries = 5,
        )

        assertEquals(5, (result as M3uImportResult.Success).totalParsed)
    }
}
