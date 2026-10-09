package fr.zyviotv.player.data.catalog

/**
 * In-memory detail index for the currently mapped M3U catalog.
 *
 * The active provider session owns a single restored playlist, so replacing the
 * index on each M3U catalog import keeps series detail lookup aligned with the
 * active playlist without persisting credential-bearing stream URLs.
 */
object M3uSeriesDetailRegistry {
    private val details = LinkedHashMap<String, SeriesDetailSource>()

    @Synchronized
    fun replace(values: Map<String, SeriesDetailSource>) {
        details.clear()
        details.putAll(values)
    }

    @Synchronized
    fun load(seriesId: String): SeriesDetailSource? = details[seriesId]

    @Synchronized
    fun snapshot(): Map<String, SeriesDetailSource> = LinkedHashMap(details)

    @Synchronized
    fun clear() {
        details.clear()
    }
}
