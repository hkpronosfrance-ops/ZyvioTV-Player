package fr.zyviotv.player.ui.player

import fr.zyviotv.player.data.network.NetworkAvailability
import fr.zyviotv.player.shared.playback.PlaybackKind
import fr.zyviotv.player.shared.playback.PlaybackRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackLaunchPolicyTest {
    private fun request(url: String, kind: PlaybackKind = PlaybackKind.Live) =
        PlaybackRequest(title = "Chaîne", streamUrl = url, kind = kind)

    @Test
    fun liveChannelLaunchesWhenNetworkIsAvailable() {
        val decision = PlaybackLaunchPolicy.decide(
            request("http://provider.example:8080/u/p/42"),
            NetworkAvailability.Available,
        )

        assertTrue(decision is PlaybackLaunchDecision.Launch)
    }

    @Test
    fun unknownConnectivityNeverBlocksPlayback() {
        // A connectivity query that has not answered yet must not reproduce the
        // Pixel 7 "hors connexion" false positive.
        listOf(PlaybackKind.Live, PlaybackKind.Movie, PlaybackKind.Episode).forEach { kind ->
            val decision = PlaybackLaunchPolicy.decide(
                request("https://provider.example/stream.m3u8", kind),
                NetworkAvailability.Unknown,
            )
            assertTrue(kind.name, decision is PlaybackLaunchDecision.Launch)
        }
    }

    @Test
    fun confirmedOfflineBlocksWithExplicitMessage() {
        val decision = PlaybackLaunchPolicy.decide(
            request("https://provider.example/stream.m3u8"),
            NetworkAvailability.Unavailable,
        )

        decision as PlaybackLaunchDecision.Blocked
        assertEquals(PlaybackBlockReason.NoNetwork, decision.reason)
        assertTrue(decision.message.isNotBlank())
    }

    @Test
    fun missingSourceIsReportedBeforeConnectivity() {
        val decision = PlaybackLaunchPolicy.decide(
            request("   ", PlaybackKind.Movie),
            NetworkAvailability.Unavailable,
        )

        decision as PlaybackLaunchDecision.Blocked
        assertEquals(PlaybackBlockReason.MissingSource, decision.reason)
        assertEquals(PlaybackLaunchPolicy.MISSING_SOURCE_MESSAGE, decision.message)
    }

    @Test
    fun unsupportedSchemeIsRejectedWithValidatorMessage() {
        val decision = PlaybackLaunchPolicy.decide(
            request("rtmp://provider.example/live/1"),
            NetworkAvailability.Available,
        )

        decision as PlaybackLaunchDecision.Blocked
        assertEquals(PlaybackBlockReason.InvalidSource, decision.reason)
    }

    @Test
    fun launchedRequestIsTrimmedAndKeepsResumePosition() {
        val decision = PlaybackLaunchPolicy.decide(
            PlaybackRequest(
                title = "Film",
                streamUrl = "  https://provider.example/movie/7.mp4 ",
                kind = PlaybackKind.Movie,
                resumePositionMs = 90_000L,
            ),
            NetworkAvailability.Available,
        )

        decision as PlaybackLaunchDecision.Launch
        assertEquals("https://provider.example/movie/7.mp4", decision.request.streamUrl)
        assertEquals(90_000L, decision.request.resumePositionMs)
    }

    @Test
    fun blockedMessagesNeverEchoTheStreamUrl() {
        val secretUrl = "http://provider.example/live/secretuser/secretpass/1.ts"
        val decision = PlaybackLaunchPolicy.decide(request(secretUrl), NetworkAvailability.Unavailable)

        decision as PlaybackLaunchDecision.Blocked
        assertTrue(!decision.message.contains("secretuser") && !decision.message.contains("secretpass"))
    }
}
