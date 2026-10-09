package fr.zyviotv.player.data.catalog

import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogRefreshPolicyTest {
    private val now = 1_800_000_000_000L
    private val hour = 60L * 60L * 1_000L

    @Test
    fun automaticReloadUsesCacheYoungerThanTwelveHours() {
        val decision = CatalogRefreshPolicy.decide(
            trigger = CatalogRefreshTrigger.Startup,
            hasPlayableCache = true,
            fetchedAtEpochMs = now - 11 * hour,
            nowEpochMs = now,
        )
        assertEquals(CatalogRefreshDecision.UseFreshCache, decision)
        assertFalse(decision.shouldRefresh)
    }

    @Test
    fun automaticReloadRefreshesAtTwelveHours() {
        val decision = CatalogRefreshPolicy.decide(
            CatalogRefreshTrigger.ProfileChanged,
            hasPlayableCache = true,
            fetchedAtEpochMs = now - 12 * hour,
            nowEpochMs = now,
        )
        assertEquals(CatalogRefreshDecision.Stale, decision)
    }

    @Test
    fun unknownOrFutureDateOrMissingSourcesRefresh() {
        assertEquals(
            CatalogRefreshDecision.UnknownAge,
            CatalogRefreshPolicy.decide(CatalogRefreshTrigger.Startup, true, null, now),
        )
        assertEquals(
            CatalogRefreshDecision.FutureDate,
            CatalogRefreshPolicy.decide(CatalogRefreshTrigger.Startup, true, now + hour, now),
        )
        assertEquals(
            CatalogRefreshDecision.UseFreshCache,
            CatalogRefreshPolicy.decide(CatalogRefreshTrigger.Startup, true, now + 60_000L, now),
        )
        assertEquals(
            CatalogRefreshDecision.NoUsableCache,
            CatalogRefreshPolicy.decide(CatalogRefreshTrigger.Startup, false, now, now),
        )
    }

    @Test
    fun manualTriggersAlwaysRefreshEvenWithFreshCache() {
        listOf(
            CatalogRefreshTrigger.Retry,
            CatalogRefreshTrigger.Parental,
            CatalogRefreshTrigger.PlaylistChanged,
        ).forEach { trigger ->
            assertEquals(
                CatalogRefreshDecision.Requested,
                CatalogRefreshPolicy.decide(trigger, true, now - 1_000L, now),
            )
            assertFalse(CatalogRefreshPolicy.mustWaitForPlayback(trigger, playbackActive = true))
        }
    }

    @Test
    fun automaticTriggersWaitForPlaybackOnly() {
        assertTrue(CatalogRefreshPolicy.mustWaitForPlayback(CatalogRefreshTrigger.Startup, true))
        assertTrue(CatalogRefreshPolicy.mustWaitForPlayback(CatalogRefreshTrigger.ProfileChanged, true))
        assertFalse(CatalogRefreshPolicy.mustWaitForPlayback(CatalogRefreshTrigger.Startup, false))
    }

    @Test
    fun strongestPendingTriggerWins() {
        assertEquals(
            CatalogRefreshTrigger.PlaylistChanged,
            CatalogRefreshTrigger.Startup.strongest(CatalogRefreshTrigger.PlaylistChanged),
        )
        assertEquals(
            CatalogRefreshTrigger.Retry,
            CatalogRefreshTrigger.Retry.strongest(CatalogRefreshTrigger.Startup),
        )
        assertEquals(CatalogRefreshTrigger.Startup, CatalogRefreshTrigger.Startup.strongest(null))
    }

    @Test
    fun ageIsReportedInMinutesOrUnknown() {
        assertEquals(90L, CatalogRefreshPolicy.ageMinutes(now - 90L * 60_000L, now))
        assertEquals(null, CatalogRefreshPolicy.ageMinutes(null, now))
    }

    @Test
    fun deferredRefreshStartsOnlyAfterPlaybackEnds() = runBlocking {
        val tracker = PlaybackActivityTracker()
        val playback = tracker.begin()
        assertTrue(tracker.isActive)
        var started = false
        val waiting = async {
            tracker.awaitIdle()
            started = true
        }
        yield()
        assertFalse(started)
        playback.close()
        playback.close() // releasing twice must not count twice
        waiting.await()
        assertTrue(started)
        assertFalse(tracker.isActive)
    }

    @Test
    fun awaitIdleReturnsAtOnceWithoutPlayback() = runBlocking {
        val tracker = PlaybackActivityTracker()
        tracker.awaitIdle()
        val first = tracker.begin()
        val second = tracker.begin()
        first.close()
        assertTrue(tracker.isActive)
        second.close()
        assertFalse(tracker.isActive)
    }
}
