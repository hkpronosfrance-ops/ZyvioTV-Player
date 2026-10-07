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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import fr.zyviotv.player.shared.epg.EpgWindow
import fr.zyviotv.player.shared.playback.PlaybackKind
import fr.zyviotv.player.shared.playback.PlaybackRequest
import fr.zyviotv.player.ui.auth.AuthScreen
import fr.zyviotv.player.data.auth.SecureSessionStore
import fr.zyviotv.player.data.settings.AppUpdateKind
import fr.zyviotv.player.data.settings.AppUpdatePreferences
import fr.zyviotv.player.data.settings.AppUpdateRepository
import fr.zyviotv.player.data.settings.OnboardingPreferences
import fr.zyviotv.player.data.settings.InterfaceLanguageController
import fr.zyviotv.player.data.settings.OnboardingSetupPreferences
import fr.zyviotv.player.data.settings.ProfilePreferences
import fr.zyviotv.player.data.settings.ProfileRepository
import fr.zyviotv.player.data.system.SystemGateState
import fr.zyviotv.player.data.system.SystemStatePreferences
import fr.zyviotv.player.data.system.SystemStateRepository
import fr.zyviotv.player.data.catalog.AndroidXtreamSeriesDetailLoader
import fr.zyviotv.player.data.catalog.SeriesDetailLoadResult
import fr.zyviotv.player.data.catalog.SeriesEpisodeSource
import fr.zyviotv.player.data.epg.AndroidXtreamGuideLoader
import fr.zyviotv.player.data.epg.GuideLoadResult
import fr.zyviotv.player.data.sync.SupabaseCloudSyncRepository
import fr.zyviotv.player.ui.catalog.ProviderCatalogState
import fr.zyviotv.player.ui.catalog.rememberProviderCatalogSession
import fr.zyviotv.player.ui.catalog.snapshotOrEmpty
import fr.zyviotv.player.ui.catalog.toLiveState
import fr.zyviotv.player.ui.catalog.toMoviesState
import fr.zyviotv.player.ui.catalog.toSeriesState
import fr.zyviotv.player.ui.settings.AccountSettingsScreen
import fr.zyviotv.player.ui.settings.CacheSettingsScreen
import fr.zyviotv.player.ui.settings.DevicesSettingsScreen
import fr.zyviotv.player.ui.settings.AddPlaylistScreen
import fr.zyviotv.player.ui.settings.ParentalControlsScreen
import fr.zyviotv.player.ui.settings.ParentalPinRecoveryScreen
import fr.zyviotv.player.ui.settings.PlaybackDataSettingsScreen
import fr.zyviotv.player.ui.settings.PlaylistSettingsScreen
import fr.zyviotv.player.ui.settings.ProfilesSettingsScreen
import fr.zyviotv.player.ui.home.HomeNextEpisode
import fr.zyviotv.player.ui.home.HomeNextEpisodeResolver
import fr.zyviotv.player.ui.home.HomeScreen
import fr.zyviotv.player.ui.onboarding.AppUpdateGateScreen
import fr.zyviotv.player.ui.onboarding.OnboardingGateScreen
import fr.zyviotv.player.ui.onboarding.OnboardingPreferencesScreen
import fr.zyviotv.player.ui.live.LiveTvScreen
import fr.zyviotv.player.ui.library.ContinueWatchingScreen
import fr.zyviotv.player.ui.library.FavoritesScreen
import fr.zyviotv.player.ui.library.HistoryScreen
import fr.zyviotv.player.ui.library.LibraryState
import fr.zyviotv.player.ui.library.rememberLibrarySession
import fr.zyviotv.player.ui.epg.EpgChannelUi
import fr.zyviotv.player.ui.epg.EpgGuideState
import fr.zyviotv.player.ui.epg.GuideEpgScreen
import fr.zyviotv.player.ui.movies.MovieDetailScreen
import fr.zyviotv.player.ui.movies.MovieDetailState
import fr.zyviotv.player.ui.movies.MovieDetailUi
import fr.zyviotv.player.ui.movies.MoviesScreen
import fr.zyviotv.player.ui.series.EpisodeDetailUi
import fr.zyviotv.player.ui.series.EpisodeWatchState
import fr.zyviotv.player.ui.series.SeriesDetailScreen
import fr.zyviotv.player.ui.series.SeriesDetailState
import fr.zyviotv.player.ui.series.SeriesDetailUi
import fr.zyviotv.player.ui.series.SeriesScreen
import fr.zyviotv.player.ui.player.PlayerHost
import fr.zyviotv.player.ui.player.SeriesAutoNextResolver
import fr.zyviotv.player.ui.profiles.WhoIsWatchingGate
import fr.zyviotv.player.shared.sync.FavoriteContentType
import fr.zyviotv.player.shared.sync.PlayerProfile
import fr.zyviotv.player.shared.sync.PlaylistSecret
import fr.zyviotv.player.shared.sync.ProgressContentType
import fr.zyviotv.player.shared.sync.SyncedFavorite
import fr.zyviotv.player.shared.sync.SyncedWatchProgress
import fr.zyviotv.player.shared.xtream.XtreamCredentials
import fr.zyviotv.player.ui.search.SearchScreen
import fr.zyviotv.player.shared.search.SearchKind
import fr.zyviotv.player.ui.sync.DeviceSyncEffect
import fr.zyviotv.player.ui.startup.StartupSplashScreen
import fr.zyviotv.player.ui.system.SystemStateScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class PlaybackSyncContext(
    val playlistId: String,
    val contentType: ProgressContentType,
    val contentId: String,
    val title: String,
    val seriesId: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val artworkUrl: String? = null,
)

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
fun ZyvioTVPlayerApp(
    deepLink: String? = null,
    onDeepLinkConsumed: () -> Unit = {},
) {
    val navController = rememberNavController()
    val profile = rememberDeviceProfile()
    val providerCatalog = rememberProviderCatalogSession()
    val providerState = providerCatalog.state.value
    val librarySession = rememberLibrarySession()
    val libraryState = librarySession.state.value
    val scope = rememberCoroutineScope()
    val appContext = LocalContext.current.applicationContext
    val onboardingPreferences = remember(appContext) {
        OnboardingPreferences(appContext)
    }
    val profilePreferences = remember(appContext) {
        ProfilePreferences(appContext)
    }
    val setupPreferences = remember(appContext) {
        OnboardingSetupPreferences(appContext)
    }

    val activePlaylistId = (providerState as? ProviderCatalogState.Ready)?.playlistId
    val movieProgressById = remember(libraryState, activePlaylistId) {
        val ready = libraryState as? LibraryState.Ready
        ready?.snapshot?.progress
            ?.filter {
                it.playlistId == activePlaylistId &&
                    it.contentType == ProgressContentType.Movie
            }
            ?.associate { it.contentId to it.fraction }
            .orEmpty()
    }
    val seriesProgressById = remember(libraryState, activePlaylistId) {
        val ready = libraryState as? LibraryState.Ready
        ready?.snapshot?.progress
            ?.filter {
                it.playlistId == activePlaylistId &&
                    it.contentType == ProgressContentType.Episode &&
                    !it.seriesId.isNullOrBlank()
            }
            ?.groupBy { it.seriesId!! }
            ?.mapValues { (_, episodes) ->
                episodes.firstOrNull { !it.completed && it.positionMs > 0L }?.fraction
                    ?: if (episodes.isNotEmpty() && episodes.all { it.completed }) 1f else 0f
            }
            .orEmpty()
    }
    var homeNextEpisodes by remember { mutableStateOf<List<HomeNextEpisode>>(emptyList()) }

    LaunchedEffect(providerState, libraryState) {
        val readyProvider = providerState as? ProviderCatalogState.Ready
        val readyLibrary = libraryState as? LibraryState.Ready
        if (readyProvider == null || readyLibrary == null) {
            homeNextEpisodes = emptyList()
        } else {
            val resolver = HomeNextEpisodeResolver(
                SupabaseCloudSyncRepository(
                    sessionStore = SecureSessionStore(appContext),
                ),
            )
            homeNextEpisodes = resolver.resolve(
                playlistId = readyProvider.playlistId,
                visibleSeries = readyProvider.snapshot.series,
                progress = readyLibrary.snapshot.progress,
            )
        }
    }

    var selectedMovie by remember { mutableStateOf<CatalogMovie?>(null) }
    var selectedSeries by remember { mutableStateOf<CatalogSeries?>(null) }
    var seriesDetailState by remember { mutableStateOf<SeriesDetailState>(SeriesDetailState.Loading) }
    var seriesEpisodeSources by remember { mutableStateOf<Map<String, SeriesEpisodeSource>>(emptyMap()) }
    var seriesDetailReloadToken by remember { mutableIntStateOf(0) }
    var playbackRequest by remember { mutableStateOf<PlaybackRequest?>(null) }
    var playbackSyncContext by remember { mutableStateOf<PlaybackSyncContext?>(null) }
    var lastSyncedPositionMs by remember { mutableStateOf(0L) }

    val advanceToNextEpisode: (Boolean) -> Unit = { automatic ->
        val current = playbackSyncContext
        val seriesId = current?.seriesId
        val selectedProfileId = profilePreferences.selectedProfileId()
        val autoNextEnabled = selectedProfileId
            ?.let { setupPreferences.profile(it).autoNextEpisode }
            ?: true

        if (
            current != null &&
            current.contentType == ProgressContentType.Episode &&
            !seriesId.isNullOrBlank() &&
            (!automatic || autoNextEnabled)
        ) {
            scope.launch {
                val resolver = SeriesAutoNextResolver(
                    SupabaseCloudSyncRepository(
                        sessionStore = SecureSessionStore(appContext),
                    ),
                )
                val next = resolver.resolveNext(
                    playlistId = current.playlistId,
                    seriesId = seriesId,
                    currentContentId = current.contentId,
                    currentSeason = current.seasonNumber,
                    currentEpisode = current.episodeNumber,
                ) ?: return@launch
                val series = (providerState as? ProviderCatalogState.Ready)
                    ?.snapshot
                    ?.series
                    ?.firstOrNull { it.id == seriesId }
                val seriesTitle = series?.title ?: "Série"

                playbackRequest = PlaybackRequest(
                    title = seriesTitle + " — S" + next.season +
                        " E" + next.number + " — " + next.title,
                    streamUrl = next.streamUrl,
                    kind = PlaybackKind.Episode,
                )
                playbackSyncContext = PlaybackSyncContext(
                    playlistId = current.playlistId,
                    contentType = ProgressContentType.Episode,
                    contentId = next.id,
                    title = next.title,
                    seriesId = seriesId,
                    seasonNumber = next.season,
                    episodeNumber = next.number,
                    artworkUrl = series?.posterUrl ?: current.artworkUrl,
                )
                lastSyncedPositionMs = 0L
            }
        }
    }

    LaunchedEffect(deepLink) {
        if (deepLink?.startsWith("zyviotv://parental-pin-recovery") == true) {
            navController.navigate("parental-pin-recovery") {
                launchSingleTop = true
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = "splash",
    ) {
        composable("splash") {
            StartupSplashScreen(
                onSessionReady = {
                    navController.navigate("profile-gate") {
                        popUpTo("splash") { inclusive = true }
                    }
                },
                onOfflineReady = {
                    navController.navigate(AppDestination.Home.route) {
                        popUpTo("splash") { inclusive = true }
                    }
                },
                onAuthRequired = {
                    navController.navigate("auth") {
                        popUpTo("splash") { inclusive = true }
                    }
                },
            )
        }

        composable("parental-pin-recovery") {
            val link = deepLink
            if (link == null) {
                LaunchedEffect(Unit) {
                    navController.navigate("auth") {
                        popUpTo("parental-pin-recovery") { inclusive = true }
                    }
                }
            } else {
                ParentalPinRecoveryScreen(
                    deepLink = link,
                    onCompleted = {
                        onDeepLinkConsumed()
                        navController.navigate("settings-parental") {
                            popUpTo("parental-pin-recovery") { inclusive = true }
                        }
                    },
                )
            }
        }

        composable("auth") {
            AuthScreen(
                profile = profile,
                onAuthenticated = {
                    navController.navigate("profile-gate") {
                        popUpTo("auth") { inclusive = true }
                    }
                },
            )
        }

        composable("profile-gate") {
            WhoIsWatchingGate(
                deviceProfile = profile,
                onProfileSelected = {
                    providerCatalog.reload()
                    librarySession.reload()
                    val target = if (onboardingPreferences.isCompleted()) {
                        "system-gate"
                    } else {
                        onboardingPreferences.markStarted()
                        "onboarding"
                    }
                    navController.navigate(target) {
                        popUpTo("profile-gate") { inclusive = true }
                    }
                },
            )
        }

        composable("onboarding") {
            val context = LocalContext.current.applicationContext
            var hasConfiguredPlaylist by remember { mutableStateOf<Boolean?>(null) }
            var playlistCheckToken by remember { mutableIntStateOf(0) }

            LaunchedEffect(playlistCheckToken) {
                val repository = SupabaseCloudSyncRepository(
                    sessionStore = SecureSessionStore(context),
                )
                hasConfiguredPlaylist = repository.listPlaylists()
                    .getOrNull()
                    ?.any { it.isEnabled && it.secretStatus == "configured" }
                    ?: false
                if (hasConfiguredPlaylist == true) {
                    providerCatalog.reload()
                }
            }

            if (hasConfiguredPlaylist == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.material3.CircularProgressIndicator()
                }
            } else {
                OnboardingGateScreen(
                    deviceProfile = profile,
                    state = providerState,
                    hasConfiguredPlaylist = hasConfiguredPlaylist == true,
                    resumed = onboardingPreferences.hasStarted(),
                    onAddPlaylist = {
                        navController.navigate("onboarding-add-playlist")
                    },
                    onRetry = {
                        playlistCheckToken += 1
                        providerCatalog.reload()
                    },
                    onContinue = {
                        navController.navigate("onboarding-preferences")
                    },
                )
            }
        }

        composable("onboarding-preferences") {
            val context = LocalContext.current.applicationContext
            val profileRepository = remember(context) {
                ProfileRepository(SecureSessionStore(context))
            }
            val profilePreferences = remember(context) {
                ProfilePreferences(context)
            }
            val setupPreferences = remember(context) {
                OnboardingSetupPreferences(context)
            }

            var profiles by remember { mutableStateOf<List<PlayerProfile>?>(null) }
            var profilesLoadError by remember { mutableStateOf(false) }
            var profilesReloadToken by remember { mutableIntStateOf(0) }
            var defaultProfileId by remember {
                mutableStateOf(profilePreferences.defaultProfileId())
            }
            val selectedProfileId = profilePreferences.selectedProfileId()

            LaunchedEffect(profilesReloadToken) {
                profilesLoadError = false
                val result = profileRepository.listProfiles()
                profiles = result.getOrNull()
                profilesLoadError = result.isFailure
            }

            val loadedProfiles = profiles
            if (loadedProfiles == null && !profilesLoadError) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.material3.CircularProgressIndicator()
                }
            } else if (profilesLoadError || loadedProfiles.isNullOrEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Impossible de charger vos profils.",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(12.dp))
                        androidx.compose.material3.Button(
                            onClick = { profilesReloadToken += 1 },
                        ) {
                            Text("Réessayer")
                        }
                    }
                }
            } else {
                val preferenceProfileId = selectedProfileId
                    ?: loadedProfiles.firstOrNull()?.id
                    .orEmpty()
                OnboardingPreferencesScreen(
                    deviceProfile = profile,
                    profiles = loadedProfiles,
                    selectedDefaultProfileId = defaultProfileId,
                    selectedProfileId = preferenceProfileId,
                    initialProfilePreferences = setupPreferences.profile(preferenceProfileId),
                    initialDevicePreferences = setupPreferences.device(),
                    onDefaultProfileChanged = { profileId ->
                        defaultProfileId = profileId
                        profilePreferences.setDefaultProfileId(profileId)
                    },
                    onProfilePreferencesSaved = { profileId, snapshot ->
                        setupPreferences.saveProfile(profileId, snapshot)
                    },
                    onDevicePreferencesSaved = { snapshot ->
                        setupPreferences.saveDevice(snapshot)
                        InterfaceLanguageController.apply(
                            context,
                            snapshot.interfaceLanguage,
                        )
                    },
                    onFinished = {
                        onboardingPreferences.markCompleted()
                        navController.navigate("system-gate") {
                            popUpTo("onboarding") { inclusive = true }
                        }
                    },
                )
            }
        }

        composable("system-gate") {
            val context = LocalContext.current
            val appContext = context.applicationContext
            val repository = remember(appContext) {
                SystemStateRepository(SecureSessionStore(appContext))
            }
            val preferences = remember(appContext) {
                SystemStatePreferences(appContext)
            }
            var state by remember { mutableStateOf<SystemGateState?>(null) }

            LaunchedEffect(Unit) {
                val loaded = repository.loadAndroidState()
                state = if (
                    loaded is SystemGateState.PlannedMaintenance &&
                    !preferences.shouldShowPlannedMaintenance()
                ) {
                    SystemGateState.Normal
                } else {
                    loaded
                }
            }

            when (val loaded = state) {
                null -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.material3.CircularProgressIndicator()
                }

                SystemGateState.Normal -> LaunchedEffect(Unit) {
                    navController.navigate("update-gate") {
                        popUpTo("system-gate") { inclusive = true }
                    }
                }

                else -> SystemStateScreen(
                    deviceProfile = profile,
                    state = loaded,
                    onContinue = {
                        if (loaded is SystemGateState.PlannedMaintenance) {
                            preferences.dismissPlannedMaintenance()
                        }
                        navController.navigate("update-gate") {
                            popUpTo("system-gate") { inclusive = true }
                        }
                    },
                    onSupport = {
                        runCatching {
                            context.startActivity(
                                android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse("https://zyviotv.fr"),
                                ),
                            )
                        }
                    },
                    onSignOut = {
                        scope.launch {
                            fr.zyviotv.player.data.auth.SupabaseAuthRepository(
                                SecureSessionStore(appContext),
                            ).signOut()
                            navController.navigate("auth") {
                                popUpTo("system-gate") { inclusive = true }
                            }
                        }
                    },
                )
            }
        }

        composable("update-gate") {
            val context = LocalContext.current
            val appContext = context.applicationContext
            val updateRepository = remember(appContext) {
                AppUpdateRepository(SecureSessionStore(appContext))
            }
            val updatePreferences = remember(appContext) {
                AppUpdatePreferences(appContext)
            }
            var policy by remember {
                mutableStateOf<fr.zyviotv.player.data.settings.AppUpdatePolicy?>(null)
            }
            var checked by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                policy = updateRepository
                    .loadAndroidPolicy(fr.zyviotv.player.BuildConfig.VERSION_CODE.toLong())
                    .getOrNull()
                checked = true
            }

            val loadedPolicy = policy
            if (!checked) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.material3.CircularProgressIndicator()
                }
            } else if (
                loadedPolicy == null ||
                loadedPolicy.kind == AppUpdateKind.None ||
                (
                    loadedPolicy.kind == AppUpdateKind.Optional &&
                        !updatePreferences.shouldShowOptionalUpdate(
                            loadedPolicy.latestVersionCode,
                        )
                    )
            ) {
                LaunchedEffect(loadedPolicy?.latestVersionCode) {
                    navController.navigate(AppDestination.Home.route) {
                        popUpTo("update-gate") { inclusive = true }
                    }
                }
            } else {
                AppUpdateGateScreen(
                    deviceProfile = profile,
                    policy = loadedPolicy,
                    onUpdate = {
                        val url = loadedPolicy.storeUrl
                        if (url != null) {
                            runCatching {
                                context.startActivity(
                                    android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse(url),
                                    ),
                                )
                            }
                        }
                    },
                    onLater = {
                        if (loadedPolicy.kind == AppUpdateKind.Optional) {
                            updatePreferences.dismissOptionalUpdate(
                                loadedPolicy.latestVersionCode,
                            )
                            navController.navigate(AppDestination.Home.route) {
                                popUpTo("update-gate") { inclusive = true }
                            }
                        }
                    },
                    onSupport = {
                        runCatching {
                            context.startActivity(
                                android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse("https://zyviotv.fr"),
                                ),
                            )
                        }
                    },
                    onSignOut = {
                        scope.launch {
                            fr.zyviotv.player.data.auth.SupabaseAuthRepository(
                                SecureSessionStore(appContext),
                            ).signOut()
                            navController.navigate("auth") {
                                popUpTo("update-gate") { inclusive = true }
                            }
                        }
                    },
                )
            }
        }

        composable("onboarding-add-playlist") {
            AddPlaylistScreen(
                onBack = { navController.popBackStack() },
                onSaved = {
                    providerCatalog.reload()
                    navController.popBackStack()
                },
            )
        }

        composable("profile-switch") {
            WhoIsWatchingGate(
                deviceProfile = profile,
                forceChooser = true,
                onProfileSelected = {
                    providerCatalog.reload()
                    librarySession.reload()
                    navController.navigate(AppDestination.Home.route) {
                        popUpTo("profile-switch") { inclusive = true }
                    }
                },
            )
        }

        composable("guide") {
            val context = LocalContext.current
            val readyProvider = providerState as? ProviderCatalogState.Ready
            var guideState by remember { mutableStateOf<EpgGuideState>(EpgGuideState.Loading) }
            var guideReloadToken by remember { mutableIntStateOf(0) }

            LaunchedEffect(
                readyProvider?.playlistId,
                readyProvider?.snapshot?.liveChannels,
                guideReloadToken,
            ) {
                if (readyProvider == null) {
                    guideState = when (providerState) {
                        ProviderCatalogState.Loading -> EpgGuideState.Loading
                        is ProviderCatalogState.Error -> EpgGuideState.Error(providerState.message)
                        is ProviderCatalogState.Empty -> EpgGuideState.Ready(emptyList())
                        is ProviderCatalogState.Ready -> EpgGuideState.Loading
                    }
                    return@LaunchedEffect
                }

                if (readyProvider.isOffline) {
                    guideState = EpgGuideState.Error(
                        "Le guide TV nécessite une connexion Internet. Votre catalogue local reste disponible.",
                    )
                    return@LaunchedEffect
                }

                guideState = EpgGuideState.Loading
                val repository = SupabaseCloudSyncRepository(
                    sessionStore = SecureSessionStore(context.applicationContext),
                )
                val secret = repository
                    .getPlaylistSecret(readyProvider.playlistId)
                    .getOrElse {
                        guideState = EpgGuideState.Error(
                            "Impossible de restaurer la configuration de la playlist.",
                        )
                        return@LaunchedEffect
                    }

                val xtream = secret as? PlaylistSecret.Xtream
                if (xtream == null) {
                    guideState = EpgGuideState.Error(
                        "Le guide EPG réel est actuellement disponible pour les playlists Xtream.",
                    )
                    return@LaunchedEffect
                }

                val nowEpochSeconds = System.currentTimeMillis() / 1000L
                val result = AndroidXtreamGuideLoader(
                    credentials = XtreamCredentials(
                        serverUrl = xtream.serverUrl,
                        username = xtream.username,
                        password = xtream.password,
                    ),
                ).load(
                    channels = readyProvider.snapshot.liveChannels,
                    window = EpgWindow.around(nowEpochSeconds),
                )

                guideState = when (result) {
                    is GuideLoadResult.Failure -> EpgGuideState.Error(result.message)
                    is GuideLoadResult.Success -> {
                        val programmesByChannel = result.channels.associate {
                            it.channelId to it.programmes
                        }
                        EpgGuideState.Ready(
                            readyProvider.snapshot.liveChannels
                                .take(50)
                                .mapIndexed { index, channel ->
                                    EpgChannelUi(
                                        id = channel.id,
                                        number = (index + 1).toString(),
                                        name = channel.name,
                                        sourceLabel = readyProvider.playlistName,
                                        programmes = programmesByChannel[channel.id].orEmpty(),
                                    )
                                },
                        )
                    }
                }
            }

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
                    state = guideState,
                    onRetry = { guideReloadToken += 1 },
                    onWatchChannel = { channel ->
                        val source = readyProvider
                            ?.snapshot
                            ?.liveChannels
                            ?.firstOrNull { it.id == channel.id }
                        if (source != null) {
                            playbackRequest = PlaybackRequest(
                                title = source.name,
                                streamUrl = source.streamUrl,
                                kind = PlaybackKind.Live,
                            )
                            playbackSyncContext = null
                            navController.navigate("player")
                        }
                    },
                    onWatchProgramme = { channel, _ ->
                        val source = readyProvider
                            ?.snapshot
                            ?.liveChannels
                            ?.firstOrNull { it.id == channel.id }
                        if (source != null) {
                            playbackRequest = PlaybackRequest(
                                title = source.name,
                                streamUrl = source.streamUrl,
                                kind = PlaybackKind.Live,
                            )
                            navController.navigate("player")
                        }
                    },
                )
            }
        }











        composable("settings-profiles") {
            AdaptiveShell(
                profile = profile,
                destinations = AppDestination.entries,
                selectedRoute = AppDestination.Settings.route,
                onDestinationSelected = { target ->
                    navController.navigate(target.route) {
                        launchSingleTop = true
                    }
                },
            ) {
                ProfilesSettingsScreen(
                    profile = profile,
                    onBack = { navController.popBackStack() },
                    onProfileSelectionChanged = librarySession::reload,
                )
            }
        }

        composable("settings-parental") {
            AdaptiveShell(
                profile = profile,
                destinations = AppDestination.entries,
                selectedRoute = AppDestination.Settings.route,
                onDestinationSelected = { target ->
                    navController.navigate(target.route) {
                        launchSingleTop = true
                    }
                },
            ) {
                ParentalControlsScreen(
                    onBack = { navController.popBackStack() },
                )
            }
        }

        composable("settings-cache") {
            AdaptiveShell(
                profile = profile,
                destinations = AppDestination.entries,
                selectedRoute = AppDestination.Settings.route,
                onDestinationSelected = { target ->
                    navController.navigate(target.route) {
                        launchSingleTop = true
                    }
                },
            ) {
                CacheSettingsScreen(
                    onBack = { navController.popBackStack() },
                )
            }
        }

        composable("settings-playback-data") {
            AdaptiveShell(
                profile = profile,
                destinations = AppDestination.entries,
                selectedRoute = AppDestination.Settings.route,
                onDestinationSelected = { target ->
                    navController.navigate(target.route) {
                        launchSingleTop = true
                    }
                },
            ) {
                PlaybackDataSettingsScreen(
                    onBack = { navController.popBackStack() },
                )
            }
        }

        composable("settings-devices") {
            AdaptiveShell(
                profile = profile,
                destinations = AppDestination.entries,
                selectedRoute = AppDestination.Settings.route,
                onDestinationSelected = { target ->
                    navController.navigate(target.route) {
                        launchSingleTop = true
                    }
                },
            ) {
                DevicesSettingsScreen(
                    profile = profile,
                    onBack = { navController.popBackStack() },
                )
            }
        }

        composable("settings-playlists-add") {
            AdaptiveShell(
                profile = profile,
                destinations = AppDestination.entries,
                selectedRoute = AppDestination.Settings.route,
                onDestinationSelected = { target ->
                    navController.navigate(target.route) {
                        launchSingleTop = true
                    }
                },
            ) {
                AddPlaylistScreen(
                    onBack = { navController.popBackStack() },
                    onSaved = {
                        providerCatalog.reload()
                        navController.popBackStack()
                    },
                )
            }
        }

        composable("settings-playlists") {
            AdaptiveShell(
                profile = profile,
                destinations = AppDestination.entries,
                selectedRoute = AppDestination.Settings.route,
                onDestinationSelected = { target ->
                    navController.navigate(target.route) {
                        launchSingleTop = true
                    }
                },
            ) {
                PlaylistSettingsScreen(
                    profile = profile,
                    onBack = { navController.popBackStack() },
                    onAddPlaylist = { navController.navigate("settings-playlists-add") },
                    onChanged = providerCatalog::reload,
                )
            }
        }

        composable("history") {
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
                HistoryScreen(
                    profile = profile,
                    state = libraryState,
                    activePlaylistId = activePlaylistId,
                    onRetry = librarySession::reload,
                    onOpen = { progress ->
                        val readyProvider = providerState as? ProviderCatalogState.Ready
                        if (readyProvider != null && progress.playlistId == readyProvider.playlistId) {
                            when (progress.contentType) {
                                ProgressContentType.Movie -> {
                                    selectedMovie = readyProvider.snapshot.movies
                                        .firstOrNull { it.id == progress.contentId }
                                    if (selectedMovie != null) {
                                        navController.navigate("movie-detail")
                                    }
                                }

                                ProgressContentType.Episode -> {
                                    val seriesId = progress.seriesId
                                    if (!seriesId.isNullOrBlank()) {
                                        selectedSeries = readyProvider.snapshot.series
                                            .firstOrNull { it.id == seriesId }
                                        if (selectedSeries != null) {
                                            seriesDetailState = SeriesDetailState.Loading
                                            seriesEpisodeSources = emptyMap()
                                            seriesDetailReloadToken += 1
                                            navController.navigate("series-detail")
                                        }
                                    }
                                }
                            }
                        }
                    },
                    onRemove = { progress ->
                        scope.launch {
                            librarySession.removeProgress(progress)
                        }
                    },
                )
            }
        }

        composable("continue-watching") {
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
                ContinueWatchingScreen(
                    profile = profile,
                    state = libraryState,
                    activePlaylistId = activePlaylistId,
                    onRetry = librarySession::reload,
                    onOpen = { progress ->
                        val readyProvider = providerState as? ProviderCatalogState.Ready
                        if (readyProvider != null && progress.playlistId == readyProvider.playlistId) {
                            when (progress.contentType) {
                                ProgressContentType.Movie -> {
                                    selectedMovie = readyProvider.snapshot.movies
                                        .firstOrNull { it.id == progress.contentId }
                                    if (selectedMovie != null) {
                                        navController.navigate("movie-detail")
                                    }
                                }

                                ProgressContentType.Episode -> {
                                    val seriesId = progress.seriesId
                                    if (!seriesId.isNullOrBlank()) {
                                        selectedSeries = readyProvider.snapshot.series
                                            .firstOrNull { it.id == seriesId }
                                        if (selectedSeries != null) {
                                            seriesDetailState = SeriesDetailState.Loading
                                            seriesEpisodeSources = emptyMap()
                                            seriesDetailReloadToken += 1
                                            navController.navigate("series-detail")
                                        }
                                    }
                                }
                            }
                        }
                    },
                )
            }
        }

        composable("favorites") {
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
                FavoritesScreen(
                    profile = profile,
                    state = libraryState,
                    onRetry = librarySession::reload,
                    onOpenFavorite = { favorite ->
                        val readyProvider = providerState as? ProviderCatalogState.Ready
                        if (readyProvider != null && favorite.playlistId == readyProvider.playlistId) {
                            when (favorite.contentType) {
                                FavoriteContentType.Live -> {
                                    val channel = readyProvider.snapshot.liveChannels
                                        .firstOrNull { it.id == favorite.contentId }
                                    if (
                                        channel != null &&
                                        !readyProvider.isOffline &&
                                        channel.streamUrl.isNotBlank()
                                    ) {
                                        playbackRequest = PlaybackRequest(
                                            title = channel.name,
                                            streamUrl = channel.streamUrl,
                                            kind = PlaybackKind.Live,
                                        )
                                        playbackSyncContext = null
                                        navController.navigate("player")
                                    }
                                }

                                FavoriteContentType.Movie -> {
                                    selectedMovie = readyProvider.snapshot.movies
                                        .firstOrNull { it.id == favorite.contentId }
                                    if (selectedMovie != null) {
                                        navController.navigate("movie-detail")
                                    }
                                }

                                FavoriteContentType.Series -> {
                                    selectedSeries = readyProvider.snapshot.series
                                        .firstOrNull { it.id == favorite.contentId }
                                    if (selectedSeries != null) {
                                        seriesDetailState = SeriesDetailState.Loading
                                        seriesEpisodeSources = emptyMap()
                                        seriesDetailReloadToken += 1
                                        navController.navigate("series-detail")
                                    }
                                }
                            }
                        }
                    },
                    onRemoveFavorite = { favorite ->
                        scope.launch {
                            librarySession.toggleFavorite(favorite)
                        }
                    },
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
                val readyProvider = providerState as? ProviderCatalogState.Ready
                SearchScreen(
                    profile = profile,
                    snapshot = providerState.snapshotOrEmpty(),
                    lockedCategoryKeys = readyProvider?.contentLocks?.lockedCategoryKeys.orEmpty(),
                    lockedContentKeys = readyProvider?.contentLocks?.lockedContentKeys.orEmpty(),
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
                    val readyProvider = providerState as? ProviderCatalogState.Ready
                    val movieProgress = readyProvider?.let {
                        librarySession.progressFor(
                            playlistId = it.playlistId,
                            type = ProgressContentType.Movie,
                            contentId = movie.id,
                        )
                    }
                    val movieFavorite = readyProvider?.let {
                        librarySession.isFavorite(
                            playlistId = it.playlistId,
                            type = FavoriteContentType.Movie,
                            contentId = movie.id,
                        )
                    } ?: false

                    MovieDetailScreen(
                        profile = profile,
                        isOffline = readyProvider?.isOffline == true,
                        state = MovieDetailState.Ready(
                            MovieDetailUi(
                                id = movie.id,
                                title = movie.title,
                                posterUrl = movie.posterUrl,
                                progress = movieProgress?.fraction ?: 0f,
                                isFavorite = movieFavorite,
                            ),
                        ),
                        onPlay = { _, resume ->
                            if (readyProvider?.isOffline == true || movie.streamUrl.isBlank()) {
                                return@MovieDetailScreen
                            }
                            playbackRequest = PlaybackRequest(
                                title = movie.title,
                                streamUrl = movie.streamUrl,
                                kind = PlaybackKind.Movie,
                                resumePositionMs = if (resume) {
                                    movieProgress?.positionMs ?: 0L
                                } else {
                                    0L
                                },
                            )
                            playbackSyncContext = readyProvider?.let {
                                PlaybackSyncContext(
                                    playlistId = it.playlistId,
                                    contentType = ProgressContentType.Movie,
                                    contentId = movie.id,
                                    title = movie.title,
                                    artworkUrl = movie.posterUrl,
                                )
                            }
                            lastSyncedPositionMs = movieProgress?.positionMs ?: 0L
                            navController.navigate("player")
                        },
                        onToggleFavorite = {
                            val provider = readyProvider
                            if (provider != null) {
                                scope.launch {
                                librarySession.toggleFavorite(
                                    SyncedFavorite(
                                        playlistId = provider.playlistId,
                                        contentType = FavoriteContentType.Movie,
                                        contentId = movie.id,
                                        title = movie.title,
                                        artworkUrl = movie.posterUrl,
                                    ),
                                )
                                }
                            }
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

                LaunchedEffect(series.id, readyProvider.playlistId, seriesDetailReloadToken, readyProvider.isOffline) {
                    seriesDetailState = SeriesDetailState.Loading
                    seriesEpisodeSources = emptyMap()

                    if (readyProvider.isOffline) {
                        seriesDetailState = SeriesDetailState.Ready(
                            SeriesDetailUi(
                                id = series.id,
                                title = series.title,
                                isFavorite = librarySession.isFavorite(
                                    playlistId = readyProvider.playlistId,
                                    type = FavoriteContentType.Series,
                                    contentId = series.id,
                                ),
                                episodes = emptyList(),
                            ),
                        )
                        return@LaunchedEffect
                    }

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
                                    isFavorite = librarySession.isFavorite(
                                        playlistId = readyProvider.playlistId,
                                        type = FavoriteContentType.Series,
                                        contentId = series.id,
                                    ),
                                    episodes = result.detail.episodes.map { episode ->
                                        val progress = librarySession.progressFor(
                                            playlistId = readyProvider.playlistId,
                                            type = ProgressContentType.Episode,
                                            contentId = episode.id,
                                        )
                                        EpisodeDetailUi(
                                            id = episode.id,
                                            season = episode.season,
                                            number = episode.number,
                                            title = episode.title,
                                            synopsis = episode.synopsis,
                                            progress = progress?.fraction ?: 0f,
                                            state = when {
                                                progress?.completed == true -> EpisodeWatchState.Watched
                                                (progress?.positionMs ?: 0L) > 0L -> EpisodeWatchState.InProgress
                                                else -> EpisodeWatchState.Unwatched
                                            },
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
                        isOffline = readyProvider.isOffline,
                        onRetry = {
                            seriesDetailReloadToken += 1
                        },
                        onToggleFavorite = {
                            scope.launch {
                                librarySession.toggleFavorite(
                                    SyncedFavorite(
                                        playlistId = readyProvider.playlistId,
                                        contentType = FavoriteContentType.Series,
                                        contentId = series.id,
                                        title = series.title,
                                        artworkUrl = series.posterUrl,
                                    ),
                                )
                                val ready = seriesDetailState as? SeriesDetailState.Ready
                                if (ready != null) {
                                    seriesDetailState = ready.copy(
                                        series = ready.series.copy(
                                            isFavorite = librarySession.isFavorite(
                                                playlistId = readyProvider.playlistId,
                                                type = FavoriteContentType.Series,
                                                contentId = series.id,
                                            ),
                                        ),
                                    )
                                }
                            }
                        },
                        onPlayEpisode = { episode ->
                            val source = seriesEpisodeSources[episode.id]
                            if (source != null) {
                                val progress = librarySession.progressFor(
                                    playlistId = readyProvider.playlistId,
                                    type = ProgressContentType.Episode,
                                    contentId = source.id,
                                )
                                playbackRequest = PlaybackRequest(
                                    title = series.title + " — S" +
                                        source.season + " E" + source.number +
                                        " — " + source.title,
                                    streamUrl = source.streamUrl,
                                    kind = PlaybackKind.Episode,
                                    resumePositionMs = progress?.positionMs ?: 0L,
                                )
                                playbackSyncContext = PlaybackSyncContext(
                                    playlistId = readyProvider.playlistId,
                                    contentType = ProgressContentType.Episode,
                                    contentId = source.id,
                                    title = source.title,
                                    seriesId = series.id,
                                    seasonNumber = source.season,
                                    episodeNumber = source.number,
                                    artworkUrl = series.posterUrl,
                                )
                                lastSyncedPositionMs = progress?.positionMs ?: 0L
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
                    onNext = { advanceToNextEpisode(false) },
                    onBack = {
                        playbackRequest = null
                        navController.popBackStack()
                    },
                    onOpenGuide = {
                        if (request.kind == PlaybackKind.Live) {
                            navController.navigate("guide")
                        }
                    },
                    onPlaybackExit = { positionMs, durationMs ->
                        val sync = playbackSyncContext
                        if (sync != null) {
                            val completed = durationMs != null &&
                                durationMs > 0L &&
                                positionMs.toDouble() / durationMs.toDouble() >= 0.95
                            lastSyncedPositionMs = positionMs
                            scope.launch {
                                librarySession.saveProgress(
                                    SyncedWatchProgress(
                                        playlistId = sync.playlistId,
                                        contentType = sync.contentType,
                                        contentId = sync.contentId,
                                        title = sync.title,
                                        seriesId = sync.seriesId,
                                        seasonNumber = sync.seasonNumber,
                                        episodeNumber = sync.episodeNumber,
                                        artworkUrl = sync.artworkUrl,
                                        positionMs = positionMs.coerceAtLeast(0L),
                                        durationMs = durationMs,
                                        completed = completed,
                                    ),
                                )
                            }
                        }
                    },
                    onPlaybackEnded = { positionMs, durationMs ->
                        val sync = playbackSyncContext
                        if (sync != null) {
                            lastSyncedPositionMs = positionMs
                            scope.launch {
                                librarySession.saveProgress(
                                    SyncedWatchProgress(
                                        playlistId = sync.playlistId,
                                        contentType = sync.contentType,
                                        contentId = sync.contentId,
                                        title = sync.title,
                                        seriesId = sync.seriesId,
                                        seasonNumber = sync.seasonNumber,
                                        episodeNumber = sync.episodeNumber,
                                        artworkUrl = sync.artworkUrl,
                                        positionMs = positionMs.coerceAtLeast(0L),
                                        durationMs = durationMs,
                                        completed = true,
                                    ),
                                )
                            }
                            if (sync.contentType == ProgressContentType.Episode) {
                                advanceToNextEpisode(true)
                            }
                        }
                    },
                    onProgressChanged = { positionMs, durationMs ->
                        val sync = playbackSyncContext
                        if (sync != null) {
                            val completed = durationMs != null &&
                                durationMs > 0L &&
                                positionMs.toDouble() / durationMs.toDouble() >= 0.95
                            val shouldSync = completed ||
                                positionMs == 0L ||
                                kotlin.math.abs(positionMs - lastSyncedPositionMs) >= 15_000L
                            if (shouldSync) {
                                lastSyncedPositionMs = positionMs
                                scope.launch {
                                    librarySession.saveProgress(
                                        SyncedWatchProgress(
                                            playlistId = sync.playlistId,
                                            contentType = sync.contentType,
                                            contentId = sync.contentId,
                                            title = sync.title,
                                            seriesId = sync.seriesId,
                                            seasonNumber = sync.seasonNumber,
                                            episodeNumber = sync.episodeNumber,
                                            artworkUrl = sync.artworkUrl,
                                            positionMs = positionMs.coerceAtLeast(0L),
                                            durationMs = durationMs,
                                            completed = completed,
                                        ),
                                    )
                                }
                            }
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
                                providerState = providerState,
                                libraryState = libraryState,
                                nextEpisodes = homeNextEpisodes,
                                onPlayNextEpisode = { next ->
                                    playbackRequest = PlaybackRequest(
                                        title = next.seriesTitle + " — S" +
                                            next.episode.season + " E" + next.episode.number +
                                            " — " + next.episode.title,
                                        streamUrl = next.episode.streamUrl,
                                        kind = PlaybackKind.Episode,
                                    )
                                    playbackSyncContext = PlaybackSyncContext(
                                        playlistId = next.playlistId,
                                        contentType = ProgressContentType.Episode,
                                        contentId = next.episode.id,
                                        title = next.episode.title,
                                        seriesId = next.seriesId,
                                        seasonNumber = next.episode.season,
                                        episodeNumber = next.episode.number,
                                        artworkUrl = next.artworkUrl,
                                    )
                                    lastSyncedPositionMs = 0L
                                    navController.navigate("player")
                                },
                                onTuneRecentChannel = { channel ->
                                    val ready = providerState as? ProviderCatalogState.Ready
                                    if (
                                        ready != null &&
                                        !ready.isOffline &&
                                        channel.streamUrl.isNotBlank()
                                    ) {
                                        playbackRequest = PlaybackRequest(
                                            title = channel.name,
                                            streamUrl = channel.streamUrl,
                                            kind = PlaybackKind.Live,
                                        )
                                        playbackSyncContext = null
                                        scope.launch {
                                            librarySession.recordLiveHistory(
                                                playlistId = ready.playlistId,
                                                channelId = channel.id,
                                                channelName = channel.name,
                                                logoUrl = channel.logoUrl,
                                            )
                                        }
                                        navController.navigate("player")
                                    } else {
                                        navController.navigate(AppDestination.Live.route)
                                    }
                                },
                                onResumeProgress = { progress ->
                                    val ready = providerState as? ProviderCatalogState.Ready
                                    if (ready == null || ready.isOffline) {
                                        navController.navigate("continue-watching")
                                    } else {
                                        when (progress.contentType) {
                                            ProgressContentType.Movie -> {
                                                val movie = ready.snapshot.movies
                                                    .firstOrNull { it.id == progress.contentId }
                                                if (movie != null && movie.streamUrl.isNotBlank()) {
                                                    playbackRequest = PlaybackRequest(
                                                        title = movie.title,
                                                        streamUrl = movie.streamUrl,
                                                        kind = PlaybackKind.Movie,
                                                        resumePositionMs = progress.positionMs,
                                                    )
                                                    playbackSyncContext = PlaybackSyncContext(
                                                        playlistId = ready.playlistId,
                                                        contentType = ProgressContentType.Movie,
                                                        contentId = movie.id,
                                                        title = movie.title,
                                                        artworkUrl = movie.posterUrl,
                                                    )
                                                    lastSyncedPositionMs = progress.positionMs
                                                    navController.navigate("player")
                                                } else {
                                                    navController.navigate("continue-watching")
                                                }
                                            }

                                            ProgressContentType.Episode -> {
                                                val seriesId = progress.seriesId
                                                if (seriesId.isNullOrBlank()) {
                                                    navController.navigate("continue-watching")
                                                } else {
                                                    scope.launch {
                                                        val repository = SupabaseCloudSyncRepository(
                                                            sessionStore = SecureSessionStore(appContext),
                                                        )
                                                        val secret = repository
                                                            .getPlaylistSecret(ready.playlistId)
                                                            .getOrNull() as? PlaylistSecret.Xtream
                                                        if (secret == null) {
                                                            navController.navigate("continue-watching")
                                                            return@launch
                                                        }
                                                        val result = AndroidXtreamSeriesDetailLoader(
                                                            XtreamCredentials(
                                                                serverUrl = secret.serverUrl,
                                                                username = secret.username,
                                                                password = secret.password,
                                                            ),
                                                        ).load(seriesId)
                                                        val detail = (
                                                            result as? SeriesDetailLoadResult.Success
                                                            )?.detail
                                                        val episode = detail?.episodes?.firstOrNull {
                                                            it.id == progress.contentId ||
                                                                (
                                                                    it.season == progress.seasonNumber &&
                                                                        it.number == progress.episodeNumber
                                                                    )
                                                        }
                                                        if (episode == null || episode.streamUrl.isBlank()) {
                                                            navController.navigate("continue-watching")
                                                            return@launch
                                                        }
                                                        val series = ready.snapshot.series
                                                            .firstOrNull { it.id == seriesId }
                                                        playbackRequest = PlaybackRequest(
                                                            title = (series?.title ?: progress.title) +
                                                                " — S" + episode.season +
                                                                " E" + episode.number +
                                                                " — " + episode.title,
                                                            streamUrl = episode.streamUrl,
                                                            kind = PlaybackKind.Episode,
                                                            resumePositionMs = progress.positionMs,
                                                        )
                                                        playbackSyncContext = PlaybackSyncContext(
                                                            playlistId = ready.playlistId,
                                                            contentType = ProgressContentType.Episode,
                                                            contentId = episode.id,
                                                            title = episode.title,
                                                            seriesId = seriesId,
                                                            seasonNumber = episode.season,
                                                            episodeNumber = episode.number,
                                                            artworkUrl = series?.posterUrl
                                                                ?: progress.artworkUrl,
                                                        )
                                                        lastSyncedPositionMs = progress.positionMs
                                                        navController.navigate("player")
                                                    }
                                                }
                                            }
                                        }
                                    }
                                },
                                onOpenMovieItem = { movie ->
                                    selectedMovie = movie
                                    navController.navigate("movie-detail")
                                },
                                onOpenSeriesItem = { series ->
                                    selectedSeries = series
                                    seriesDetailState = SeriesDetailState.Loading
                                    seriesEpisodeSources = emptyMap()
                                    seriesDetailReloadToken += 1
                                    navController.navigate("series-detail")
                                },
                                onOpenLive = { navController.navigate(AppDestination.Live.route) },
                                onOpenMovies = { navController.navigate(AppDestination.Movies.route) },
                                onOpenSeries = { navController.navigate(AppDestination.Series.route) },
                                onOpenSearch = { navController.navigate("search") },
                                onOpenFavorites = { navController.navigate("favorites") },
                                onOpenContinueWatching = { navController.navigate("continue-watching") },
                                onOpenHistory = { navController.navigate("history") },
                            )
                        }

                        AppDestination.Live -> {
                            LiveTvScreen(
                                profile = profile,
                                state = providerState.toLiveState(),
                                onRetry = providerCatalog::reload,
                                onTuneChannel = { channel ->
                                    val ready = providerState as? ProviderCatalogState.Ready
                                    val source = ready
                                        ?.snapshot
                                        ?.liveChannels
                                        ?.firstOrNull { it.id == channel.id }
                                    if (ready != null && source != null) {
                                        playbackRequest = PlaybackRequest(
                                            title = source.name,
                                            streamUrl = source.streamUrl,
                                            kind = PlaybackKind.Live,
                                        )
                                        playbackSyncContext = null
                                        scope.launch {
                                            librarySession.recordLiveHistory(
                                                playlistId = ready.playlistId,
                                                channelId = source.id,
                                                channelName = source.name,
                                                logoUrl = source.logoUrl,
                                            )
                                        }
                                        navController.navigate("player")
                                    }
                                },
                                onOpenGuide = { navController.navigate("guide") },
                            )
                        }

                        AppDestination.Movies -> {
                            MoviesScreen(
                                profile = profile,
                                state = providerState.toMoviesState(movieProgressById),
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
                                state = providerState.toSeriesState(seriesProgressById),
                                onRetry = providerCatalog::reload,
                                onSeriesSelected = { seriesUi ->
                                    selectedSeries = providerState
                                        .snapshotOrEmpty()
                                        .series
                                        .firstOrNull { it.id == seriesUi.id }
                                    if (selectedSeries != null) {
                                        seriesDetailState = SeriesDetailState.Loading
                                        seriesEpisodeSources = emptyMap()
                                        seriesDetailReloadToken += 1
                                        navController.navigate("series-detail")
                                    }
                                },
                            )
                        }

                        AppDestination.Settings -> {
                            AccountSettingsScreen(
                                onOpenPlaylists = { navController.navigate("settings-playlists") },
                                onOpenDevices = { navController.navigate("settings-devices") },
                                onOpenPlaybackData = { navController.navigate("settings-playback-data") },
                                onOpenCache = { navController.navigate("settings-cache") },
                                onOpenParentalControls = { navController.navigate("settings-parental") },
                                onOpenProfiles = { navController.navigate("settings-profiles") },
                                onSwitchProfile = { navController.navigate("profile-switch") },
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
