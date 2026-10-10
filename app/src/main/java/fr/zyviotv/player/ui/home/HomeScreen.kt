package fr.zyviotv.player.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.zyviotv.player.shared.catalog.CatalogLiveChannel
import fr.zyviotv.player.shared.catalog.CatalogMovie
import fr.zyviotv.player.shared.catalog.CatalogSeries
import fr.zyviotv.player.shared.sync.FavoriteContentType
import fr.zyviotv.player.shared.sync.ProgressContentType
import fr.zyviotv.player.shared.sync.SyncedWatchProgress
import fr.zyviotv.player.ui.DeviceProfile
import fr.zyviotv.player.ui.theme.ZyvioBase
import fr.zyviotv.player.ui.theme.ZyvioRedTint
import fr.zyviotv.player.ui.theme.ZyvioSurface1
import fr.zyviotv.player.ui.theme.ZyvioSurface2
import fr.zyviotv.player.ui.theme.ZyvioSpace
import fr.zyviotv.player.ui.catalog.ProviderCatalogState
import fr.zyviotv.player.ui.library.LibraryState
import fr.zyviotv.player.ui.tv.tvFocusEffect
import coil.compose.AsyncImage

private data class HomeCardUi(
    val title: String,
    val poster: Boolean,
    val artworkUrl: String? = null,
    val progress: Float? = null,
    val onClick: () -> Unit,
)

@Composable
fun HomeScreen(
    profile: DeviceProfile,
    providerState: ProviderCatalogState,
    libraryState: LibraryState,
    nextEpisodes: List<HomeNextEpisode>,
    onPlayNextEpisode: (HomeNextEpisode) -> Unit,
    onTuneRecentChannel: (CatalogLiveChannel) -> Unit,
    onResumeProgress: (SyncedWatchProgress) -> Unit,
    onOpenMovieItem: (CatalogMovie) -> Unit,
    onOpenSeriesItem: (CatalogSeries) -> Unit,
    onOpenLive: () -> Unit,
    onOpenMovies: () -> Unit,
    onOpenSeries: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenContinueWatching: () -> Unit,
    onOpenHistory: () -> Unit,
    isOffline: Boolean = false,
) {
    val contentPadding = if (profile == DeviceProfile.Mobile) ZyvioSpace.s1 else ZyvioSpace.s3
    val readyProvider = providerState as? ProviderCatalogState.Ready
    val readyLibrary = libraryState as? LibraryState.Ready

    // Bloc #211: everything derived from the whole catalogue is computed once
    // per snapshot, not on every recomposition of the home screen.
    val catalogSnapshot = readyProvider?.snapshot
    val allowedMovieIds = remember(catalogSnapshot) {
        catalogSnapshot?.movies?.mapTo(hashSetOf()) { it.id }.orEmpty()
    }
    val allowedSeriesIds = remember(catalogSnapshot) {
        catalogSnapshot?.series?.mapTo(hashSetOf()) { it.id }.orEmpty()
    }
    val allowedLiveIds = remember(catalogSnapshot) {
        catalogSnapshot?.liveChannels?.mapTo(hashSetOf()) { it.id }.orEmpty()
    }

    val filteredProgress = readyLibrary?.snapshot?.progress
        .orEmpty()
        .filter { progress ->
            progress.playlistId == readyProvider?.playlistId &&
                !progress.completed &&
                progress.positionMs > 0L &&
                when (progress.contentType) {
                    ProgressContentType.Movie -> progress.contentId in allowedMovieIds
                    ProgressContentType.Episode ->
                        progress.seriesId != null && progress.seriesId in allowedSeriesIds
                }
        }

    val continueItems = filteredProgress
        .asSequence()
        .take(MAX_HOME_ITEMS)
        .map {
            HomeCardUi(
                title = it.title,
                poster = it.contentType != ProgressContentType.Movie || it.artworkUrl != null,
                artworkUrl = it.artworkUrl,
                progress = it.fraction.takeIf { fraction -> fraction > 0f },
                onClick = { onResumeProgress(it) },
            )
        }
        .toList()

    val filteredFavorites = readyLibrary?.snapshot?.favorites
        .orEmpty()
        .filter { favorite ->
            favorite.playlistId == readyProvider?.playlistId &&
                when (favorite.contentType) {
                FavoriteContentType.Live -> favorite.contentId in allowedLiveIds
                FavoriteContentType.Movie -> favorite.contentId in allowedMovieIds
                FavoriteContentType.Series -> favorite.contentId in allowedSeriesIds
            }
        }

    val favoriteItems = filteredFavorites
        .take(MAX_HOME_ITEMS)
        .mapNotNull { favorite ->
            when (favorite.contentType) {
                FavoriteContentType.Live -> readyProvider?.snapshot?.liveChannels
                    ?.firstOrNull { it.id == favorite.contentId }
                    ?.let { channel ->
                        HomeCardUi(
                            title = favorite.title,
                            poster = false,
                            artworkUrl = channel.logoUrl,
                            onClick = { onTuneRecentChannel(channel) },
                        )
                    }

                FavoriteContentType.Movie -> readyProvider?.snapshot?.movies
                    ?.firstOrNull { it.id == favorite.contentId }
                    ?.let { movie ->
                        HomeCardUi(
                            title = favorite.title,
                            poster = true,
                            artworkUrl = movie.posterUrl,
                            onClick = { onOpenMovieItem(movie) },
                        )
                    }

                FavoriteContentType.Series -> readyProvider?.snapshot?.series
                    ?.firstOrNull { it.id == favorite.contentId }
                    ?.let { series ->
                        HomeCardUi(
                            title = favorite.title,
                            poster = true,
                            artworkUrl = series.posterUrl,
                            onClick = { onOpenSeriesItem(series) },
                        )
                    }
            }
        }

    val recentMovies = remember(catalogSnapshot) {
        catalogSnapshot?.movies
            .orEmpty()
            .filter { it.addedAtEpochSeconds != null }
            .sortedByDescending { it.addedAtEpochSeconds }
    }
    val recentMovieItems = recentMovies
        .take(MAX_HOME_ITEMS)
        .map { movie ->
            HomeCardUi(
                title = movie.title,
                poster = true,
                artworkUrl = movie.posterUrl,
                onClick = { onOpenMovieItem(movie) },
            )
        }

    val recentSeries = remember(catalogSnapshot) {
        catalogSnapshot?.series
            .orEmpty()
            .filter { it.addedAtEpochSeconds != null }
            .sortedByDescending { it.addedAtEpochSeconds }
    }
    val recentSeriesItems = recentSeries
        .take(MAX_HOME_ITEMS)
        .map { series ->
            HomeCardUi(
                title = series.title,
                poster = true,
                artworkUrl = series.posterUrl,
                onClick = { onOpenSeriesItem(series) },
            )
        }

    val activePlaylistProgress = readyLibrary?.snapshot?.progress
        .orEmpty()
        .filter { it.playlistId == readyProvider?.playlistId }
    val lastWatched = activePlaylistProgress.firstOrNull { progress ->
        when (progress.contentType) {
            ProgressContentType.Movie -> progress.contentId in allowedMovieIds
            ProgressContentType.Episode ->
                progress.seriesId != null && progress.seriesId in allowedSeriesIds
        }
    }

    val lastWatchedMovieId = lastWatched?.takeIf { it.contentType == ProgressContentType.Movie }?.contentId
    val lastWatchedSeriesId = lastWatched?.takeIf { it.contentType == ProgressContentType.Episode }?.seriesId
    val sameCategoryMovies = remember(catalogSnapshot, lastWatchedMovieId) {
        val movies = catalogSnapshot?.movies.orEmpty()
        val source = lastWatchedMovieId?.let { id -> movies.firstOrNull { it.id == id } }
        val categoryId = source?.categoryId
        if (categoryId != null) {
            movies.filter { it.categoryId == categoryId && it.id != source.id }
        } else {
            emptyList()
        }
    }

    val sameCategorySeries = remember(catalogSnapshot, lastWatchedSeriesId) {
        val series = catalogSnapshot?.series.orEmpty()
        val source = lastWatchedSeriesId?.let { id -> series.firstOrNull { it.id == id } }
        val categoryId = source?.categoryId
        if (categoryId != null) {
            series.filter { it.categoryId == categoryId && it.id != source.id }
        } else {
            emptyList()
        }
    }

    val sameCategorySource = when {
        sameCategoryMovies.size >= MIN_CATEGORY_RECOMMENDATIONS ->
            SameCategorySource.Movies
        sameCategorySeries.size >= MIN_CATEGORY_RECOMMENDATIONS ->
            SameCategorySource.Series
        else -> null
    }
    val sameCategoryItems = when (sameCategorySource) {
        SameCategorySource.Movies -> sameCategoryMovies
            .take(MAX_HOME_ITEMS)
            .map { movie ->
                HomeCardUi(
                    title = movie.title,
                    poster = true,
                    onClick = { onOpenMovieItem(movie) },
                )
            }
        SameCategorySource.Series -> sameCategorySeries
            .take(MAX_HOME_ITEMS)
            .map { series ->
                HomeCardUi(
                    title = series.title,
                    poster = true,
                    onClick = { onOpenSeriesItem(series) },
                )
            }
        null -> emptyList()
    }

    val liveById = remember(catalogSnapshot) {
        catalogSnapshot?.liveChannels
            .orEmpty()
            .associateBy { it.id }
    }
    val recentChannels = readyLibrary?.snapshot?.liveHistory
        .orEmpty()
        .asSequence()
        .filter { it.playlistId == readyProvider?.playlistId }
        .mapNotNull { liveById[it.channelId] }
        .distinctBy { it.id }
        .take(MAX_HOME_ITEMS)
        .toList()

    val heroNextEpisode = nextEpisodes.firstOrNull()
    val newestMovie = recentMovies.firstOrNull()
    val newestSeries = recentSeries.firstOrNull()
    val newestAdded = listOfNotNull(
        newestMovie?.let { Triple(it.title, it.addedAtEpochSeconds ?: 0L, true) },
        newestSeries?.let { Triple(it.title, it.addedAtEpochSeconds ?: 0L, false) },
    ).maxByOrNull { it.second }
    val heroTitle = heroNextEpisode?.let {
        it.seriesTitle + " — S" + it.episode.season + " E" + it.episode.number
    } ?: newestAdded?.first
        ?: continueItems.firstOrNull()?.title
        ?: recentChannels.firstOrNull()?.name
    val heroArtworkUrl = when {
        heroNextEpisode != null -> heroNextEpisode.artworkUrl
        newestAdded?.third == true -> newestMovie?.posterUrl
        newestAdded?.third == false -> newestSeries?.posterUrl
        continueItems.isNotEmpty() -> continueItems.first().artworkUrl
        else -> recentChannels.firstOrNull()?.logoUrl
    }
    val visibleNextEpisodes = if (heroNextEpisode != null) {
        nextEpisodes.filterNot {
            it.seriesId == heroNextEpisode.seriesId &&
                it.episode.id == heroNextEpisode.episode.id
        }
    } else {
        nextEpisodes
    }

    val visibleContinueItems = if (
        heroNextEpisode == null &&
        newestAdded == null &&
        continueItems.isNotEmpty()
    ) {
        continueItems.drop(1)
    } else {
        continueItems
    }

    val visibleRecentChannels = if (
        heroNextEpisode == null &&
        newestAdded == null &&
        continueItems.isEmpty() &&
        recentChannels.isNotEmpty()
    ) {
        recentChannels.drop(1)
    } else {
        recentChannels
    }

    val visibleRecentMovieItems = if (
        heroNextEpisode == null &&
        newestAdded?.third == true &&
        newestMovie != null
    ) {
        recentMovies
            .filterNot { it.id == newestMovie.id }
            .take(MAX_HOME_ITEMS)
            .map { movie ->
                HomeCardUi(
                    title = movie.title,
                    poster = true,
                    onClick = { onOpenMovieItem(movie) },
                )
            }
    } else {
        recentMovieItems
    }

    val visibleRecentSeriesItems = if (
        heroNextEpisode == null &&
        newestAdded?.third == false &&
        newestSeries != null
    ) {
        recentSeries
            .filterNot { it.id == newestSeries.id }
            .take(MAX_HOME_ITEMS)
            .map { series ->
                HomeCardUi(
                    title = series.title,
                    poster = true,
                    onClick = { onOpenSeriesItem(series) },
                )
            }
    } else {
        recentSeriesItems
    }

    val hasAnyContent =
        nextEpisodes.isNotEmpty() ||
            continueItems.isNotEmpty() ||
            favoriteItems.isNotEmpty() ||
            readyProvider?.snapshot?.movies.orEmpty().isNotEmpty() ||
            readyProvider?.snapshot?.series.orEmpty().isNotEmpty() ||
            recentChannels.isNotEmpty()

    // Device connectivity only: a catalog restored from disk or a failed
    // account request is reported separately and never as "offline".
    val offlineMode = isOffline
    val syncNotice = when {
        offlineMode -> null
        // PR #219: first synchronisation, channels and films already complete.
        readyProvider?.seriesPending == true && readyProvider.syncWarning != null ->
            "Chaînes et films sont disponibles ; la synchronisation des séries n’a pas abouti. Réessayez depuis l’onglet Séries."
        readyProvider?.seriesPending == true ->
            "Chaînes et films sont prêts. Les séries arrivent dans quelques minutes."
        readyLibrary?.snapshot?.isFromCache == true ->
            "Votre catalogue et la lecture restent disponibles. Favoris et progression sont affichés depuis l’appareil."
        readyProvider?.syncWarning != null ->
            "Le catalogue enregistré reste lisible ; la dernière actualisation de la playlist n’a pas abouti."
        else -> null
    }
    val syncNoticeTitle = if (readyProvider?.seriesPending == true && readyProvider.syncWarning == null) {
        "Synchronisation en cours"
    } else {
        "Synchronisation incomplète"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = contentPadding)
            .verticalScroll(rememberScrollState()),
    ) {
        if (offlineMode) {
            LocalStatusBanner(
                title = "Mode hors connexion",
                message = "Les contenus enregistrés localement restent consultables. La lecture, le guide et les actions nécessitant Internet sont temporairement indisponibles.",
            )
            Spacer(Modifier.height(16.dp))
        }
        if (syncNotice != null) {
            LocalStatusBanner(
                title = syncNoticeTitle,
                message = syncNotice,
            )
            Spacer(Modifier.height(16.dp))
        }
        if (heroTitle != null) {
            Hero(
                profile = profile,
                title = heroTitle,
                artworkUrl = heroArtworkUrl,
                subtitle = when {
                    heroNextEpisode != null -> "Votre prochain épisode est prêt."
                    newestAdded != null -> "Nouveau contenu ajouté à votre catalogue."
                    continueItems.isNotEmpty() -> "Reprenez votre lecture là où vous l’avez arrêtée."
                    else -> "Revenez rapidement à votre dernière chaîne."
                },
                actionLabel = when {
                    heroNextEpisode != null -> "Lire l’épisode"
                    newestAdded != null -> "Découvrir"
                    continueItems.isNotEmpty() -> "Continuer"
                    else -> "Regarder"
                },
                eyebrow = when {
                    heroNextEpisode != null -> "Prochain épisode"
                    newestAdded != null -> "Nouveauté"
                    continueItems.isNotEmpty() -> "À reprendre"
                    else -> "Dernière chaîne"
                },
                onAction = {
                    when {
                        heroNextEpisode != null -> onPlayNextEpisode(heroNextEpisode)
                        newestAdded?.third == true -> onOpenMovies()
                        newestAdded != null -> onOpenSeries()
                        continueItems.isNotEmpty() -> onOpenContinueWatching()
                        recentChannels.isNotEmpty() -> onTuneRecentChannel(recentChannels.first())
                    }
                },
            )
            Spacer(Modifier.height(24.dp))
        }

        QuickActions(
            profile = profile,
            onOpenLive = onOpenLive,
            onOpenMovies = onOpenMovies,
            onOpenSeries = onOpenSeries,
            onOpenSearch = onOpenSearch,
            onOpenFavorites = onOpenFavorites,
            onOpenContinueWatching = onOpenContinueWatching,
            onOpenHistory = onOpenHistory,
        )

        Spacer(Modifier.height(28.dp))

        when {
            providerState is ProviderCatalogState.Loading &&
                libraryState is LibraryState.Loading -> {
                HomeLoading()
            }

            !hasAnyContent -> {
                HomeEmpty(
                    providerState = providerState,
                    libraryState = libraryState,
                    onOpenLive = onOpenLive,
                    onOpenMovies = onOpenMovies,
                    onOpenSeries = onOpenSeries,
                )
            }

            else -> {
                if (visibleContinueItems.isNotEmpty()) {
                    HomeSection(
                        title = "Continuer à regarder",
                        items = visibleContinueItems,
                        showAll = filteredProgress.size > MAX_HOME_ITEMS,
                        isTelevision = profile == DeviceProfile.Television,
                        onOpenSection = onOpenContinueWatching,
                    )
                }

                if (visibleNextEpisodes.isNotEmpty()) {
                    HomeNextEpisodesSection(
                        items = visibleNextEpisodes.take(MAX_HOME_ITEMS),
                        isTelevision = profile == DeviceProfile.Television,
                        onPlay = onPlayNextEpisode,
                    )
                }

                if (visibleRecentChannels.isNotEmpty()) {
                    HomeRecentChannelsSection(
                        items = visibleRecentChannels,
                        isTelevision = profile == DeviceProfile.Television,
                        onTune = onTuneRecentChannel,
                    )
                }

                if (favoriteItems.isNotEmpty()) {
                    HomeSection(
                        title = "Favoris",
                        items = favoriteItems,
                        showAll = filteredFavorites.size > MAX_HOME_ITEMS,
                        isTelevision = profile == DeviceProfile.Television,
                        onOpenSection = onOpenFavorites,
                    )
                }

                if (visibleRecentMovieItems.isNotEmpty()) {
                    HomeSection(
                        title = "Films récemment ajoutés",
                        items = visibleRecentMovieItems,
                        showAll = recentMovies.size > MAX_HOME_ITEMS,
                        isTelevision = profile == DeviceProfile.Television,
                        onOpenSection = onOpenMovies,
                    )
                }

                if (visibleRecentSeriesItems.isNotEmpty()) {
                    HomeSection(
                        title = "Séries récemment ajoutées",
                        items = visibleRecentSeriesItems,
                        showAll = recentSeries.size > MAX_HOME_ITEMS,
                        isTelevision = profile == DeviceProfile.Television,
                        onOpenSection = onOpenSeries,
                    )
                }

                if (sameCategoryItems.isNotEmpty()) {
                    HomeSection(
                        title = "Même catégorie que le dernier titre regardé",
                        items = sameCategoryItems,
                        showAll = when (sameCategorySource) {
                            SameCategorySource.Movies ->
                                sameCategoryMovies.size > MAX_HOME_ITEMS
                            SameCategorySource.Series ->
                                sameCategorySeries.size > MAX_HOME_ITEMS
                            null -> false
                        },
                        isTelevision = profile == DeviceProfile.Television,
                        onOpenSection = {
                            when (sameCategorySource) {
                                SameCategorySource.Movies -> onOpenMovies()
                                SameCategorySource.Series -> onOpenSeries()
                                null -> Unit
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun LocalStatusBanner(
    title: String,
    message: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun Hero(
    profile: DeviceProfile,
    title: String,
    artworkUrl: String?,
    subtitle: String,
    actionLabel: String,
    eyebrow: String = "À la une",
    onAction: () -> Unit,
) {
    val heroHeight = when (profile) {
        DeviceProfile.Mobile -> 220.dp
        DeviceProfile.Tablet -> 260.dp
        DeviceProfile.Television -> 390.dp
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(heroHeight)
            .background(
                color = ZyvioBase,
                shape = RoundedCornerShape(24.dp),
            ),
    ) {
        if (!artworkUrl.isNullOrBlank()) {
            AsyncImage(
                model = artworkUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            ZyvioRedTint.copy(alpha = 0.96f),
                            ZyvioBase.copy(alpha = 0.88f),
                            ZyvioBase.copy(alpha = 0.20f),
                        ),
                    ),
                    shape = RoundedCornerShape(24.dp),
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth(if (profile == DeviceProfile.Mobile) 0.94f else 0.78f)
                .padding(when (profile) {
                    DeviceProfile.Mobile -> ZyvioSpace.s4
                    DeviceProfile.Tablet -> ZyvioSpace.s6
                    DeviceProfile.Television -> ZyvioSpace.s12
                }),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = eyebrow,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
                modifier = Modifier.tvFocusEffect(
                    profile == DeviceProfile.Television,
                    cornerRadiusDp = 12,
                ),
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(actionLabel)
            }
        }
    }
}

@Composable
private fun HomeLoading() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Chargement de votre contenu…",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HomeEmpty(
    providerState: ProviderCatalogState,
    libraryState: LibraryState,
    onOpenLive: () -> Unit,
    onOpenMovies: () -> Unit,
    onOpenSeries: () -> Unit,
) {
    val message = when {
        providerState is ProviderCatalogState.Error -> providerState.message
        libraryState is LibraryState.Error -> libraryState.message
        providerState is ProviderCatalogState.Empty -> providerState.message
        else -> "Aucun contenu n’est disponible pour le moment."
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Votre Accueil est prêt",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onOpenLive) { Text("TV") }
            Button(onClick = onOpenMovies) { Text("Films") }
            Button(onClick = onOpenSeries) { Text("Séries") }
        }
    }
}

@Composable
private fun HomeRecentChannelsSection(
    items: List<CatalogLiveChannel>,
    isTelevision: Boolean,
    onTune: (CatalogLiveChannel) -> Unit,
) {
    if (items.isEmpty()) return

    Text(
        text = "Chaînes récentes",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(12.dp))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items.forEach { channel ->
            Card(
                onClick = { onTune(channel) },
                modifier = Modifier
                    .width(if (isTelevision) 280.dp else 210.dp)
                    .aspectRatio(16f / 9f)
                    .tvFocusEffect(isTelevision),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
                shape = RoundedCornerShape(16.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    ZyvioSurface2,
                                    ZyvioSurface1,
                                ),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Tv,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    )
                    if (!channel.logoUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = channel.logoUrl,
                            contentDescription = channel.name,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(22.dp),
                            contentScale = ContentScale.Fit,
                        )
                    }
                    Text(
                        text = channel.name,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.72f))
                            .padding(10.dp),
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }

    Spacer(Modifier.height(28.dp))
}

@Composable
private fun HomeNextEpisodesSection(
    items: List<HomeNextEpisode>,
    isTelevision: Boolean,
    onPlay: (HomeNextEpisode) -> Unit,
) {
    if (items.isEmpty()) return

    Text(
        text = "Prochains épisodes",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(12.dp))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items.forEach { item ->
            Card(
                onClick = { onPlay(item) },
                modifier = Modifier
                    .width(if (isTelevision) 280.dp else 210.dp)
                    .aspectRatio(16f / 9f)
                    .tvFocusEffect(isTelevision),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
                shape = RoundedCornerShape(16.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF2A2A2A),
                                    Color(0xFF151515),
                                ),
                            ),
                        ),
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.align(Alignment.Center),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    )
                    if (!item.artworkUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = item.artworkUrl,
                            contentDescription = item.seriesTitle,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    }
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.72f))
                            .padding(10.dp),
                    ) {
                        Text(
                            text = item.seriesTitle,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "S" + item.episode.season +
                                " E" + item.episode.number +
                                " — " + item.episode.title,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }

    Spacer(Modifier.height(28.dp))
}

@Composable
private fun QuickActions(
    profile: DeviceProfile,
    onOpenLive: () -> Unit,
    onOpenMovies: () -> Unit,
    onOpenSeries: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenContinueWatching: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    val isTelevision = profile == DeviceProfile.Television
    val cardWidth = when (profile) {
        DeviceProfile.Mobile -> 150.dp
        DeviceProfile.Tablet -> 176.dp
        DeviceProfile.Television -> 220.dp
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        QuickActionCard("TV en direct", Icons.Default.LiveTv, isTelevision, cardWidth, onOpenLive)
        QuickActionCard("Films", Icons.Default.Movie, isTelevision, cardWidth, onOpenMovies)
        QuickActionCard("Séries", Icons.Default.VideoLibrary, isTelevision, cardWidth, onOpenSeries)
        QuickActionCard("Recherche", Icons.Default.Search, isTelevision, cardWidth, onOpenSearch)
        QuickActionCard("Favoris", Icons.Default.Favorite, isTelevision, cardWidth, onOpenFavorites)
        QuickActionCard("Continuer", Icons.Default.PlayArrow, isTelevision, cardWidth, onOpenContinueWatching)
        QuickActionCard("Historique", Icons.Default.History, isTelevision, cardWidth, onOpenHistory)
    }
}

@Composable
private fun QuickActionCard(
    label: String,
    icon: ImageVector,
    isTelevision: Boolean,
    cardWidth: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .width(cardWidth)
            .tvFocusEffect(isTelevision, cornerRadiusDp = 18),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(14.dp))
            Text(text = label, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun HomeSection(
    title: String,
    items: List<HomeCardUi>,
    showAll: Boolean,
    isTelevision: Boolean,
    onOpenSection: () -> Unit,
) {
    if (items.isEmpty()) return

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        if (showAll) {
            Button(
                onClick = onOpenSection,
                modifier = Modifier.tvFocusEffect(isTelevision, cornerRadiusDp = 10),
            ) {
                Text("Tout voir")
            }
        }
    }
    Spacer(Modifier.height(12.dp))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items.forEach { item ->
            Card(
                onClick = item.onClick,
                modifier = Modifier
                    .width(
                        if (isTelevision) {
                            if (item.poster) 180.dp else 280.dp
                        } else {
                            if (item.poster) 132.dp else 210.dp
                        },
                    )
                    .tvFocusEffect(isTelevision)
                    .then(
                        if (item.poster) Modifier.aspectRatio(2f / 3f)
                        else Modifier.aspectRatio(16f / 9f),
                    ),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
                shape = RoundedCornerShape(16.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF2A2A2A),
                                    Color(0xFF151515),
                                ),
                            ),
                        ),
                ) {
                    Icon(
                        imageVector = if (item.poster) Icons.Default.Movie else Icons.Default.Tv,
                        contentDescription = null,
                        modifier = Modifier.align(Alignment.Center),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                    )
                    if (!item.artworkUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = item.artworkUrl,
                            contentDescription = item.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = if (item.poster) ContentScale.Crop else ContentScale.Fit,
                        )
                    }
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.68f))
                            .padding(10.dp),
                    ) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        item.progress?.let { progress ->
                            Spacer(Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .background(Color.White.copy(alpha = 0.18f)),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                                        .height(3.dp)
                                        .background(MaterialTheme.colorScheme.primary),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    Spacer(Modifier.height(28.dp))
}

private enum class SameCategorySource {
    Movies,
    Series,
}

private const val MIN_CATEGORY_RECOMMENDATIONS = 2
private const val MAX_HOME_ITEMS = 20
