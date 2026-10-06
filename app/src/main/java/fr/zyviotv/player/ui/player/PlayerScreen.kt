package fr.zyviotv.player.ui.player

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.zyviotv.player.shared.playback.PlaybackKind
import fr.zyviotv.player.shared.playback.PlaybackState
import fr.zyviotv.player.ui.DeviceProfile
import fr.zyviotv.player.ui.theme.ZyvioSurface1
import fr.zyviotv.player.ui.theme.ZyvioSurface2
import fr.zyviotv.player.ui.theme.ZyvioTextSecondary
import fr.zyviotv.player.ui.tv.tvFocusEffect
import kotlinx.coroutines.delay

data class PlayerMetadataUi(
    val title: String,
    val kind: PlaybackKind,
    val eyebrow: String? = null,
    val secondary: String? = null,
    val currentProgramme: String? = null,
    val nextProgramme: String? = null,
    val channelNumber: String? = null,
    val isFavorite: Boolean = false,
)

data class PlayerTimelineUi(
    val positionMs: Long = 0L,
    val durationMs: Long? = null,
)

enum class PlayerPanel {
    None,
    Tracks,
    Resume,
    ChannelNumber,
}

data class PlayerScreenUiState(
    val playbackState: PlaybackState = PlaybackState.Idle,
    val isPlaying: Boolean = true,
    val controlsVisible: Boolean = true,
    val panel: PlayerPanel = PlayerPanel.None,
    val metadata: PlayerMetadataUi,
    val timeline: PlayerTimelineUi = PlayerTimelineUi(),
    val audioLabel: String? = null,
    val subtitlesLabel: String? = null,
    val errorMessage: String? = null,
    val unavailable: Boolean = false,
)

@Composable
fun PlayerScreen(
    profile: DeviceProfile,
    state: PlayerScreenUiState,
    onBack: () -> Unit = {},
    onTogglePlayPause: () -> Unit = {},
    onSeekBack: () -> Unit = {},
    onSeekForward: () -> Unit = {},
    onRetry: () -> Unit = {},
    onNext: () -> Unit = {},
    onOpenTracks: () -> Unit = {},
    onOpenGuide: () -> Unit = {},
    onToggleFavorite: () -> Unit = {},
    videoContent: @Composable () -> Unit = { VideoSurfacePlaceholder() },
) {
    var controlsVisible by remember(state.controlsVisible) { mutableStateOf(state.controlsVisible) }
    val blocking = !state.isPlaying ||
        state.playbackState == PlaybackState.Buffering ||
        state.playbackState == PlaybackState.Error ||
        state.panel != PlayerPanel.None

    LaunchedEffect(state.isPlaying, state.playbackState, state.panel, controlsVisible, profile) {
        if (controlsVisible && !blocking) {
            delay(if (profile == DeviceProfile.Television) 5_000L else 4_000L)
            controlsVisible = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable {
                if (state.panel == PlayerPanel.None && state.playbackState != PlaybackState.Error) {
                    controlsVisible = !controlsVisible
                }
            },
    ) {
        videoContent()

        when {
            state.unavailable -> UnavailableOverlay(
                profile = profile,
                title = state.metadata.title,
                onBack = onBack,
            )

            state.playbackState == PlaybackState.Error -> ErrorOverlay(
                profile = profile,
                message = state.errorMessage ?: "Flux indisponible",
                nextLabel = if (state.metadata.kind == PlaybackKind.Live) "Chaîne suivante" else "Épisode suivant",
                onRetry = onRetry,
                onNext = onNext,
                onBack = onBack,
            )

            state.playbackState == PlaybackState.Buffering -> BufferingOverlay(
                title = state.metadata.title,
            )

            else -> {
                if (controlsVisible || blocking) {
                    PlayerControlsOverlay(
                        profile = profile,
                        state = state,
                        onBack = onBack,
                        onTogglePlayPause = onTogglePlayPause,
                        onSeekBack = onSeekBack,
                        onSeekForward = onSeekForward,
                        onNext = onNext,
                        onOpenTracks = onOpenTracks,
                        onOpenGuide = onOpenGuide,
                        onToggleFavorite = onToggleFavorite,
                    )
                }
            }
        }

        when (state.panel) {
            PlayerPanel.Tracks -> TracksPanel(
                profile = profile,
                audioLabel = state.audioLabel,
                subtitlesLabel = state.subtitlesLabel,
            )
            PlayerPanel.Resume -> ResumePanel(
                profile = profile,
                title = state.metadata.title,
                timeline = state.timeline,
            )
            PlayerPanel.ChannelNumber -> ChannelNumberPanel(profile)
            PlayerPanel.None -> Unit
        }
    }
}

@Composable
private fun VideoSurfacePlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF151515),
                        Color(0xFF050506),
                    ),
                ),
            ),
    )
}

@Composable
private fun PlayerControlsOverlay(
    profile: DeviceProfile,
    state: PlayerScreenUiState,
    onBack: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onNext: () -> Unit,
    onOpenTracks: () -> Unit,
    onOpenGuide: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.Black.copy(alpha = 0.72f),
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.82f),
                    ),
                ),
            )
            .padding(
                horizontal = if (profile == DeviceProfile.Television) 48.dp else 18.dp,
                vertical = if (profile == DeviceProfile.Television) 28.dp else 14.dp,
            ),
    ) {
        Row(
            modifier = Modifier.align(Alignment.TopStart),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                modifier = Modifier.tvFocusEffect(profile == DeviceProfile.Television, cornerRadiusDp = 12),
                onClick = onBack,
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = null)
            }
            Spacer(Modifier.width(14.dp))
            Column {
                state.metadata.eyebrow?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    text = state.metadata.title,
                    style = if (profile == DeviceProfile.Television) {
                        MaterialTheme.typography.headlineMedium
                    } else {
                        MaterialTheme.typography.titleLarge
                    },
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                state.metadata.secondary?.let {
                    Text(
                        text = it,
                        color = ZyvioTextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        Row(
            modifier = Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                modifier = Modifier.tvFocusEffect(profile == DeviceProfile.Television, cornerRadiusDp = 999),
                onClick = onSeekBack,
            ) {
                Icon(Icons.Default.FastRewind, contentDescription = null)
            }

            Button(
                modifier = Modifier.tvFocusEffect(profile == DeviceProfile.Television, cornerRadiusDp = 999),
                onClick = onTogglePlayPause,
            ) {
                Icon(
                    imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = null,
                )
            }

            OutlinedButton(
                modifier = Modifier.tvFocusEffect(profile == DeviceProfile.Television, cornerRadiusDp = 999),
                onClick = onSeekForward,
            ) {
                Icon(Icons.Default.FastForward, contentDescription = null)
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(),
        ) {
            if (state.metadata.kind != PlaybackKind.Live) {
                val duration = state.timeline.durationMs?.takeIf { it > 0L }
                val progress = if (duration != null) {
                    (state.timeline.positionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                } else {
                    0f
                }
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (profile == DeviceProfile.Television) 8.dp else 4.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.White.copy(alpha = 0.24f),
                )
                Spacer(Modifier.height(12.dp))
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlayerAction(
                    profile = profile,
                    icon = Icons.Default.Audiotrack,
                    label = state.audioLabel ?: "Audio",
                    onClick = onOpenTracks,
                )
                PlayerAction(
                    profile = profile,
                    icon = Icons.Default.Subtitles,
                    label = state.subtitlesLabel ?: "Sous-titres",
                    onClick = onOpenTracks,
                )

                if (state.metadata.kind == PlaybackKind.Live) {
                    PlayerAction(
                        profile = profile,
                        icon = Icons.Default.List,
                        label = "Guide",
                        onClick = onOpenGuide,
                    )
                    PlayerAction(
                        profile = profile,
                        icon = Icons.Default.Favorite,
                        label = "Favori",
                        active = state.metadata.isFavorite,
                        onClick = onToggleFavorite,
                    )
                } else {
                    PlayerAction(
                        profile = profile,
                        icon = Icons.Default.SkipNext,
                        label = if (state.metadata.kind == PlaybackKind.Episode) "Épisode suivant" else "Suivant",
                        onClick = onNext,
                    )
                }
            }

            state.metadata.currentProgramme?.let {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            state.metadata.nextProgramme?.let {
                Text(
                    text = "À suivre : $it",
                    color = ZyvioTextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun PlayerAction(
    profile: DeviceProfile,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    OutlinedButton(
        modifier = Modifier.tvFocusEffect(profile == DeviceProfile.Television, cornerRadiusDp = 10),
        onClick = onClick,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(6.dp))
        Text(label)
    }
}

@Composable
private fun BufferingOverlay(title: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.42f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Mise en mémoire…",
                color = ZyvioTextSecondary,
            )
        }
    }
}

@Composable
private fun ErrorOverlay(
    profile: DeviceProfile,
    message: String,
    nextLabel: String,
    onRetry: () -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF05090C)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = "Flux indisponible",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = message,
                modifier = Modifier.padding(top = 8.dp),
                color = ZyvioTextSecondary,
            )
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    modifier = Modifier.tvFocusEffect(profile == DeviceProfile.Television, cornerRadiusDp = 10),
                    onClick = onRetry,
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Réessayer")
                }
                OutlinedButton(
                    modifier = Modifier.tvFocusEffect(profile == DeviceProfile.Television, cornerRadiusDp = 10),
                    onClick = onNext,
                ) {
                    Icon(Icons.Default.SkipNext, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(nextLabel)
                }
                OutlinedButton(
                    modifier = Modifier.tvFocusEffect(profile == DeviceProfile.Television, cornerRadiusDp = 10),
                    onClick = onBack,
                ) {
                    Icon(Icons.Default.List, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Retour liste")
                }
            }
        }
    }
}

@Composable
private fun UnavailableOverlay(
    profile: DeviceProfile,
    title: String,
    onBack: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF05090C)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = ZyvioTextSecondary,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = "Contenu indisponible",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = title,
                modifier = Modifier.padding(top = 8.dp),
                color = ZyvioTextSecondary,
            )
            Spacer(Modifier.height(20.dp))
            Button(
                modifier = Modifier.tvFocusEffect(profile == DeviceProfile.Television, cornerRadiusDp = 10),
                onClick = onBack,
            ) {
                Text("Retour liste")
            }
        }
    }
}

@Composable
private fun TracksPanel(
    profile: DeviceProfile,
    audioLabel: String?,
    subtitlesLabel: String?,
) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                start = if (profile == DeviceProfile.Television) 520.dp else 28.dp,
                top = if (profile == DeviceProfile.Television) 0.dp else 180.dp,
            ),
        color = ZyvioSurface1,
    ) {
        Column(Modifier.padding(24.dp)) {
            Text(
                text = "Audio et sous-titres",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(20.dp))
            Text("AUDIO", color = ZyvioTextSecondary, style = MaterialTheme.typography.labelSmall)
            Text(audioLabel ?: "Automatique", modifier = Modifier.padding(top = 8.dp))
            Spacer(Modifier.height(18.dp))
            Text("SOUS-TITRES", color = ZyvioTextSecondary, style = MaterialTheme.typography.labelSmall)
            Text(subtitlesLabel ?: "Désactivés", modifier = Modifier.padding(top = 8.dp))
            Spacer(Modifier.height(18.dp))
            Text(
                text = "La préférence de langue s’applique à tous les contenus.",
                color = ZyvioTextSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ResumePanel(
    profile: DeviceProfile,
    title: String,
    timeline: PlayerTimelineUi,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.width(if (profile == DeviceProfile.Television) 520.dp else 320.dp),
            color = ZyvioSurface1,
            shape = RoundedCornerShape(18.dp),
        ) {
            Column(Modifier.padding(22.dp)) {
                Text(
                    text = "Reprendre la lecture ?",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = title,
                    modifier = Modifier.padding(top = 6.dp),
                    color = ZyvioTextSecondary,
                )
                Spacer(Modifier.height(16.dp))
                val duration = timeline.durationMs?.takeIf { it > 0L }
                if (duration != null) {
                    LinearProgressIndicator(
                        progress = {
                            (timeline.positionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = ZyvioSurface2,
                    )
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = {}) { Text("Depuis le début") }
                    Button(onClick = {}) { Text("Reprendre") }
                }
            }
        }
    }
}

@Composable
private fun ChannelNumberPanel(profile: DeviceProfile) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                top = if (profile == DeviceProfile.Television) 70.dp else 20.dp,
                end = if (profile == DeviceProfile.Television) 70.dp else 20.dp,
            ),
        contentAlignment = Alignment.TopEnd,
    ) {
        Surface(
            color = ZyvioSurface1,
            shape = RoundedCornerShape(14.dp),
        ) {
            Column(Modifier.padding(18.dp)) {
                Text(
                    text = "ALLER À LA CHAÎNE",
                    color = ZyvioTextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                )
                Text(
                    text = "10_",
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Validation automatique après 1,5 s",
                    modifier = Modifier.padding(top = 8.dp),
                    color = ZyvioTextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
