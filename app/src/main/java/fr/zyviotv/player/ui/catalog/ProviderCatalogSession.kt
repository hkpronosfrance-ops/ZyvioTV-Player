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
import fr.zyviotv.player.data.catalog.AndroidXtreamCatalogLoader
import fr.zyviotv.player.data.catalog.M3uCatalogMapper
import fr.zyviotv.player.data.m3u.AndroidM3uClient
import fr.zyviotv.player.data.settings.ParentalControlsRepository
import fr.zyviotv.player.data.settings.ProfileContentLocks
import fr.zyviotv.player.data.settings.ProfilePreferences
import fr.zyviotv.player.data.sync.SupabaseCloudSyncRepository
import fr.zyviotv.player.shared.catalog.CatalogLoadResult
import fr.zyviotv.player.shared.catalog.CatalogSnapshot
import fr.zyviotv.player.shared.m3u.M3uImportResult
import fr.zyviotv.player.shared.m3u.M3uSource
import fr.zyviotv.player.shared.sync.PlaylistSecret
import fr.zyviotv.player.shared.sync.SyncedPlaylist
import fr.zyviotv.player.shared.xtream.XtreamCredentials

sealed interface ProviderCatalogState {
    data object Loading : ProviderCatalogState

    data class Ready(
        val playlistId: String,
        val playlistName: String,
        val snapshot: CatalogSnapshot,
        val rawSnapshot: CatalogSnapshot = snapshot,
        val contentLocks: ProfileContentLocks? = null,
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
    val m3uClient = remember { AndroidM3uClient() }

    val state = remember {
        mutableStateOf<ProviderCatalogState>(ProviderCatalogState.Loading)
    }
    var reloadToken by remember { mutableIntStateOf(0) }

    LaunchedEffect(reloadToken) {
        state.value = ProviderCatalogState.Loading

        val playlists = repository.listPlaylists().getOrElse {
            state.value = ProviderCatalogState.Error(
                "Impossible de récupérer les playlists de votre compte.",
            )
            return@LaunchedEffect
        }

        val playlist = playlists
            .filter { it.isEnabled && it.secretStatus == "configured" }
            .minWithOrNull(compareBy<SyncedPlaylist> { it.priority }.thenBy { it.name.lowercase() })

        if (playlist == null) {
            state.value = ProviderCatalogState.Empty(
                "Aucune playlist active et configurée n’est disponible.",
            )
            return@LaunchedEffect
        }

        val secret = repository.getPlaylistSecret(playlist.id).getOrElse {
            state.value = ProviderCatalogState.Error(
                "Impossible de restaurer la configuration sécurisée de la playlist.",
            )
            return@LaunchedEffect
        }

        if (secret == null) {
            state.value = ProviderCatalogState.Error(
                "La configuration sécurisée de cette playlist est absente.",
            )
            return@LaunchedEffect
        }

        val loaded = loadCatalog(
            playlist = playlist,
            secret = secret,
            m3uClient = m3uClient,
        )

        val profileId = profilePreferences.selectedProfileId()
        val locks = if (profileId != null) {
            parentalRepository.loadContentLocks(profileId).getOrNull()
        } else {
            null
        }

        state.value = when (loaded) {
            is ProviderCatalogState.Ready -> loaded.copy(
                snapshot = applyParentalCatalogPolicy(
                    snapshot = loaded.snapshot,
                    locks = locks,
                ),
                rawSnapshot = loaded.snapshot,
                contentLocks = locks,
            )
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

private suspend fun loadCatalog(
    playlist: SyncedPlaylist,
    secret: PlaylistSecret,
    m3uClient: AndroidM3uClient,
): ProviderCatalogState {
    val snapshot = when (secret) {
        is PlaylistSecret.Xtream -> {
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
            when (
                val result = m3uClient.import(
                    source = M3uSource(secret.url),
                    maxEntries = MAX_M3U_ENTRIES,
                )
            ) {
                is M3uImportResult.Success -> M3uCatalogMapper.map(result.entries)
                is M3uImportResult.Failure -> {
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

private const val MAX_M3U_ENTRIES = 20_000


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
