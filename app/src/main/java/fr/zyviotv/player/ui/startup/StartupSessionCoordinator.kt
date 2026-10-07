package fr.zyviotv.player.ui.startup

import fr.zyviotv.player.data.auth.SupabaseAuthRepository
import kotlinx.coroutines.delay

sealed interface StartupSessionResult {
    data object AuthRequired : StartupSessionResult
    data object SessionReady : StartupSessionResult
    data object OfflineReady : StartupSessionResult
    data object ConnectionRequired : StartupSessionResult
}

class StartupSessionCoordinator(
    private val repository: SupabaseAuthRepository,
    private val canUseOffline: () -> Boolean = { false },
) {
    suspend fun restore(): StartupSessionResult {
        val startedAtNanos = System.nanoTime()
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
            if (last == SupabaseAuthRepository.SessionRestoreResult.NetworkUnavailable) {
                val elapsedMs = (System.nanoTime() - startedAtNanos) / 1_000_000L
                if (canUseOffline() && elapsedMs >= OFFLINE_FALLBACK_AFTER_MS) {
                    return StartupSessionResult.OfflineReady
                }

                val untilOfflineMs = OFFLINE_FALLBACK_AFTER_MS - elapsedMs
                if (
                    canUseOffline() &&
                    untilOfflineMs > 0L &&
                    untilOfflineMs <= delayMs
                ) {
                    delay(untilOfflineMs)
                    return StartupSessionResult.OfflineReady
                }
            }

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

        return if (canUseOffline()) {
            StartupSessionResult.OfflineReady
        } else {
            StartupSessionResult.ConnectionRequired
        }
    }

    companion object {
        val RETRY_DELAYS_MS = longArrayOf(1_000L, 2_000L, 4_000L)
        const val OFFLINE_FALLBACK_AFTER_MS = 8_000L
    }
}
