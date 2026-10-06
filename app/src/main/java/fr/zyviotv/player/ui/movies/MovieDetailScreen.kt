package fr.zyviotv.player.ui.movies

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.zyviotv.player.ui.DeviceProfile
import fr.zyviotv.player.ui.theme.ZyvioSurface1
import fr.zyviotv.player.ui.theme.ZyvioSurface2
import fr.zyviotv.player.ui.theme.ZyvioTextSecondary
import fr.zyviotv.player.ui.tv.tvFocusEffect

data class MovieDetailUi(
    val id: String,
    val title: String,
    val year: String? = null,
    val durationLabel: String? = null,
    val rating: String? = null,
    val genres: List<String> = emptyList(),
    val synopsis: String? = null,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val progress: Float = 0f,
    val progressLabel: String? = null,
    val isFavorite: Boolean = false,
)

sealed interface MovieDetailState {
    data object Loading : MovieDetailState
    data class Ready(val movie: MovieDetailUi) : MovieDetailState
    data class Error(val message: String) : MovieDetailState
}

@Composable
fun MovieDetailScreen(
    profile: DeviceProfile,
    state: MovieDetailState,
    onRetry: () -> Unit = {},
    onPlay: (MovieDetailUi, Boolean) -> Unit = { _, _ -> },
    onToggleFavorite: (MovieDetailUi) -> Unit = {},
) {
    when (state) {
        MovieDetailState.Loading -> MovieDetailLoading()
        is MovieDetailState.Error -> MovieDetailError(state.message, onRetry)
        is MovieDetailState.Ready -> MovieDetailReady(
            profile = profile,
            movie = state.movie,
            onPlay = onPlay,
            onToggleFavorite = onToggleFavorite,
        )
    }
}

@Composable
private fun MovieDetailReady(
    profile: DeviceProfile,
    movie: MovieDetailUi,
    onPlay: (MovieDetailUi, Boolean) -> Unit,
    onToggleFavorite: (MovieDetailUi) -> Unit,
) {
    val isLarge = profile != DeviceProfile.Mobile
    val progress = movie.progress.coerceIn(0f, 1f)
    val primaryLabel = when {
        progress > 0.95f -> "Revoir"
        progress > 0f -> "Reprendre"
        else -> "Lire"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isLarge) 300.dp else 220.dp),
            color = ZyvioSurface1,
            shape = RoundedCornerShape(20.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ZyvioSurface2),
            ) {
                if (movie.backdropUrl.isNullOrBlank()) {
                    Icon(
                        imageVector = Icons.Default.Movie,
                        contentDescription = null,
                        modifier = Modifier.align(Alignment.Center),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        if (isLarge) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                PosterFallback(movie, Modifier.width(220.dp))
                MovieInfo(
                    modifier = Modifier.weight(1f),
                    movie = movie,
                    primaryLabel = primaryLabel,
                    onPlay = onPlay,
                    onToggleFavorite = onToggleFavorite,
                    isTelevision = profile == DeviceProfile.Television,
                )
            }
        } else {
            MovieInfo(
                modifier = Modifier.fillMaxWidth(),
                movie = movie,
                primaryLabel = primaryLabel,
                onPlay = onPlay,
                onToggleFavorite = onToggleFavorite,
                isTelevision = false,
            )
        }
    }
}

@Composable
private fun PosterFallback(
    movie: MovieDetailUi,
    modifier: Modifier,
) {
    Surface(
        modifier = modifier.aspectRatio(2f / 3f),
        color = ZyvioSurface1,
        shape = RoundedCornerShape(14.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ZyvioSurface2),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = movie.title,
                modifier = Modifier.padding(16.dp),
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun MovieInfo(
    modifier: Modifier,
    movie: MovieDetailUi,
    primaryLabel: String,
    onPlay: (MovieDetailUi, Boolean) -> Unit,
    onToggleFavorite: (MovieDetailUi) -> Unit,
    isTelevision: Boolean,
) {
    Column(modifier = modifier) {
        Text(
            text = movie.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
        )

        val meta = listOfNotNull(
            movie.year,
            movie.durationLabel,
            movie.rating?.let { "★ $it" },
        ).joinToString(" • ")

        if (meta.isNotBlank()) {
            Text(
                text = meta,
                modifier = Modifier.padding(top = 6.dp),
                color = ZyvioTextSecondary,
            )
        }

        if (movie.genres.isNotEmpty()) {
            Text(
                text = movie.genres.joinToString(" • "),
                modifier = Modifier.padding(top = 8.dp),
                color = ZyvioTextSecondary,
            )
        }

        movie.synopsis?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                modifier = Modifier.padding(top = 16.dp),
                color = ZyvioTextSecondary,
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        if (movie.progress > 0f) {
            Spacer(Modifier.height(18.dp))
            LinearProgressIndicator(
                progress = { movie.progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = ZyvioSurface2,
            )
            movie.progressLabel?.let {
                Text(
                    text = it,
                    modifier = Modifier.padding(top = 6.dp),
                    color = ZyvioTextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                modifier = Modifier.tvFocusEffect(isTelevision, cornerRadiusDp = 12),
                onClick = { onPlay(movie, movie.progress > 0f && movie.progress <= 0.95f) },
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(primaryLabel)
            }

            OutlinedButton(
                modifier = Modifier.tvFocusEffect(isTelevision, cornerRadiusDp = 12),
                onClick = { onToggleFavorite(movie) },
            ) {
                Icon(
                    imageVector = if (movie.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = null,
                    tint = if (movie.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.width(8.dp))
                Text("Favori")
            }
        }
    }
}

@Composable
private fun MovieDetailLoading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text("Chargement du film…")
        }
    }
}

@Composable
private fun MovieDetailError(
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
                    text = "Fiche indisponible",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = message,
                    modifier = Modifier.padding(top = 8.dp),
                    color = ZyvioTextSecondary,
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = onRetry) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Réessayer")
                }
            }
        }
    }
}
