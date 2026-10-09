package fr.zyviotv.player.data.catalog

import fr.zyviotv.player.shared.catalog.CatalogCategory
import fr.zyviotv.player.shared.catalog.CatalogLiveChannel
import fr.zyviotv.player.shared.catalog.CatalogMovie
import fr.zyviotv.player.shared.catalog.CatalogSeries
import fr.zyviotv.player.shared.catalog.CatalogSnapshot
import fr.zyviotv.player.shared.m3u.M3uEntry

object M3uCatalogMapper {
    private data class EpisodeIdentity(
        val seriesTitle: String,
        val season: Int,
        val episode: Int,
        val episodeTitle: String?,
    )

    private enum class Kind { Live, Movie, Episode }

    fun map(entries: List<M3uEntry>): CatalogSnapshot = builder().run {
        entries.forEach(::add)
        build()
    }

    fun builder(): Builder = Builder()

    class Builder internal constructor() {
        private val liveCategories = linkedMapOf<String, CatalogCategory>()
        private val movieCategories = linkedMapOf<String, CatalogCategory>()
        private val seriesCategories = linkedMapOf<String, CatalogCategory>()
        private val liveChannels = mutableListOf<CatalogLiveChannel>()
        private val movies = mutableListOf<CatalogMovie>()
        private val seriesBuilders = linkedMapOf<String, MutableSeries>()
        private val usedLiveIds = mutableSetOf<String>()
        private var inputIndex = 0

        fun add(entry: M3uEntry) {
            val index = inputIndex++
            val streamUrl = entry.streamUrl.trim()
            if (streamUrl.isBlank()) return
            val group = normalizedGroup(entry.groupTitle)
            val episodeIdentity = parseEpisode(entry.name)

            when (classify(entry, episodeIdentity)) {
                Kind.Live -> {
                    val categoryId = group?.let { ensureCategory(liveCategories, "m3u-live", it).id }
                    val baseId = entry.tvgId?.trim()?.takeIf(String::isNotBlank)
                        ?: stableId("live|$index|${entry.name}|$streamUrl")
                    var id = baseId
                    var duplicateIndex = 2
                    while (!usedLiveIds.add(id)) {
                        id = "$baseId-$duplicateIndex"
                        duplicateIndex += 1
                    }
                    liveChannels += CatalogLiveChannel(
                        id = id,
                        name = entry.name.trim(),
                        categoryId = categoryId,
                        logoUrl = entry.logoUrl?.trim()?.takeIf(String::isNotBlank),
                        streamUrl = streamUrl,
                        epgId = entry.tvgId?.trim()?.takeIf(String::isNotBlank),
                    )
                }

                Kind.Movie -> {
                    val categoryId = group?.let { ensureCategory(movieCategories, "m3u-movie", it).id }
                    movies += CatalogMovie(
                        id = stableId("movie|${entry.name}|${group.orEmpty()}|$streamUrl"),
                        title = entry.name.trim(),
                        categoryId = categoryId,
                        posterUrl = entry.logoUrl?.trim()?.takeIf(String::isNotBlank),
                        streamUrl = streamUrl,
                        containerExtension = streamUrl.substringAfterLast('.', "")
                            .substringBefore('?')
                            .takeIf { it.length in 2..5 }
                            ?: "mp4",
                    )
                }

                Kind.Episode -> {
                    val identity = episodeIdentity ?: return
                    val seriesKey = identity.seriesTitle.lowercase()
                    val categoryId = group?.let { ensureCategory(seriesCategories, "m3u-series", it).id }
                    val builder = seriesBuilders.getOrPut(seriesKey) {
                        MutableSeries(
                            id = stableId("series|$seriesKey|${group.orEmpty()}"),
                            title = identity.seriesTitle,
                            categoryId = categoryId,
                            posterUrl = entry.logoUrl?.trim()?.takeIf(String::isNotBlank),
                        )
                    }
                    val episodeNumber = identity.episode.coerceAtLeast(1)
                    builder.episodes += SeriesEpisodeSource(
                        id = stableId("episode|${builder.id}|${identity.season}|$episodeNumber|$streamUrl"),
                        season = identity.season.coerceAtLeast(1),
                        number = episodeNumber,
                        title = identity.episodeTitle?.takeIf(String::isNotBlank)
                            ?: "Épisode $episodeNumber",
                        synopsis = null,
                        streamUrl = streamUrl,
                    )
                }
            }
        }

        fun build(): CatalogSnapshot {
            val series = seriesBuilders.values.map { builder ->
                CatalogSeries(
                    id = builder.id,
                    title = builder.title,
                    categoryId = builder.categoryId,
                    posterUrl = builder.posterUrl,
                )
            }

            M3uSeriesDetailRegistry.replace(
                seriesBuilders.values.associate { builder ->
                    builder.id to SeriesDetailSource(
                        title = builder.title,
                        year = null,
                        synopsis = null,
                        genres = emptyList(),
                        episodes = builder.episodes.sortedWith(
                            compareBy<SeriesEpisodeSource> { it.season }.thenBy { it.number },
                        ),
                    )
                },
            )

            return CatalogSnapshot(
                liveCategories = liveCategories.values.toList(),
                liveChannels = liveChannels,
                movieCategories = movieCategories.values.toList(),
                movies = movies,
                seriesCategories = seriesCategories.values.toList(),
                series = series,
            )
        }
    }

    private fun classify(entry: M3uEntry, episode: EpisodeIdentity?): Kind {
        if (episode != null) return Kind.Episode
        val group = normalize(entry.groupTitle)
        if (MOVIE_TOKENS.any(group::contains)) return Kind.Movie
        if (LIVE_TOKENS.any(group::contains)) return Kind.Live

        val url = entry.streamUrl.lowercase()
        if ("/movie/" in url || "/vod/" in url) return Kind.Movie
        if ("/live/" in url) return Kind.Live

        val extension = url.substringBefore('?').substringAfterLast('.', "")
        if (extension in MOVIE_EXTENSIONS) return Kind.Movie
        return Kind.Live
    }

    private fun parseEpisode(value: String): EpisodeIdentity? {
        val original = value.trim()
        for (pattern in EPISODE_PATTERNS) {
            val match = pattern.find(original) ?: continue
            val title = normalizeTitle(match.groupValues[1])
            if (title.isBlank()) continue
            return EpisodeIdentity(
                seriesTitle = title,
                season = match.groupValues[2].toIntOrNull() ?: 1,
                episode = match.groupValues[3].toIntOrNull() ?: 1,
                episodeTitle = normalizeTitle(match.groupValues[4]).takeIf(String::isNotBlank),
            )
        }
        return null
    }

    private fun normalizeTitle(value: String): String = value
        .trim()
        .trimStart(' ', '.', '_', '-')
        .replace('.', ' ')
        .replace('_', ' ')
        .replace(WHITESPACE_PATTERN, " ")
        .trim()

    private fun ensureCategory(
        target: LinkedHashMap<String, CatalogCategory>,
        prefix: String,
        name: String,
    ): CatalogCategory {
        val key = name.lowercase()
        return target.getOrPut(key) {
            CatalogCategory(id = "$prefix-${stableId(key)}", name = name)
        }
    }

    private fun normalizedGroup(value: String?): String? = value
        ?.trim()
        ?.replace(WHITESPACE_PATTERN, " ")
        ?.takeIf(String::isNotBlank)

    private fun normalize(value: String?): String = value
        .orEmpty()
        .lowercase()
        .replace('é', 'e')
        .replace('è', 'e')
        .replace('ê', 'e')
        .replace('à', 'a')
        .replace('â', 'a')
        .replace('î', 'i')
        .replace('ï', 'i')
        .replace('ô', 'o')
        .replace('ù', 'u')
        .replace('û', 'u')

    private fun stableId(value: String): String =
        "m3u-" + value.hashCode().toUInt().toString(16)

    private data class MutableSeries(
        val id: String,
        val title: String,
        val categoryId: String?,
        val posterUrl: String?,
        val episodes: MutableList<SeriesEpisodeSource> = mutableListOf(),
    )

    private val EPISODE_PATTERNS = listOf(
        Regex("""^(.*?)[\s._-]+s(\d{1,2})[\s._-]*e(\d{1,3})(?:\b|[\s._-])(.*)$""", RegexOption.IGNORE_CASE),
        Regex("""^(.*?)[\s._-]+(\d{1,2})x(\d{1,3})(?:\b|[\s._-])(.*)$""", RegexOption.IGNORE_CASE),
        Regex("""^(.*?)[\s._-]+saison[\s._-]*(\d{1,2})[\s._-]+(?:episode|ep)[\s._-]*(\d{1,3})(?:\b|[\s._-])(.*)$""", RegexOption.IGNORE_CASE),
    )
    private val WHITESPACE_PATTERN = Regex("""\s+""")
    private val MOVIE_EXTENSIONS = setOf("mp4", "mkv", "avi", "mov", "m4v", "webm")
    private val MOVIE_TOKENS = listOf("vod", "movie", "movies", "film", "films", "cinema", "cine")
    private val LIVE_TOKENS = listOf("live", "tv", "chaine", "channel", "sport", "news", "info", "radio")
}
