package fr.zyviotv.player.data.catalog

import fr.zyviotv.player.shared.catalog.CatalogCategory
import fr.zyviotv.player.shared.catalog.CatalogLiveChannel
import fr.zyviotv.player.shared.catalog.CatalogMovie
import fr.zyviotv.player.shared.catalog.CatalogSeries
import fr.zyviotv.player.shared.catalog.CatalogSnapshot
import fr.zyviotv.player.shared.m3u.M3uEntry
import fr.zyviotv.player.shared.playback.PlaybackSource

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

    /**
     * @param idIncumbents alias table saved with the previous catalog: which
     *   entry keeps an id shared by several entries (see [M3uIdResolver]).
     */
    fun builder(idIncumbents: Map<String, Long> = emptyMap()): Builder = Builder(idIncumbents)

    class Builder internal constructor(
        private val idIncumbents: Map<String, Long>,
    ) {
        private val liveCategories = linkedMapOf<String, CatalogCategory>()
        private val movieCategories = linkedMapOf<String, CatalogCategory>()
        private val seriesCategories = linkedMapOf<String, CatalogCategory>()
        private val liveChannels = mutableListOf<CatalogLiveChannel>()
        private val movies = mutableListOf<CatalogMovie>()
        private val seriesBuilders = linkedMapOf<String, MutableSeries>()
        private val usedLiveIds = mutableSetOf<String>()
        private var inputIndex = 0

        /** Alias table of the last [build]; persisted with the catalog metadata. */
        var idAliases: Map<String, Long> = emptyMap()
            private set

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
                        containerExtension = PlaybackSource.parse(streamUrl).url
                            .substringBefore('?')
                            .substringAfterLast('/')
                            .substringAfterLast('.', "")
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
            val aliases = LinkedHashMap<String, Long>()

            val movieIds = M3uIdResolver.resolve(
                legacyIds = movies.map { it.id },
                fingerprintOf = { index ->
                    val movie = movies[index]
                    M3uIdResolver.fingerprint("movie", movie.title, movie.categoryId, movie.streamUrl)
                },
                incumbents = idIncumbents,
            )
            aliases.putAll(movieIds.aliases)
            movieIds.replacements.forEach { (index, id) -> movies[index] = movies[index].copy(id = id) }

            val seriesList = seriesBuilders.values.toList()
            val seriesIds = M3uIdResolver.resolve(
                legacyIds = seriesList.map { it.id },
                fingerprintOf = { index ->
                    val builder = seriesList[index]
                    M3uIdResolver.fingerprint("series", builder.title.lowercase(), builder.categoryId)
                },
                incumbents = idIncumbents,
            )
            aliases.putAll(seriesIds.aliases)
            val resolvedSeriesIds = Array(seriesList.size) { index ->
                seriesIds.replacements[index] ?: seriesList[index].id
            }

            // Episode ids must be unique across the whole catalogue: progress
            // and resume points are keyed by episode id.
            val episodeCount = seriesList.sumOf { it.episodes.size }
            val episodeOwner = IntArray(episodeCount)
            val episodePosition = IntArray(episodeCount)
            val episodeIds = ArrayList<String>(episodeCount)
            seriesList.forEachIndexed { owner, builder ->
                builder.episodes.forEachIndexed { position, episode ->
                    episodeOwner[episodeIds.size] = owner
                    episodePosition[episodeIds.size] = position
                    episodeIds += episode.id
                }
            }
            val resolvedEpisodes = M3uIdResolver.resolve(
                legacyIds = episodeIds,
                fingerprintOf = { index ->
                    val episode = seriesList[episodeOwner[index]].episodes[episodePosition[index]]
                    M3uIdResolver.fingerprint(
                        "episode",
                        episode.season.toString(),
                        episode.number.toString(),
                        episode.title,
                        episode.streamUrl,
                    )
                },
                incumbents = idIncumbents,
            )
            aliases.putAll(resolvedEpisodes.aliases)
            resolvedEpisodes.replacements.forEach { (index, id) ->
                val owner = seriesList[episodeOwner[index]].episodes
                val position = episodePosition[index]
                owner[position] = owner[position].copy(id = id)
            }
            idAliases = aliases

            val series = seriesList.mapIndexed { index, builder ->
                CatalogSeries(
                    id = resolvedSeriesIds[index],
                    title = builder.title,
                    categoryId = builder.categoryId,
                    posterUrl = builder.posterUrl,
                )
            }

            val details = LinkedHashMap<String, SeriesDetailSource>(seriesList.size * 2)
            seriesList.forEachIndexed { index, builder ->
                details[resolvedSeriesIds[index]] = SeriesDetailSource(
                    title = builder.title,
                    year = null,
                    synopsis = null,
                    genres = emptyList(),
                    episodes = builder.episodes.sortedWith(
                        compareBy<SeriesEpisodeSource> { it.season }.thenBy { it.number },
                    ),
                )
            }
            M3uSeriesDetailRegistry.replace(details)

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

        val url = PlaybackSource.parse(entry.streamUrl).url.lowercase()
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
