package fr.zyviotv.player.ui.series

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.zyviotv.player.ui.DeviceProfile
import fr.zyviotv.player.ui.theme.ZyvioRedTint
import fr.zyviotv.player.ui.theme.ZyvioSurface1
import fr.zyviotv.player.ui.theme.ZyvioSurface2
import fr.zyviotv.player.ui.theme.ZyvioTextSecondary
import fr.zyviotv.player.ui.theme.ZyvioTextTertiary
import fr.zyviotv.player.ui.tv.tvFocusEffect

enum class EpisodeWatchState {
    Unwatched,
    InProgress,
    Watched,
    Next,
    Announced,
}

data class EpisodeDetailUi(
    val id: String,
    val season: Int,
    val number: Int,
    val title: String,
    val synopsis: String? = null,
    val durationLabel: String? = null,
    val progress: Float = 0f,
    val state: EpisodeWatchState = EpisodeWatchState.Unwatched,
)

data class SeriesDetailUi(
    val id: String,
    val title: String,
    val year: String? = null,
    val genres: List<String> = emptyList(),
    val synopsis: String? = null,
    val seasonsCount: Int? = null,
    val episodesCount: Int? = null,
    val isFavorite: Boolean = false,
    val episodes: List<EpisodeDetailUi> = emptyList(),
)

sealed interface SeriesDetailState {
    data object Loading : SeriesDetailState
    data class Ready(val series: SeriesDetailUi) : SeriesDetailState
    data class Error(val message: String) : SeriesDetailState
}

@Composable
fun SeriesDetailScreen(
    profile: DeviceProfile,
    state: SeriesDetailState,
    onRetry: () -> Unit = {},
    onPlayEpisode: (EpisodeDetailUi) -> Unit = {},
    onToggleFavorite: (SeriesDetailUi) -> Unit = {},
) {
    when (state) {
        SeriesDetailState.Loading -> SeriesDetailLoading()
        is SeriesDetailState.Error -> SeriesDetailError(state.message, onRetry)
        is SeriesDetailState.Ready -> SeriesDetailReady(
            profile = profile,
            series = state.series,
            onPlayEpisode = onPlayEpisode,
            onToggleFavorite = onToggleFavorite,
        )
    }
}

@Composable
private fun SeriesDetailReady(
    profile: DeviceProfile,
    series: SeriesDetailUi,
    onPlayEpisode: (EpisodeDetailUi) -> Unit,
    onToggleFavorite: (SeriesDetailUi) -> Unit,
) {
    val seasons = remember(series.episodes) {
        series.episodes.map { it.season }.distinct().sorted()
    }
    var selectedSeason by remember(series.id) {
        mutableIntStateOf(seasons.firstOrNull() ?: 1)
    }
    val visibleEpisodes = series.episodes
        .filter { it.season == selectedSeason }
        .sortedBy { it.number }

    val currentEpisode = series.episodes.firstOrNull { it.state == EpisodeWatchState.InProgress }
        ?: series.episodes.firstOrNull { it.state == EpisodeWatchState.Next }
        ?: series.episodes.firstOrNull { it.state == EpisodeWatchState.Unwatched }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (profile == DeviceProfile.Mobile) 220.dp else 300.dp),
            color = ZyvioSurface1,
            shape = RoundedCornerShape(20.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ZyvioSurface2),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = series.title,
                    modifier = Modifier.padding(20.dp),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text = series.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
        )

        val meta = listOfNotNull(
            series.year,
            series.seasonsCount?.let { count -> count.toString() + " saison" + if (count > 1) "s" else "" },
            series.episodesCount?.let { count -> count.toString() + " épisodes" },
        ).joinToString(" • ")
        if (meta.isNotBlank()) {
            Text(
                text = meta,
                modifier = Modifier.padding(top = 6.dp),
                color = ZyvioTextSecondary,
            )
        }

        if (series.genres.isNotEmpty()) {
            Text(
                text = series.genres.joinToString(" • "),
                modifier = Modifier.padding(top = 8.dp),
                color = ZyvioTextSecondary,
            )
        }

        series.synopsis?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                modifier = Modifier.padding(top = 16.dp),
                color = ZyvioTextSecondary,
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        Spacer(Modifier.height(18.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            currentEpisode?.let { episode ->
                Button(
                    modifier = Modifier.tvFocusEffect(profile == DeviceProfile.Television, cornerRadiusDp = 12),
                    onClick = { onPlayEpisode(episode) },
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when (episode.state) {
                            EpisodeWatchState.InProgress -> "Reprendre"
                            EpisodeWatchState.Watched -> "Revoir"
                            else -> "Lire"
                        },
                    )
                }
            }

            OutlinedButton(
                modifier = Modifier.tvFocusEffect(profile == DeviceProfile.Television, cornerRadiusDp = 12),
                onClick = { onToggleFavorite(series) },
            ) {
                Icon(
                    imageVector = if (series.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = null,
                    tint = if (series.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.width(8.dp))
                Text("Favori")
            }
        }

        Spacer(Modifier.height(24.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            seasons.forEach { season ->
                FilterChip(
                    modifier = Modifier.tvFocusEffect(profile == DeviceProfile.Television, cornerRadiusDp = 999),
                    selected = season == selectedSeason,
                    onClick = { selectedSeason = season },
                    label = { Text("Saison " + season) },
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        if (visibleEpisodes.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = ZyvioSurface1,
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(
                    text = "Aucun épisode disponible pour cette saison.",
                    modifier = Modifier.padding(20.dp),
                    color = ZyvioTextSecondary,
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                visibleEpisodes.forEach { episode ->
                    EpisodeRow(
                        episode = episode,
                        isTelevision = profile == DeviceProfile.Television,
                        onClick = { onPlayEpisode(episode) },
                    )
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(
    episode: EpisodeDetailUi,
    isTelevision: Boolean,
    onClick: () -> Unit,
) {
    val selectable = episode.state != EpisodeWatchState.Announced
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .tvFocusEffect(isTelevision && selectable, cornerRadiusDp = 14)
            .clickable(enabled = selectable, onClick = onClick),
        color = when (episode.state) {
            EpisodeWatchState.InProgress, EpisodeWatchState.Next -> ZyvioRedTint
            else -> ZyvioSurface1
        },
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier
                    .width(112.dp)
                    .height(68.dp),
                color = ZyvioSurface2,
                shape = RoundedCornerShape(10.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = when (episode.state) {
                            EpisodeWatchState.Watched -> Icons.Default.CheckCircle
                            EpisodeWatchState.Announced -> Icons.Default.LockClock
                            else -> Icons.Default.PlayArrow
                        },
                        contentDescription = null,
                        tint = if (episode.state == EpisodeWatchState.Announced) {
                            ZyvioTextTertiary
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = "Épisode " + episode.number + " — " + episode.title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold,
                    color = if (episode.state == EpisodeWatchState.Announced) {
                        ZyvioTextTertiary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )

                val stateLabel = when (episode.state) {
                    EpisodeWatchState.Watched -> "Vu"
                    EpisodeWatchState.InProgress -> "En cours"
                    EpisodeWatchState.Next -> "Suivant"
                    EpisodeWatchState.Announced -> "Annoncé"
                    EpisodeWatchState.Unwatched -> null
                }
                stateLabel?.let {
                    Text(
                        text = it,
                        modifier = Modifier.padding(top = 4.dp),
                        color = if (episode.state == EpisodeWatchState.Announced) {
                            ZyvioTextTertiary
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                episode.synopsis?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        modifier = Modifier.padding(top = 4.dp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = ZyvioTextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                if (episode.progress > 0f && episode.progress < 1f) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { episode.progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = ZyvioSurface2,
                    )
                }
            }
        }
    }
}

@Composable
private fun SeriesDetailLoading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text("Chargement de la série…")
        }
    }
}

@Composable
private fun SeriesDetailError(
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
