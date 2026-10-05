package fr.zyviotv.player.ui.series

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoLibrary
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

private data class EpisodePreview(
    val season: Int,
    val number: Int,
    val title: String,
    val progress: Float,
)

private data class SeriesPreview(
    val title: String,
    val year: String,
    val rating: String,
    val synopsis: String,
    val episodes: List<EpisodePreview>,
)

private val previewSeries = listOf(
    SeriesPreview(
        "Série en vedette",
        "2026",
        "8,4",
        "Retrouvez toutes les saisons et reprenez exactement là où vous vous êtes arrêté.",
        listOf(
            EpisodePreview(1, 1, "Le commencement", 1f),
            EpisodePreview(1, 2, "Le choix", 0.42f),
            EpisodePreview(1, 3, "La suite", 0f),
            EpisodePreview(2, 1, "Nouveau départ", 0f),
        ),
    ),
    SeriesPreview(
        "Nouvelle saison",
        "2026",
        "8,1",
        "Une nouvelle saison récemment ajoutée à votre catalogue.",
        listOf(
            EpisodePreview(1, 1, "Épisode 1", 0f),
            EpisodePreview(1, 2, "Épisode 2", 0f),
        ),
    ),
)

@Composable
fun SeriesScreen(profile: DeviceProfile) {
    var selectedSeries by remember { mutableStateOf(previewSeries.first()) }
    var selectedSeason by remember { mutableIntStateOf(1) }

    val seasons = selectedSeries.episodes.map { it.season }.distinct().sorted()
    val episodes = selectedSeries.episodes.filter { it.season == selectedSeason }

    if (selectedSeason !in seasons) {
        selectedSeason = seasons.firstOrNull() ?: 1
    }

    if (profile == DeviceProfile.Mobile) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        ) {
            SeriesHero(selectedSeries)
            Spacer(Modifier.height(20.dp))
            SeriesSelector(previewSeries, selectedSeries, false) {
                selectedSeries = it
                selectedSeason = it.episodes.minOfOrNull { ep -> ep.season } ?: 1
            }
            Spacer(Modifier.height(22.dp))
            SeasonSelector(seasons, selectedSeason, false) { selectedSeason = it }
            Spacer(Modifier.height(14.dp))
            EpisodeList(episodes, false)
        }
    } else {
        Row(
            Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(
                Modifier.width(340.dp).fillMaxHeight().verticalScroll(rememberScrollState()),
            ) {
                Text("Séries", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(18.dp))
                SeriesSelector(previewSeries, selectedSeries, profile == DeviceProfile.Television) {
                    selectedSeries = it
                    selectedSeason = it.episodes.minOfOrNull { ep -> ep.season } ?: 1
                }
            }

            Column(
                Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
            ) {
                SeriesHero(selectedSeries)
                Spacer(Modifier.height(22.dp))
                SeasonSelector(seasons, selectedSeason, profile == DeviceProfile.Television) { selectedSeason = it }
                Spacer(Modifier.height(14.dp))
                EpisodeList(episodes, profile == DeviceProfile.Television)
            }
        }
    }
}

@Composable
private fun SeriesHero(series: SeriesPreview) {
    Box(
        Modifier.fillMaxWidth().height(280.dp)
            .background(
                Brush.horizontalGradient(listOf(Color(0xFF260004), Color(0xFF111111), Color(0xFF080808))),
                RoundedCornerShape(24.dp),
            )
            .padding(24.dp),
    ) {
        Column(
            Modifier.align(Alignment.CenterStart).fillMaxWidth(0.8f),
        ) {
            Text("SÉRIES", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(series.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            Text(series.year + " • ★ " + series.rating, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Text(series.synopsis, maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SeriesSelector(
    series: List<SeriesPreview>,
    selected: SeriesPreview,
    isTelevision: Boolean,
    onSelect: (SeriesPreview) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        series.forEach { item ->
            Card(
                modifier = Modifier.fillMaxWidth().tvFocusEffect(isTelevision).clickable { onSelect(item) },
                colors = CardDefaults.cardColors(
                    containerColor = if (item == selected) MaterialTheme.colorScheme.surfaceVariant
                    else MaterialTheme.colorScheme.surface,
                ),
                shape = RoundedCornerShape(16.dp),
            ) {
                Row(
                    Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(width = 76.dp, height = 104.dp)
                            .background(Color(0xFF1E1E1E), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.VideoLibrary, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.title, fontWeight = FontWeight.Bold)
                        Text(item.year + " • ★ " + item.rating, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun SeasonSelector(
    seasons: List<Int>,
    selectedSeason: Int,
    isTelevision: Boolean,
    onSelect: (Int) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        seasons.forEach { season ->
            FilterChip(
                modifier = Modifier.tvFocusEffect(isTelevision, cornerRadiusDp = 999),
                selected = season == selectedSeason,
                onClick = { onSelect(season) },
                label = { Text("Saison $season") },
            )
        }
    }
}

@Composable
private fun EpisodeList(
    episodes: List<EpisodePreview>,
    isTelevision: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        episodes.sortedBy { it.number }.forEach { episode ->
            Card(
                modifier = Modifier.fillMaxWidth().tvFocusEffect(isTelevision).focusable(enabled = isTelevision),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
            ) {
                Row(
                    Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(width = 110.dp, height = 68.dp)
                            .background(Color(0xFF1D1D1D), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Épisode " + episode.number + " — " + episode.title,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { episode.progress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
