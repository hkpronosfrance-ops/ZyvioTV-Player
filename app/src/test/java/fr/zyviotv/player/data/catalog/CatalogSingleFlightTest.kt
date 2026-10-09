package fr.zyviotv.player.data.catalog

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogSingleFlightTest {
    @Test
    fun concurrentRefreshesForSameProfileRunLoaderOnlyOnce() = runBlocking {
        val starts = AtomicInteger()
        val release = CompletableDeferred<Unit>()
        val singleFlight = CatalogSingleFlight<String>()
        suspend fun load(): String {
            starts.incrementAndGet()
            release.await()
            return "catalog"
        }

        val first = async { singleFlight.run("profile-1", ::load) }
        yield()
        val second = async { singleFlight.run("profile-1", ::load) }
        yield()
        release.complete(Unit)

        assertEquals("catalog", first.await())
        assertEquals("catalog", second.await())
        assertEquals(1, starts.get())
    }

    @Test
    fun cancellingOneScreenDoesNotCancelSharedRefresh() = runBlocking {
        val starts = AtomicInteger()
        val release = CompletableDeferred<Unit>()
        val singleFlight = CatalogSingleFlight<String>()
        suspend fun load(): String {
            starts.incrementAndGet()
            release.await()
            return "catalog"
        }

        val leavingScreen = async { singleFlight.run("profile-1", ::load) }
        yield()
        val remainingScreen = async { singleFlight.run("profile-1", ::load) }
        yield()
        leavingScreen.cancelAndJoin()
        release.complete(Unit)

        assertEquals("catalog", remainingScreen.await())
        assertEquals(1, starts.get())
    }

    @Test
    fun finishedRefreshIsReleasedEvenWhenEveryCallerWasCancelled() = runBlocking {
        val release = CompletableDeferred<Unit>()
        val finished = CompletableDeferred<Unit>()
        val singleFlight = CatalogSingleFlight<String>()

        val onlyScreen = async {
            singleFlight.run("profile-1") {
                release.await()
                "catalog".also { finished.complete(Unit) }
            }
        }
        yield()
        onlyScreen.cancelAndJoin()
        release.complete(Unit)
        finished.await()
        // Completion handlers run right after the task ends.
        withTimeout(1_000L) {
            while (singleFlight.inFlightCount() != 0) delay(5L)
        }
        assertEquals(0, singleFlight.inFlightCount())
    }

    @Test
    fun laterSequentialCallStartsAFreshLoad() = runBlocking {
        val starts = AtomicInteger()
        val singleFlight = CatalogSingleFlight<Int>()
        assertEquals(1, singleFlight.run("profile-1") { starts.incrementAndGet() })
        assertEquals(2, singleFlight.run("profile-1") { starts.incrementAndGet() })
        assertEquals(0, singleFlight.inFlightCount())
    }
}
