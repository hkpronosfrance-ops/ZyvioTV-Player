package fr.zyviotv.player.ui.catalog

import fr.zyviotv.player.shared.catalog.CatalogSnapshot
import fr.zyviotv.player.shared.catalog.DisplayTitle
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
            val lockedCategories = contentLocks?.lockedCategoryKeys.orEmpty()
            val lockedContents = contentLocks?.lockedContentKeys.orEmpty()
            LiveScreenState.Ready(
                channels = snapshot.liveChannels.map { channel ->
                    LiveChannelUi(
                        id = channel.id,
                        name = channel.name,
                        logoUrl = channel.logoUrl?.takeIf { it.isNotBlank() },
                        category = channel.categoryId?.let(categories::get) ?: "Autres",
                        isLocked = contentLocks?.parentalEnabled == true &&
                            contentLocks?.isChild == true &&
                            (
                                "live:" + channel.id in lockedContents ||
                                    channel.categoryId?.let { "live:" + it in lockedCategories } == true
                                ),
                    )
                },
                lockedCategories = snapshot.liveCategories
                    .filter { "live:" + it.id in lockedCategories }
                    .mapTo(mutableSetOf()) { it.name },
            )
        }
    }

fun ProviderCatalogState.toMoviesState(
    progressByMovieId: Map<String, Float> = emptyMap(),
): MoviesScreenState =
    when (this) {
        ProviderCatalogState.Loading -> MoviesScreenState.Loading
        is ProviderCatalogState.Error -> MoviesScreenState.Error(message)
        is ProviderCatalogState.Empty -> MoviesScreenState.Ready(emptyList(), emptyList())
        is ProviderCatalogState.Ready -> {
            val categories = snapshot.movieCategories.associate { it.id to it.name }
            val lockedCategories = contentLocks?.lockedCategoryKeys.orEmpty()
            val lockedContents = contentLocks?.lockedContentKeys.orEmpty()
            MoviesScreenState.Ready(
                items = snapshot.movies.map { movie ->
                    MovieCatalogItem(
                        id = movie.id,
                        title = DisplayTitle.clean(movie.title),
                        category = movie.categoryId?.let(categories::get) ?: "Autres",
                        posterUrl = movie.posterUrl,
                        progress = progressByMovieId[movie.id],
                        isLocked = contentLocks?.parentalEnabled == true &&
                            contentLocks?.isChild == true &&
                            (
                                "movie:" + movie.id in lockedContents ||
                                    movie.categoryId?.let { "movie:" + it in lockedCategories } == true
                                ),
                    )
                },
                categories = snapshot.movieCategories.map { it.name },
                lockedCategories = snapshot.movieCategories
                    .filter { "movie:" + it.id in lockedCategories }
                    .mapTo(mutableSetOf()) { it.name },
            )
        }
    }

fun ProviderCatalogState.toSeriesState(
    progressBySeriesId: Map<String, Float> = emptyMap(),
): SeriesScreenState =
    when (this) {
        ProviderCatalogState.Loading -> SeriesScreenState.Loading
        is ProviderCatalogState.Error -> SeriesScreenState.Error(message)
        is ProviderCatalogState.Empty -> SeriesScreenState.Ready(emptyList(), emptyList())
        // PR #219: series still arriving (first synchronisation), or failed after
        // channels and films were shown: never "no series available".
        is ProviderCatalogState.Ready -> if (seriesPending) {
            if (syncWarning != null) {
                SeriesScreenState.Error("La synchronisation des séries n’a pas abouti. Réessayez.")
            } else {
                SeriesScreenState.Loading
            }
        } else {
            val categories = snapshot.seriesCategories.associate { it.id to it.name }
            val lockedCategories = contentLocks?.lockedCategoryKeys.orEmpty()
            val lockedContents = contentLocks?.lockedContentKeys.orEmpty()
            SeriesScreenState.Ready(
                items = snapshot.series.map { series ->
                    SeriesCatalogItem(
                        id = series.id,
                        title = DisplayTitle.clean(series.title),
                        category = series.categoryId?.let(categories::get) ?: "Autres",
                        posterUrl = series.posterUrl,
                        progress = progressBySeriesId[series.id],
                        isLocked = contentLocks?.parentalEnabled == true &&
                            contentLocks?.isChild == true &&
                            (
                                "series:" + series.id in lockedContents ||
                                    series.categoryId?.let { "series:" + it in lockedCategories } == true
                                ),
                    )
                },
                categories = snapshot.seriesCategories.map { it.name },
                lockedCategories = snapshot.seriesCategories
                    .filter { "series:" + it.id in lockedCategories }
                    .mapTo(mutableSetOf()) { it.name },
            )
        }
    }

fun ProviderCatalogState.snapshotOrEmpty(): CatalogSnapshot =
    when (this) {
        is ProviderCatalogState.Ready -> snapshot
        else -> CatalogSnapshot()
    }
