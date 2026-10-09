package fr.zyviotv.player.ui.player

import androidx.media3.common.PlaybackException
import fr.zyviotv.player.ui.DeviceProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase #209: navigation, lifecycle and layout decisions of the Android player. */
class PlayerLifecycleTest {
    @Test
    fun backWithoutPanelLeavesThePlayer() {
        assertEquals(PlayerBackAction.ExitPlayer, PlayerBackPolicy.onBack(PlayerPanel.None))
    }

    @Test
    fun backClosesOpenPanelsBeforeLeaving() {
        assertEquals(PlayerBackAction.ClosePanel, PlayerBackPolicy.onBack(PlayerPanel.Tracks))
        assertEquals(PlayerBackAction.ClosePanel, PlayerBackPolicy.onBack(PlayerPanel.Resume))
        assertEquals(PlayerBackAction.CancelChannelNumber, PlayerBackPolicy.onBack(PlayerPanel.ChannelNumber))
    }

    @Test
    fun everyPanelHasABackAction() {
        // A new panel must be given an explicit back behaviour.
        PlayerPanel.entries.forEach { PlayerBackPolicy.onBack(it) }
    }

    @Test
    fun exitPopsOnlyWhileThePlayerIsTheCurrentDestination() {
        assertTrue(PlayerExitNavigation.shouldPop("player", "player"))
        // Second tap on Retour, or the exiting entry recomposing: never pop
        // the catalogue screen underneath.
        assertFalse(PlayerExitNavigation.shouldPop("movies", "player"))
        assertFalse(PlayerExitNavigation.shouldPop(null, "player"))
    }

    @Test
    fun commandsAreExecutedOnceAndNeverReplayedOnANewPlayer() {
        val first = PlayerCommandGate(initialToken = 0L)
        assertTrue(first.accept(1L))
        assertFalse(first.accept(1L))
        assertTrue(first.accept(2L))

        // Next episode: the new player starts at the current token, so the
        // last Pause/Retry sent to the previous player is ignored.
        val next = PlayerCommandGate(initialToken = 2L)
        assertFalse(next.accept(2L))
        assertTrue(next.accept(3L))
    }

    @Test
    fun foregroundResumesOnlyWhatWasPlaying() {
        val policy = PlayerResumePolicy()
        policy.onStop(wasPlaying = true)
        assertTrue(policy.onStart())
        // A single resume per background trip.
        assertFalse(policy.onStart())

        policy.onStop(wasPlaying = false)
        assertFalse("A user pause survives background/foreground", policy.onStart())
    }

    @Test
    fun startWithoutStopDoesNotForcePlayback() {
        assertFalse(PlayerResumePolicy().onStart())
    }

    @Test
    fun portraitPhoneUsesIconOnlyActions() {
        val portrait = PlayerControlsLayout.of(DeviceProfile.Mobile, widthDp = 411, heightDp = 914)
        assertTrue(portrait.iconOnlyActions)
        assertFalse(portrait.isLandscape)
        assertTrue(portrait.showFullscreenToggle)
    }

    @Test
    fun landscapePhoneAndTabletShowLabels() {
        val landscape = PlayerControlsLayout.of(DeviceProfile.Mobile, widthDp = 914, heightDp = 411)
        assertFalse(landscape.iconOnlyActions)
        assertTrue(landscape.isLandscape)

        val tablet = PlayerControlsLayout.of(DeviceProfile.Tablet, widthDp = 800, heightDp = 1280)
        assertFalse(tablet.iconOnlyActions)
    }

    @Test
    fun televisionKeepsLabelsAndHasNoFullscreenToggle() {
        val tv = PlayerControlsLayout.of(DeviceProfile.Television, widthDp = 960, heightDp = 540)
        assertFalse(tv.iconOnlyActions)
        assertFalse(tv.showFullscreenToggle)
    }

    @Test
    fun scaleModeTogglesBetweenFitAndZoomOnly() {
        assertEquals(PlayerScaleMode.Zoom, PlayerScaleMode.Fit.toggled())
        assertEquals(PlayerScaleMode.Fit, PlayerScaleMode.Zoom.toggled())
        assertEquals(listOf(PlayerScaleMode.Fit, PlayerScaleMode.Zoom), PlayerScaleMode.entries)
    }

    @Test
    fun timelineIsFormattedWithoutLocale() {
        assertEquals("0:00", PlayerTimeFormat.clock(0L))
        assertEquals("1:05", PlayerTimeFormat.clock(65_000L))
        assertEquals("1:02:03", PlayerTimeFormat.clock(3_723_000L))
        assertEquals("12:34 / 1:02:03", PlayerTimeFormat.timeline(754_000L, 3_723_000L))
        assertEquals("0:10", PlayerTimeFormat.timeline(10_000L, null))
    }

    @Test
    fun hevcFailureOnTheEmulatorIsReportedAsAnEmulatorLimitation() {
        val decoder = DecoderDiagnosis(
            mimeType = "video/hevc",
            codecName = "c2.goldfish.hevc.decoder",
            isEmulator = true,
        )
        val failure = PlaybackErrorClassifier.classify(
            PlaybackException.ERROR_CODE_DECODING_FAILED,
            httpStatus = null,
            decoder = decoder,
        )

        assertEquals(PlaybackErrorKind.Decoder, failure.kind)
        assertFalse(failure.isTransient)
        assertTrue(decoder.isLikelyEmulatorLimitation)
        assertTrue(failure.userMessage.contains("émulateur"))
        assertTrue(failure.userMessage.contains("HEVC"))
        assertEquals("mime=video/hevc codec=c2.goldfish.hevc.decoder emulator=true", decoder.logFields())
    }

    @Test
    fun hevcFailureOnARealDeviceIsADeviceCodecLimit() {
        val failure = PlaybackErrorClassifier.classify(
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            httpStatus = null,
            decoder = DecoderDiagnosis("video/hevc", "c2.qti.hevc.decoder", isEmulator = false),
        )

        assertTrue(failure.userMessage.contains("HEVC"))
        assertFalse(failure.userMessage.contains("émulateur"))
    }

    @Test
    fun decoderDetailsAreOnlyKeptForDecoderFailures() {
        val failure = PlaybackErrorClassifier.classify(
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            httpStatus = null,
            decoder = DecoderDiagnosis("video/hevc", null, isEmulator = true),
        )

        assertEquals(PlaybackErrorKind.Network, failure.kind)
        assertNull(failure.decoder)
    }

    @Test
    fun emulatorIsRecognisedFromBuildFields() {
        assertTrue(DecoderDiagnosis.isEmulator("google/sdk_gphone64_x86_64/emu64x:17/X", "ranchu", "sdk_gphone64_x86_64"))
        assertTrue(DecoderDiagnosis.isEmulator("generic/sdk/generic:9/X", "goldfish", "sdk"))
        assertFalse(DecoderDiagnosis.isEmulator("google/panther/panther:17/X", "panther", "panther"))
    }

    // --- PR #210: full screen, orientation, back -----------------------------

    private fun immersive(state: PlayerWindowState, landscape: Boolean, compact: Boolean = true, tv: Boolean = false) =
        PlayerFullscreenPolicy.isImmersive(state, isTelevision = tv, isCompactDevice = compact, isLandscape = landscape)

    private fun toggle(state: PlayerWindowState, landscape: Boolean, compact: Boolean = true, tv: Boolean = false) =
        PlayerFullscreenPolicy.toggle(state, isTelevision = tv, isCompactDevice = compact, isLandscape = landscape)

    @Test
    fun fullscreenButtonIsShownOnAPortraitPixel7() {
        // Pixel 7 portrait: 411 x 914 dp, Mobile profile.
        assertTrue(PlayerControlsLayout.of(DeviceProfile.Mobile, 411, 914).showFullscreenToggle)
        // Turned sideways it resolves to the Tablet profile: still shown.
        assertTrue(PlayerControlsLayout.of(DeviceProfile.Tablet, 914, 411).showFullscreenToggle)
    }

    @Test
    fun portraitButtonEntersLandscapeFullscreen() {
        val start = PlayerWindowState()
        assertFalse(immersive(start, landscape = false))

        val entered = toggle(start, landscape = false)
        assertEquals(PlayerWindowState(userFullscreen = true, orientation = PlayerOrientationRequest.Landscape), entered)
        // Immersive at once, and still after the rotation lands.
        assertTrue(immersive(entered, landscape = false))
        assertTrue(immersive(entered, landscape = true))
    }

    @Test
    fun leavingFullscreenInLandscapeReturnsToPortrait() {
        val entered = PlayerWindowState(userFullscreen = true, orientation = PlayerOrientationRequest.Landscape)
        val left = toggle(entered, landscape = true)

        assertEquals(PlayerWindowState(userFullscreen = false, orientation = PlayerOrientationRequest.Portrait), left)
        // Bars come back immediately, before the rotation completes.
        assertFalse(immersive(left, landscape = true))
        assertFalse(immersive(left, landscape = false))
    }

    @Test
    fun turningThePhoneSidewaysIsFullscreenAndTheButtonLeavesIt() {
        val start = PlayerWindowState()
        assertTrue("Rotating a phone shows the video full screen", immersive(start, landscape = true))

        val left = toggle(start, landscape = true)
        assertEquals(PlayerOrientationRequest.Portrait, left.orientation)
        assertFalse(immersive(left, landscape = true))

        // And the button enters it again.
        assertTrue(immersive(toggle(left, landscape = false), landscape = false))
    }

    @Test
    fun tabletRotationAloneIsNotFullscreen() {
        assertFalse(immersive(PlayerWindowState(), landscape = true, compact = false))
        assertTrue(immersive(toggle(PlayerWindowState(), landscape = true, compact = false), landscape = true, compact = false))
    }

    @Test
    fun televisionWindowIsNeverChanged() {
        val state = PlayerWindowState()
        assertFalse(immersive(state, landscape = true, compact = false, tv = true))
        assertEquals(state, toggle(state, landscape = true, compact = false, tv = true))
    }

    @Test
    fun backLeavesFullscreenBeforeLeavingThePlayer() {
        assertEquals(PlayerBackAction.ExitFullscreen, PlayerBackPolicy.onBack(PlayerPanel.None, immersive = true))
        assertEquals(PlayerBackAction.ExitPlayer, PlayerBackPolicy.onBack(PlayerPanel.None, immersive = false))
        // An open panel still closes first, even in full screen.
        assertEquals(PlayerBackAction.ClosePanel, PlayerBackPolicy.onBack(PlayerPanel.Tracks, immersive = true))
    }

    @Test
    fun backSequenceFromFullscreenReachesTheCatalogueExactlyOnce() {
        var state = PlayerWindowState(userFullscreen = true, orientation = PlayerOrientationRequest.Landscape)
        var landscape = true
        var exits = 0
        repeat(2) {
            when (PlayerBackPolicy.onBack(PlayerPanel.None, immersive(state, landscape))) {
                PlayerBackAction.ExitFullscreen -> {
                    state = toggle(state, landscape)
                    landscape = false
                }
                PlayerBackAction.ExitPlayer -> exits++
                else -> Unit
            }
        }
        assertEquals(1, exits)
        assertFalse(state.userFullscreen)
        // The route pops once; a late second request finds another screen.
        assertTrue(PlayerExitNavigation.shouldPop("player", "player"))
        assertFalse(PlayerExitNavigation.shouldPop("live", "player"))
    }
}

