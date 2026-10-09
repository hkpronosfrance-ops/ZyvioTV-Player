package fr.zyviotv.player.ui.catalog

import fr.zyviotv.player.data.catalog.CatalogPerformanceDiagnostics
import fr.zyviotv.player.ui.live.LiveScreenState
import fr.zyviotv.player.ui.movies.MoviesScreenState
import fr.zyviotv.player.ui.series.SeriesScreenState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Bloc #211: the Live, Films and Séries screen states are built from the full
 * catalogue (tens of thousands of items). They used to be built on the main
 * thread each time a tab was opened. They are now built on a background
 * dispatcher and kept here, at the root of the app, so going back to a tab
 * reuses the previous result as long as the catalogue and progress are the
 * same objects.
 */
class CatalogUiStateCache(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private class Entry<V>(val keys: Array<out Any?>, val value: V) {
        fun matches(other: Array<out Any?>): Boolean =
            keys.size == other.size && keys.indices.all { keys[it] === other[it] }
    }

    @Volatile private var live: Entry<LiveScreenState>? = null
    @Volatile private var movies: Entry<MoviesScreenState>? = null
    @Volatile private var series: Entry<SeriesScreenState>? = null

    fun cachedLive(state: ProviderCatalogState): LiveScreenState? =
        live?.takeIf { it.matches(arrayOf(state)) }?.value

    fun cachedMovies(state: ProviderCatalogState, progress: Map<String, Float>): MoviesScreenState? =
        movies?.takeIf { it.matches(arrayOf(state, progress)) }?.value

    fun cachedSeries(state: ProviderCatalogState, progress: Map<String, Float>): SeriesScreenState? =
        series?.takeIf { it.matches(arrayOf(state, progress)) }?.value

    suspend fun live(state: ProviderCatalogState): LiveScreenState =
        cachedLive(state) ?: build("ui_map_live", state) { state.toLiveState() }
            .also { live = Entry(arrayOf(state), it) }

    suspend fun movies(state: ProviderCatalogState, progress: Map<String, Float>): MoviesScreenState =
        cachedMovies(state, progress) ?: build("ui_map_movies", state) { state.toMoviesState(progress) }
            .also { movies = Entry(arrayOf(state, progress), it) }

    suspend fun series(state: ProviderCatalogState, progress: Map<String, Float>): SeriesScreenState =
        cachedSeries(state, progress) ?: build("ui_map_series", state) { state.toSeriesState(progress) }
            .also { series = Entry(arrayOf(state, progress), it) }

    /** Placeholder states are trivial; only a ready catalogue moves off the main thread. */
    private suspend fun <V> build(phase: String, state: ProviderCatalogState, mapper: () -> V): V {
        if (state !is ProviderCatalogState.Ready) return mapper()
        return withContext(dispatcher) {
            val startedAt = CatalogPerformanceDiagnostics.startedAt()
            val value = mapper()
            val snapshot = state.snapshot
            val count = when (phase) {
                "ui_map_live" -> snapshot.liveChannels.size
                "ui_map_movies" -> snapshot.movies.size
                else -> snapshot.series.size
            }
            CatalogPerformanceDiagnostics.phase(phase, startedAt, count)
            value
        }
    }
}
