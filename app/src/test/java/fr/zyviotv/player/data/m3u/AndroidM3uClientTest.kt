package fr.zyviotv.player.data.m3u

import com.sun.net.httpserver.HttpServer
import fr.zyviotv.player.shared.m3u.M3uImportResult
import fr.zyviotv.player.shared.m3u.M3uSource
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AndroidM3uClientTest {
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
    fun interruptedBodyIsRetriedAndNeverReturnedAsSuccess() = runBlocking {
        val requests = AtomicInteger()
        val partial = "#EXTM3U\n#EXTINF:-1,Channel 1\nhttp://stream.example/1.ts\n".toByteArray()
        server.createContext("/interrupted") { exchange ->
            requests.incrementAndGet()
            exchange.sendResponseHeaders(200, partial.size.toLong() + 128L)
            exchange.responseBody.use { it.write(partial) }
        }

        val result = AndroidM3uClient(readTimeoutMs = 2_000).import(
            M3uSource(url("/interrupted")),
            maxEntries = Int.MAX_VALUE,
        )

        assertTrue(result is M3uImportResult.Failure)
        assertEquals(2, requests.get())
        val message = (result as M3uImportResult.Failure).message
        assertTrue("fin" in message || "interrompue" in message)
    }

    @Test
    fun transientHttpFailureIsRetriedOnlyOnce() = runBlocking {
        val requests = AtomicInteger()
        server.createContext("/unavailable") { exchange ->
            requests.incrementAndGet()
            exchange.sendResponseHeaders(503, -1)
            exchange.close()
        }

        val result = AndroidM3uClient().import(
            M3uSource(url("/unavailable")),
            maxEntries = Int.MAX_VALUE,
        )

        assertEquals(2, requests.get())
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
        }.toByteArray()
        server.createContext("/large") { exchange ->
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }

        val result = AndroidM3uClient().import(M3uSource(url("/large")), maxEntries = 5)

        assertEquals(5, (result as M3uImportResult.Success).totalParsed)
    }

    private fun url(path: String): String = "http://127.0.0.1:${server.address.port}$path"
}
