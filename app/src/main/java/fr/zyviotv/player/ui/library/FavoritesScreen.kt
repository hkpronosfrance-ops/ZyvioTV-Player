package fr.zyviotv.player.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.zyviotv.player.shared.sync.FavoriteContentType
import fr.zyviotv.player.shared.sync.SyncedFavorite
import fr.zyviotv.player.ui.DeviceProfile
import fr.zyviotv.player.ui.theme.ZyvioSurface1
import fr.zyviotv.player.ui.theme.ZyvioSurface2
import fr.zyviotv.player.ui.theme.ZyvioTextSecondary
import fr.zyviotv.player.ui.tv.tvFocusEffect

private enum class FavoriteFilter(val label: String) {
    All("Tout"),
    Live("TV"),
    Movies("Films"),
    Series("Séries"),
}

@Composable
fun FavoritesScreen(
    profile: DeviceProfile,
    state: LibraryState,
    onRetry: () -> Unit,
    onOpenFavorite: (SyncedFavorite) -> Unit,
    onRemoveFavorite: (SyncedFavorite) -> Unit,
    isOffline: Boolean = false,
) {
    when (state) {
        LibraryState.Loading -> LoadingFavorites()
        is LibraryState.Error -> ErrorFavorites(state.message, onRetry)
        is LibraryState.Ready -> ReadyFavorites(
            profile = profile,
            favorites = state.snapshot.favorites,
            isOffline = isOffline,
            isFromCache = state.snapshot.isFromCache,
            onOpenFavorite = onOpenFavorite,
            onRemoveFavorite = onRemoveFavorite,
        )
    }
}

@Composable
private fun ReadyFavorites(
    profile: DeviceProfile,
    favorites: List<SyncedFavorite>,
    isOffline: Boolean,
    isFromCache: Boolean,
    onOpenFavorite: (SyncedFavorite) -> Unit,
    onRemoveFavorite: (SyncedFavorite) -> Unit,
) {
    var filter by remember { mutableStateOf(FavoriteFilter.All) }
    var sortAlphabetically by remember { mutableStateOf(false) }

    val filtered = remember(favorites, filter, sortAlphabetically) {
        val base = when (filter) {
            FavoriteFilter.All -> favorites
            FavoriteFilter.Live -> favorites.filter { it.contentType == FavoriteContentType.Live }
            FavoriteFilter.Movies -> favorites.filter { it.contentType == FavoriteContentType.Movie }
            FavoriteFilter.Series -> favorites.filter { it.contentType == FavoriteContentType.Series }
        }
        if (sortAlphabetically) base.sortedBy { it.title.lowercase() } else base
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Favoris",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    text = "${favorites.size} contenu" + if (favorites.size > 1) "s" else "",
                    color = ZyvioTextSecondary,
                )
            }
            TextButton(onClick = { sortAlphabetically = !sortAlphabetically }) {
                Text(if (sortAlphabetically) "Récents" else "A-Z")
            }
        }

        val statusMessage = when {
            isOffline -> "Hors connexion : favoris consultables en lecture seule."
            isFromCache -> "Favoris affichés depuis l’appareil : la synchronisation du compte n’a pas abouti."
            else -> null
        }
        if (statusMessage != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = statusMessage,
                color = ZyvioTextSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Spacer(Modifier.height(14.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FavoriteFilter.entries.forEach { item ->
                val count = when (item) {
                    FavoriteFilter.All -> favorites.size
                    FavoriteFilter.Live -> favorites.count { it.contentType == FavoriteContentType.Live }
                    FavoriteFilter.Movies -> favorites.count { it.contentType == FavoriteContentType.Movie }
                    FavoriteFilter.Series -> favorites.count { it.contentType == FavoriteContentType.Series }
                }
                AssistChip(
                    onClick = { filter = item },
                    label = { Text("${item.label} ($count)") },
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        if (filtered.isEmpty()) {
            EmptyFavorites()
            return
        }

        val columns = when (profile) {
            DeviceProfile.Mobile -> 2
            DeviceProfile.Tablet -> 3
            DeviceProfile.Television -> 5
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(
                items = filtered,
                key = { "${it.playlistId}:${it.contentType}:${it.contentId}" },
            ) { favorite ->
                FavoriteCard(
                    favorite = favorite,
                    isTelevision = profile == DeviceProfile.Television,
                    enabled = !isOffline || favorite.contentType != FavoriteContentType.Live,
                    canRemove = !isOffline && !isFromCache,
                    onOpen = { onOpenFavorite(favorite) },
                    onRemove = { onRemoveFavorite(favorite) },
                )
            }
        }
    }
}

@Composable
private fun FavoriteCard(
    favorite: SyncedFavorite,
    isTelevision: Boolean,
    enabled: Boolean,
    canRemove: Boolean,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .tvFocusEffect(isTelevision, cornerRadiusDp = 16)
            .clickable(enabled = enabled, onClick = onOpen),
        color = ZyvioSurface1,
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (favorite.contentType == FavoriteContentType.Live) 88.dp else 150.dp),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = ZyvioSurface2,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = when (favorite.contentType) {
                                FavoriteContentType.Live -> Icons.Default.LiveTv
                                FavoriteContentType.Movie -> Icons.Default.Movie
                                FavoriteContentType.Series -> Icons.Default.VideoLibrary
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            Text(
                text = favorite.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Bold,
            )

            Text(
                text = when (favorite.contentType) {
                    FavoriteContentType.Live -> "TV"
                    FavoriteContentType.Movie -> "Film"
                    FavoriteContentType.Series -> "Série"
                },
                modifier = Modifier.padding(top = 4.dp),
                color = ZyvioTextSecondary,
                style = MaterialTheme.typography.bodySmall,
            )

            TextButton(
                enabled = canRemove,
                onClick = onRemove,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text("Retirer")
            }
        }
    }
}

@Composable
private fun LoadingFavorites() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorFavorites(
    message: String,
    onRetry: () -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(
            color = ZyvioSurface1,
            shape = RoundedCornerShape(18.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Favoris indisponibles",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = message,
                    modifier = Modifier.padding(top = 8.dp),
                    color = ZyvioTextSecondary,
                )
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onRetry) {
                    Text("Réessayer")
                }
            }
        }
    }
}

@Composable
private fun EmptyFavorites() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Aucun favori",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Ajoutez une chaîne, un film ou une série à vos favoris.",
                modifier = Modifier.padding(top = 6.dp),
                color = ZyvioTextSecondary,
            )
        }
    }
}
