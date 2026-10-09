package fr.zyviotv.player.data.catalog

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Shares one active catalog refresh per profile without tying it to a screen lifecycle. */
internal class CatalogSingleFlight<T>(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val mutex = Mutex()
    private val inFlight = mutableMapOf<String, Deferred<T>>()

    suspend fun run(key: String, loader: suspend () -> T): T {
        val task = mutex.withLock {
            inFlight[key]?.takeIf { it.isActive }
                ?: scope.async { loader() }.also { inFlight[key] = it }
        }
        return try {
            task.await()
        } finally {
            if (task.isCompleted) {
                mutex.withLock {
                    if (inFlight[key] === task) inFlight.remove(key)
                }
            }
        }
    }
}
