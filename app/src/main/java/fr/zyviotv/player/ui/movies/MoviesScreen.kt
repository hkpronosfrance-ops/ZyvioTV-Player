package fr.zyviotv.player.ui.movies

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.zyviotv.player.ui.DeviceProfile
import fr.zyviotv.player.ui.tv.tvFocusEffect

private data class MoviePreview(
    val title: String,
    val year: String,
    val rating: String,
    val synopsis: String,
)

private val previewMovies = listOf(
    MoviePreview("Film en vedette", "2026", "8,2", "Une sélection mise en avant depuis votre catalogue."),
    MoviePreview("Nouveauté", "2026", "7,9", "Un film récemment ajouté à votre playlist."),
    MoviePreview("À découvrir", "2025", "7,6", "Une suggestion issue de votre bibliothèque."),
    MoviePreview("Votre favori", "2024", "8,5", "Retrouvez rapidement vos films préférés."),
)

@Composable
fun MoviesScreen(profile: DeviceProfile) {
    var selected by remember { mutableStateOf(previewMovies.first()) }

    if (profile == DeviceProfile.Mobile) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        ) {
            MovieHero(selected)
            Spacer(Modifier.height(22.dp))
            MovieGrid(previewMovies, selected, false) { selected = it }
        }
    } else {
        Row(
            Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(
                Modifier.width(360.dp).verticalScroll(rememberScrollState()),
            ) {
                Text("Films", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(18.dp))
                MovieGrid(previewMovies, selected, profile == DeviceProfile.Television) { selected = it }
            }

            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
            ) {
                MovieHero(selected)
            }
        }
    }
}

@Composable
private fun MovieHero(movie: MoviePreview) {
    Box(
        Modifier.fillMaxWidth().height(300.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFF2A0004), Color(0xFF111111), Color(0xFF080808)),
                ),
                RoundedCornerShape(24.dp),
            )
            .padding(24.dp),
    ) {
        Column(
            Modifier.align(Alignment.CenterStart).fillMaxWidth(0.78f),
        ) {
            Text("FILMS", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(movie.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(6.dp))
            Text(movie.year + " • ★ " + movie.rating, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Text(movie.synopsis, maxLines = 4, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(18.dp))
            Button(onClick = {}) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Lire")
            }
        }
    }
}

@Composable
private fun MovieGrid(
    movies: List<MoviePreview>,
    selected: MoviePreview,
    isTelevision: Boolean,
    onSelect: (MoviePreview) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        movies.chunked(2).forEach { rowMovies ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                rowMovies.forEach { movie ->
                    Card(
                        modifier = Modifier.weight(1f).aspectRatio(2f / 3f).tvFocusEffect(isTelevision).clickable { onSelect(movie) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (movie == selected) MaterialTheme.colorScheme.surfaceVariant
                            else MaterialTheme.colorScheme.surface,
                        ),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Box(
                            Modifier.fillMaxSize().background(
                                Brush.verticalGradient(listOf(Color(0xFF262626), Color(0xFF111111))),
                            ),
                        ) {
                            Icon(
                                Icons.Default.Movie,
                                contentDescription = null,
                                modifier = Modifier.align(Alignment.Center),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
                            )
                            Column(
                                Modifier.align(Alignment.BottomStart).fillMaxWidth()
                                    .background(Color.Black.copy(alpha = 0.72f)).padding(10.dp),
                            ) {
                                Text(movie.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(movie.year, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                if (rowMovies.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
