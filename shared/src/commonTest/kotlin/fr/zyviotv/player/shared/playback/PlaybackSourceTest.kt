package fr.zyviotv.player.shared.playback

import fr.zyviotv.player.shared.m3u.M3uParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlaybackSourceTest {
    @Test
    fun plainUrlIsKeptVerbatimIncludingAccessQuery() {
        val line = "http://provider.example:8080/live/stream.ts?token=abc&expires=99"
        val source = PlaybackSource.parse(line)

        assertEquals(line, source.url)
        assertTrue(source.headers.isEmpty())
    }

    @Test
    fun kodiPipeHeadersAreSplitFromTheUrl() {
        val source = PlaybackSource.parse(
            "https://provider.example/hls/42.m3u8?t=1|User-Agent=Mozilla%2F5.0%20(X11)&Referer=https%3A%2F%2Fprovider.example%2F",
        )

        assertEquals("https://provider.example/hls/42.m3u8?t=1", source.url)
        assertEquals("Mozilla/5.0 (X11)", source.headers["User-Agent"])
        assertEquals("https://provider.example/", source.headers["Referer"])
    }

    @Test
    fun unsupportedOrInjectedHeadersAreIgnored() {
        val source = PlaybackSource.parse(
            "https://provider.example/a.ts|Authorization=secret&User-Agent=ok%0D%0AX-Evil: 1",
        )

        // Nothing usable after the pipe: the whole line stays the URL.
        assertEquals("https://provider.example/a.ts|Authorization=secret&User-Agent=ok%0D%0AX-Evil: 1", source.url)
        assertTrue(source.headers.isEmpty())
    }

    @Test
    fun composeRoundTripsThroughParse() {
        val headers = mapOf("User-Agent" to "VLC/3.0.20 LibVLC/3.0.20", "Referer" to "https://provider.example/?a=1&b=2")
        val line = PlaybackSource.compose("https://provider.example/movie/7.mkv", headers)
        val source = PlaybackSource.parse(line)

        assertEquals("https://provider.example/movie/7.mkv", source.url)
        assertEquals(headers, source.headers)
    }

    @Test
    fun composeKeepsAnExistingPipeLine() {
        val line = "https://provider.example/a.ts|User-Agent=Kodi"
        assertEquals(line, PlaybackSource.compose(line, mapOf("User-Agent" to "VLC")))
    }

    @Test
    fun parserFoldsExtVlcOptHeadersIntoTheStreamLine() {
        val entries = M3uParser.parse(
            """
            #EXTM3U
            #EXTINF:-1 group-title="Sport",Chaîne Sport
            #EXTVLCOPT:http-user-agent=VLC/3.0.20 LibVLC/3.0.20
            #EXTVLCOPT:http-referrer=https://provider.example/
            http://provider.example:8080/live/1.ts
            #EXTINF:-1 group-title="Info",Chaîne Info
            http://provider.example:8080/live/2.ts
            """.trimIndent(),
        )

        val first = PlaybackSource.parse(entries[0].streamUrl)
        assertEquals("http://provider.example:8080/live/1.ts", first.url)
        assertEquals("VLC/3.0.20 LibVLC/3.0.20", first.headers["User-Agent"])
        assertEquals("https://provider.example/", first.headers["Referer"])
        // Options never leak to the next entry.
        assertEquals("http://provider.example:8080/live/2.ts", entries[1].streamUrl)
    }

    @Test
    fun containerDetectionAndValidationUseTheUrlPart() {
        val request = PlaybackRequest(
            title = "Film",
            streamUrl = "https://provider.example/vod/7.m3u8|User-Agent=VLC",
            kind = PlaybackKind.Movie,
        )

        assertEquals(PlaybackMediaType.Hls, PlaybackMediaTypeResolver.resolve(request))
        assertEquals(PlaybackValidationResult.Valid, PlaybackValidator.validate(request))
    }
}
