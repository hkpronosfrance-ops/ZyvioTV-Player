package fr.zyviotv.player.data.catalog

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update

/**
 * Counts the Media3 players currently alive so automatic catalogue
 * synchronisations can wait for the end of playback (bloc #211).
 */
class PlaybackActivityTracker {
    private val activePlayers = MutableStateFlow(0)

    val isActive: Boolean
        get() = activePlayers.value > 0

    /** Call when a player is created; close the returned handle once on release. */
    fun begin(): AutoCloseable {
        activePlayers.update { it + 1 }
        val closed = AtomicBoolean(false)
        return AutoCloseable {
            if (closed.compareAndSet(false, true)) {
                activePlayers.update { (it - 1).coerceAtLeast(0) }
            }
        }
    }

    /** Suspends until no player is active (returns at once when idle). */
    suspend fun awaitIdle() {
        activePlayers.first { it == 0 }
    }
}

/** Process-wide instance shared by the player and the catalogue session. */
object PlaybackActivity {
    val tracker = PlaybackActivityTracker()
}
