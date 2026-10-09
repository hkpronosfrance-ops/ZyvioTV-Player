package fr.zyviotv.player.data.catalog

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async

/** Shares one active catalog refresh per profile without tying it to a screen lifecycle. */
internal class CatalogSingleFlight<T>(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val lock = Any()
    private val inFlight = mutableMapOf<String, Deferred<T>>()

    suspend fun run(key: String, loader: suspend () -> T): T {
        val task = synchronized(lock) {
            inFlight[key]?.takeIf { it.isActive }
                ?: scope.async(start = CoroutineStart.LAZY) { loader() }.also { created ->
                    inFlight[key] = created
                    // Bloc #211: the task removes itself when it ends, even if
                    // every caller was cancelled. A finished task left in the
                    // map kept a whole decoded catalogue reachable.
                    created.invokeOnCompletion {
                        synchronized(lock) {
                            if (inFlight[key] === created) inFlight.remove(key)
                        }
                    }
                    created.start()
                }
        }
        return task.await()
    }

    internal fun inFlightCount(): Int = synchronized(lock) { inFlight.size }
}
