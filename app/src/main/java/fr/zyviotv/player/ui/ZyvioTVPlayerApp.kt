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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import fr.zyviotv.player.shared.AppIdentity
import fr.zyviotv.player.ui.auth.AuthScreen
import fr.zyviotv.player.ui.settings.AccountSettingsScreen
import fr.zyviotv.player.ui.home.HomeScreen
import fr.zyviotv.player.ui.live.LiveTvScreen
import fr.zyviotv.player.ui.movies.MoviesScreen
import fr.zyviotv.player.ui.series.SeriesScreen
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

@Composable
fun ZyvioTVPlayerApp() {
    val navController = rememberNavController()
    val profile = rememberDeviceProfile()

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
                    navController.navigate(AppDestination.Home.route) {
                        popUpTo("auth") { inclusive = true }
                    }
                },
            )
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
                            LiveTvScreen(profile = profile)
                        }

                        AppDestination.Movies -> {
                            MoviesScreen(profile = profile)
                        }

                        AppDestination.Series -> {
                            SeriesScreen(profile = profile)
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
