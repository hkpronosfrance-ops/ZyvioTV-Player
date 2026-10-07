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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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

@Composable
fun ContinueWatchingScreen(
    profile: DeviceProfile,
    state: LibraryState,
    activePlaylistId: String?,
    onRetry: () -> Unit,
    onOpen: (SyncedWatchProgress) -> Unit,
) {
    when (state) {
        LibraryState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }

        is LibraryState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Continuer à regarder indisponible",
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
            val visibleItems = state.snapshot.progress.filter { progress ->
                progress.playlistId == activePlaylistId &&
                    !progress.completed &&
                    (
                        progress.positionMs >= MIN_POSITION_MS ||
                            progress.fraction >= MIN_PROGRESS_FRACTION
                    )
            }

            Column(Modifier.fillMaxSize()) {
                Text(
                    text = "Continuer à regarder",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    text = "${visibleItems.size} contenu" + if (visibleItems.size > 1) "s" else "",
                    color = ZyvioTextSecondary,
                )
                if (state.snapshot.isOffline) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Hors connexion : votre progression reste consultable, mais la lecture nécessite Internet.",
                        color = ZyvioTextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(Modifier.height(18.dp))

                if (visibleItems.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Aucune lecture en cours.",
                            color = ZyvioTextSecondary,
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(
                            items = visibleItems,
                            key = { "${it.playlistId}:${it.contentType}:${it.contentId}" },
                        ) { progress ->
                            ContinueWatchingRow(
                                progress = progress,
                                isTelevision = profile == DeviceProfile.Television,
                                enabled = !state.snapshot.isOffline,
                                onClick = { onOpen(progress) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContinueWatchingRow(
    progress: SyncedWatchProgress,
    isTelevision: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .tvFocusEffect(isTelevision, cornerRadiusDp = 16)
            .clickable(enabled = enabled, onClick = onClick),
        color = ZyvioSurface1,
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.28f)
                    .height(86.dp),
                color = ZyvioSurface2,
                shape = RoundedCornerShape(12.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
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

                val meta = when (progress.contentType) {
                    ProgressContentType.Movie -> "Film"
                    ProgressContentType.Episode -> listOfNotNull(
                        progress.seasonNumber?.let { "S$it" },
                        progress.episodeNumber?.let { "E$it" },
                    ).joinToString(" • ").ifBlank { "Épisode" }
                }
                Text(
                    text = meta,
                    modifier = Modifier.padding(top = 4.dp),
                    color = ZyvioTextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )

                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { progress.fraction.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = ZyvioSurface2,
                )
            }
        }
    }
}

private const val MIN_POSITION_MS = 2 * 60 * 1000L
private const val MIN_PROGRESS_FRACTION = 0.02f
