package fr.zyviotv.player.data.catalog

import fr.zyviotv.player.shared.m3u.M3uEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class M3uIdResolverTest {
    // "Aa" and "BB" share String.hashCode, so these titles produce the same
    // 32-bit legacy id when group and URL are identical.
    private fun movie(title: String, url: String = "https://media.invalid/films/shared.mkv") = M3uEntry(
        name = title,
        streamUrl = url,
        groupTitle = "FILMS",
    )

    private fun mapMovies(entries: List<M3uEntry>, incumbents: Map<String, Long> = emptyMap()) =
        M3uCatalogMapper.builder(incumbents).run {
            entries.forEach(::add)
            build() to idAliases
        }

    @Test
    fun uniqueLegacyIdsAreKeptUnchanged() {
        val (snapshot, aliases) = mapMovies(
            listOf(movie("Film un", "https://media.invalid/1.mkv"), movie("Film deux", "https://media.invalid/2.mkv")),
        )
        assertTrue(snapshot.movies.all { it.id.matches(Regex("m3u-[0-9a-f]{1,8}")) })
        assertTrue(aliases.isEmpty())
    }

    @Test
    fun collidingMoviesGetUniqueIdsAndOneKeepsTheLegacyId() {
        val (snapshot, aliases) = mapMovies(listOf(movie("Aa"), movie("BB")))
        val ids = snapshot.movies.map { it.id }
        assertEquals(2, ids.toSet().size)
        assertEquals(1, aliases.size)
        val legacyId = aliases.keys.single()
        assertTrue(legacyId in ids)
        assertTrue(ids.any { it.matches(Regex("m3u-[0-9a-f]{16}")) })
    }

    @Test
    fun resolutionDoesNotDependOnPlaylistOrder() {
        val forward = mapMovies(listOf(movie("Aa"), movie("BB"), movie("Film", "https://media.invalid/3.mkv")))
        val reversed = mapMovies(listOf(movie("Film", "https://media.invalid/3.mkv"), movie("BB"), movie("Aa")))
        assertEquals(
            forward.first.movies.associate { it.title to it.id },
            reversed.first.movies.associate { it.title to it.id },
        )
        assertEquals(forward.second, reversed.second)
    }

    @Test
    fun reimportKeepsIdsStable() {
        val first = mapMovies(listOf(movie("Aa"), movie("BB")))
        val second = mapMovies(listOf(movie("BB"), movie("Aa")), incumbents = first.second)
        assertEquals(
            first.first.movies.associate { it.title to it.id },
            second.first.movies.associate { it.title to it.id },
        )
    }

    @Test
    fun aliasTableKeepsTheLegacyIdOnTheEntryThatHeldIt() {
        // Only "BB" existed: it held the legacy id (and its favourites).
        val before = mapMovies(listOf(movie("BB")))
        val legacyId = before.first.movies.single().id
        val holderFingerprint = M3uIdResolver.fingerprint("movie", "BB", before.first.movies.single().categoryId, "https://media.invalid/films/shared.mkv")
        // A new colliding entry appears; whichever fingerprint is smaller, the
        // alias table recorded at the first collision keeps "BB" on its id.
        val after = mapMovies(listOf(movie("Aa"), movie("BB")), incumbents = mapOf(legacyId to holderFingerprint))
        assertEquals(legacyId, after.first.movies.single { it.title == "BB" }.id)
        assertNotEquals(legacyId, after.first.movies.single { it.title == "Aa" }.id)
        assertEquals(holderFingerprint, after.second[legacyId])
    }

    @Test
    fun exactDuplicatesReceiveDistinctSecondaryIds() {
        val (snapshot, _) = mapMovies(listOf(movie("Aa"), movie("Aa"), movie("Aa")))
        val ids = snapshot.movies.map { it.id }
        assertEquals(3, ids.toSet().size)
        assertTrue(ids.any { it.endsWith("-2") })
    }

    @Test
    fun collidingSeriesKeepSeparateEpisodeIndexes() {
        val snapshot = M3uCatalogMapper.map(
            listOf(
                M3uEntry(name = "Aa S01E01", streamUrl = "https://media.invalid/a1.mkv", groupTitle = "SERIES"),
                M3uEntry(name = "BB S01E01", streamUrl = "https://media.invalid/b1.mkv", groupTitle = "SERIES"),
                M3uEntry(name = "BB S01E02", streamUrl = "https://media.invalid/b2.mkv", groupTitle = "SERIES"),
            ),
        )
        assertEquals(2, snapshot.series.map { it.id }.toSet().size)
        val episodes = snapshot.series.associate { it.title to (M3uSeriesDetailRegistry.load(it.id)?.episodes?.size ?: 0) }
        assertEquals(mapOf("Aa" to 1, "BB" to 2), episodes)
    }

    @Test
    fun sharedIdGroupsFindEveryCollisionInLargeLists() {
        val ids = List(150_000) { "id-$it" } + listOf("id-7", "id-149999", "id-7")
        val groups = M3uIdResolver.sharedIdGroups(ids)
        assertEquals(listOf(listOf(7, 150_000, 150_002), listOf(149_999, 150_001)), groups)
    }

    @Test
    fun secondaryIdsNeverUseTheLegacyFormat() {
        assertEquals("m3u-000000000000002a", M3uIdResolver.secondaryId(42L, 1))
        assertEquals("m3u-000000000000002a-3", M3uIdResolver.secondaryId(42L, 3))
        assertNotEquals(M3uIdResolver.fingerprint("ab", "c"), M3uIdResolver.fingerprint("a", "bc"))
    }

    @Test
    fun volumeCatalogMapsAllEntriesWithUniqueIds() {
        val builder = M3uCatalogMapper.builder()
        repeat(13_000) { index ->
            builder.add(M3uEntry(name = "Film $index", streamUrl = "https://media.invalid/f/$index.mkv", groupTitle = "FILMS"))
        }
        repeat(120_000) { index ->
            val series = index % 4_000
            val episode = index / 4_000 + 1
            builder.add(
                M3uEntry(
                    name = "Serie $series S01E${episode.toString().padStart(2, '0')}",
                    streamUrl = "https://media.invalid/e/$index.mkv",
                    groupTitle = "SERIES",
                ),
            )
        }
        val snapshot = builder.build()
        assertEquals(13_000, snapshot.movies.map { it.id }.toSet().size)
        assertEquals(4_000, snapshot.series.map { it.id }.toSet().size)
        val episodeIds = snapshot.series.flatMap { M3uSeriesDetailRegistry.load(it.id)!!.episodes.map { e -> e.id } }
        assertEquals(120_000, episodeIds.size)
        assertEquals(120_000, episodeIds.toSet().size)
    }
}
