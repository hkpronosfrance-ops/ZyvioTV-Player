package fr.zyviotv.player.ui.diagnostics

/**
 * Per-screen frame statistics (bloc #211), aggregated in memory and reported
 * as one line when the screen is left: never periodically.
 *
 * Thresholds follow Android vitals: a frame over [SLOW_FRAME_MS] is slow, one
 * of [FROZEN_FRAME_MS] or more is frozen. The 95th percentile is the upper
 * bound of a fixed histogram bucket, so memory stays constant.
 */
class FrameStatsAggregator {
    private var screen: String = "unknown"
    private var frames = 0L
    private var slow = 0L
    private var frozen = 0L
    private var maxMs = 0L
    private val buckets = LongArray(BUCKET_UPPER_MS.size + 1)

    @Synchronized
    fun onFrame(totalDurationNanos: Long) {
        if (totalDurationNanos <= 0L) return
        val durationMs = totalDurationNanos / 1_000_000L
        frames += 1
        if (durationMs > SLOW_FRAME_MS) slow += 1
        if (durationMs >= FROZEN_FRAME_MS) frozen += 1
        if (durationMs > maxMs) maxMs = durationMs
        val bucket = BUCKET_UPPER_MS.indexOfFirst { durationMs <= it }
        buckets[if (bucket < 0) BUCKET_UPPER_MS.size else bucket] += 1
    }

    /**
     * Starts a new screen. Returns the summary of the screen being left, or
     * null when nothing was drawn there.
     */
    @Synchronized
    fun switchTo(nextScreen: String?): String? {
        val summary = summaryOrNull()
        screen = screenName(nextScreen)
        frames = 0L
        slow = 0L
        frozen = 0L
        maxMs = 0L
        buckets.fill(0L)
        return summary
    }

    @Synchronized
    fun summaryOrNull(): String? {
        if (frames == 0L) return null
        return "frames screen=$screen total=$frames slow=$slow frozen=$frozen " +
            "max_ms=$maxMs p95_ms_le=${percentileUpperBound(0.95)}"
    }

    private fun percentileUpperBound(fraction: Double): String {
        val target = kotlin.math.ceil(frames * fraction).toLong()
        var seen = 0L
        buckets.forEachIndexed { index, count ->
            seen += count
            if (seen >= target) {
                return BUCKET_UPPER_MS.getOrNull(index)?.toString() ?: "more"
            }
        }
        return "more"
    }

    companion object {
        const val SLOW_FRAME_MS = 50L
        const val FROZEN_FRAME_MS = 700L
        private val BUCKET_UPPER_MS = longArrayOf(16L, 33L, 50L, 100L, 200L, 400L, 700L, 1_000L, 2_000L)

        /** Navigation routes are app constants; anything else is reduced. */
        internal fun screenName(route: String?): String {
            val value = route?.substringBefore('/')?.substringBefore('?')?.lowercase().orEmpty()
            return if (Regex("[a-z][a-z0-9_-]{0,40}").matches(value)) value else "unknown"
        }
    }
}
