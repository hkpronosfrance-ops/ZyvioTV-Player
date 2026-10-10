package fr.zyviotv.player.data.catalog

/**
 * In-memory detail index for the currently mapped M3U catalog.
 *
 * The active provider session owns a single restored playlist, so replacing the
 * index on each M3U catalog import keeps series detail lookup aligned with the
 * active playlist without persisting credential-bearing stream URLs.
 *
 * Bloc #213 (PR B): a catalogue restored from the Room store keeps its episodes
 * on disk. The session then installs a [storedLookup] that reads one series at
 * a time; any import ([replace]) or [clear] removes it.
 */
object M3uSeriesDetailRegistry {
    private val details = LinkedHashMap<String, SeriesDetailSource>()

    @Volatile private var storedLookup: (suspend (String) -> SeriesDetailSource?)? = null

    @Synchronized
    fun replace(values: Map<String, SeriesDetailSource>) {
        details.clear()
        details.putAll(values)
        storedLookup = null
    }

    @Synchronized
    fun load(seriesId: String): SeriesDetailSource? = details[seriesId]

    /** In-memory index first, then the store of a catalogue restored from Room. */
    suspend fun loadLocal(seriesId: String): SeriesDetailSource? =
        load(seriesId) ?: storedLookup?.invoke(seriesId)

    /** Called by the session after restoring a catalogue from the store. */
    @Synchronized
    fun useStored(lookup: suspend (String) -> SeriesDetailSource?) {
        details.clear()
        storedLookup = lookup
    }

    @Synchronized
    fun snapshot(): Map<String, SeriesDetailSource> = LinkedHashMap(details)

    @Synchronized
    fun clear() {
        details.clear()
        storedLookup = null
    }
}
