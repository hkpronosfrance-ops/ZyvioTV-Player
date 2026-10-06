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

    val movieItems = readyProvider?.snapshot?.movies
        .orEmpty()
        .take(MAX_HOME_ITEMS)
        .map { HomeCardUi(title = it.title, poster = true) }

    val seriesItems = readyProvider?.snapshot?.series
        .orEmpty()
        .take(MAX_HOME_ITEMS)
        .map { HomeCardUi(title = it.title, poster = true) }

    val liveItems = readyProvider?.snapshot?.liveChannels
        .orEmpty()
        .take(MAX_HOME_ITEMS)
        .map { HomeCardUi(title = it.name, poster = false) }

    val heroNextEpisode = nextEpisodes.firstOrNull()
    val heroTitle = heroNextEpisode?.let {
        it.seriesTitle + " — S" + it.episode.season + " E" + it.episode.number
    } ?: continueItems.firstOrNull()?.title
    val hasAnyContent =
        nextEpisodes.isNotEmpty() ||
            continueItems.isNotEmpty() ||
            favoriteItems.isNotEmpty() ||
            movieItems.isNotEmpty() ||
            seriesItems.isNotEmpty() ||
            liveItems.isNotEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = contentPadding)
            .verticalScroll(rememberScrollState()),
    ) {
        if (heroTitle != null) {
            Hero(
                profile = profile,
                title = heroTitle,
                subtitle = if (heroNextEpisode != null) {
                    "Votre prochain épisode est prêt."
                } else {
                    "Reprenez votre lecture là où vous l’avez arrêtée."
                },
                actionLabel = if (heroNextEpisode != null) "Lire l’épisode" else "Continuer",
                onAction = {
                    if (heroNextEpisode != null) {
                        onPlayNextEpisode(heroNextEpisode)
                    } else {
                        onOpenContinueWatching()
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

                if (favoriteItems.isNotEmpty()) {
                    HomeSection(
                        title = "Favoris",
                        items = favoriteItems,
                        showAll = filteredFavorites.size > MAX_HOME_ITEMS,
                        isTelevision = profile == DeviceProfile.Television,
                        onOpenSection = onOpenFavorites,
                    )
                }

                if (liveItems.isNotEmpty()) {
                    HomeSection(
                        title = "Chaînes disponibles",
                        items = liveItems,
                        showAll = readyProvider?.snapshot?.liveChannels.orEmpty().size > MAX_HOME_ITEMS,
                        isTelevision = profile == DeviceProfile.Television,
                        onOpenSection = onOpenLive,
                    )
                }

                if (movieItems.isNotEmpty()) {
                    HomeSection(
                        title = "Films disponibles",
                        items = movieItems,
                        showAll = readyProvider?.snapshot?.movies.orEmpty().size > MAX_HOME_ITEMS,
                        isTelevision = profile == DeviceProfile.Television,
                        onOpenSection = onOpenMovies,
                    )
                }

                if (seriesItems.isNotEmpty()) {
                    HomeSection(
                        title = "Séries disponibles",
                        items = seriesItems,
                        showAll = readyProvider?.snapshot?.series.orEmpty().size > MAX_HOME_ITEMS,
                        isTelevision = profile == DeviceProfile.Television,
                        onOpenSection = onOpenSeries,
                    )
                }
            }
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

private const val MAX_HOME_ITEMS = 20
