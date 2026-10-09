package fr.zyviotv.player.data.cache

import fr.zyviotv.player.data.catalog.AndroidSeriesDetailLoader
import fr.zyviotv.player.data.catalog.M3uCatalogMapper
import fr.zyviotv.player.data.catalog.M3uSeriesDetailRegistry
import fr.zyviotv.player.data.catalog.SeriesDetailLoadResult
import fr.zyviotv.player.data.network.NetworkAvailability
import fr.zyviotv.player.shared.catalog.CatalogLiveChannel
import fr.zyviotv.player.shared.catalog.CatalogMovie
import fr.zyviotv.player.shared.catalog.CatalogSnapshot
import fr.zyviotv.player.shared.m3u.M3uParser
import fr.zyviotv.player.shared.playback.PlaybackKind
import fr.zyviotv.player.shared.playback.PlaybackRequest
import fr.zyviotv.player.shared.playback.PlaybackSource
import fr.zyviotv.player.ui.player.PlaybackBlockReason
import fr.zyviotv.player.ui.player.PlaybackLaunchDecision
import fr.zyviotv.player.ui.player.PlaybackLaunchPolicy
import java.io.File
import java.nio.file.Files
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Bloc #208: the source of a Live channel, a film and an episode must travel
 * unchanged from the M3U line to the request handed to Media3, through the
 * encrypted/compressed cache and an app restart.
 */
class M3uPlaybackSourcePipelineTest {
    private val directory: File = Files.createTempDirectory("zyvio-catalog-test").toFile()
    private val key: SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val store = EncryptedCatalogFile { key }
    private val cacheFile = File(directory, "profile.catalog")

    @After
    fun cleanUp() {
        M3uSeriesDetailRegistry.clear()
        directory.deleteRecursively()
    }

    @Test
    fun liveMovieAndEpisodeSourcesSurviveCacheAndRestart() = runBlocking {
        val fresh = importPlaylist()
        assertTrue(fresh.isPlayable)
        assertEquals(3, fresh.sourceReport.liveTotal)
        assertEquals(2, fresh.sourceReport.moviesTotal)
        assertEquals(3, fresh.sourceReport.episodesTotal)
        assertEquals(0, fresh.sourceReport.missingSources)

        store.write(cacheFile, fresh)
        // App restart: nothing survives in memory but the cache file.
        M3uSeriesDetailRegistry.clear()
        val restored = requireNotNull(store.read(cacheFile))
        M3uSeriesDetailRegistry.replace(restored.seriesDetails)

        assertEquals(fresh.snapshot, restored.snapshot)
        assertEquals(fresh.seriesDetails, restored.seriesDetails)
        assertTrue(restored.isPlayable)

        // Live: tap on a channel restored from the cache.
        val channel = restored.snapshot.liveChannels.single { it.name == "Chaîne Sport" }
        val live = launch(channel.streamUrl, PlaybackKind.Live)
        val liveSource = PlaybackSource.parse(live.streamUrl)
        assertEquals(SPORT_URL, liveSource.url)
        assertEquals("VLC/3.0.20 LibVLC/3.0.20", liveSource.headers["User-Agent"])
        assertEquals(INFO_URL, launch(channelNamed(restored.snapshot, "Chaîne Info").streamUrl, PlaybackKind.Live).streamUrl)

        // Film: tokenised query and Kodi-style headers are preserved.
        val movie = restored.snapshot.movies.single { it.title == "Film Exemple" }
        val movieRequest = launch(movie.streamUrl, PlaybackKind.Movie)
        val movieSource = PlaybackSource.parse(movieRequest.streamUrl)
        assertEquals(MOVIE_URL, movieSource.url)
        assertEquals("https://provider.example/", movieSource.headers["Referer"])
        assertEquals("mkv", movie.containerExtension)
        assertEquals(DOC_URL, launch(movieTitled(restored.snapshot, "Documentaire").streamUrl, PlaybackKind.Movie).streamUrl)

        // Episode: index read from the local registry, never from Supabase.
        val series = restored.snapshot.series.single { it.title == "Ma Série" }
        val detail = AndroidSeriesDetailLoader.loadPreferLocal(series.id) {
            fail("M3U episodes must not need the playlist secret")
            Result.success(null)
        }
        val episodes = (detail as SeriesDetailLoadResult.Success).detail.episodes
        assertEquals(listOf(1 to 1, 1 to 2, 2 to 1), episodes.map { it.season to it.number })
        val episode = episodes.single { it.season == 1 && it.number == 2 }
        assertEquals(EPISODE_S01E02_URL, launch(episode.streamUrl, PlaybackKind.Episode).streamUrl)
    }

    @Test
    fun freshSyncAndRestoredCacheExposeIdenticalSources() {
        val fresh = importPlaylist()
        store.write(cacheFile, fresh)
        val restored = requireNotNull(store.read(cacheFile))

        fun sources(catalog: CachedCatalog) =
            catalog.snapshot.liveChannels.map { it.id to it.streamUrl } +
                catalog.snapshot.movies.map { it.id to it.streamUrl } +
                catalog.seriesDetails.values.flatMap { detail -> detail.episodes.map { it.id to it.streamUrl } }

        assertEquals(sources(fresh), sources(restored))
        assertTrue(sources(restored).all { (_, url) -> url.startsWith("http") })
    }

    @Test
    fun legacyCatalogWithoutSourcesIsNeverPlayableAndExplainsWhy() {
        val legacy = CachedCatalog(
            playlistId = "playlist",
            playlistName = "Ancien cache",
            snapshot = CatalogSnapshot(
                liveChannels = listOf(CatalogLiveChannel("c1", "Chaîne", null, null, streamUrl = "", epgId = null)),
                movies = listOf(CatalogMovie("m1", "Film", null, null, streamUrl = "", containerExtension = "mp4")),
            ),
            origin = CatalogCacheOrigin.LegacyWithoutSources,
        )

        assertFalse(legacy.isPlayable)
        assertEquals(2, legacy.sourceReport.missingSources)

        val decision = PlaybackLaunchPolicy.decide(
            request = PlaybackRequest("Chaîne", "", PlaybackKind.Live),
            network = NetworkAvailability.Available,
            catalogSourcesPending = !legacy.isPlayable,
        )
        decision as PlaybackLaunchDecision.Blocked
        assertEquals(PlaybackBlockReason.SourcesPending, decision.reason)
        assertEquals(PlaybackLaunchPolicy.SOURCES_PENDING_MESSAGE, decision.message)
    }

    @Test
    fun catalogWithAnyMissingSourceIsNotPlayable() {
        val fresh = importPlaylist()
        val broken = fresh.copy(
            snapshot = fresh.snapshot.copy(
                movies = fresh.snapshot.movies.mapIndexed { index, movie ->
                    if (index == 0) movie.copy(streamUrl = " ") else movie
                },
            ),
        )

        assertFalse(broken.isPlayable)
        assertEquals(1, broken.sourceReport.missingSources)
    }

    @Test
    fun missingCacheFileReadsAsAbsent() {
        assertNull(store.read(cacheFile))
    }

    @Test
    fun tamperedOrForeignCacheIsRejectedInsteadOfRestored() {
        store.write(cacheFile, importPlaylist())
        val bytes = cacheFile.readBytes()
        bytes[bytes.size - 5] = (bytes[bytes.size - 5].toInt() xor 0x55).toByte()
        File(directory, "tampered.catalog").writeBytes(bytes)

        assertThrows { store.read(File(directory, "tampered.catalog")) }

        val otherKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        assertThrows { EncryptedCatalogFile { otherKey }.read(cacheFile) }
    }

    @Test
    fun atomicReplaceKeepsTheNewestValidatedCatalog() {
        val first = importPlaylist()
        store.write(cacheFile, first)
        val second = first.copy(playlistName = "Actualisée")
        store.write(cacheFile, second)

        assertEquals("Actualisée", store.read(cacheFile)?.playlistName)
        assertFalse(File(directory, "profile.catalog.tmp").exists())
        assertFalse(File(directory, "profile.catalog.bak").exists())
    }

    private fun importPlaylist(): CachedCatalog {
        M3uSeriesDetailRegistry.clear()
        val builder = M3uCatalogMapper.builder()
        val report = M3uParser.parseLinesDetailed(PLAYLIST.lineSequence(), onEntry = builder::add)
        assertEquals(8, report.emitted)
        val snapshot = builder.build()
        return CachedCatalog(
            playlistId = "playlist",
            playlistName = "Playlist M3U",
            snapshot = snapshot,
            seriesDetails = M3uSeriesDetailRegistry.snapshot(),
        )
    }

    private fun launch(streamUrl: String, kind: PlaybackKind): PlaybackRequest {
        val decision = PlaybackLaunchPolicy.decide(
            request = PlaybackRequest(title = "Titre", streamUrl = streamUrl, kind = kind),
            network = NetworkAvailability.Available,
        )
        return (decision as PlaybackLaunchDecision.Launch).request
    }

    private fun channelNamed(snapshot: CatalogSnapshot, name: String) =
        snapshot.liveChannels.single { it.name == name }

    private fun movieTitled(snapshot: CatalogSnapshot, title: String) =
        snapshot.movies.single { it.title == title }

    private fun assertThrows(block: () -> Unit) {
        try {
            block()
        } catch (expected: Exception) {
            return
        }
        fail("Expected the cache read to fail")
    }

    private companion object {
        const val SPORT_URL = "http://provider.example:8080/live/demo/demo/101.ts"
        const val INFO_URL = "http://provider.example:8080/live/demo/demo/102.m3u8"
        const val MOVIE_URL = "https://provider.example/movie/demo/demo/201.mkv?token=a1b2&exp=1700000000"
        const val DOC_URL = "http://provider.example/get.php?type=vod&id=202&output=mp4"
        const val EPISODE_S01E02_URL = "http://provider.example:8080/series/demo/demo/302.mkv"

        val PLAYLIST = """
            #EXTM3U url-tvg="https://provider.example/epg.xml"
            #EXTINF:-1 tvg-id="sport.fr" tvg-logo="https://provider.example/sport.png" group-title="FR | Sport",Chaîne Sport
            #EXTVLCOPT:http-user-agent=VLC/3.0.20 LibVLC/3.0.20
            $SPORT_URL
            #EXTINF:-1 tvg-id="info.fr" group-title="FR | Info",Chaîne Info
            $INFO_URL
            #EXTINF:-1 group-title="FR | Radio",Radio
            http://provider.example:8080/live/demo/demo/103.ts
            #EXTINF:-1 tvg-logo="https://provider.example/film.jpg" group-title="VOD | Films",Film Exemple
            $MOVIE_URL|Referer=https%3A%2F%2Fprovider.example%2F
            #EXTINF:-1 group-title="VOD | Documentaires",Documentaire
            $DOC_URL
            #EXTINF:-1 group-title="Séries | Drame",Ma Série S01 E01
            http://provider.example:8080/series/demo/demo/301.mkv
            #EXTINF:-1 group-title="Séries | Drame",Ma Série S01 E02
            $EPISODE_S01E02_URL
            #EXTINF:-1 group-title="Séries | Drame",Ma Série S02 E01
            http://provider.example:8080/series/demo/demo/303.mkv
        """.trimIndent()
    }
}
