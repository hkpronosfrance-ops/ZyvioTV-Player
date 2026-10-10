package fr.zyviotv.player.data.store

import android.content.Context
import fr.zyviotv.player.data.cache.CachedCatalog
import fr.zyviotv.player.data.cache.CatalogCacheOrigin
import fr.zyviotv.player.data.cache.CatalogFileStamp
import fr.zyviotv.player.data.cache.RestoredCatalog
import fr.zyviotv.player.data.catalog.CatalogPerformanceDiagnostics
import fr.zyviotv.player.data.catalog.PlaybackActivity
import fr.zyviotv.player.data.catalog.PlaybackActivityTracker
import fr.zyviotv.player.data.catalog.SeriesDetailSource
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Bloc #213: the Room catalogue store.
 *
 * - PR B: the session restores the catalogue from the store at startup
 *   ([restore]): titles and ids only, stream URLs decrypted one at a time on
 *   playback ([resolveSource]), episodes read per series ([seriesDetail]).
 * - When only a V1 file cache exists (first launch after the update), it is
 *   imported once in the background ([mirrorRestoredCache]); skipped when the
 *   active generation already mirrors that file or is newer.
 * - A validated provider refresh is written right away, raw (unfiltered),
 *   before the session drops the V1 cache ([writeRefreshNow]).
 *
 * Jobs run one at a time, never start during playback (same rule as automatic
 * refreshes, bloc #211), and a later refresh of a profile and playlist
 * supersedes any older request for them that has not started yet. Each
 * playlist keeps its own active generation (multi-playlist ready).
 */
class CatalogStore internal constructor(
    private val writer: CatalogGenerationWriter,
    private val reader: CatalogStoreReader,
    private val dao: CatalogStoreDao,
    private val databaseFile: File?,
    private val playback: PlaybackActivityTracker,
    private val scope: CoroutineScope,
) {
    private val mutex = Mutex()
    private val sequence = AtomicLong()
    /** Latest refresh request per profile and playlist. */
    private val latestRefresh = ConcurrentHashMap<String, Long>()
    @Volatile private var cleanedUp = false

    /** One-time import of a restored V1 cache (profile-filtered copy). */
    fun mirrorRestoredCache(profileId: String, restored: RestoredCatalog) {
        val stamp = restored.fileStamp ?: return
        val catalog = restored.catalog
        if (catalog.origin != CatalogCacheOrigin.Encrypted || !catalog.isPlayable) return
        submit(
            catalog = catalog,
            source = GenerationSource(
                profileKey = profileKey(profileId),
                scope = GenerationScope.ProfileFiltered,
                origin = GenerationOrigin.V1Import,
                fetchedAtEpochMs = restored.fetchedAtEpochMs,
                idAliases = restored.idAliases,
                v1Stamp = stamp,
            ),
        )
    }

    /**
     * Writes the raw catalogue of a validated provider refresh now (the
     * refresh already waited for the end of playback). True once the new
     * generation is active; false keeps the previous store content.
     */
    suspend fun writeRefreshNow(
        profileId: String,
        rawCatalog: CachedCatalog,
        fetchedAtEpochMs: Long,
        idAliases: Map<String, Long>,
    ): Boolean {
        val source = GenerationSource(
            profileKey = profileKey(profileId),
            scope = GenerationScope.Raw,
            origin = GenerationOrigin.Refresh,
            fetchedAtEpochMs = fetchedAtEpochMs,
            idAliases = idAliases,
            v1Stamp = null,
        )
        // Pending older requests (a V1 import) of this playlist are superseded.
        latestRefresh[source.profileKey + "|" + rawCatalog.playlistId] = sequence.incrementAndGet()
        return mutex.withLock { execute(rawCatalog, source) } is GenerationWriteResult.Activated
    }

    /**
     * Startup read of the profile's catalogue (blocking: call on IO). Null
     * when the store holds no active generation for it, or cannot be read.
     */
    fun restore(profileId: String): StoredCatalog? {
        val startedAt = CatalogPerformanceDiagnostics.startedAt()
        val stored = try {
            reader.latest(profileKey(profileId))
        } catch (error: Exception) {
            CatalogPerformanceDiagnostics.event(
                name = "catalog_store_unreadable",
                fields = "failure=" + error.javaClass.simpleName,
                warning = true,
            )
            null
        } ?: return null
        val snapshot = stored.catalog.snapshot
        CatalogPerformanceDiagnostics.phase(
            name = "catalog_store_load",
            startedAtMs = startedAt,
            itemCount = snapshot.liveChannels.size + snapshot.movies.size + snapshot.series.size,
        )
        return stored
    }

    fun hasActiveGeneration(profileId: String): Boolean =
        runCatching { reader.hasActiveGeneration(profileKey(profileId)) }.getOrDefault(false)

    /**
     * Decrypts the stream URL behind a [CatalogSourceRef]; any other value is
     * returned unchanged. Null when the referenced item no longer exists.
     */
    suspend fun resolveSource(streamUrl: String): String? {
        val ref = CatalogSourceRef.parse(streamUrl) ?: return streamUrl.takeUnless(CatalogSourceRef::isRef)
        return withContext(Dispatchers.IO) {
            try {
                reader.resolve(ref)
            } catch (error: Exception) {
                CatalogPerformanceDiagnostics.event(
                    name = "catalog_source_unresolved",
                    fields = "kind=${ref.kind.wire} failure=" + error.javaClass.simpleName,
                    warning = true,
                )
                null
            }
        }
    }

    /** Stored episodes of a series of the given generation, or null. */
    suspend fun seriesDetail(generationId: Long, playlistId: String, seriesId: String): SeriesDetailSource? =
        withContext(Dispatchers.IO) {
            runCatching { reader.seriesDetail(generationId, playlistId, seriesId) }.getOrNull()
        }

    private fun submit(catalog: CachedCatalog, source: GenerationSource) {
        val request = sequence.incrementAndGet()
        val slot = source.profileKey + "|" + catalog.playlistId
        if (source.origin == GenerationOrigin.Refresh) latestRefresh[slot] = request
        scope.launch {
            // Never compete with Media3 for CPU and I/O (bloc #211 rule).
            playback.awaitIdle()
            mutex.withLock {
                if ((latestRefresh[slot] ?: 0L) > request) {
                    CatalogPerformanceDiagnostics.event("catalog_store_skipped", "reason=superseded")
                    return@withLock
                }
                execute(catalog, source)
            }
        }
    }

    private suspend fun execute(catalog: CachedCatalog, source: GenerationSource): GenerationWriteResult? {
        val context = currentCoroutineContext()
        try {
            if (!cleanedUp) {
                // Building generations left by a killed process, retired ones
                // whose deletion was interrupted.
                val discarded = writer.discardInactive()
                cleanedUp = true
                if (discarded > 0) {
                    CatalogPerformanceDiagnostics.event("catalog_store_cleanup", "discarded=$discarded")
                }
            }
            if (source.origin == GenerationOrigin.V1Import) {
                val skip = v1ImportSkipReason(dao.activeGeneration(source.profileKey, catalog.playlistId), source)
                if (skip != null) {
                    CatalogPerformanceDiagnostics.event("catalog_store_skipped", "reason=$skip")
                    return null
                }
            }
            val startedAt = CatalogPerformanceDiagnostics.startedAt()
            val result = writer.write(catalog, source) { context.ensureActive() }
            when (result) {
                is GenerationWriteResult.Activated -> {
                    CatalogPerformanceDiagnostics.phase(
                        name = "catalog_store_write",
                        startedAtMs = startedAt,
                        itemCount = result.liveCount + result.movieCount + result.seriesCount,
                    )
                    CatalogPerformanceDiagnostics.event(
                        name = "catalog_store_generation",
                        fields = "origin=${source.origin.wire} scope=${source.scope.wire} " +
                            "live=${result.liveCount} movies=${result.movieCount} " +
                            "series=${result.seriesCount} episodes=${result.episodeCount} " +
                            "retired=${result.retiredGenerations} db_size_mb=${databaseSizeMb()}",
                    )
                }
                is GenerationWriteResult.Rejected -> CatalogPerformanceDiagnostics.event(
                    name = "catalog_store_rejected",
                    fields = "origin=${source.origin.wire} reason=${result.reason}",
                    warning = true,
                )
            }
            return result
        } catch (error: Throwable) {
            // The previous active generation and the V1 cache are untouched.
            CatalogPerformanceDiagnostics.event(
                name = "catalog_store_failed",
                fields = "origin=${source.origin.wire} failure=" + error.javaClass.simpleName,
                warning = true,
            )
            if (error is CancellationException) throw error
            return null
        }
    }

    private fun databaseSizeMb(): String {
        val file = databaseFile ?: return "unknown"
        val bytes = listOf(file, File(file.path + "-wal"), File(file.path + "-shm"))
            .sumOf { if (it.exists()) it.length() else 0L }
        return (bytes / BYTES_PER_MEBIBYTE).toString()
    }

    companion object {
        private const val BYTES_PER_MEBIBYTE = 1024L * 1024L

        @Volatile private var instance: CatalogStore? = null

        fun get(context: Context): CatalogStore =
            instance ?: synchronized(this) {
                instance ?: run {
                    val appContext = context.applicationContext
                    val database = CatalogStoreDatabase.get(appContext)
                    val keyWrapper = KeystoreKeyWrapper()
                    CatalogStore(
                        writer = CatalogGenerationWriter(database, keyWrapper),
                        reader = CatalogStoreReader(database.dao(), keyWrapper),
                        dao = database.dao(),
                        databaseFile = appContext.getDatabasePath(CatalogStoreDatabase.FILE_NAME),
                        playback = PlaybackActivity.tracker,
                        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
                    )
                }.also { instance = it }
            }

        /** Same digest as the V1 cache file name: the profile id itself is never stored. */
        fun profileKey(profileId: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(profileId.toByteArray(Charsets.UTF_8))
                .joinToString("") { byte -> "%02x".format(byte) }

        /**
         * Null when the V1 cache should be imported. A V1 copy never replaces
         * a generation it already mirrors, nor a newer refresh generation
         * (a refresh whose V1 save failed leaves an older V1 file behind).
         */
        internal fun v1ImportSkipReason(active: GenerationEntity?, source: GenerationSource): String? {
            if (active == null) return null
            if (active.mirrors(source.v1Stamp)) return "already_mirrored"
            if (active.origin == GenerationOrigin.V1Import.wire) return null
            val activeFetchedAt = active.fetchedAtEpochMs
            val cacheFetchedAt = source.fetchedAtEpochMs
            return if (activeFetchedAt != null && cacheFetchedAt != null && cacheFetchedAt > activeFetchedAt) {
                null
            } else {
                "store_newer"
            }
        }

        internal fun GenerationEntity.mirrors(stamp: CatalogFileStamp?): Boolean =
            stamp != null && v1Length == stamp.length &&
                v1Sha256 == stamp.sha256.joinToString("") { byte -> "%02x".format(byte) }
    }
}
