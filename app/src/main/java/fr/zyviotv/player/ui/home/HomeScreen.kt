package fr.zyviotv.player.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
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
import fr.zyviotv.player.ui.DeviceProfile
import fr.zyviotv.player.ui.tv.tvFocusEffect

@Composable
fun HomeScreen(
    profile: DeviceProfile,
    onOpenLive: () -> Unit,
    onOpenMovies: () -> Unit,
    onOpenSeries: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenFavorites: () -> Unit,
) {
    val contentPadding = if (profile == DeviceProfile.Mobile) 4.dp else 12.dp

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = contentPadding)
            .verticalScroll(rememberScrollState()),
    ) {
        Hero(
            profile = profile,
            onOpenLive = onOpenLive,
        )

        Spacer(Modifier.height(24.dp))

        QuickActions(
            isTelevision = profile == DeviceProfile.Television,
            onOpenLive = onOpenLive,
            onOpenMovies = onOpenMovies,
            onOpenSeries = onOpenSeries,
            onOpenSearch = onOpenSearch,
            onOpenFavorites = onOpenFavorites,
        )

        Spacer(Modifier.height(28.dp))

        HomeSection(
            title = "Reprendre la lecture",
            items = listOf(
                "Votre dernier contenu",
                "Épisode en cours",
                "Film commencé",
            ),
            poster = false,
            isTelevision = profile == DeviceProfile.Television,
        )

        HomeSection(
            title = "TV en direct",
            items = listOf(
                "Chaînes récentes",
                "Sports",
                "Information",
                "Divertissement",
            ),
            poster = false,
            isTelevision = profile == DeviceProfile.Television,
        )

        HomeSection(
            title = "Films",
            items = listOf(
                "Films populaires",
                "Nouveautés",
                "À découvrir",
                "Vos favoris",
            ),
            poster = true,
            isTelevision = profile == DeviceProfile.Television,
        )

        HomeSection(
            title = "Séries",
            items = listOf(
                "Séries populaires",
                "Nouvelles saisons",
                "À continuer",
                "Vos favoris",
            ),
            poster = true,
            isTelevision = profile == DeviceProfile.Television,
        )
    }
}

@Composable
private fun Hero(
    profile: DeviceProfile,
    onOpenLive: () -> Unit,
) {
    val heroHeight = when (profile) {
        DeviceProfile.Mobile -> 220.dp
        DeviceProfile.Tablet -> 270.dp
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
                .fillMaxWidth(0.75f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "ZYVIOTV",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
            )
            Text(
                text = "PLAYER",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(18.dp))
            Text(
                text = "Tout votre univers au même endroit.",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Retrouvez vos chaînes, films, séries et votre progression sur vos appareils.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = onOpenLive,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Regarder la TV")
            }
        }
    }
}

@Composable
private fun QuickActions(
    isTelevision: Boolean,
    onOpenLive: () -> Unit,
    onOpenMovies: () -> Unit,
    onOpenSeries: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenFavorites: () -> Unit,
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
        Column(
            modifier = Modifier.padding(18.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = label,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun HomeSection(
    title: String,
    items: List<String>,
    poster: Boolean,
    isTelevision: Boolean,
) {
    Text(
        text = title,
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
        items.forEachIndexed { index, label ->
            Card(
                modifier = Modifier
                    .width(
                        if (isTelevision) {
                            if (poster) 180.dp else 280.dp
                        } else {
                            if (poster) 132.dp else 210.dp
                        },
                    )
                    .tvFocusEffect(isTelevision)
                    .then(
                        if (poster) Modifier.aspectRatio(2f / 3f)
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
                        imageVector = if (poster) Icons.Default.Movie else Icons.Default.Tv,
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
                            text = label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (index == 0 && title == "Reprendre la lecture") {
                            Spacer(Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.62f)
                                    .height(3.dp)
                                    .background(MaterialTheme.colorScheme.primary),
                            )
                        }
                    }
                }
            }
        }
    }

    Spacer(Modifier.height(28.dp))
}
