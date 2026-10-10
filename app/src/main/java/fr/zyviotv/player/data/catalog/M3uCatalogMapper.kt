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

    /** [SeriesFile]: a /series/ video without season/episode, kept as a film (PR #219). */
    private enum class Kind { Live, Movie, Episode, SeriesFile }

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

        // PR #219: playlist order, to know when channels and films are complete.
        private var episodesSinceFirst = 0
        private var seriesPhaseStarted = false

        /** Live channels or films received after the first episode (file not ordered by family). */
        var orderViolations: Int = 0
            private set

        /** Alias table of the last [build]; persisted with the catalog metadata. */
        var idAliases: Map<String, Long> = emptyMap()
            private set

        /**
         * True once the playlist is visibly ordered "channels and films, then
         * series": at least one channel or film, then [EARLY_EPISODE_RUN]
         * episodes in a row with no channel or film after the first episode.
         * Xtream-generated playlists (get.php) follow this order; any other
         * order keeps the complete-file rule.
         */
        val liveAndMoviesComplete: Boolean
            get() = seriesPhaseStarted &&
                orderViolations == 0 &&
                episodesSinceFirst >= EARLY_EPISODE_RUN &&
                (liveChannels.isNotEmpty() || movies.isNotEmpty())

        fun add(entry: M3uEntry) {
            val index = inputIndex++
            val streamUrl = entry.streamUrl.trim()
            if (streamUrl.isBlank()) return
            val group = normalizedGroup(entry.groupTitle)
            val episodeIdentity = parseEpisode(entry.name)
            val kind = classify(entry, episodeIdentity)
            if (kind == Kind.Episode && episodeIdentity != null) {
                seriesPhaseStarted = true
                episodesSinceFirst += 1
            } else if (seriesPhaseStarted && kind != Kind.Episode && kind != Kind.SeriesFile) {
                orderViolations += 1
            }

            when (kind) {
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

                Kind.Movie, Kind.SeriesFile -> {
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

        /**
         * PR #219: channels and films received so far, without series (they
         * are still arriving). Film ids are resolved exactly as [build] will
         * resolve them, so a favourite added now keeps its id. Nothing is
         * mutated: [build] stays the only source of the complete catalogue.
         */
        fun buildLiveAndMovies(): CatalogSnapshot {
            val movieIds = resolveMovieIds()
            val resolvedMovies = if (movieIds.replacements.isEmpty()) {
                movies.toList()
            } else {
                movies.mapIndexed { index, movie ->
                    movieIds.replacements[index]?.let { movie.copy(id = it) } ?: movie
                }
            }
            return CatalogSnapshot(
                liveCategories = liveCategories.values.toList(),
                liveChannels = liveChannels.toList(),
                movieCategories = movieCategories.values.toList(),
                movies = resolvedMovies,
                seriesCategories = emptyList(),
                series = emptyList(),
            )
        }

        private fun resolveMovieIds() = M3uIdResolver.resolve(
            legacyIds = movies.map { it.id },
            fingerprintOf = { index ->
                val movie = movies[index]
                M3uIdResolver.fingerprint("movie", movie.title, movie.categoryId, movie.streamUrl)
            },
            incumbents = idIncumbents,
        )

        fun build(): CatalogSnapshot {
            val aliases = LinkedHashMap<String, Long>()

            val movieIds = resolveMovieIds()
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

    /**
     * PR #219: the provider's URL path wins over the group name. On the
     * recetted playlist 404 entries were misfiled by group words ("EMISSION
     * TV", "SPORTS", "CINE"…) although their URL said /movie/ or a live
     * stream, and a live channel whose name looked like "1x02" became a series.
     * A name like an episode only makes an episode for a file (video
     * extension or /series/ path), never for a live stream.
     */
    private fun classify(entry: M3uEntry, episode: EpisodeIdentity?): Kind {
        val url = PlaybackSource.parse(entry.streamUrl).url.lowercase().substringBefore('?')
        val path = url.substringAfter("://", url).substringAfter('/', "")
        val lastSegment = path.substringAfterLast('/')
        val extension = if ('.' in lastSegment) lastSegment.substringAfterLast('.') else ""
        val isFile = extension in MOVIE_EXTENSIONS

        if ("/series/" in "/$path") {
            // A series file without season/episode in its name is a playable
            // video, never a live channel ("APPLE TV+" in its group said live).
            return if (episode != null) Kind.Episode else Kind.SeriesFile
        }
        if ("/movie/" in "/$path" || "/vod/" in "/$path") return Kind.Movie
        if ("/live/" in "/$path") return Kind.Live
        // Xtream live stream without /live/: <account>/<secret>/<number>[.ts|.m3u8].
        if (XTREAM_LIVE_PATH.matches(path)) return Kind.Live
        if (episode != null && isFile) return Kind.Episode

        val group = normalize(entry.groupTitle)
        if (MOVIE_TOKENS.any(group::contains)) return Kind.Movie
        if (LIVE_TOKENS.any(group::contains)) return Kind.Live
        return if (isFile) Kind.Movie else Kind.Live
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
    private val XTREAM_LIVE_PATH = Regex("""[^/]+/[^/]+/\d+(?:\.(?:ts|m3u8))?""")
    private val MOVIE_EXTENSIONS = setOf("mp4", "mkv", "avi", "mov", "m4v", "webm")
    private val MOVIE_TOKENS = listOf("vod", "movie", "movies", "film", "films", "cinema", "cine")
    /**
     * Episodes in a row, with no channel or film since the first one, before
     * channels and films are taken as complete (about 4 % of the recetted
     * playlist's 117 990 episodes).
     */
    const val EARLY_EPISODE_RUN = 5_000

    private val LIVE_TOKENS = listOf("live", "tv", "chaine", "channel", "sport", "news", "info", "radio")
}
