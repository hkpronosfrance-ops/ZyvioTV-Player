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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
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
import fr.zyviotv.player.shared.sync.ProgressContentType
import fr.zyviotv.player.shared.sync.SyncedWatchProgress
import fr.zyviotv.player.ui.DeviceProfile
import fr.zyviotv.player.ui.theme.ZyvioSurface1
import fr.zyviotv.player.ui.theme.ZyvioSurface2
import fr.zyviotv.player.ui.theme.ZyvioTextSecondary
import fr.zyviotv.player.ui.tv.tvFocusEffect

private enum class HistoryFilter(val label: String) {
    All("Tout"),
    Movies("Films"),
    Episodes("Épisodes"),
}

@Composable
fun HistoryScreen(
    profile: DeviceProfile,
    state: LibraryState,
    activePlaylistId: String?,
    onRetry: () -> Unit,
    onOpen: (SyncedWatchProgress) -> Unit,
    onRemove: (SyncedWatchProgress) -> Unit,
) {
    when (state) {
        LibraryState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }

        is LibraryState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Historique indisponible",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = state.message,
                    modifier = Modifier.padding(top = 8.dp),
                    color = ZyvioTextSecondary,
                )
                TextButton(onClick = onRetry) {
                    Text("Réessayer")
                }
            }
        }

        is LibraryState.Ready -> {
            var filter by remember { mutableStateOf(HistoryFilter.All) }
            val history = remember(state.snapshot.progress, activePlaylistId, filter) {
                val base = state.snapshot.progress.filter {
                    it.playlistId == activePlaylistId && it.positionMs >= MIN_HISTORY_POSITION_MS
                }
                when (filter) {
                    HistoryFilter.All -> base
                    HistoryFilter.Movies -> base.filter { it.contentType == ProgressContentType.Movie }
                    HistoryFilter.Episodes -> base.filter { it.contentType == ProgressContentType.Episode }
                }
            }

            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Historique",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        Text(
                            text = "Vos lectures récentes",
                            color = ZyvioTextSecondary,
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }

                Spacer(Modifier.height(14.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HistoryFilter.entries.forEach { item ->
                        AssistChip(
                            onClick = { filter = item },
                            label = { Text(item.label) },
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                if (history.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Aucune lecture récente.",
                            color = ZyvioTextSecondary,
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(
                            items = history,
                            key = { "${it.playlistId}:${it.contentType}:${it.contentId}" },
                        ) { progress ->
                            HistoryRow(
                                progress = progress,
                                isTelevision = profile == DeviceProfile.Television,
                                onOpen = { onOpen(progress) },
                                onRemove = { onRemove(progress) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(
    progress: SyncedWatchProgress,
    isTelevision: Boolean,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .tvFocusEffect(isTelevision, cornerRadiusDp = 14)
            .clickable(onClick = onOpen),
        color = ZyvioSurface1,
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.18f)
                    .height(72.dp),
                color = ZyvioSurface2,
                shape = RoundedCornerShape(10.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (progress.contentType == ProgressContentType.Movie) {
                            Icons.Default.Movie
                        } else {
                            Icons.Default.PlayArrow
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp),
            ) {
                Text(
                    text = progress.title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = when (progress.contentType) {
                        ProgressContentType.Movie -> if (progress.completed) "Film • Terminé" else "Film"
                        ProgressContentType.Episode -> buildString {
                            append("Épisode")
                            progress.seasonNumber?.let { append(" • S$it") }
                            progress.episodeNumber?.let { append(" E$it") }
                            if (progress.completed) append(" • Terminé")
                        }
                    },
                    modifier = Modifier.padding(top = 4.dp),
                    color = ZyvioTextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            TextButton(onClick = onRemove) {
                Text("Retirer")
            }
        }
    }
}

private const val MIN_HISTORY_POSITION_MS = 60_000L
