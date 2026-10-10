package fr.zyviotv.player.ui.catalog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import fr.zyviotv.player.data.auth.SecureSessionStore
import fr.zyviotv.player.data.cache.CachedCatalog
import fr.zyviotv.player.data.cache.OfflineContentCache
import fr.zyviotv.player.data.cache.RestoredCatalog
import fr.zyviotv.player.data.catalog.AndroidXtreamCatalogLoader
import fr.zyviotv.player.data.catalog.CatalogPerformanceDiagnostics
import fr.zyviotv.player.data.catalog.CatalogRefreshPolicy
import fr.zyviotv.player.data.catalog.CatalogRefreshTrigger
import fr.zyviotv.player.data.catalog.CatalogSingleFlight
import fr.zyviotv.player.data.catalog.M3uCatalogMapper
import fr.zyviotv.player.data.catalog.M3uSeriesDetailRegistry
import fr.zyviotv.player.data.catalog.PlaybackActivity
import fr.zyviotv.player.data.m3u.AndroidM3uClient
import fr.zyviotv.player.data.m3u.M3uStreamingResult
import fr.zyviotv.player.data.settings.ParentalControlsRepository
import fr.zyviotv.player.data.settings.ProfileContentLocks
import fr.zyviotv.player.data.settings.ProfilePreferences
import fr.zyviotv.player.data.store.CatalogStore
import fr.zyviotv.player.data.sync.SupabaseCloudSyncRepository
import fr.zyviotv.player.shared.catalog.CatalogLoadResult
import fr.zyviotv.player.shared.catalog.CatalogSnapshot
import fr.zyviotv.player.shared.m3u.M3uSource
import fr.zyviotv.player.shared.sync.PlaylistSecret
import fr.zyviotv.player.shared.sync.SyncedPlaylist
import fr.zyviotv.player.shared.xtream.XtreamCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface ProviderCatalogState {
    data object Loading : ProviderCatalogState

    data class Ready(
        val playlistId: String,
        val playlistName: String,
        val snapshot: CatalogSnapshot,
        val rawSnapshot: CatalogSnapshot = snapshot,
        val contentLocks: ProfileContentLocks? = null,
        /**
         * True while the snapshot comes from the encrypted disk cache and no
         * network refresh has completed yet. It never means "device offline"
         * and must not gate playback (bloc #207): use NetworkAvailability.
         */
        val isFromCache: Boolean = false,
        /**
         * The restored catalog lost its playback sources (pre-#206 JSON
         * cache). It may be browsed, but play actions explain that a
         * controlled resynchronisation is needed (bloc #208).
         */
        val sourcesPending: Boolean = false,
        val syncWarning: String? = null,
        val isRefreshing: Boolean = false,
    ) : ProviderCatalogState

    data class Empty(val message: String) : ProviderCatalogState

    data class Error(val message: String) : ProviderCatalogState
}

class ProviderCatalogSession internal constructor(
    val state: State<ProviderCatalogState>,
    private val onReload: (CatalogRefreshTrigger) -> Unit,
) {
    /**
     * Requests a reload. Automatic triggers follow the 12-hour freshness rule
     * and wait for the end of playback; the others always synchronise (#211).
     */
    fun reload(trigger: CatalogRefreshTrigger = CatalogRefreshTrigger.Retry) = onReload(trigger)
}

/** Result of one provider synchronisation, shared by every waiting screen. */
private class CatalogRefreshOutcome(
    val state: ProviderCatalogState,
    val idAliases: Map<String, Long> = emptyMap(),
    val fetchedAtEpochMs: Long = 0L,
)

private object ProviderCatalogSyncCoordinator {
    private val singleFlight = CatalogSingleFlight<CatalogRefreshOutcome>()

    suspend fun run(
        key: String,
        loader: suspend () -> CatalogRefreshOutcome,
    ): CatalogRefreshOutcome {
        return singleFlight.run(key, loader)
    }
}

/** A restored catalogue and where it came from. */
private class SessionRestore(
    val restored: RestoredCatalog,
    /** True when read from the Room store (bloc #213), false for the V1 file cache. */
    val fromStore: Boolean,
)

/**
 * The start-up reload (profile gate) cancels the first session effect while
 * its blocking cache read keeps running; sharing the read avoids reading the
 * 20k+ item catalogue twice in parallel.
 */
private object ProviderCatalogCacheCoordinator {
    private val singleFlight = CatalogSingleFlight<SessionRestore?>()

    suspend fun load(
        profileId: String,
        loader: () -> SessionRestore?,
    ): SessionRestore? = singleFlight.run(profileId) { loader() }
}

/**
 * Bloc #213 (PR B): the Room store first. Its catalogue carries source
 * references (no URL in memory) and no episodes; they are read per series.
 * Once a complete generation is active, the V1 file cache is deleted. The V1
 * file is only read while no generation exists yet (first launch after the
 * update), and is then copied into the store in the background.
 */
private fun restoreCatalogForSession(
    profileId: String,
    catalogStore: CatalogStore,
    offlineCache: OfflineContentCache,
): SessionRestore? {
    val startedAt = CatalogPerformanceDiagnostics.startedAt()
    val stored = catalogStore.restore(profileId)
    if (stored != null) {
        val generation = stored.generation
        M3uSeriesDetailRegistry.useStored { seriesId ->
            catalogStore.seriesDetail(generation.id, generation.playlistId, seriesId)
        }
        offlineCache.deleteCatalogFiles(profileId)
        CatalogPerformanceDiagnostics.phase(
            name = "catalog_ready",
            startedAtMs = startedAt,
            itemCount = stored.catalog.snapshot.itemCount(),
        )
        CatalogPerformanceDiagnostics.event("catalog_ready_source", "source=store scope=${generation.scope}")
        return SessionRestore(
            restored = RestoredCatalog(
                catalog = stored.catalog,
                fetchedAtEpochMs = generation.fetchedAtEpochMs,
                idAliases = stored.idAliases,
            ),
            fromStore = true,
        )
    }
    val restored = offlineCache.restoreCatalog(profileId) ?: return null
    CatalogPerformanceDiagnostics.phase(
        name = "catalog_ready",
        startedAtMs = startedAt,
        itemCount = restored.catalog.snapshot.itemCount(),
    )
    CatalogPerformanceDiagnostics.event("catalog_ready_source", "source=v1")
    return SessionRestore(restored = restored, fromStore = false)
}

private fun CatalogSnapshot.itemCount(): Int = liveChannels.size + movies.size + series.size

@Composable
fun rememberProviderCatalogSession(): ProviderCatalogSession {
    val context = LocalContext.current
    val applicationContext = context.applicationContext
    val sessionStore = remember(applicationContext) {
        SecureSessionStore(applicationContext)
    }
    val repository = remember(applicationContext) {
        SupabaseCloudSyncRepository(
            sessionStore = sessionStore,
        )
    }
    val parentalRepository = remember(applicationContext) {
        ParentalControlsRepository(sessionStore)
    }
    val profilePreferences = remember(applicationContext) {
        ProfilePreferences(applicationContext)
    }
    val m3uClient = remember(applicationContext) {
        AndroidM3uClient(tempDirectory = applicationContext.cacheDir)
    }
    val offlineCache = remember(applicationContext) {
        OfflineContentCache(applicationContext)
    }
    // Bloc #213: the catalogue store, read at startup since PR B.
    val catalogStore = remember(applicationContext) {
        CatalogStore.get(applicationContext)
    }

    val state = remember {
        mutableStateOf<ProviderCatalogState>(ProviderCatalogState.Loading)
    }
    var reloadToken by remember { mutableIntStateOf(0) }
    // Plain holders (not snapshot state): read by the effect, never drawn.
    val session = remember { CatalogSessionMemory() }

    LaunchedEffect(reloadToken) {
        val trigger = session.pendingTrigger ?: CatalogRefreshTrigger.Startup
        val profileId = profilePreferences.selectedProfileId()
        if (profileId.isNullOrBlank()) {
            state.value = ProviderCatalogState.Loading
            return@LaunchedEffect
        }

        // A catalogue shown for another profile is never reused (bloc #211):
        // each profile has its own cache and parental filtering.
        var previousReady = (state.value as? ProviderCatalogState.Ready)
            ?.takeIf { session.readyProfileId == profileId }
        if (previousReady == null) {
            val restore = ProviderCatalogCacheCoordinator.load(profileId) {
                restoreCatalogForSession(profileId, catalogStore, offlineCache)
            }
            val restored = restore?.restored
            if (restored != null) {
                val cached = restored.catalog
                previousReady = ProviderCatalogState.Ready(
                    playlistId = cached.playlistId,
                    playlistName = cached.playlistName,
                    snapshot = cached.snapshot,
                    rawSnapshot = cached.snapshot,
                    isFromCache = true,
                    sourcesPending = !cached.isPlayable,
                    isRefreshing = true,
                )
                state.value = previousReady
                session.readyProfileId = profileId
                session.fetchedAtEpochMs = restored.fetchedAtEpochMs
                session.idAliases = restored.idAliases
                // One-time copy of the V1 cache into the store (skipped once mirrored).
                if (!restore.fromStore) catalogStore.mirrorRestoredCache(profileId, restored)
            } else {
                state.value = ProviderCatalogState.Loading
                session.readyProfileId = null
                session.fetchedAtEpochMs = null
                session.idAliases = withContext(Dispatchers.IO) { offlineCache.loadIdAliases(profileId) }
            }
        }

        val now = System.currentTimeMillis()
        val decision = CatalogRefreshPolicy.decide(
            trigger = trigger,
            hasPlayableCache = previousReady != null && !previousReady.sourcesPending,
            fetchedAtEpochMs = session.fetchedAtEpochMs,
            nowEpochMs = now,
        )
        val playbackActive = PlaybackActivity.tracker.isActive
        CatalogPerformanceDiagnostics.event(
            name = "refresh_decision",
            fields = "trigger=${trigger.logName} decision=${decision.logName} " +
                "age_min=${CatalogRefreshPolicy.ageMinutes(session.fetchedAtEpochMs, now) ?: "unknown"} " +
                "playback_active=$playbackActive",
            warning = playbackActive && !trigger.isAutomatic,
        )

        if (!decision.shouldRefresh && previousReady != null) {
            // Fresh validated cache: no download, no parsing, no rewrite. Only
            // the profile's parental locks are re-applied.
            val locks = parentalRepository.loadContentLocks(profileId).getOrNull()
            val raw = previousReady.rawSnapshot
            val visible = withContext(Dispatchers.Default) {
                applyParentalCatalogPolicy(snapshot = raw, locks = locks)
            }
            state.value = previousReady.copy(
                snapshot = visible,
                contentLocks = locks,
                syncWarning = null,
                isRefreshing = false,
            )
            session.pendingTrigger = null
            return@LaunchedEffect
        }

        if (CatalogRefreshPolicy.mustWaitForPlayback(trigger, playbackActive)) {
            // Nothing has started yet: wait for the end of playback instead of
            // competing with Media3 for CPU, memory and bandwidth.
            previousReady?.let { state.value = it.copy(isRefreshing = false) }
            val waitStartedAt = CatalogPerformanceDiagnostics.startedAt()
            PlaybackActivity.tracker.awaitIdle()
            CatalogPerformanceDiagnostics.phase(name = "refresh_deferred_for_playback", startedAtMs = waitStartedAt)
        }
        previousReady?.let { state.value = it.copy(isRefreshing = true, syncWarning = null) }

        val previousCatalog = previousReady
        val idAliases = session.idAliases
        val outcome = ProviderCatalogSyncCoordinator.run(profileId) {
            refreshCatalog(
                repository = repository,
                m3uClient = m3uClient,
                previous = previousCatalog,
                idAliases = idAliases,
            )
        }
        val loaded = outcome.state
        val locks = parentalRepository.loadContentLocks(profileId).getOrNull()

        state.value = when (loaded) {
            is ProviderCatalogState.Ready -> {
                val filtered = withContext(Dispatchers.Default) {
                    applyParentalCatalogPolicy(
                        snapshot = loaded.snapshot,
                        locks = locks,
                    )
                }
                // Bloc #213: the store keeps the raw catalogue; parental
                // rules are applied when it is read (PR B).
                val raw = CachedCatalog(
                    playlistId = loaded.playlistId,
                    playlistName = loaded.playlistName,
                    snapshot = loaded.snapshot,
                    seriesDetails = M3uSeriesDetailRegistry.snapshot(),
                )
                val sourcesPending = withContext(Dispatchers.Default) { !raw.sourceReport.isPlayable }
                // PR #217: the validated catalogue is shown now, from memory
                // (its URLs are in it); writing it to the store no longer
                // delays the home screen. The write outlives this effect.
                catalogStore.persistRefresh(
                    profileId = profileId,
                    rawCatalog = raw,
                    fetchedAtEpochMs = outcome.fetchedAtEpochMs,
                    idAliases = outcome.idAliases,
                ) { stored ->
                    if (stored) {
                        // The new generation is complete, verified and active:
                        // the V1 file cache is obsolete.
                        offlineCache.deleteCatalogFiles(profileId)
                    } else {
                        // Fallback: keep a V1 cache. saveCatalog only replaces it
                        // with a catalog whose every source survived (bloc #208),
                        // then attests it with its fetch date (bloc #211).
                        val visibleSeriesIds = filtered.series.mapTo(HashSet()) { it.id }
                        offlineCache.saveCatalog(
                            profileId = profileId,
                            catalog = CachedCatalog(
                                playlistId = loaded.playlistId,
                                playlistName = loaded.playlistName,
                                snapshot = filtered,
                                seriesDetails = raw.seriesDetails.filterKeys(visibleSeriesIds::contains),
                            ),
                            fetchedAtEpochMs = outcome.fetchedAtEpochMs,
                            idAliases = outcome.idAliases,
                        )
                    }
                }
                session.readyProfileId = profileId
                session.fetchedAtEpochMs = outcome.fetchedAtEpochMs
                session.idAliases = outcome.idAliases
                loaded.copy(
                    snapshot = filtered,
                    rawSnapshot = loaded.snapshot,
                    contentLocks = locks,
                    sourcesPending = sourcesPending,
                    isRefreshing = false,
                )
            }
            is ProviderCatalogState.Error -> {
                if (previousReady != null) {
                    // The catalogue already validated stays (error, cancellation
                    // or rejected partial response alike).
                    previousReady.copy(
                        syncWarning = loaded.message,
                        isRefreshing = false,
                    )
                } else {
                    val restored = ProviderCatalogCacheCoordinator.load(profileId) {
                        restoreCatalogForSession(profileId, catalogStore, offlineCache)
                    }?.restored
                    if (restored != null) {
                        val cached = restored.catalog
                        session.readyProfileId = profileId
                        session.fetchedAtEpochMs = restored.fetchedAtEpochMs
                        ProviderCatalogState.Ready(
                            playlistId = cached.playlistId,
                            playlistName = cached.playlistName,
                            snapshot = cached.snapshot,
                            rawSnapshot = cached.snapshot,
                            isFromCache = true,
                            sourcesPending = !cached.isPlayable,
                            syncWarning = loaded.message,
                            isRefreshing = false,
                        )
                    } else {
                        loaded
                    }
                }
            }
            else -> {
                session.readyProfileId = null
                loaded
            }
        }
        session.pendingTrigger = null
    }

    return remember(state) {
        ProviderCatalogSession(
            state = state,
            onReload = { trigger ->
                session.pendingTrigger = trigger.strongest(session.pendingTrigger)
                reloadToken += 1
            },
        )
    }
}

/** What the session remembers between reloads; never displayed. */
private class CatalogSessionMemory {
    /** Strongest reason requested since the last completed reload. */
    @Volatile var pendingTrigger: CatalogRefreshTrigger? = null

    /** Profile whose catalogue is in the Ready state. */
    @Volatile var readyProfileId: String? = null

    /** Authenticated fetch date of the catalogue shown, null when unknown. */
    @Volatile var fetchedAtEpochMs: Long? = null

    @Volatile var idAliases: Map<String, Long> = emptyMap()
}

private suspend fun refreshCatalog(
    repository: SupabaseCloudSyncRepository,
    m3uClient: AndroidM3uClient,
    previous: ProviderCatalogState.Ready?,
    idAliases: Map<String, Long>,
): CatalogRefreshOutcome {
    val playlists = repository.listPlaylists().getOrElse {
        return CatalogRefreshOutcome(
            ProviderCatalogState.Error(
                "Impossible de récupérer les playlists de votre compte.",
            ),
        )
    }
    val playlist = playlists
        .asSequence()
        .filter { it.isEnabled && it.secretStatus == "configured" }
        .minWithOrNull(compareBy<SyncedPlaylist> { it.priority }.thenBy { it.name.lowercase() })
        ?: return CatalogRefreshOutcome(
            ProviderCatalogState.Empty(
                "Aucune playlist active et configurée n’est disponible.",
            ),
        )
    val secret = repository.getPlaylistSecret(playlist.id).getOrElse {
        return CatalogRefreshOutcome(
            ProviderCatalogState.Error(
                "Impossible de restaurer la configuration sécurisée de la playlist.",
            ),
        )
    } ?: return CatalogRefreshOutcome(
        ProviderCatalogState.Error(
            "La configuration sécurisée de cette playlist est absente.",
        ),
    )
    // Only a catalogue of the same playlist can make an empty list suspicious.
    val previousSnapshot = previous?.takeIf { it.playlistId == playlist.id }?.rawSnapshot
    return loadCatalog(playlist, secret, m3uClient, previousSnapshot, idAliases)
}

private suspend fun loadCatalog(
    playlist: SyncedPlaylist,
    secret: PlaylistSecret,
    m3uClient: AndroidM3uClient,
    previousSnapshot: CatalogSnapshot?,
    idAliases: Map<String, Long>,
): CatalogRefreshOutcome {
    var resolvedAliases = emptyMap<String, Long>()
    val snapshot = when (secret) {
        is PlaylistSecret.Xtream -> {
            val result = AndroidXtreamCatalogLoader(
                credentials = XtreamCredentials(
                    serverUrl = secret.serverUrl,
                    username = secret.username,
                    password = secret.password,
                ),
            ).load(previous = previousSnapshot)

            when (result) {
                is CatalogLoadResult.Success -> {
                    // Cleared only now: a failed refresh keeps the episode index
                    // of the catalogue still displayed (bloc #211).
                    M3uSeriesDetailRegistry.clear()
                    result.snapshot
                }
                is CatalogLoadResult.Failure -> {
                    return CatalogRefreshOutcome(ProviderCatalogState.Error(result.message))
                }
            }
        }

        is PlaylistSecret.M3u -> {
            // A fresh builder per attempt: a retried download starts from zero.
            var builder = M3uCatalogMapper.builder(idIncumbents = idAliases)
            val result = m3uClient.importStreaming(
                source = M3uSource(secret.url),
                onAttemptStart = { builder = M3uCatalogMapper.builder(idIncumbents = idAliases) },
                onEntry = { entry -> builder.add(entry) },
            )
            when (result) {
                is M3uStreamingResult.Success -> {
                    val startedAt = CatalogPerformanceDiagnostics.startedAt()
                    builder.build().also {
                        resolvedAliases = builder.idAliases
                        CatalogPerformanceDiagnostics.phase(
                            name = "m3u_map_finalize",
                            startedAtMs = startedAt,
                            itemCount = result.totalParsed,
                        )
                        if (resolvedAliases.isNotEmpty()) {
                            CatalogPerformanceDiagnostics.event(
                                name = "m3u_shared_ids",
                                fields = "groups=${resolvedAliases.size}",
                            )
                        }
                    }
                }
                is M3uStreamingResult.Failure -> {
                    return CatalogRefreshOutcome(ProviderCatalogState.Error(result.message))
                }
            }
        }
    }

    return CatalogRefreshOutcome(
        state = ProviderCatalogState.Ready(
            playlistId = playlist.id,
            playlistName = playlist.name,
            snapshot = snapshot,
        ),
        idAliases = resolvedAliases,
        fetchedAtEpochMs = System.currentTimeMillis(),
    )
}

private fun applyParentalCatalogPolicy(
    snapshot: CatalogSnapshot,
    locks: ProfileContentLocks?,
): CatalogSnapshot {
    if (locks == null || !locks.parentalEnabled || !locks.isChild) return snapshot

    val liveAdultCategories = snapshot.liveCategories
        .filter { isAdultCategory(it.name) }
        .mapTo(mutableSetOf()) { it.id }
    val movieAdultCategories = snapshot.movieCategories
        .filter { isAdultCategory(it.name) }
        .mapTo(mutableSetOf()) { it.id }
    val seriesAdultCategories = snapshot.seriesCategories
        .filter { isAdultCategory(it.name) }
        .mapTo(mutableSetOf()) { it.id }

    val hiddenCategoryKeys = if (locks.hideLocked) locks.lockedCategoryKeys else emptySet()
    val hiddenContentKeys = if (locks.hideLocked) locks.lockedContentKeys else emptySet()

    fun categoryHidden(kind: String, categoryId: String?): Boolean =
        categoryId != null && (kind + ":" + categoryId) in hiddenCategoryKeys

    return snapshot.copy(
        liveCategories = snapshot.liveCategories.filterNot {
            it.id in liveAdultCategories || "live:" + it.id in hiddenCategoryKeys
        },
        liveChannels = snapshot.liveChannels.filterNot {
            it.categoryId in liveAdultCategories ||
                categoryHidden("live", it.categoryId) ||
                "live:" + it.id in hiddenContentKeys
        },
        movieCategories = snapshot.movieCategories.filterNot {
            it.id in movieAdultCategories || "movie:" + it.id in hiddenCategoryKeys
        },
        movies = snapshot.movies.filterNot {
            it.categoryId in movieAdultCategories ||
                categoryHidden("movie", it.categoryId) ||
                "movie:" + it.id in hiddenContentKeys
        },
        seriesCategories = snapshot.seriesCategories.filterNot {
            it.id in seriesAdultCategories || "series:" + it.id in hiddenCategoryKeys
        },
        series = snapshot.series.filterNot {
            it.categoryId in seriesAdultCategories ||
                categoryHidden("series", it.categoryId) ||
                "series:" + it.id in hiddenContentKeys
        },
    )
}

private fun isAdultCategory(name: String): Boolean {
    val normalized = name
        .lowercase()
        .replace("é", "e")
        .replace("è", "e")
        .replace("ê", "e")
        .replace("à", "a")
        .replace("â", "a")
        .replace("î", "i")
        .replace("ï", "i")
        .replace("ô", "o")
        .replace("ù", "u")
        .replace("û", "u")

    return ADULT_CATEGORY_TOKENS.any { token -> normalized.contains(token) }
}

private val ADULT_CATEGORY_TOKENS = listOf(
    "adult",
    "adulte",
    "xxx",
    "porn",
    "erotic",
    "erotique",
    "18+",
    "+18",
)
