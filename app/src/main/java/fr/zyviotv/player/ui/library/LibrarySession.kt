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
import fr.zyviotv.player.data.sync.SupabaseLibrarySyncRepository
import fr.zyviotv.player.shared.sync.FavoriteContentType
import fr.zyviotv.player.shared.sync.ProgressContentType
import fr.zyviotv.player.shared.sync.SyncedFavorite
import fr.zyviotv.player.shared.sync.SyncedWatchProgress
import fr.zyviotv.player.shared.sync.SyncResult

data class LibrarySnapshot(
    val favorites: List<SyncedFavorite> = emptyList(),
    val progress: List<SyncedWatchProgress> = emptyList(),
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
        val exists = ready.snapshot.favorites.any {
            it.playlistId == favorite.playlistId &&
                it.contentType == favorite.contentType &&
                it.contentId == favorite.contentId
        }

        val result = if (exists) {
            repository.removeFavorite(
                playlistId = favorite.playlistId,
                contentType = favorite.contentType,
                contentId = favorite.contentId,
            )
        } else {
            repository.upsertFavorite(favorite)
        }

        if (result is SyncResult.Success) {
            val next = if (exists) {
                ready.snapshot.favorites.filterNot {
                    it.playlistId == favorite.playlistId &&
                        it.contentType == favorite.contentType &&
                        it.contentId == favorite.contentId
                }
            } else {
                listOf(favorite) + ready.snapshot.favorites
            }
            updateState(ready.copy(snapshot = ready.snapshot.copy(favorites = next)))
        }
        return result
    }

    suspend fun saveProgress(progress: SyncedWatchProgress): SyncResult {
        val result = repository.upsertWatchProgress(progress)
        if (result is SyncResult.Success) {
            val ready = state.value as? LibraryState.Ready
            if (ready != null) {
                val next = listOf(progress) + ready.snapshot.progress.filterNot {
                    it.playlistId == progress.playlistId &&
                        it.contentType == progress.contentType &&
                        it.contentId == progress.contentId
                }
                updateState(
                    ready.copy(
                        snapshot = ready.snapshot.copy(
                            progress = next.take(MAX_PROGRESS),
                        ),
                    ),
                )
            }
        }
        return result
    }

    suspend fun removeProgress(progress: SyncedWatchProgress): SyncResult {
        val result = repository.removeWatchProgress(
            playlistId = progress.playlistId,
            contentType = progress.contentType,
            contentId = progress.contentId,
        )
        if (result is SyncResult.Success) {
            val ready = state.value as? LibraryState.Ready
            if (ready != null) {
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
    }
}

@Composable
fun rememberLibrarySession(): LibrarySession {
    val context = LocalContext.current
    val repository = remember(context.applicationContext) {
        SupabaseLibrarySyncRepository(
            sessionStore = SecureSessionStore(context.applicationContext),
        )
    }
    val state = remember { mutableStateOf<LibraryState>(LibraryState.Loading) }
    var reloadToken by remember { mutableIntStateOf(0) }

    LaunchedEffect(reloadToken) {
        state.value = LibraryState.Loading

        val favorites = repository.listFavorites().getOrElse {
            state.value = LibraryState.Error("Impossible de charger vos favoris.")
            return@LaunchedEffect
        }
        val progress = repository.listWatchProgress(limit = 200).getOrElse {
            state.value = LibraryState.Error("Impossible de charger votre progression.")
            return@LaunchedEffect
        }

        state.value = LibraryState.Ready(
            LibrarySnapshot(
                favorites = favorites,
                progress = progress,
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
