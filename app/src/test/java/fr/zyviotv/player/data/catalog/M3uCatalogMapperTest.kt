package fr.zyviotv.player.data.catalog

import fr.zyviotv.player.shared.m3u.M3uEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class M3uCatalogMapperTest {
    @Test
    fun streamingBuilderMapsMoreThanFiftyThousandEntriesCompletely() {
        val builder = M3uCatalogMapper.builder()
        repeat(30_000) { index ->
            builder.add(
                M3uEntry(
                    name = "Chaîne $index",
                    streamUrl = "https://media.invalid/channels/$index.ts",
                    tvgId = "channel-$index",
                    groupTitle = "TV ${(index % 20) + 1}",
                ),
            )
        }
        repeat(15_000) { index ->
            builder.add(
                M3uEntry(
                    name = "Film $index",
                    streamUrl = "https://media.invalid/films/$index.mkv",
                    groupTitle = "FILMS ${(index % 20) + 1}",
                ),
            )
        }
        repeat(10_000) { index ->
            val series = index % 100
            val episode = (index / 100) + 1
            builder.add(
                M3uEntry(
                    name = "Série $series S01E${episode.toString().padStart(3, '0')} Épisode $episode",
                    streamUrl = "https://media.invalid/episodes/$index.mkv",
                    groupTitle = "SERIES ${(series % 10) + 1}",
                ),
            )
        }

        val snapshot = builder.build()

        assertEquals(30_000, snapshot.liveChannels.size)
        assertEquals(15_000, snapshot.movies.size)
        assertEquals(100, snapshot.series.size)
        assertEquals(
            10_000,
            snapshot.series.sumOf { series ->
                M3uSeriesDetailRegistry.load(series.id)?.episodes?.size ?: 0
            },
        )
    }

    @Test
    fun duplicateTvgIdsReceiveStableUniqueIds() {
        val snapshot = M3uCatalogMapper.map(
            listOf(
                M3uEntry(
                    name = "Channel A",
                    streamUrl = "https://stream.example/a.ts",
                    tvgId = "duplicate",
                    groupTitle = "France",
                ),
                M3uEntry(
                    name = "Channel B",
                    streamUrl = "https://stream.example/b.ts",
                    tvgId = "duplicate",
                    groupTitle = "France",
                ),
            ),
        )

        assertEquals(2, snapshot.liveChannels.size)
        assertEquals("duplicate", snapshot.liveChannels[0].id)
        assertEquals("duplicate-2", snapshot.liveChannels[1].id)
        assertNotEquals(snapshot.liveChannels[0].id, snapshot.liveChannels[1].id)
    }

    @Test
    fun duplicateTvgIdsKeepOriginalEpgIdForXmlTvMatching() {
        val snapshot = M3uCatalogMapper.map(
            listOf(
                M3uEntry(
                    name = "One",
                    streamUrl = "https://stream.example/1.ts",
                    tvgId = "france2.fr",
                ),
                M3uEntry(
                    name = "Two",
                    streamUrl = "https://stream.example/2.ts",
                    tvgId = "france2.fr",
                ),
            ),
        )

        assertEquals("france2.fr", snapshot.liveChannels[0].epgId)
        assertEquals("france2.fr", snapshot.liveChannels[1].epgId)
        assertNotEquals(snapshot.liveChannels[0].id, snapshot.liveChannels[1].id)
    }

    @Test
    fun groupWhitespaceAndCaseVariantsCollapseIntoOneCategory() {
        val snapshot = M3uCatalogMapper.map(
            listOf(
                M3uEntry(
                    name = "One",
                    streamUrl = "https://stream.example/1.ts",
                    groupTitle = " FRANCE   SPORTS ",
                ),
                M3uEntry(
                    name = "Two",
                    streamUrl = "https://stream.example/2.ts",
                    groupTitle = "france sports",
                ),
            ),
        )

        assertEquals(1, snapshot.liveCategories.size)
        assertEquals("FRANCE SPORTS", snapshot.liveCategories.single().name)
        assertEquals(
            snapshot.liveChannels[0].categoryId,
            snapshot.liveChannels[1].categoryId,
        )
    }
    @Test
    fun fullM3uCatalogMapsLiveMoviesSeriesAndEpisodes() {
        val snapshot = M3uCatalogMapper.map(
            listOf(
                M3uEntry(
                    name = "Actu 24",
                    streamUrl = "https://stream.example/live/1.ts",
                    tvgId = "news.fr",
                    groupTitle = "FR - LIVE",
                ),
                M3uEntry(
                    name = "Film Exemple",
                    streamUrl = "https://stream.example/movie/2.mkv",
                    groupTitle = "FR - FILMS",
                    logoUrl = "https://img.example/movie.jpg",
                ),
                M3uEntry(
                    name = "Ma Serie S01E01 Pilote",
                    streamUrl = "https://stream.example/series/3.mkv",
                    groupTitle = "FR - SERIES",
                ),
                M3uEntry(
                    name = "Ma Serie S01E02 Suite",
                    streamUrl = "https://stream.example/series/4.mkv",
                    groupTitle = "FR - SERIES",
                ),
            ),
        )

        assertEquals(1, snapshot.liveChannels.size)
        assertEquals(1, snapshot.movies.size)
        assertEquals(1, snapshot.series.size)

        val series = snapshot.series.single()
        assertEquals("Ma Serie", series.title)

        val detail = M3uSeriesDetailRegistry.load(series.id)
        requireNotNull(detail)
        assertEquals(2, detail.episodes.size)
        assertEquals(1, detail.episodes[0].season)
        assertEquals(1, detail.episodes[0].number)
        assertEquals(2, detail.episodes[1].number)
        assertEquals("Pilote", detail.episodes[0].title)
        assertEquals("Suite", detail.episodes[1].title)
    }

    @Test
    fun alternateEpisodeNamingConventionsAreRecognized() {
        val snapshot = M3uCatalogMapper.map(
            listOf(
                M3uEntry(
                    name = "Autre Serie 2x03 Episode Trois",
                    streamUrl = "https://stream.example/series/a.mp4",
                    groupTitle = "TV SHOWS",
                ),
                M3uEntry(
                    name = "Troisieme Serie Saison 3 Episode 4 Finale",
                    streamUrl = "https://stream.example/series/b.mp4",
                    groupTitle = "SERIES",
                ),
            ),
        )

        assertEquals(2, snapshot.series.size)
        val details = snapshot.series.mapNotNull { M3uSeriesDetailRegistry.load(it.id) }
        assertEquals(2, details.size)
        assertEquals(setOf(2, 3), details.flatMap { it.episodes }.map { it.season }.toSet())
        assertEquals(setOf(3, 4), details.flatMap { it.episodes }.map { it.number }.toSet())
    }

    @Test
    fun theProviderUrlKindWinsOverGroupWords() {
        // Cases of the recetted playlist (synthetic URLs): group words said
        // the opposite of the provider's URL.
        val snapshot = M3uCatalogMapper.map(
            listOf(
                M3uEntry("Documentaire A", "http://provider.example/movie/acc/key/1.mkv", groupTitle = "DOCUMENTAIRES | EMISSION TV"),
                M3uEntry("Workout B", "http://provider.example/movie/acc/key/2.mp4", groupTitle = "WORKOUT | SPORTS"),
                M3uEntry("Alwan Cine", "http://provider.example/acc/key/3", groupTitle = "ALWAN SPORT | CULTE | CINE ( ARABIC )"),
                M3uEntry("FR Cinema", "http://provider.example/acc/key/4.ts", groupTitle = "FR TV CINEMA FHD"),
                // A live channel whose name looks like an episode stays live.
                M3uEntry("BH ARENA SPORT 1x2", "http://provider.example/acc/key/5", groupTitle = "BOSNIAQUE"),
                M3uEntry("Espion S01E01 Pilote", "http://provider.example/series/acc/key/6.mkv", groupTitle = "ESPIONNAGE ( APPLE TV+ )"),
            ),
        )

        assertEquals(listOf("Alwan Cine", "FR Cinema", "BH ARENA SPORT 1x2"), snapshot.liveChannels.map { it.name })
        assertEquals(listOf("Documentaire A", "Workout B"), snapshot.movies.map { it.title })
        assertEquals(listOf("Espion"), snapshot.series.map { it.title })
    }

    @Test
    fun liveAndMoviesAreCompleteOnlyAfterALongRunOfEpisodes() {
        val builder = M3uCatalogMapper.builder()
        builder.add(M3uEntry("Chaîne", "http://provider.example/acc/key/1", groupTitle = "FR"))
        builder.add(M3uEntry("Film", "http://provider.example/movie/acc/key/2.mkv", groupTitle = "FILMS"))
        assertEquals(false, builder.liveAndMoviesComplete)

        repeat(M3uCatalogMapper.EARLY_EPISODE_RUN - 1) { index ->
            builder.add(M3uEntry("Serie S01E${index % 900 + 1}", "http://provider.example/series/acc/key/$index.mkv"))
        }
        assertEquals(false, builder.liveAndMoviesComplete)
        builder.add(M3uEntry("Serie S02E01", "http://provider.example/series/acc/key/last.mkv"))
        assertEquals(true, builder.liveAndMoviesComplete)

        val early = builder.buildLiveAndMovies()
        assertEquals(1, early.liveChannels.size)
        assertEquals(1, early.movies.size)
        assertEquals(0, early.series.size)
        // The early films keep the ids of the final catalogue.
        assertEquals(early.movies.map { it.id }, builder.build().movies.map { it.id })
    }

    @Test
    fun aChannelAfterTheSeriesKeepsTheCompleteFileRule() {
        val builder = M3uCatalogMapper.builder()
        builder.add(M3uEntry("Film", "http://provider.example/movie/acc/key/1.mkv", groupTitle = "FILMS"))
        repeat(M3uCatalogMapper.EARLY_EPISODE_RUN) { index ->
            builder.add(M3uEntry("Serie S01E${index % 900 + 1}", "http://provider.example/series/acc/key/$index.mkv"))
            if (index == 10) builder.add(M3uEntry("Chaîne tardive", "http://provider.example/acc/key/99999", groupTitle = "FR"))
        }

        assertEquals(1, builder.orderViolations)
        assertEquals(false, builder.liveAndMoviesComplete)
    }

    @Test
    fun unnamedSeriesEntriesDoNotFabricateEpisodes() {
        val snapshot = M3uCatalogMapper.map(
            listOf(
                M3uEntry(
                    name = "Série sans numéro",
                    streamUrl = "https://stream.example/series/unknown.mkv",
                    groupTitle = "FR - SERIES",
                ),
                M3uEntry(
                    name = "Série identifiée S01E02",
                    streamUrl = "https://stream.example/series/real.mkv",
                    groupTitle = "FR - SERIES",
                ),
            ),
        )
        assertEquals(1, snapshot.series.size)
        val detail = M3uSeriesDetailRegistry.load(snapshot.series.single().id)
        requireNotNull(detail)
        assertEquals(1, detail.episodes.size)
        assertEquals(2, detail.episodes.single().number)
    }

}
