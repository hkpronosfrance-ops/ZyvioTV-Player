package fr.zyviotv.player.data.store

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import fr.zyviotv.player.data.cache.CachedCatalog
import fr.zyviotv.player.data.catalog.SeriesDetailSource
import fr.zyviotv.player.data.catalog.SeriesEpisodeSource
import fr.zyviotv.player.shared.catalog.CatalogCategory
import fr.zyviotv.player.shared.catalog.CatalogLiveChannel
import fr.zyviotv.player.shared.catalog.CatalogMovie
import fr.zyviotv.player.shared.catalog.CatalogSeries
import fr.zyviotv.player.shared.catalog.CatalogSnapshot
import javax.crypto.KeyGenerator

/** Neutral synthetic catalogues: only `provider.example` hosts, no real titles. */
internal object CatalogStoreFixtures {
    const val PROFILE_KEY = "profile-key-test"

    fun inMemoryDatabase(): CatalogStoreDatabase =
        Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            CatalogStoreDatabase::class.java,
        ).allowMainThreadQueries().build()

    fun softwareKeyWrapper(): GenerationKeyWrapper {
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        return AesGcmKeyWrapper { key }
    }

    fun liveUrl(index: Int) = "http://provider.example/live/$index.ts"

    fun movieUrl(index: Int) = "http://provider.example/movie/$index.mkv"

    fun episodeUrl(series: Int, episode: Int) = "http://provider.example/series/$series/$episode.mkv"

    fun catalog(
        live: Int = 3,
        movies: Int = 4,
        series: Int = 2,
        episodesPerSeries: Int = 3,
        playlistId: String = "playlist-1",
    ): CachedCatalog = CachedCatalog(
        playlistId = playlistId,
        playlistName = "Playlist test",
        snapshot = CatalogSnapshot(
            liveCategories = listOf(CatalogCategory("lc1", "Infos"), CatalogCategory("lc2", "Sport")),
            liveChannels = List(live) { index ->
                CatalogLiveChannel(
                    id = "live-$index",
                    name = "${index + 1} - Chaîne $index",
                    categoryId = if (index % 2 == 0) "lc1" else "lc2",
                    logoUrl = null,
                    streamUrl = liveUrl(index),
                    epgId = "epg-$index",
                )
            },
            movieCategories = listOf(CatalogCategory("mc1", "Films")),
            movies = List(movies) { index ->
                CatalogMovie(
                    id = "movie-$index",
                    title = "Été $index",
                    categoryId = "mc1",
                    posterUrl = null,
                    streamUrl = movieUrl(index),
                    containerExtension = "mkv",
                    addedAtEpochSeconds = 1_700_000_000L + index,
                )
            },
            seriesCategories = listOf(CatalogCategory("sc1", "Séries")),
            series = List(series) { index ->
                CatalogSeries(
                    id = "series-$index",
                    title = "Série $index",
                    categoryId = "sc1",
                    posterUrl = null,
                    addedAtEpochSeconds = null,
                )
            },
        ),
        seriesDetails = (0 until series).associate { seriesIndex ->
            "series-$seriesIndex" to SeriesDetailSource(
                title = "Série $seriesIndex",
                year = "2026",
                synopsis = null,
                genres = listOf("Drame", "Comédie"),
                episodes = List(episodesPerSeries) { episode ->
                    SeriesEpisodeSource(
                        id = "ep-$episode",
                        season = 1 + episode / 10,
                        number = 1 + episode % 10,
                        title = "Épisode $episode",
                        synopsis = null,
                        streamUrl = episodeUrl(seriesIndex, episode),
                    )
                },
            )
        },
    )

    fun source(
        origin: GenerationOrigin = GenerationOrigin.Refresh,
        fetchedAtEpochMs: Long? = 1_000L,
        aliases: Map<String, Long> = emptyMap(),
        profileKey: String = PROFILE_KEY,
    ) = GenerationSource(
        profileKey = profileKey,
        scope = if (origin == GenerationOrigin.Refresh) GenerationScope.Raw else GenerationScope.ProfileFiltered,
        origin = origin,
        fetchedAtEpochMs = fetchedAtEpochMs,
        idAliases = aliases,
        v1Stamp = null,
    )
}
