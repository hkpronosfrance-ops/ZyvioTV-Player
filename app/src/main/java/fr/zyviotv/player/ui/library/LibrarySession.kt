package fr.zyviotv.player.ui.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import fr.zyviotv.player.data.auth.SecureSessionStore
import fr.zyviotv.player.data.settings.ProfilePreferences
import fr.zyviotv.player.data.settings.ProfileRepository
import fr.zyviotv.player.data.sync.SupabaseLibrarySyncRepository
import fr.zyviotv.player.data.sync.SyncedLiveHistory
import fr.zyviotv.player.shared.sync.FavoriteContentType
import fr.zyviotv.player.shared.sync.ProgressContentType
import fr.zyviotv.player.shared.sync.SyncedFavorite
import fr.zyviotv.player.shared.sync.SyncedWatchProgress
import fr.zyviotv.player.shared.sync.SyncResult

data class LibrarySnapshot(
    val profileId: String,
    val favorites: List<SyncedFavorite> = emptyList(),
    val progress: List<SyncedWatchProgress> = emptyList(),
    val liveHistory: List<SyncedLiveHistory> = emptyList(),
)

sealed interface LibraryState {
    data object Loading : LibraryState
    data class Ready(val snapshot: LibrarySnapshot) : LibraryState
    data class Error(val message: String) : LibraryState
}

class LibrarySession internal constructor(
    val state: State<LibraryState>,
    private val repository: SupabaseLibrarySyncRepository,
    private val reloadAction: () -> Unit,
    private val updateState: (LibraryState) -> Unit,
) {
    fun reload() = reloadAction()

    suspend fun toggleFavorite(favorite: SyncedFavorite): SyncResult {
        val ready = state.value as? LibraryState.Ready
            ?: return SyncResult.Failure("Bibliothèque indisponible.")
        val scopedFavorite = favorite.copy(profileId = ready.snapshot.profileId)
        val exists = ready.snapshot.favorites.any {
            it.playlistId == favorite.playlistId &&
                it.contentType == favorite.contentType &&
                it.contentId == favorite.contentId
        }

        val result = if (exists) {
            repository.removeFavorite(
                profileId = ready.snapshot.profileId,
                playlistId = favorite.playlistId,
                contentType = favorite.contentType,
                contentId = favorite.contentId,
            )
        } else {
            repository.upsertFavorite(scopedFavorite)
        }

        if (result is SyncResult.Success) {
            val next = if (exists) {
                ready.snapshot.favorites.filterNot {
                    it.playlistId == favorite.playlistId &&
                        it.contentType == favorite.contentType &&
                        it.contentId == favorite.contentId
                }
            } else {
                listOf(scopedFavorite) + ready.snapshot.favorites
            }
            updateState(ready.copy(snapshot = ready.snapshot.copy(favorites = next)))
        }
        return result
    }

    suspend fun saveProgress(progress: SyncedWatchProgress): SyncResult {
        val ready = state.value as? LibraryState.Ready
            ?: return SyncResult.Failure("Bibliothèque indisponible.")
        val scopedProgress = progress.copy(profileId = ready.snapshot.profileId)
        val result = repository.upsertWatchProgress(scopedProgress)
        if (result is SyncResult.Success) {
            val next = listOf(scopedProgress) + ready.snapshot.progress.filterNot {
                    it.playlistId == scopedProgress.playlistId &&
                        it.contentType == scopedProgress.contentType &&
                        it.contentId == scopedProgress.contentId
                }
            updateState(
                ready.copy(
                    snapshot = ready.snapshot.copy(
                        progress = next.take(MAX_PROGRESS),
                    ),
                ),
            )
        }
        return result
    }

    suspend fun recordLiveHistory(
        playlistId: String,
        channelId: String,
        channelName: String,
        logoUrl: String?,
    ): SyncResult {
        val ready = state.value as? LibraryState.Ready
            ?: return SyncResult.Failure("Bibliothèque indisponible.")
        val item = SyncedLiveHistory(
            profileId = ready.snapshot.profileId,
            playlistId = playlistId,
            channelId = channelId,
            channelName = channelName,
            logoUrl = logoUrl,
        )
        val result = repository.recordLiveHistory(item)
        if (result is SyncResult.Success) {
            val next = listOf(item) + ready.snapshot.liveHistory.filterNot {
                it.playlistId == playlistId && it.channelId == channelId
            }
            updateState(
                ready.copy(
                    snapshot = ready.snapshot.copy(
                        liveHistory = next.take(MAX_LIVE_HISTORY),
                    ),
                ),
            )
        }
        return result
    }

    suspend fun removeProgress(progress: SyncedWatchProgress): SyncResult {
        val ready = state.value as? LibraryState.Ready
            ?: return SyncResult.Failure("Bibliothèque indisponible.")
        val result = repository.removeWatchProgress(
            profileId = ready.snapshot.profileId,
            playlistId = progress.playlistId,
            contentType = progress.contentType,
            contentId = progress.contentId,
        )
        if (result is SyncResult.Success) {
            updateState(
                ready.copy(
                    snapshot = ready.snapshot.copy(
                        progress = ready.snapshot.progress.filterNot {
                            it.playlistId == progress.playlistId &&
                                it.contentType == progress.contentType &&
                                it.contentId == progress.contentId
                        },
                    ),
                ),
            )
        }
        return result
    }

    fun isFavorite(
        playlistId: String,
        type: FavoriteContentType,
        contentId: String,
    ): Boolean {
        val ready = state.value as? LibraryState.Ready ?: return false
        return ready.snapshot.favorites.any {
            it.playlistId == playlistId &&
                it.contentType == type &&
                it.contentId == contentId
        }
    }

    fun progressFor(
        playlistId: String,
        type: ProgressContentType,
        contentId: String,
    ): SyncedWatchProgress? {
        val ready = state.value as? LibraryState.Ready ?: return null
        return ready.snapshot.progress.firstOrNull {
            it.playlistId == playlistId &&
                it.contentType == type &&
                it.contentId == contentId
        }
    }

    private companion object {
        const val MAX_PROGRESS = 200
        const val MAX_LIVE_HISTORY = 50
    }
}

@Composable
fun rememberLibrarySession(): LibrarySession {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val sessionStore = remember(appContext) { SecureSessionStore(appContext) }
    val repository = remember(appContext) {
        SupabaseLibrarySyncRepository(sessionStore = sessionStore)
    }
    val profileRepository = remember(appContext) {
        ProfileRepository(sessionStore = sessionStore)
    }
    val profilePreferences = remember(appContext) {
        ProfilePreferences(appContext)
    }
    val state = remember { mutableStateOf<LibraryState>(LibraryState.Loading) }
    var reloadToken by remember { mutableIntStateOf(0) }

    LaunchedEffect(reloadToken) {
        state.value = LibraryState.Loading

        val primaryProfileId = profileRepository.ensurePrimaryProfile().getOrElse {
            state.value = LibraryState.Error("Impossible de préparer votre profil.")
            return@LaunchedEffect
        }
        val profiles = profileRepository.listProfiles().getOrElse {
            state.value = LibraryState.Error("Impossible de charger vos profils.")
            return@LaunchedEffect
        }

        val selectedProfileId = profilePreferences.selectedProfileId()
        val defaultProfileId = profilePreferences.defaultProfileId()
        val activeProfileId = profiles.firstOrNull { it.id == selectedProfileId }?.id
            ?: profiles.firstOrNull { it.id == defaultProfileId }?.id
            ?: profiles.firstOrNull { it.isPrimary }?.id
            ?: primaryProfileId

        if (selectedProfileId != activeProfileId) {
            profilePreferences.setSelectedProfileId(activeProfileId)
        }

        val favorites = repository.listFavorites(profileId = activeProfileId).getOrElse {
            state.value = LibraryState.Error("Impossible de charger vos favoris.")
            return@LaunchedEffect
        }
        val progress = repository.listWatchProgress(
            profileId = activeProfileId,
            limit = 200,
        ).getOrElse {
            state.value = LibraryState.Error("Impossible de charger votre progression.")
            return@LaunchedEffect
        }
        val liveHistory = repository.listLiveHistory(
            profileId = activeProfileId,
            limit = MAX_LIVE_HISTORY,
        ).getOrElse {
            state.value = LibraryState.Error("Impossible de charger vos chaînes récentes.")
            return@LaunchedEffect
        }

        state.value = LibraryState.Ready(
            LibrarySnapshot(
                profileId = activeProfileId,
                favorites = favorites,
                progress = progress,
                liveHistory = liveHistory,
            ),
        )
    }

    return remember(state, repository) {
        LibrarySession(
            state = state,
            repository = repository,
            reloadAction = { reloadToken += 1 },
            updateState = { state.value = it },
        )
    }
}
