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

class PlaybackMediaAttemptsTest {
    private fun request(url: String, kind: PlaybackKind = PlaybackKind.Live) =
        PlaybackRequest(title = "Test", streamUrl = url, kind = kind)

    @Test
    fun queryOutputHintSelectsHls() {
        assertEquals(
            PlaybackMediaType.Hls,
            PlaybackMediaTypeResolver.resolve(
                request("http://provider.example/get.php?username=u&password=p&output=m3u8"),
            ),
        )
        assertEquals(
            PlaybackMediaType.Hls,
            PlaybackMediaTypeResolver.resolve(request("http://provider.example/play?type=hls")),
        )
    }

    @Test
    fun unrelatedQueryValuesDoNotSelectHls() {
        assertEquals(
            PlaybackMediaType.Progressive,
            PlaybackMediaTypeResolver.resolve(
                request("https://provider.example/movie/1.mp4?title=m3u8-guide", PlaybackKind.Movie),
            ),
        )
    }

    @Test
    fun extensionlessLiveFallsBackToHls() {
        assertEquals(
            listOf(PlaybackMediaType.TransportStream, PlaybackMediaType.Hls),
            PlaybackMediaTypeResolver.attempts(request("http://provider.example/u/p/42")),
        )
    }

    @Test
    fun unknownVodFallsBackToHls() {
        assertEquals(
            listOf(PlaybackMediaType.Unknown, PlaybackMediaType.Hls),
            PlaybackMediaTypeResolver.attempts(
                request("http://provider.example/movie/u/p/7", PlaybackKind.Movie),
            ),
        )
    }

    @Test
    fun explicitHlsAndProgressiveHaveSingleAttempt() {
        assertEquals(
            listOf(PlaybackMediaType.Hls),
            PlaybackMediaTypeResolver.attempts(request("https://provider.example/live/1.m3u8")),
        )
        assertEquals(
            listOf(PlaybackMediaType.Progressive),
            PlaybackMediaTypeResolver.attempts(
                request("https://provider.example/movie/1.mkv", PlaybackKind.Movie),
            ),
        )
    }
}
