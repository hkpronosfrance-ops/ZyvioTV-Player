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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.zyviotv.player.shared.catalog.CatalogLiveChannel
import fr.zyviotv.player.shared.sync.FavoriteContentType
import fr.zyviotv.player.shared.sync.ProgressContentType
import fr.zyviotv.player.ui.DeviceProfile
import fr.zyviotv.player.ui.catalog.ProviderCatalogState
import fr.zyviotv.player.ui.library.LibraryState
import fr.zyviotv.player.ui.tv.tvFocusEffect

private data class HomeCardUi(
    val title: String,
    val poster: Boolean,
    val progress: Float? = null,
)

@Composable
fun HomeScreen(
    profile: DeviceProfile,
    providerState: ProviderCatalogState,
    libraryState: LibraryState,
    nextEpisodes: List<HomeNextEpisode>,
    onPlayNextEpisode: (HomeNextEpisode) -> Unit,
    onTuneRecentChannel: (CatalogLiveChannel) -> Unit,
    onOpenLive: () -> Unit,
    onOpenMovies: () -> Unit,
    onOpenSeries: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenContinueWatching: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    val contentPadding = if (profile == DeviceProfile.Mobile) 4.dp else 12.dp
    val readyProvider = providerState as? ProviderCatalogState.Ready
    val readyLibrary = libraryState as? LibraryState.Ready

    val allowedMovieIds = readyProvider?.snapshot?.movies?.mapTo(hashSetOf()) { it.id }.orEmpty()
    val allowedSeriesIds = readyProvider?.snapshot?.series?.mapTo(hashSetOf()) { it.id }.orEmpty()
    val allowedLiveIds = readyProvider?.snapshot?.liveChannels?.mapTo(hashSetOf()) { it.id }.orEmpty()

    val filteredProgress = readyLibrary?.snapshot?.progress
        .orEmpty()
        .filter { progress ->
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
                progress = it.fraction.takeIf { fraction -> fraction > 0f },
            )
        }
        .toList()

    val filteredFavorites = readyLibrary?.snapshot?.favorites
        .orEmpty()
        .filter { favorite ->
            when (favorite.contentType) {
                FavoriteContentType.Live -> favorite.contentId in allowedLiveIds
                FavoriteContentType.Movie -> favorite.contentId in allowedMovieIds
                FavoriteContentType.Series -> favorite.contentId in allowedSeriesIds
            }
        }

    val favoriteItems = filteredFavorites
        .take(MAX_HOME_ITEMS)
        .map {
            HomeCardUi(
                title = it.title,
                poster = it.contentType != FavoriteContentType.Live,
            )
        }

    val recentMovies = readyProvider?.snapshot?.movies
        .orEmpty()
        .filter { it.addedAtEpochSeconds != null }
        .sortedByDescending { it.addedAtEpochSeconds }
    val recentMovieItems = recentMovies
        .take(MAX_HOME_ITEMS)
        .map { HomeCardUi(title = it.title, poster = true) }

    val recentSeries = readyProvider?.snapshot?.series
        .orEmpty()
        .filter { it.addedAtEpochSeconds != null }
        .sortedByDescending { it.addedAtEpochSeconds }
    val recentSeriesItems = recentSeries
        .take(MAX_HOME_ITEMS)
        .map { HomeCardUi(title = it.title, poster = true) }

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

    val sameCategoryMovies = if (lastWatched?.contentType == ProgressContentType.Movie) {
        val source = readyProvider?.snapshot?.movies
            .orEmpty()
            .firstOrNull { it.id == lastWatched.contentId }
        val categoryId = source?.categoryId
        if (categoryId != null) {
            readyProvider?.snapshot?.movies
                .orEmpty()
                .filter { it.categoryId == categoryId && it.id != source.id }
                .orEmpty()
        } else {
            emptyList()
        }
    } else {
        emptyList()
    }

    val sameCategorySeries = if (lastWatched?.contentType == ProgressContentType.Episode) {
        val sourceSeriesId = lastWatched.seriesId
        val source = readyProvider?.snapshot?.series
            .orEmpty()
            .firstOrNull { it.id == sourceSeriesId }
        val categoryId = source?.categoryId
        if (categoryId != null) {
            readyProvider?.snapshot?.series
                .orEmpty()
                .filter { it.categoryId == categoryId && it.id != source.id }
                .orEmpty()
        } else {
            emptyList()
        }
    } else {
        emptyList()
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
            .map { HomeCardUi(title = it.title, poster = true) }
        SameCategorySource.Series -> sameCategorySeries
            .take(MAX_HOME_ITEMS)
            .map { HomeCardUi(title = it.title, poster = true) }
        null -> emptyList()
    }

    val liveById = readyProvider?.snapshot?.liveChannels
        .orEmpty()
        .associateBy { it.id }
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
    val hasAnyContent =
        nextEpisodes.isNotEmpty() ||
            continueItems.isNotEmpty() ||
            favoriteItems.isNotEmpty() ||
            readyProvider?.snapshot?.movies.orEmpty().isNotEmpty() ||
            readyProvider?.snapshot?.series.orEmpty().isNotEmpty() ||
            recentChannels.isNotEmpty()

    val offlineMode = readyProvider?.isOffline == true ||
        readyLibrary?.snapshot?.isOffline == true

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
        if (heroTitle != null) {
            Hero(
                profile = profile,
                title = heroTitle,
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
            isTelevision = profile == DeviceProfile.Television,
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
                if (continueItems.isNotEmpty()) {
                    HomeSection(
                        title = "Continuer à regarder",
                        items = continueItems,
                        showAll = filteredProgress.size > MAX_HOME_ITEMS,
                        isTelevision = profile == DeviceProfile.Television,
                        onOpenSection = onOpenContinueWatching,
                    )
                }

                if (nextEpisodes.isNotEmpty()) {
                    HomeNextEpisodesSection(
                        items = nextEpisodes.take(MAX_HOME_ITEMS),
                        isTelevision = profile == DeviceProfile.Television,
                        onPlay = onPlayNextEpisode,
                    )
                }

                if (recentChannels.isNotEmpty()) {
                    HomeRecentChannelsSection(
                        items = recentChannels,
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

                if (recentMovieItems.isNotEmpty()) {
                    HomeSection(
                        title = "Films récemment ajoutés",
                        items = recentMovieItems,
                        showAll = recentMovies.size > MAX_HOME_ITEMS,
                        isTelevision = profile == DeviceProfile.Television,
                        onOpenSection = onOpenMovies,
                    )
                }

                if (recentSeriesItems.isNotEmpty()) {
                    HomeSection(
                        title = "Séries récemment ajoutées",
                        items = recentSeriesItems,
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
    subtitle: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    val heroHeight = when (profile) {
        DeviceProfile.Mobile -> 220.dp
        DeviceProfile.Tablet -> 260.dp
        DeviceProfile.Television -> 330.dp
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(heroHeight)
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFF250004),
                        Color(0xFF0E0E0E),
                        Color(0xFF080808),
                    ),
                ),
                shape = RoundedCornerShape(24.dp),
            )
            .padding(24.dp),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth(0.78f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "À reprendre",
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
                                    Color(0xFF2A2A2A),
                                    Color(0xFF151515),
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
    isTelevision: Boolean,
    onOpenLive: () -> Unit,
    onOpenMovies: () -> Unit,
    onOpenSeries: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenContinueWatching: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        QuickActionCard("TV en direct", Icons.Default.LiveTv, isTelevision, onOpenLive)
        QuickActionCard("Films", Icons.Default.Movie, isTelevision, onOpenMovies)
        QuickActionCard("Séries", Icons.Default.VideoLibrary, isTelevision, onOpenSeries)
        QuickActionCard("Recherche", Icons.Default.Search, isTelevision, onOpenSearch)
        QuickActionCard("Favoris", Icons.Default.Favorite, isTelevision, onOpenFavorites)
        QuickActionCard("Continuer", Icons.Default.PlayArrow, isTelevision, onOpenContinueWatching)
        QuickActionCard("Historique", Icons.Default.History, isTelevision, onOpenHistory)
    }
}

@Composable
private fun QuickActionCard(
    label: String,
    icon: ImageVector,
    isTelevision: Boolean,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .width(if (isTelevision) 190.dp else 150.dp)
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
                onClick = onOpenSection,
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
