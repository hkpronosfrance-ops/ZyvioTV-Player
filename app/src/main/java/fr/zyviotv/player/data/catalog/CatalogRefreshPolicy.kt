package fr.zyviotv.player.data.catalog

/** Why a catalog reload was requested (logged by name, never with data). */
enum class CatalogRefreshTrigger(val logName: String, val isAutomatic: Boolean, private val rank: Int) {
    /** App start or profile gate: follows the freshness rule. */
    Startup("startup", isAutomatic = true, rank = 0),

    /** Another profile was picked: its own cache and freshness apply. */
    ProfileChanged("profile", isAutomatic = true, rank = 1),

    /** Retry button: the person asked for a new synchronisation. */
    Retry("retry", isAutomatic = false, rank = 2),

    /** Parental settings changed: the visible catalogue must be rebuilt. */
    Parental("parental", isAutomatic = false, rank = 3),

    /** A playlist was added, edited, enabled or removed. */
    PlaylistChanged("playlist", isAutomatic = false, rank = 4),
    ;

    /** Several reloads requested before the session ran keep the strongest reason. */
    fun strongest(other: CatalogRefreshTrigger?): CatalogRefreshTrigger =
        if (other == null || rank >= other.rank) this else other
}

enum class CatalogRefreshDecision(val logName: String, val shouldRefresh: Boolean) {
    UseFreshCache("use_fresh_cache", shouldRefresh = false),
    NoUsableCache("no_usable_cache", shouldRefresh = true),
    UnknownAge("unknown_age", shouldRefresh = true),
    FutureDate("future_date", shouldRefresh = true),
    Stale("stale", shouldRefresh = true),
    Requested("requested", shouldRefresh = true),
}

/**
 * Bloc #211: the catalogue is no longer downloaded and re-parsed at every
 * start. An automatic reload uses the encrypted cache while its authenticated
 * fetch date is younger than [MAX_AUTOMATIC_AGE_MS]; manual reloads always run.
 */
object CatalogRefreshPolicy {
    const val MAX_AUTOMATIC_AGE_MS: Long = 12L * 60L * 60L * 1_000L

    /** Clock adjustments smaller than this are not treated as a future date. */
    const val CLOCK_TOLERANCE_MS: Long = 5L * 60L * 1_000L

    /**
     * @param hasPlayableCache a decoded cache whose every source is present.
     * @param fetchedAtEpochMs authenticated fetch date of that exact cache file,
     *   or null when unknown (cache written before #211, metadata missing or
     *   not matching the file).
     */
    fun decide(
        trigger: CatalogRefreshTrigger,
        hasPlayableCache: Boolean,
        fetchedAtEpochMs: Long?,
        nowEpochMs: Long,
    ): CatalogRefreshDecision {
        if (!trigger.isAutomatic) return CatalogRefreshDecision.Requested
        if (!hasPlayableCache) return CatalogRefreshDecision.NoUsableCache
        if (fetchedAtEpochMs == null || fetchedAtEpochMs <= 0L) return CatalogRefreshDecision.UnknownAge
        val ageMs = nowEpochMs - fetchedAtEpochMs
        if (ageMs < -CLOCK_TOLERANCE_MS) return CatalogRefreshDecision.FutureDate
        return if (ageMs < MAX_AUTOMATIC_AGE_MS) {
            CatalogRefreshDecision.UseFreshCache
        } else {
            CatalogRefreshDecision.Stale
        }
    }

    /**
     * Automatic synchronisations never start while a video plays: they wait
     * until playback ends. A synchronisation already running is not suspended
     * (a paused parser would keep its whole graph in memory). Manual ones run
     * at once and are logged with a warning when playback is active.
     */
    fun mustWaitForPlayback(trigger: CatalogRefreshTrigger, playbackActive: Boolean): Boolean =
        trigger.isAutomatic && playbackActive

    /** Rounded age for logs; null when unknown. */
    fun ageMinutes(fetchedAtEpochMs: Long?, nowEpochMs: Long): Long? =
        fetchedAtEpochMs?.takeIf { it > 0L }?.let { (nowEpochMs - it) / 60_000L }
}
