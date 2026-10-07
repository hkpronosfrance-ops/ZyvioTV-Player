package fr.zyviotv.player.ui.startup

import fr.zyviotv.player.data.auth.SecureSessionStore
import fr.zyviotv.player.data.auth.SupabaseAuthRepository
import kotlinx.coroutines.delay

sealed interface StartupSessionResult {
    data object AuthRequired : StartupSessionResult
    data object SessionReady : StartupSessionResult
    data object ConnectionRequired : StartupSessionResult
}

class StartupSessionCoordinator(
    private val repository: SupabaseAuthRepository,
) {
    suspend fun restore(): StartupSessionResult {
        var last = repository.restoreSession()
        if (last == SupabaseAuthRepository.SessionRestoreResult.Valid) {
            return StartupSessionResult.SessionReady
        }
        if (
            last == SupabaseAuthRepository.SessionRestoreResult.NoSession ||
            last == SupabaseAuthRepository.SessionRestoreResult.Invalid
        ) {
            return StartupSessionResult.AuthRequired
        }

        for (delayMs in RETRY_DELAYS_MS) {
            delay(delayMs)
            last = repository.restoreSession()
            when (last) {
                SupabaseAuthRepository.SessionRestoreResult.Valid ->
                    return StartupSessionResult.SessionReady
                SupabaseAuthRepository.SessionRestoreResult.NoSession,
                SupabaseAuthRepository.SessionRestoreResult.Invalid,
                -> return StartupSessionResult.AuthRequired
                SupabaseAuthRepository.SessionRestoreResult.NetworkUnavailable -> Unit
            }
        }

        return StartupSessionResult.ConnectionRequired
    }

    companion object {
        val RETRY_DELAYS_MS = longArrayOf(1_000L, 2_000L, 4_000L)
    }
}
