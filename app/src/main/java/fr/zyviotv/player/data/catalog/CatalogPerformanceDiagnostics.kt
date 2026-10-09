package fr.zyviotv.player.data.catalog

import android.os.SystemClock
import android.util.Log

/**
 * Anonymous catalog timings. Never pass provider URLs, credentials, tokens or titles here.
 */
object CatalogPerformanceDiagnostics {
    fun startedAt(): Long = SystemClock.elapsedRealtime()

    fun phase(
        name: String,
        startedAtMs: Long,
        itemCount: Int? = null,
    ) {
        val runtime = Runtime.getRuntime()
        val usedBytes = runtime.totalMemory() - runtime.freeMemory()
        val fields = buildString {
            append("catalog phase=")
            append(name)
            append(" duration_ms=")
            append((SystemClock.elapsedRealtime() - startedAtMs).coerceAtLeast(0L))
            if (itemCount != null) {
                append(" items=")
                append(itemCount.coerceAtLeast(0))
            }
            append(" heap_used_mb=")
            append(usedBytes / BYTES_PER_MEBIBYTE)
        }
        Log.i(TAG, fields)
    }

    /** Counts and failure kinds only: callers must never pass URLs or titles. */
    fun event(name: String, fields: String = "", warning: Boolean = false) {
        val message = "catalog event=" + name + if (fields.isBlank()) "" else " " + fields
        if (warning) Log.w(TAG, message) else Log.i(TAG, message)
    }

    private const val TAG = "ZyvioCatalog"
    private const val BYTES_PER_MEBIBYTE = 1024L * 1024L
}
