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
    fun streamingImportParsesWhileDownloadingAndReportsEveryEntry() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody(playlist(entries = 300)))
        val received = mutableListOf<String>()
        var attempts = 0

        val result = AndroidM3uClient().importStreaming(
            source = M3uSource(server.url("/full").toString()),
            onAttemptStart = {
                attempts += 1
                received.clear()
            },
            onEntry = { received += it.name },
        )

        assertEquals(300, (result as M3uStreamingResult.Success).totalParsed)
        assertEquals(1, attempts)
        assertEquals(300, received.size)
        assertEquals("Channel 299", received.last())
    }

    @Test
    fun streamingImportRestartsFromZeroAfterAnInterruptedBodyAndNeverSucceedsOnIt() = runBlocking {
        repeat(2) {
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(playlist(entries = 2_000))
                    .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY),
            )
        }
        var attempts = 0

        val result = AndroidM3uClient(readTimeoutMs = 2_000).importStreaming(
            source = M3uSource(server.url("/interrupted").toString()),
            onAttemptStart = { attempts += 1 },
            onEntry = {},
        )

        assertTrue(result is M3uStreamingResult.Failure)
        assertEquals(2, attempts)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun streamingImportRetriesATruncatedBodyThenKeepsTheCompleteOne() = runBlocking {
        // Ends on a metadata line without its URL: cut in the middle of an entry.
        val truncated = playlist(entries = 10) + "#EXTINF:-1,Channel 10\n"
        server.enqueue(MockResponse().setResponseCode(200).setBody(truncated))
        server.enqueue(MockResponse().setResponseCode(200).setBody(playlist(entries = 12)))
        val received = mutableListOf<String>()

        val result = AndroidM3uClient().importStreaming(
            source = M3uSource(server.url("/truncated").toString()),
            onAttemptStart = { received.clear() },
            onEntry = { received += it.name },
        )

        assertEquals(12, (result as M3uStreamingResult.Success).totalParsed)
        assertEquals(12, received.size)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun streamingImportRejectsAnEmptyPlaylist() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("#EXTM3U\n"))

        val result = AndroidM3uClient().importStreaming(
            source = M3uSource(server.url("/empty").toString()),
            onEntry = {},
        )

        assertEquals(
            "La playlist M3U est vide ou invalide.",
            (result as M3uStreamingResult.Failure).message,
        )
    }

    private fun playlist(entries: Int): String = buildString {
        appendLine("#EXTM3U")
        repeat(entries) { index ->
            appendLine("#EXTINF:-1 group-title=\"Live\",Channel $index")
            appendLine("http://stream.example/live/$index.ts")
        }
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
