package fr.zyviotv.player.data.store

import fr.zyviotv.player.data.cache.CachedCatalog
import fr.zyviotv.player.data.cache.CatalogFileStamp
import fr.zyviotv.player.shared.catalog.CatalogCategory
import fr.zyviotv.player.shared.search.CatalogSearchEngine

/** What a generation is made from, besides the catalogue itself. */
data class GenerationSource(
    val profileKey: String,
    val scope: GenerationScope,
    val origin: GenerationOrigin,
    val fetchedAtEpochMs: Long?,
    val idAliases: Map<String, Long>,
    /** The V1 cache file holding the same catalogue, when known. */
    val v1Stamp: CatalogFileStamp?,
)

sealed interface GenerationWriteResult {
    data class Activated(
        val generationId: Long,
        val liveCount: Int,
        val movieCount: Int,
        val seriesCount: Int,
        val episodeCount: Int,
        val retiredGenerations: Int,
    ) : GenerationWriteResult

    /** Nothing was activated; the previous active generation (if any) is untouched. */
    data class Rejected(val reason: String) : GenerationWriteResult
}

/**
 * Writes one complete catalogue into a new `building` generation, in batched
 * transactions, verifies every count, then switches it to `active` in a
 * single transaction. The previous active generation of the same profile and
 * playlist (other playlists keep theirs) is only
 * retired by that switch and deleted afterwards. Any failure (exception,
 * cancellation, process death) leaves the previous active generation intact;
 * the incomplete one is removed now or by [discardInactive] at the next run.
 */
class CatalogGenerationWriter(
    private val database: CatalogStoreDatabase,
    private val keyWrapper: GenerationKeyWrapper,
    private val clock: () -> Long = System::currentTimeMillis,
    private val batchSize: Int = DEFAULT_BATCH_SIZE,
) {
    private val dao = database.dao()

    /** Cancellation probe of the write in progress (writes are serialised by the store). */
    private var ensureActive: () -> Unit = {}

    /**
     * @param ensureActive called before each batch; throwing from it (e.g.
     *   coroutine cancellation) abandons the generation.
     */
    fun write(
        catalog: CachedCatalog,
        source: GenerationSource,
        ensureActive: () -> Unit = {},
    ): GenerationWriteResult {
        this.ensureActive = ensureActive
        val report = catalog.sourceReport
        if (!catalog.isPlayable) {
            // Same rule as the V1 cache (bloc #208): never store a catalogue
            // that lost some playback sources.
            return GenerationWriteResult.Rejected("missing_sources")
        }
        val details = catalog.seriesDetails
        val snapshot = catalog.snapshot
        val episodeCount = details.values.sumOf { it.episodes.size }
        val (cipher, wrappedKey) = CatalogUrlCipher.create(keyWrapper)
        val generationId = dao.insertGeneration(
            GenerationEntity(
                profileKey = source.profileKey,
                playlistId = catalog.playlistId,
                playlistName = catalog.playlistName,
                state = GenerationState.Building.wire,
                scope = source.scope.wire,
                origin = source.origin.wire,
                fetchedAtEpochMs = source.fetchedAtEpochMs,
                createdAtEpochMs = clock(),
                liveCount = snapshot.liveChannels.size,
                movieCount = snapshot.movies.size,
                seriesCount = snapshot.series.size,
                episodeCount = episodeCount,
                v1Length = source.v1Stamp?.length,
                v1Sha256 = source.v1Stamp?.sha256?.toHex(),
                wrappedKey = wrappedKey,
            ),
        )
        try {
            writeCategories(generationId, CatalogKind.Live, snapshot.liveCategories)
            writeCategories(generationId, CatalogKind.Movie, snapshot.movieCategories)
            writeCategories(generationId, CatalogKind.Series, snapshot.seriesCategories)

            val liveNames = snapshot.liveCategories.associate { it.id to it.name }
            snapshot.liveChannels.withIndex().inBatches { batch ->
                dao.insertLiveChannels(
                    batch.map { (ordinal, channel) ->
                        LiveChannelEntity(
                            generationId = generationId,
                            ordinal = ordinal,
                            id = channel.id,
                            name = channel.name,
                            categoryId = channel.categoryId,
                            logoUrl = channel.logoUrl,
                            epgId = channel.epgId,
                            searchKey = searchKey(
                                channel.name,
                                CatalogSearchEngine.channelNumber(channel.name),
                                liveNames[channel.categoryId],
                            ),
                            urlBlob = cipher.seal(generationId, CatalogKind.Live, channel.id, channel.streamUrl),
                        )
                    },
                )
            }

            val movieNames = snapshot.movieCategories.associate { it.id to it.name }
            snapshot.movies.withIndex().inBatches { batch ->
                dao.insertMovies(
                    batch.map { (ordinal, movie) ->
                        MovieEntity(
                            generationId = generationId,
                            ordinal = ordinal,
                            id = movie.id,
                            title = movie.title,
                            sortTitle = CatalogSearchEngine.normalize(movie.title),
                            categoryId = movie.categoryId,
                            posterUrl = movie.posterUrl,
                            containerExtension = movie.containerExtension,
                            addedAtEpochSeconds = movie.addedAtEpochSeconds,
                            searchKey = searchKey(movie.title, null, movieNames[movie.categoryId]),
                            urlBlob = cipher.seal(generationId, CatalogKind.Movie, movie.id, movie.streamUrl),
                        )
                    },
                )
            }

            val seriesNames = snapshot.seriesCategories.associate { it.id to it.name }
            snapshot.series.withIndex().inBatches { batch ->
                dao.insertSeries(
                    batch.map { (ordinal, series) ->
                        SeriesEntity(
                            generationId = generationId,
                            ordinal = ordinal,
                            id = series.id,
                            title = series.title,
                            sortTitle = CatalogSearchEngine.normalize(series.title),
                            categoryId = series.categoryId,
                            posterUrl = series.posterUrl,
                            addedAtEpochSeconds = series.addedAtEpochSeconds,
                            searchKey = searchKey(series.title, null, seriesNames[series.categoryId]),
                        )
                    },
                )
            }

            details.entries.withIndex().inBatches { batch ->
                dao.insertSeriesDetails(
                    batch.map { (ordinal, entry) ->
                        val detail = entry.value
                        SeriesDetailEntity(
                            generationId = generationId,
                            ordinal = ordinal,
                            seriesId = entry.key,
                            title = detail.title,
                            year = detail.year,
                            synopsis = detail.synopsis,
                            genres = detail.genres.joinToString(GENRE_SEPARATOR.toString()),
                        )
                    },
                )
            }

            val pending = ArrayList<EpisodeEntity>(batchSize)
            details.forEach { (seriesId, detail) ->
                detail.episodes.forEachIndexed { ordinal, episode ->
                    pending += EpisodeEntity(
                        generationId = generationId,
                        seriesId = seriesId,
                        ordinal = ordinal,
                        id = episode.id,
                        season = episode.season,
                        number = episode.number,
                        title = episode.title,
                        synopsis = episode.synopsis,
                        urlBlob = cipher.seal(generationId, CatalogKind.Episode, episodeAadId(seriesId, episode.id), episode.streamUrl),
                    )
                    if (pending.size >= batchSize) {
                        insertInTransaction { dao.insertEpisodes(pending) }
                        pending.clear()
                    }
                }
            }
            if (pending.isNotEmpty()) insertInTransaction { dao.insertEpisodes(pending) }

            source.idAliases.entries.toList().inBatches { batch ->
                dao.insertIdAliases(
                    batch.map { (alias, fingerprint) -> IdAliasEntity(generationId, alias, fingerprint) },
                )
            }

            val mismatch = verify(generationId, catalog, episodeCount, source.idAliases.size)
            if (mismatch != null) {
                deleteGeneration(generationId)
                return GenerationWriteResult.Rejected(mismatch)
            }

            var retired = 0
            database.runInTransaction {
                retired = dao.retireActive(source.profileKey, catalog.playlistId)
                check(dao.markActive(generationId) == 1) { "Generation is no longer building" }
            }
            // Retired generations are no longer visible to any reader.
            discardInactive()
            return GenerationWriteResult.Activated(
                generationId = generationId,
                liveCount = report.liveTotal,
                movieCount = report.moviesTotal,
                seriesCount = snapshot.series.size,
                episodeCount = episodeCount,
                retiredGenerations = retired,
            )
        } catch (error: Throwable) {
            runCatching { deleteGeneration(generationId) }
            throw error
        }
    }

    /**
     * Deletes every generation that is not active: retired ones, and building
     * ones left by a failed write or a killed process. Only call while no
     * write is running (the store serialises its jobs).
     */
    fun discardInactive(): Int {
        val ids = dao.inactiveGenerationIds()
        ids.forEach(::deleteGeneration)
        return ids.size
    }

    private fun verify(generationId: Long, catalog: CachedCatalog, episodeCount: Int, aliasCount: Int): String? {
        val snapshot = catalog.snapshot
        val categoryCount = snapshot.liveCategories.size + snapshot.movieCategories.size + snapshot.seriesCategories.size
        return when {
            dao.countCategories(generationId) != categoryCount -> "count_mismatch_categories"
            dao.countLiveChannels(generationId) != snapshot.liveChannels.size -> "count_mismatch_live"
            dao.countMovies(generationId) != snapshot.movies.size -> "count_mismatch_movies"
            dao.countSeries(generationId) != snapshot.series.size -> "count_mismatch_series"
            dao.countSeriesDetails(generationId) != catalog.seriesDetails.size -> "count_mismatch_series_details"
            dao.countEpisodes(generationId) != episodeCount -> "count_mismatch_episodes"
            dao.countIdAliases(generationId) != aliasCount -> "count_mismatch_aliases"
            else -> null
        }
    }

    private fun deleteGeneration(generationId: Long) {
        // Large tables are emptied in their own statements so that no single
        // transaction holds the whole old catalogue.
        dao.deleteEpisodes(generationId)
        dao.deleteLiveChannels(generationId)
        dao.deleteMovies(generationId)
        database.runInTransaction {
            dao.deleteSeries(generationId)
            dao.deleteSeriesDetails(generationId)
            dao.deleteCategories(generationId)
            dao.deleteIdAliases(generationId)
            dao.deleteGenerationRow(generationId)
        }
    }

    private fun writeCategories(generationId: Long, kind: CatalogKind, categories: List<CatalogCategory>) {
        categories.withIndex().inBatches { batch ->
            dao.insertCategories(
                batch.map { (ordinal, category) ->
                    CategoryEntity(generationId, kind.wire, ordinal, category.id, category.name)
                },
            )
        }
    }

    private fun <T> Iterable<T>.inBatches(insert: (List<T>) -> Unit) {
        chunked(batchSize).forEach { batch -> insertInTransaction { insert(batch) } }
    }

    private fun insertInTransaction(block: () -> Unit) {
        ensureActive()
        database.runInTransaction(block)
    }

    companion object {
        const val DEFAULT_BATCH_SIZE = 1_000

        /** Episode ids are unique only inside their series. */
        fun episodeAadId(seriesId: String, episodeId: String): String = seriesId + "|" + episodeId

        /** Mirrors what CatalogSearchEngine matches: name, channel number and category. */
        internal fun searchKey(title: String, number: String?, category: String?): String =
            CatalogSearchEngine.normalize(listOfNotNull(title, number, category).joinToString(" "))

        private fun ByteArray.toHex(): String = joinToString("") { byte -> "%02x".format(byte) }
    }
}
