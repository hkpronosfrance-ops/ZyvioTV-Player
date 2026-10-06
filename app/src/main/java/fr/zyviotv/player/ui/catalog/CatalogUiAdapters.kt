package fr.zyviotv.player.ui.catalog

import fr.zyviotv.player.shared.catalog.CatalogSnapshot
import fr.zyviotv.player.ui.live.LiveChannelUi
import fr.zyviotv.player.ui.live.LiveScreenState
import fr.zyviotv.player.ui.movies.MovieCatalogItem
import fr.zyviotv.player.ui.movies.MoviesScreenState
import fr.zyviotv.player.ui.series.SeriesCatalogItem
import fr.zyviotv.player.ui.series.SeriesScreenState

fun ProviderCatalogState.toLiveState(): LiveScreenState =
    when (this) {
        ProviderCatalogState.Loading -> LiveScreenState.Loading
        is ProviderCatalogState.Error -> LiveScreenState.Error(message)
        is ProviderCatalogState.Empty -> LiveScreenState.Ready(emptyList())
        is ProviderCatalogState.Ready -> {
            val categories = snapshot.liveCategories.associate { it.id to it.name }
            LiveScreenState.Ready(
                snapshot.liveChannels.map { channel ->
                    LiveChannelUi(
                        id = channel.id,
                        name = channel.name,
                        category = channel.categoryId?.let(categories::get) ?: "Autres",
                    )
                },
            )
        }
    }

fun ProviderCatalogState.toMoviesState(): MoviesScreenState =
    when (this) {
        ProviderCatalogState.Loading -> MoviesScreenState.Loading
        is ProviderCatalogState.Error -> MoviesScreenState.Error(message)
        is ProviderCatalogState.Empty -> MoviesScreenState.Ready(emptyList(), emptyList())
        is ProviderCatalogState.Ready -> {
            val categories = snapshot.movieCategories.associate { it.id to it.name }
            MoviesScreenState.Ready(
                items = snapshot.movies.map { movie ->
                    MovieCatalogItem(
                        id = movie.id,
                        title = movie.title,
                        category = movie.categoryId?.let(categories::get) ?: "Autres",
                        posterUrl = movie.posterUrl,
                    )
                },
                categories = snapshot.movieCategories.map { it.name },
            )
        }
    }

fun ProviderCatalogState.toSeriesState(): SeriesScreenState =
    when (this) {
        ProviderCatalogState.Loading -> SeriesScreenState.Loading
        is ProviderCatalogState.Error -> SeriesScreenState.Error(message)
        is ProviderCatalogState.Empty -> SeriesScreenState.Ready(emptyList(), emptyList())
        is ProviderCatalogState.Ready -> {
            val categories = snapshot.seriesCategories.associate { it.id to it.name }
            SeriesScreenState.Ready(
                items = snapshot.series.map { series ->
                    SeriesCatalogItem(
                        id = series.id,
                        title = series.title,
                        category = series.categoryId?.let(categories::get) ?: "Autres",
                        posterUrl = series.posterUrl,
                    )
                },
                categories = snapshot.seriesCategories.map { it.name },
            )
        }
    }

fun ProviderCatalogState.snapshotOrEmpty(): CatalogSnapshot =
    when (this) {
        is ProviderCatalogState.Ready -> snapshot
        else -> CatalogSnapshot()
    }
