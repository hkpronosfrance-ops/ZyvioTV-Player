package fr.zyviotv.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import fr.zyviotv.player.shared.AppIdentity
import fr.zyviotv.player.shared.catalog.CatalogMovie
import fr.zyviotv.player.shared.catalog.CatalogSeries
import fr.zyviotv.player.shared.playback.PlaybackKind
import fr.zyviotv.player.shared.playback.PlaybackRequest
import fr.zyviotv.player.ui.auth.AuthScreen
import fr.zyviotv.player.data.auth.SecureSessionStore
import fr.zyviotv.player.data.catalog.AndroidXtreamSeriesDetailLoader
import fr.zyviotv.player.data.catalog.SeriesDetailLoadResult
import fr.zyviotv.player.data.catalog.SeriesEpisodeSource
import fr.zyviotv.player.data.sync.SupabaseCloudSyncRepository
import fr.zyviotv.player.ui.catalog.ProviderCatalogState
import fr.zyviotv.player.ui.catalog.rememberProviderCatalogSession
import fr.zyviotv.player.ui.catalog.snapshotOrEmpty
import fr.zyviotv.player.ui.catalog.toLiveState
import fr.zyviotv.player.ui.catalog.toMoviesState
import fr.zyviotv.player.ui.catalog.toSeriesState
import fr.zyviotv.player.ui.settings.AccountSettingsScreen
import fr.zyviotv.player.ui.home.HomeScreen
import fr.zyviotv.player.ui.live.LiveTvScreen
import fr.zyviotv.player.ui.epg.GuideEpgScreen
import fr.zyviotv.player.ui.movies.MovieDetailScreen
import fr.zyviotv.player.ui.movies.MovieDetailState
import fr.zyviotv.player.ui.movies.MovieDetailUi
import fr.zyviotv.player.ui.movies.MoviesScreen
import fr.zyviotv.player.ui.series.EpisodeDetailUi
import fr.zyviotv.player.ui.series.SeriesDetailScreen
import fr.zyviotv.player.ui.series.SeriesDetailState
import fr.zyviotv.player.ui.series.SeriesDetailUi
import fr.zyviotv.player.ui.series.SeriesScreen
import fr.zyviotv.player.ui.player.PlayerHost
import fr.zyviotv.player.shared.sync.PlaylistSecret
import fr.zyviotv.player.shared.xtream.XtreamCredentials
import fr.zyviotv.player.ui.search.SearchScreen
import fr.zyviotv.player.shared.search.SearchKind
import fr.zyviotv.player.ui.sync.DeviceSyncEffect
import kotlinx.coroutines.delay

private enum class AppDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    Home("home", "Accueil", Icons.Default.Home),
    Live("live", "TV", Icons.Default.LiveTv),
    Movies("movies", "Films", Icons.Default.Movie),
    Series("series", "Séries", Icons.Default.VideoLibrary),
    Settings("settings", "Plus", Icons.Default.Settings),
}

@UnstableApi
@Composable
fun ZyvioTVPlayerApp() {
    val navController = rememberNavController()
    val profile = rememberDeviceProfile()
    val providerCatalog = rememberProviderCatalogSession()
    val providerState = providerCatalog.state.value
    var selectedMovie by remember { mutableStateOf<CatalogMovie?>(null) }
    var selectedSeries by remember { mutableStateOf<CatalogSeries?>(null) }
    var seriesDetailState by remember { mutableStateOf<SeriesDetailState>(SeriesDetailState.Loading) }
    var seriesEpisodeSources by remember { mutableStateOf<Map<String, SeriesEpisodeSource>>(emptyMap()) }
    var playbackRequest by remember { mutableStateOf<PlaybackRequest?>(null) }

    NavHost(
        navController = navController,
        startDestination = "splash",
    ) {
        composable("splash") {
            SplashScreen {
                navController.navigate("auth") {
                    popUpTo("splash") { inclusive = true }
                }
            }
        }

        composable("auth") {
            AuthScreen(
                profile = profile,
                onAuthenticated = {
                    providerCatalog.reload()
                    navController.navigate(AppDestination.Home.route) {
                        popUpTo("auth") { inclusive = true }
                    }
                },
            )
        }

        composable("guide") {
            AdaptiveShell(
                profile = profile,
                destinations = AppDestination.entries,
                selectedRoute = AppDestination.Live.route,
                onDestinationSelected = { target ->
                    navController.navigate(target.route) {
                        launchSingleTop = true
                    }
                },
            ) {
                GuideEpgScreen(
                    profile = profile,
                )
            }
        }

        composable("search") {
            AdaptiveShell(
                profile = profile,
                destinations = AppDestination.entries,
                selectedRoute = "",
                onDestinationSelected = { target ->
                    navController.navigate(target.route) {
                        launchSingleTop = true
                    }
                },
            ) {
                SearchScreen(
                    profile = profile,
                    snapshot = providerState.snapshotOrEmpty(),
                    onBack = { navController.popBackStack() },
                    onResultSelected = { result ->
                        val route = when (result.kind) {
                            SearchKind.Live -> AppDestination.Live.route
                            SearchKind.Movie -> AppDestination.Movies.route
                            SearchKind.Series -> AppDestination.Series.route
                        }
                        navController.navigate(route)
                    },
                )
            }
        }


        composable("movie-detail") {
            val movie = selectedMovie
            if (movie == null) {
                LaunchedEffect(Unit) { navController.popBackStack() }
            } else {
                AdaptiveShell(
                    profile = profile,
                    destinations = AppDestination.entries,
                    selectedRoute = AppDestination.Movies.route,
                    onDestinationSelected = { target ->
                        navController.navigate(target.route) {
                            launchSingleTop = true
                        }
                    },
                ) {
                    MovieDetailScreen(
                        profile = profile,
                        state = MovieDetailState.Ready(
                            MovieDetailUi(
                                id = movie.id,
                                title = movie.title,
                                posterUrl = movie.posterUrl,
                            ),
                        ),
                        onPlay = { _, _ ->
                            playbackRequest = PlaybackRequest(
                                title = movie.title,
                                streamUrl = movie.streamUrl,
                                kind = PlaybackKind.Movie,
                            )
                            navController.navigate("player")
                        },
                    )
                }
            }
        }


        composable("series-detail") {
            val series = selectedSeries
            val context = LocalContext.current
            val readyProvider = providerState as? ProviderCatalogState.Ready

            if (series == null || readyProvider == null) {
                LaunchedEffect(Unit) { navController.popBackStack() }
            } else {
                val repository = remember(context.applicationContext) {
                    SupabaseCloudSyncRepository(
                        sessionStore = SecureSessionStore(context.applicationContext),
                    )
                }

                LaunchedEffect(series.id, readyProvider.playlistId) {
                    seriesDetailState = SeriesDetailState.Loading
                    seriesEpisodeSources = emptyMap()

                    val secret = repository
                        .getPlaylistSecret(readyProvider.playlistId)
                        .getOrElse {
                            seriesDetailState = SeriesDetailState.Error(
                                "Impossible de restaurer la configuration de la playlist.",
                            )
                            return@LaunchedEffect
                        }

                    val xtream = secret as? PlaylistSecret.Xtream
                    if (xtream == null) {
                        seriesDetailState = SeriesDetailState.Error(
                            "Les saisons et épisodes détaillés sont disponibles pour les playlists Xtream.",
                        )
                        return@LaunchedEffect
                    }

                    when (
                        val result = AndroidXtreamSeriesDetailLoader(
                            credentials = XtreamCredentials(
                                serverUrl = xtream.serverUrl,
                                username = xtream.username,
                                password = xtream.password,
                            ),
                        ).load(series.id)
                    ) {
                        is SeriesDetailLoadResult.Failure -> {
                            seriesDetailState = SeriesDetailState.Error(result.message)
                        }

                        is SeriesDetailLoadResult.Success -> {
                            seriesEpisodeSources = result.detail.episodes.associateBy { it.id }
                            seriesDetailState = SeriesDetailState.Ready(
                                SeriesDetailUi(
                                    id = series.id,
                                    title = result.detail.title ?: series.title,
                                    year = result.detail.year,
                                    genres = result.detail.genres,
                                    synopsis = result.detail.synopsis,
                                    seasonsCount = result.detail.episodes
                                        .map { it.season }
                                        .distinct()
                                        .size,
                                    episodesCount = result.detail.episodes.size,
                                    episodes = result.detail.episodes.map { episode ->
                                        EpisodeDetailUi(
                                            id = episode.id,
                                            season = episode.season,
                                            number = episode.number,
                                            title = episode.title,
                                            synopsis = episode.synopsis,
                                        )
                                    },
                                ),
                            )
                        }
                    }
                }

                AdaptiveShell(
                    profile = profile,
                    destinations = AppDestination.entries,
                    selectedRoute = AppDestination.Series.route,
                    onDestinationSelected = { target ->
                        navController.navigate(target.route) {
                            launchSingleTop = true
                        }
                    },
                ) {
                    SeriesDetailScreen(
                        profile = profile,
                        state = seriesDetailState,
                        onRetry = {
                            seriesDetailState = SeriesDetailState.Loading
                            navController.navigate("series-detail") {
                                launchSingleTop = true
                            }
                        },
                        onPlayEpisode = { episode ->
                            val source = seriesEpisodeSources[episode.id]
                            if (source != null) {
                                playbackRequest = PlaybackRequest(
                                    title = series.title + " — S" +
                                        source.season + " E" + source.number +
                                        " — " + source.title,
                                    streamUrl = source.streamUrl,
                                    kind = PlaybackKind.Episode,
                                )
                                navController.navigate("player")
                            }
                        },
                    )
                }
            }
        }

        composable("player") {
            val request = playbackRequest
            if (request == null) {
                LaunchedEffect(Unit) { navController.popBackStack() }
            } else {
                PlayerHost(
                    profile = profile,
                    request = request,
                    onBack = {
                        playbackRequest = null
                        navController.popBackStack()
                    },
                    onOpenGuide = {
                        if (request.kind == PlaybackKind.Live) {
                            navController.navigate("guide")
                        }
                    },
                )
            }
        }

        AppDestination.entries.forEach { destination ->
            composable(destination.route) {
                AdaptiveShell(
                    profile = profile,
                    destinations = AppDestination.entries,
                    selectedRoute = destination.route,
                    onDestinationSelected = { target ->
                        navController.navigate(target.route) {
                            launchSingleTop = true
                            restoreState = true
                            popUpTo(AppDestination.Home.route) {
                                saveState = true
                            }
                        }
                    },
                ) {
                    when (destination) {
                        AppDestination.Home -> {
                            DeviceSyncEffect()
                            HomeScreen(
                                profile = profile,
                                onOpenLive = { navController.navigate(AppDestination.Live.route) },
                                onOpenMovies = { navController.navigate(AppDestination.Movies.route) },
                                onOpenSeries = { navController.navigate(AppDestination.Series.route) },
                                onOpenSearch = { navController.navigate("search") },
                            )
                        }

                        AppDestination.Live -> {
                            LiveTvScreen(
                                profile = profile,
                                state = providerState.toLiveState(),
                                onRetry = providerCatalog::reload,
                                onTuneChannel = { channel ->
                                    val snapshot = providerState.snapshotOrEmpty()
                                    val source = snapshot.liveChannels.firstOrNull { it.id == channel.id }
                                    if (source != null) {
                                        playbackRequest = PlaybackRequest(
                                            title = source.name,
                                            streamUrl = source.streamUrl,
                                            kind = PlaybackKind.Live,
                                        )
                                        navController.navigate("player")
                                    }
                                },
                                onOpenGuide = { navController.navigate("guide") },
                            )
                        }

                        AppDestination.Movies -> {
                            MoviesScreen(
                                profile = profile,
                                state = providerState.toMoviesState(),
                                onRetry = providerCatalog::reload,
                                onMovieSelected = { movieUi ->
                                    selectedMovie = providerState
                                        .snapshotOrEmpty()
                                        .movies
                                        .firstOrNull { it.id == movieUi.id }
                                    if (selectedMovie != null) {
                                        navController.navigate("movie-detail")
                                    }
                                },
                            )
                        }

                        AppDestination.Series -> {
                            SeriesScreen(
                                profile = profile,
                                state = providerState.toSeriesState(),
                                onRetry = providerCatalog::reload,
                                onSeriesSelected = { seriesUi ->
                                    selectedSeries = providerState
                                        .snapshotOrEmpty()
                                        .series
                                        .firstOrNull { it.id == seriesUi.id }
                                    if (selectedSeries != null) {
                                        seriesDetailState = SeriesDetailState.Loading
                                        seriesEpisodeSources = emptyMap()
                                        navController.navigate("series-detail")
                                    }
                                },
                            )
                        }

                        AppDestination.Settings -> {
                            AccountSettingsScreen(
                                onSignedOut = {
                                    navController.navigate("auth") {
                                        popUpTo(AppDestination.Home.route) { inclusive = true }
                                    }
                                },
                            )
                        }

                        else -> FoundationScreen(destination.label, profile)
                    }
                }
            }
        }
    }
}

@Composable
private fun SplashScreen(onFinished: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(1200)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = AppIdentity.name.removeSuffix(" Player").uppercase(),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Black,
            )
            Text(
                text = "PLAYER",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = AppIdentity.tagline,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun AdaptiveShell(
    profile: DeviceProfile,
    destinations: List<AppDestination>,
    selectedRoute: String,
    onDestinationSelected: (AppDestination) -> Unit,
    content: @Composable () -> Unit,
) {
    if (profile != DeviceProfile.Mobile) {
        Row(Modifier.fillMaxSize()) {
            NavigationRail(
                modifier = if (profile == DeviceProfile.Television) {
                    Modifier.width(132.dp)
                } else {
                    Modifier
                },
            ) {
                destinations.forEach { destination ->
                    NavigationRailItem(
                        selected = selectedRoute == destination.route,
                        onClick = { onDestinationSelected(destination) },
                        icon = {
                            Icon(
                                destination.icon,
                                contentDescription = destination.label,
                            )
                        },
                        label = {
                            Text(
                                text = destination.label,
                                style = if (profile == DeviceProfile.Television) {
                                    MaterialTheme.typography.titleSmall
                                } else {
                                    MaterialTheme.typography.labelMedium
                                },
                            )
                        },
                        alwaysShowLabel = true,
                    )
                    if (profile == DeviceProfile.Television) {
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (profile == DeviceProfile.Television) 32.dp else 24.dp),
            ) {
                content()
            }
        }
    } else {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    destinations.forEach { destination ->
                        NavigationBarItem(
                            selected = selectedRoute == destination.route,
                            onClick = { onDestinationSelected(destination) },
                            icon = { Icon(destination.icon, contentDescription = destination.label) },
                            label = { Text(destination.label) },
                        )
                    }
                }
            },
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
            ) {
                content()
            }
        }
    }
}

@Composable
private fun FoundationScreen(title: String, profile: DeviceProfile) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Default.Tv,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = AppIdentity.name,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Écran ${profile.name.lowercase()} en préparation.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
