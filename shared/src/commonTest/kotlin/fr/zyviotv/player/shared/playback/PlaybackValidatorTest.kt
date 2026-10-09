package fr.zyviotv.player.shared.playback

import kotlin.test.Test
import kotlin.test.assertIs

class PlaybackValidatorTest {
    @Test
    fun validHttpsStreamPasses() {
        assertIs<PlaybackValidationResult.Valid>(
            PlaybackValidator.validate(
                PlaybackRequest(
                    title = "Live",
                    streamUrl = "https://stream.example/live.m3u8",
                    kind = PlaybackKind.Live,
                ),
            ),
        )
    }

    @Test
    fun invalidSchemeFails() {
        assertIs<PlaybackValidationResult.Invalid>(
            PlaybackValidator.validate(
                PlaybackRequest(
                    title = "Invalid",
                    streamUrl = "file:///tmp/video.ts",
                    kind = PlaybackKind.Live,
                ),
            ),
        )
    }

    @Test
    fun cleartextProviderStreamPasses() {
        // PR #203: many IPTV providers only expose HTTP streams.
        assertIs<PlaybackValidationResult.Valid>(
            PlaybackValidator.validate(
                PlaybackRequest(
                    title = "Live",
                    streamUrl = "http://provider.example:8080/user/pass/42",
                    kind = PlaybackKind.Live,
                ),
            ),
        )
    }

    @Test
    fun blankStreamFails() {
        assertIs<PlaybackValidationResult.Invalid>(
            PlaybackValidator.validate(
                PlaybackRequest(title = "Live", streamUrl = "  ", kind = PlaybackKind.Live),
            ),
        )
    }
}
