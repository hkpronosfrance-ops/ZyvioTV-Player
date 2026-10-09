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
import fr.zyviotv.player.data.catalog.AndroidXtreamCatalogLoader
import fr.zyviotv.player.data.catalog.CatalogPerformanceDiagnostics
import fr.zyviotv.player.data.catalog.CatalogSingleFlight
import fr.zyviotv.player.data.catalog.M3uCatalogMapper
import fr.zyviotv.player.data.catalog.M3uSeriesDetailRegistry
import fr.zyviotv.player.data.m3u.AndroidM3uClient
import fr.zyviotv.player.data.m3u.M3uStreamingResult
import fr.zyviotv.player.data.settings.ParentalControlsRepository
import fr.zyviotv.player.data.settings.ProfileContentLocks
import fr.zyviotv.player.data.settings.ProfilePreferences
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
        val isOffline: Boolean = false,
        val syncWarning: String? = null,
        val isRefreshing: Boolean = false,
    ) : ProviderCatalogState

    data class Empty(val message: String) : ProviderCatalogState

    data class Error(val message: String) : ProviderCatalogState
}

class ProviderCatalogSession internal constructor(
    val state: State<ProviderCatalogState>,
    private val onReload: () -> Unit,
) {
    fun reload() = onReload()
}

private object ProviderCatalogSyncCoordinator {
    private val singleFlight = CatalogSingleFlight<ProviderCatalogState>()

    suspend fun run(
        key: String,
        loader: suspend () -> ProviderCatalogState,
    ): ProviderCatalogState {
        return singleFlight.run(key, loader)
    }
}

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

    val state = remember {
        mutableStateOf<ProviderCatalogState>(ProviderCatalogState.Loading)
    }
    var reloadToken by remember { mutableIntStateOf(0) }

    LaunchedEffect(reloadToken) {
        val profileId = profilePreferences.selectedProfileId()
        if (profileId.isNullOrBlank()) {
            state.value = ProviderCatalogState.Loading
            return@LaunchedEffect
        }

        var previousReady = state.value as? ProviderCatalogState.Ready
        if (previousReady == null) {
            val cached = withContext(Dispatchers.IO) { offlineCache.loadCatalog(profileId) }
            if (cached != null) {
                previousReady = ProviderCatalogState.Ready(
                    playlistId = cached.playlistId,
                    playlistName = cached.playlistName,
                    snapshot = cached.snapshot,
                    rawSnapshot = cached.snapshot,
                    isOffline = !cached.snapshot.hasPlaybackSources(),
                    isRefreshing = true,
                )
                state.value = previousReady
            } else {
                state.value = ProviderCatalogState.Loading
            }
        } else {
            state.value = previousReady.copy(isRefreshing = true, syncWarning = null)
        }

        val loaded = ProviderCatalogSyncCoordinator.run(profileId) {
            refreshCatalog(repository, m3uClient)
        }
        val locks = parentalRepository.loadContentLocks(profileId).getOrNull()

        state.value = when (loaded) {
            is ProviderCatalogState.Ready -> {
                val filtered = applyParentalCatalogPolicy(
                    snapshot = loaded.snapshot,
                    locks = locks,
                )
                withContext(Dispatchers.IO) {
                    val visibleSeriesIds = filtered.series.mapTo(HashSet()) { it.id }
                    offlineCache.saveCatalog(
                        profileId = profileId,
                        catalog = CachedCatalog(
                            playlistId = loaded.playlistId,
                            playlistName = loaded.playlistName,
                            snapshot = filtered,
                            seriesDetails = M3uSeriesDetailRegistry.snapshot()
                                .filterKeys(visibleSeriesIds::contains),
                        ),
                    )
                }
                loaded.copy(
                    snapshot = filtered,
                    rawSnapshot = loaded.snapshot,
                    contentLocks = locks,
                    isRefreshing = false,
                )
            }
            is ProviderCatalogState.Error -> {
                if (previousReady != null) {
                    previousReady.copy(
                        syncWarning = loaded.message,
                        isRefreshing = false,
                    )
                } else {
                    val cached = withContext(Dispatchers.IO) { offlineCache.loadCatalog(profileId) }
                    if (cached != null) {
                        ProviderCatalogState.Ready(
                            playlistId = cached.playlistId,
                            playlistName = cached.playlistName,
                            snapshot = cached.snapshot,
                            rawSnapshot = cached.snapshot,
                            isOffline = !cached.snapshot.hasPlaybackSources(),
                            syncWarning = loaded.message,
                            isRefreshing = false,
                        )
                    } else {
                        loaded
                    }
                }
            }
            else -> loaded
        }
    }

    return remember(state) {
        ProviderCatalogSession(
            state = state,
            onReload = { reloadToken += 1 },
        )
    }
}

private suspend fun refreshCatalog(
    repository: SupabaseCloudSyncRepository,
    m3uClient: AndroidM3uClient,
): ProviderCatalogState {
    val playlists = repository.listPlaylists().getOrElse {
        return ProviderCatalogState.Error(
            "Impossible de récupérer les playlists de votre compte.",
        )
    }
    val playlist = playlists
        .asSequence()
        .filter { it.isEnabled && it.secretStatus == "configured" }
        .minWithOrNull(compareBy<SyncedPlaylist> { it.priority }.thenBy { it.name.lowercase() })
        ?: return ProviderCatalogState.Empty(
            "Aucune playlist active et configurée n’est disponible.",
        )
    val secret = repository.getPlaylistSecret(playlist.id).getOrElse {
        return ProviderCatalogState.Error(
            "Impossible de restaurer la configuration sécurisée de la playlist.",
        )
    } ?: return ProviderCatalogState.Error(
        "La configuration sécurisée de cette playlist est absente.",
    )
    return loadCatalog(playlist, secret, m3uClient)
}

private suspend fun loadCatalog(
    playlist: SyncedPlaylist,
    secret: PlaylistSecret,
    m3uClient: AndroidM3uClient,
): ProviderCatalogState {
    val snapshot = when (secret) {
        is PlaylistSecret.Xtream -> {
            M3uSeriesDetailRegistry.clear()
            val result = AndroidXtreamCatalogLoader(
                credentials = XtreamCredentials(
                    serverUrl = secret.serverUrl,
                    username = secret.username,
                    password = secret.password,
                ),
            ).load()

            when (result) {
                is CatalogLoadResult.Success -> result.snapshot
                is CatalogLoadResult.Failure -> {
                    return ProviderCatalogState.Error(result.message)
                }
            }
        }

        is PlaylistSecret.M3u -> {
            val builder = M3uCatalogMapper.builder()
            when (val result = m3uClient.importStreaming(M3uSource(secret.url), builder::add)) {
                is M3uStreamingResult.Success -> {
                    val startedAt = CatalogPerformanceDiagnostics.startedAt()
                    builder.build().also {
                        CatalogPerformanceDiagnostics.phase(
                            name = "m3u_map_finalize",
                            startedAtMs = startedAt,
                            itemCount = result.totalParsed,
                        )
                    }
                }
                is M3uStreamingResult.Failure -> {
                    return ProviderCatalogState.Error(result.message)
                }
            }
        }
    }

    return ProviderCatalogState.Ready(
        playlistId = playlist.id,
        playlistName = playlist.name,
        snapshot = snapshot,
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

private fun CatalogSnapshot.hasPlaybackSources(): Boolean =
    liveChannels.any { it.streamUrl.isNotBlank() } ||
        movies.any { it.streamUrl.isNotBlank() } ||
        M3uSeriesDetailRegistry.snapshot().values.any { detail ->
            detail.episodes.any { it.streamUrl.isNotBlank() }
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
