package fr.zyviotv.player.shared.playback

import kotlin.test.Test
import kotlin.test.assertEquals

class PlaybackModelsTest {
    @Test
    fun detectsHlsIgnoringQueryCredentials() {
        val request = PlaybackRequest(
            title = "Live",
            streamUrl = "https://provider.example/live/channel.m3u8?token=secret",
            kind = PlaybackKind.Live,
        )

        assertEquals(PlaybackMediaType.Hls, PlaybackMediaTypeResolver.resolve(request))
    }

    @Test
    fun detectsXtreamTransportStream() {
        val request = PlaybackRequest(
            title = "Live",
            streamUrl = "https://provider.example/live/user/pass/42.ts",
            kind = PlaybackKind.Live,
        )

        assertEquals(PlaybackMediaType.TransportStream, PlaybackMediaTypeResolver.resolve(request))
    }

    @Test
    fun unknownLiveDefaultsToTransportStream() {
        val request = PlaybackRequest(
            title = "Live",
            streamUrl = "https://provider.example/live/user/pass/42",
            kind = PlaybackKind.Live,
        )

        assertEquals(PlaybackMediaType.TransportStream, PlaybackMediaTypeResolver.resolve(request))
    }

    @Test
    fun movieMp4IsProgressive() {
        val request = PlaybackRequest(
            title = "Movie",
            streamUrl = "https://provider.example/movie/user/pass/7.mp4",
            kind = PlaybackKind.Movie,
        )

        assertEquals(PlaybackMediaType.Progressive, PlaybackMediaTypeResolver.resolve(request))
    }
}
