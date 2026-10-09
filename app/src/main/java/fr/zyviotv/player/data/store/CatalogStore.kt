package fr.zyviotv.player.data.store

import android.content.Context
import fr.zyviotv.player.data.cache.CachedCatalog
import fr.zyviotv.player.data.cache.CatalogCacheOrigin
import fr.zyviotv.player.data.cache.CatalogFileStamp
import fr.zyviotv.player.data.cache.RestoredCatalog
import fr.zyviotv.player.data.catalog.CatalogPerformanceDiagnostics
import fr.zyviotv.player.data.catalog.PlaybackActivity
import fr.zyviotv.player.data.catalog.PlaybackActivityTracker
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

/**
 * Bloc #213, PR A: fills the Room catalogue store in the background while the
 * app keeps reading the V1 file cache exactly as before. Nothing reads the
 * store yet (PR B), and the V1 cache is never deleted here.
 *
 * - After a V1 cache was restored, its catalogue is imported once (skipped
 *   when the active generation already mirrors that file or is newer).
 * - After a validated provider refresh, the raw (unfiltered) catalogue is
 *   written as a new generation.
 *
 * Jobs run one at a time, never start during playback (same rule as automatic
 * refreshes, bloc #211), and a later refresh of a profile supersedes any older
 * request of that profile that has not started yet.
 */
class CatalogStore internal constructor(
    private val writer: CatalogGenerationWriter,
    private val dao: CatalogStoreDao,
    private val databaseFile: File?,
    private val playback: PlaybackActivityTracker,
    private val scope: CoroutineScope,
) {
    private val mutex = Mutex()
    private val sequence = AtomicLong()
    /** Latest refresh request per profile key. */
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

    /** Raw catalogue of a validated provider refresh, before any parental filtering. */
    fun mirrorRefresh(
        profileId: String,
        rawCatalog: CachedCatalog,
        fetchedAtEpochMs: Long,
        idAliases: Map<String, Long>,
        v1Stamp: CatalogFileStamp?,
    ) {
        submit(
            catalog = rawCatalog,
            source = GenerationSource(
                profileKey = profileKey(profileId),
                scope = GenerationScope.Raw,
                origin = GenerationOrigin.Refresh,
                fetchedAtEpochMs = fetchedAtEpochMs,
                idAliases = idAliases,
                v1Stamp = v1Stamp,
            ),
        )
    }

    private fun submit(catalog: CachedCatalog, source: GenerationSource) {
        val request = sequence.incrementAndGet()
        if (source.origin == GenerationOrigin.Refresh) latestRefresh[source.profileKey] = request
        scope.launch {
            // Never compete with Media3 for CPU and I/O (bloc #211 rule).
            playback.awaitIdle()
            mutex.withLock {
                if ((latestRefresh[source.profileKey] ?: 0L) > request) {
                    CatalogPerformanceDiagnostics.event("catalog_store_skipped", "reason=superseded")
                    return@withLock
                }
                execute(catalog, source)
            }
        }
    }

    internal suspend fun runNow(catalog: CachedCatalog, source: GenerationSource) {
        mutex.withLock { execute(catalog, source) }
    }

    private suspend fun execute(catalog: CachedCatalog, source: GenerationSource) {
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
                val skip = v1ImportSkipReason(dao.activeGeneration(source.profileKey), source)
                if (skip != null) {
                    CatalogPerformanceDiagnostics.event("catalog_store_skipped", "reason=$skip")
                    return
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
        } catch (error: Throwable) {
            // The previous active generation and the V1 cache are untouched.
            CatalogPerformanceDiagnostics.event(
                name = "catalog_store_failed",
                fields = "origin=${source.origin.wire} failure=" + error.javaClass.simpleName,
                warning = true,
            )
            if (error is CancellationException) throw error
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
                    CatalogStore(
                        writer = CatalogGenerationWriter(database, KeystoreKeyWrapper()),
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
