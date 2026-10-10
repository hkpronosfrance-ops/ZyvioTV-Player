package fr.zyviotv.player.data.store

import fr.zyviotv.player.data.cache.CachedCatalog
import fr.zyviotv.player.data.catalog.SeriesDetailSource
import fr.zyviotv.player.data.catalog.SeriesEpisodeSource
import fr.zyviotv.player.shared.catalog.CatalogCategory
import fr.zyviotv.player.shared.catalog.CatalogLiveChannel
import fr.zyviotv.player.shared.catalog.CatalogMovie
import fr.zyviotv.player.shared.catalog.CatalogSeries
import fr.zyviotv.player.shared.catalog.CatalogSnapshot

/** A catalogue restored from the store, with what the session needs besides it. */
class StoredCatalog(
    val generation: GenerationEntity,
    /** Items carry [CatalogSourceRef] references instead of URLs; no episodes. */
    val catalog: CachedCatalog,
    val idAliases: Map<String, Long>,
)

/**
 * Reads the Room store (PR B). The startup read loads titles, artwork, ids
 * and categories only: stream URLs stay encrypted on disk and episodes are
 * read per series when a series is opened. Blocking: call off the main thread.
 */
class CatalogStoreReader(
    private val dao: CatalogStoreDao,
    private val keyWrapper: GenerationKeyWrapper,
) {
    private val ciphers = LinkedHashMap<Long, CatalogUrlCipher>()

    /** The most recent active generation of the profile, or null. */
    fun latest(profileKey: String): StoredCatalog? {
        val generation = dao.activeGenerations(profileKey).maxByOrNull { it.id } ?: return null
        return load(generation)
    }

    fun hasActiveGeneration(profileKey: String): Boolean = dao.activeGenerations(profileKey).isNotEmpty()

    private fun load(generation: GenerationEntity): StoredCatalog {
        val gen = generation.id
        val playlistId = generation.playlistId
        // Built once per kind (PR #215): each reference is then one concatenation.
        val livePrefix = CatalogSourceRef.prefix(CatalogKind.Live, gen, playlistId)
        val moviePrefix = CatalogSourceRef.prefix(CatalogKind.Movie, gen, playlistId)
        fun categories(kind: CatalogKind) =
            dao.categoryRows(gen, kind.wire).map { CatalogCategory(id = it.id, name = it.name) }

        val snapshot = CatalogSnapshot(
            liveCategories = categories(CatalogKind.Live),
            liveChannels = dao.liveRows(gen).map {
                CatalogLiveChannel(
                    id = it.id,
                    name = it.name,
                    categoryId = it.categoryId,
                    logoUrl = it.logoUrl,
                    streamUrl = CatalogSourceRef.ofItem(livePrefix, it.id),
                    epgId = it.epgId,
                )
            },
            movieCategories = categories(CatalogKind.Movie),
            movies = dao.movieRows(gen).map {
                CatalogMovie(
                    id = it.id,
                    title = it.title,
                    categoryId = it.categoryId,
                    posterUrl = it.posterUrl,
                    streamUrl = CatalogSourceRef.ofItem(moviePrefix, it.id),
                    containerExtension = it.containerExtension,
                    addedAtEpochSeconds = it.addedAtEpochSeconds,
                )
            },
            seriesCategories = categories(CatalogKind.Series),
            series = dao.seriesRows(gen).map {
                CatalogSeries(
                    id = it.id,
                    title = it.title,
                    categoryId = it.categoryId,
                    posterUrl = it.posterUrl,
                    addedAtEpochSeconds = it.addedAtEpochSeconds,
                )
            },
        )
        return StoredCatalog(
            generation = generation,
            catalog = CachedCatalog(
                playlistId = playlistId,
                playlistName = generation.playlistName,
                snapshot = snapshot,
            ),
            idAliases = dao.idAliases(gen).associate { it.alias to it.fingerprint },
        )
    }

    /** Episodes of one series, their sources as references; null when not stored. */
    fun seriesDetail(generationId: Long, playlistId: String, seriesId: String): SeriesDetailSource? {
        val detail = dao.seriesDetail(generationId, seriesId) ?: return null
        return SeriesDetailSource(
            title = detail.title,
            year = detail.year,
            synopsis = detail.synopsis,
            genres = detail.genres.split(GENRE_SEPARATOR).filter(String::isNotEmpty),
            episodes = dao.episodeRows(generationId, seriesId).map {
                SeriesEpisodeSource(
                    id = it.id,
                    season = it.season,
                    number = it.number,
                    title = it.title,
                    synopsis = it.synopsis,
                    streamUrl = CatalogSourceRef(CatalogKind.Episode, generationId, playlistId, it.id, seriesId).encode(),
                )
            },
        )
    }

    /**
     * Decrypts the URL a reference points to. When its generation was replaced
     * by a refresh meanwhile, the active generation of the same playlist is
     * used (ids are stable across refreshes, bloc #211). Null when gone.
     */
    fun resolve(ref: CatalogSourceRef): String? {
        val generation = dao.generation(ref.generationId)?.takeIf { it.state == GenerationState.Active.wire }
            ?: dao.activeGenerationOfPlaylist(ref.playlistId)
            ?: return null
        val gen = generation.id
        val (blob, aadId) = when (ref.kind) {
            CatalogKind.Live -> dao.liveUrlBlob(gen, ref.id) to ref.id
            CatalogKind.Movie -> dao.movieUrlBlob(gen, ref.id) to ref.id
            CatalogKind.Episode -> {
                val seriesId = ref.seriesId ?: return null
                dao.episodeUrlBlob(gen, seriesId, ref.id) to CatalogGenerationWriter.episodeAadId(seriesId, ref.id)
            }
            CatalogKind.Series -> return null
        }
        blob ?: return null
        return cipherOf(generation).open(gen, ref.kind, aadId, blob)
    }

    /** One Keystore unwrap per generation, kept for the few generations in use. */
    @Synchronized
    private fun cipherOf(generation: GenerationEntity): CatalogUrlCipher =
        ciphers[generation.id] ?: CatalogUrlCipher.restore(keyWrapper, generation.wrappedKey).also { cipher ->
            ciphers[generation.id] = cipher
            while (ciphers.size > MAX_CACHED_CIPHERS) ciphers.remove(ciphers.keys.first())
        }

    private companion object {
        const val MAX_CACHED_CIPHERS = 4
    }
}
