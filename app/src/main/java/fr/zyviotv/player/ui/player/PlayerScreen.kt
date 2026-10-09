package fr.zyviotv.player.ui.player

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.zyviotv.player.shared.playback.PlaybackKind
import fr.zyviotv.player.shared.playback.PlaybackState
import fr.zyviotv.player.ui.DeviceProfile
import fr.zyviotv.player.ui.theme.ZyvioBase
import fr.zyviotv.player.ui.theme.ZyvioSpace
import fr.zyviotv.player.ui.theme.ZyvioRedTint
import fr.zyviotv.player.ui.theme.ZyvioSurface1
import fr.zyviotv.player.ui.theme.ZyvioSurface2
import fr.zyviotv.player.ui.theme.ZyvioTextPrimary
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

data class PlayerTrackUi(
    val id: String,
    val label: String,
    val language: String?,
    val selected: Boolean,
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
    val audioTracks: List<PlayerTrackUi> = emptyList(),
    val subtitleTracks: List<PlayerTrackUi> = emptyList(),
    val subtitlesEnabled: Boolean = true,
    val errorMessage: String? = null,
    val unavailable: Boolean = false,
    val channelNumberInput: String? = null,
    val isFullscreen: Boolean = false,
    val scaleMode: PlayerScaleMode = PlayerScaleMode.Fit,
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
    onResumePlayback: () -> Unit = {},
    onRestartFromBeginning: () -> Unit = {},
    onOpenTracks: () -> Unit = {},
    onOpenGuide: () -> Unit = {},
    onToggleFavorite: () -> Unit = {},
    onSelectAudioTrack: (PlayerTrackUi) -> Unit = {},
    onSelectSubtitleTrack: (PlayerTrackUi) -> Unit = {},
    onDisableSubtitles: () -> Unit = {},
    onChannelUp: () -> Unit = {},
    onChannelDown: () -> Unit = {},
    onChannelDigit: (Int) -> Unit = {},
    onToggleFullscreen: () -> Unit = {},
    onToggleScaleMode: () -> Unit = {},
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

    // The player route has no Surface above it, so LocalContentColor was the
    // Compose default (black): every icon and text without an explicit
    // colour, including the full screen button, was black on black (#210).
    CompositionLocalProvider(LocalContentColor provides ZyvioTextPrimary) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .onPreviewKeyEvent { keyEvent ->
                    if (
                        profile != DeviceProfile.Television ||
                        keyEvent.type != KeyEventType.KeyDown
                    ) {
                        false
                    } else {
                        when (val keyCode = keyEvent.key.keyCode.toInt()) {
                            AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                            AndroidKeyEvent.KEYCODE_ENTER,
                            -> {
                                if (!controlsVisible && state.panel == PlayerPanel.None) {
                                    controlsVisible = true
                                    true
                                } else {
                                    false
                                }
                            }

                            AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                                onTogglePlayPause()
                                controlsVisible = true
                                true
                            }

                            AndroidKeyEvent.KEYCODE_DPAD_LEFT -> {
                                if (state.metadata.kind != PlaybackKind.Live && state.panel == PlayerPanel.None) {
                                    onSeekBack()
                                    controlsVisible = true
                                    true
                                } else {
                                    false
                                }
                            }

                            AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
                                if (state.metadata.kind != PlaybackKind.Live && state.panel == PlayerPanel.None) {
                                    onSeekForward()
                                    controlsVisible = true
                                    true
                                } else {
                                    false
                                }
                            }

                            AndroidKeyEvent.KEYCODE_DPAD_UP -> {
                                if (
                                    state.metadata.kind == PlaybackKind.Live &&
                                    !controlsVisible &&
                                    state.panel == PlayerPanel.None
                                ) {
                                    onChannelUp()
                                    true
                                } else {
                                    false
                                }
                            }

                            AndroidKeyEvent.KEYCODE_DPAD_DOWN -> {
                                if (
                                    state.metadata.kind == PlaybackKind.Live &&
                                    !controlsVisible &&
                                    state.panel == PlayerPanel.None
                                ) {
                                    onChannelDown()
                                    true
                                } else {
                                    false
                                }
                            }

                            AndroidKeyEvent.KEYCODE_CHANNEL_UP -> {
                                if (state.metadata.kind == PlaybackKind.Live) {
                                    onChannelUp()
                                    controlsVisible = true
                                    true
                                } else {
                                    false
                                }
                            }

                            AndroidKeyEvent.KEYCODE_CHANNEL_DOWN -> {
                                if (state.metadata.kind == PlaybackKind.Live) {
                                    onChannelDown()
                                    controlsVisible = true
                                    true
                                } else {
                                    false
                                }
                            }

                            else -> {
                                val digit = keyCodeToDigit(keyCode)
                                if (digit != null && state.metadata.kind == PlaybackKind.Live) {
                                    onChannelDigit(digit)
                                    controlsVisible = true
                                    true
                                } else {
                                    false
                                }
                            }
                        }
                    }
                }
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
                    nextLabel = when (state.metadata.kind) {
                        PlaybackKind.Live -> "Chaîne suivante"
                        PlaybackKind.Episode -> "Épisode suivant"
                        PlaybackKind.Movie -> null
                    },
                    onRetry = onRetry,
                    onNext = onNext,
                    onBack = onBack,
                )

                state.playbackState == PlaybackState.Buffering -> BufferingOverlay(
                    profile = profile,
                    title = state.metadata.title,
                    onBack = onBack,
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
                            onToggleFullscreen = onToggleFullscreen,
                            onToggleScaleMode = onToggleScaleMode,
                        )
                    }
                }
            }

            when (state.panel) {
                PlayerPanel.Tracks -> TracksPanel(
                    profile = profile,
                    audioTracks = state.audioTracks,
                    subtitleTracks = state.subtitleTracks,
                    subtitlesEnabled = state.subtitlesEnabled,
                    onSelectAudioTrack = onSelectAudioTrack,
                    onSelectSubtitleTrack = onSelectSubtitleTrack,
                    onDisableSubtitles = onDisableSubtitles,
                )
                PlayerPanel.Resume -> ResumePanel(
                    profile = profile,
                    title = state.metadata.title,
                    timeline = state.timeline,
                    onRestartFromBeginning = onRestartFromBeginning,
                    onResumePlayback = onResumePlayback,
                )
                PlayerPanel.ChannelNumber -> ChannelNumberPanel(
                    profile = profile,
                    input = state.channelNumberInput.orEmpty(),
                )
                PlayerPanel.None -> Unit
            }
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
                        ZyvioSurface2,
                        ZyvioBase,
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
    onToggleFullscreen: () -> Unit,
    onToggleScaleMode: () -> Unit,
) {
    BoxWithConstraints(
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
            // Edge-to-edge: keep Retour and the actions out of the status bar,
            // the camera cut-out and the gesture area, in both orientations.
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(
                horizontal = when (profile) {
                    DeviceProfile.Mobile -> ZyvioSpace.s4
                    DeviceProfile.Tablet -> ZyvioSpace.s8
                    DeviceProfile.Television -> ZyvioSpace.s12
                },
                vertical = if (profile == DeviceProfile.Television) ZyvioSpace.s7 else ZyvioSpace.s4,
            ),
    ) {
        val layout = PlayerControlsLayout.of(
            profile = profile,
            widthDp = maxWidth.value.toInt(),
            heightDp = maxHeight.value.toInt(),
        )
        val tv = profile == DeviceProfile.Television

        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                modifier = Modifier.tvFocusEffect(tv, cornerRadiusDp = 12),
                onClick = onBack,
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Retour")
            }
            Spacer(Modifier.width(14.dp))
            // Takes the remaining width only: long titles end with an ellipsis
            // instead of pushing or wrapping the other controls.
            Column(modifier = Modifier.weight(1f)) {
                state.metadata.eyebrow?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = state.metadata.title,
                    style = if (tv) {
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
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (layout.showFullscreenToggle) {
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onToggleScaleMode) {
                    Icon(
                        imageVector = if (state.scaleMode == PlayerScaleMode.Fit) {
                            Icons.Default.ZoomOutMap
                        } else {
                            Icons.Default.FitScreen
                        },
                        contentDescription = if (state.scaleMode == PlayerScaleMode.Fit) {
                            "Remplir l'écran"
                        } else {
                            "Ajuster à l'écran"
                        },
                        tint = ZyvioTextPrimary,
                    )
                }
            }
        }

        Row(
            modifier = Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.metadata.kind != PlaybackKind.Live) {
                OutlinedButton(
                    modifier = Modifier.tvFocusEffect(tv, cornerRadiusDp = 999),
                    onClick = onSeekBack,
                ) {
                    Icon(Icons.Default.FastRewind, contentDescription = "Reculer de 10 secondes")
                }
            }

            Button(
                modifier = Modifier.tvFocusEffect(tv, cornerRadiusDp = 999),
                onClick = onTogglePlayPause,
            ) {
                Icon(
                    imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (state.isPlaying) "Pause" else "Lecture",
                )
            }

            if (state.metadata.kind != PlaybackKind.Live) {
                OutlinedButton(
                    modifier = Modifier.tvFocusEffect(tv, cornerRadiusDp = 999),
                    onClick = onSeekForward,
                ) {
                    Icon(Icons.Default.FastForward, contentDescription = "Avancer de 10 secondes")
                }
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .weight(1f)
                            .height(if (tv) 8.dp else 4.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.White.copy(alpha = 0.24f),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = PlayerTimeFormat.timeline(state.timeline.positionMs, duration),
                        style = MaterialTheme.typography.labelMedium,
                        color = ZyvioTextSecondary,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // One line in every orientation: icon-only on narrow phones,
                // scrollable rather than wrapped when labels do not fit. The full
                // screen button sits outside the scroll so it is never pushed off.
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PlayerAction(
                        profile = profile,
                        icon = Icons.Default.Audiotrack,
                        label = state.audioLabel ?: "Audio",
                        description = "Pistes audio",
                        iconOnly = layout.iconOnlyActions,
                        onClick = onOpenTracks,
                    )
                    PlayerAction(
                        profile = profile,
                        icon = Icons.Default.Subtitles,
                        label = state.subtitlesLabel ?: "Sous-titres",
                        description = "Sous-titres",
                        iconOnly = layout.iconOnlyActions,
                        onClick = onOpenTracks,
                    )

                    if (state.metadata.kind == PlaybackKind.Live) {
                        PlayerAction(
                            profile = profile,
                            icon = Icons.Default.List,
                            label = "Guide",
                            description = "Guide des programmes",
                            iconOnly = layout.iconOnlyActions,
                            onClick = onOpenGuide,
                        )
                        PlayerAction(
                            profile = profile,
                            icon = Icons.Default.Favorite,
                            label = "Favori",
                            description = "Favori",
                            active = state.metadata.isFavorite,
                            iconOnly = layout.iconOnlyActions,
                            onClick = onToggleFavorite,
                        )
                    } else if (state.metadata.kind == PlaybackKind.Episode) {
                        // Films have no "next" item: no silent button.
                        PlayerAction(
                            profile = profile,
                            icon = Icons.Default.SkipNext,
                            label = "Épisode suivant",
                            description = "Épisode suivant",
                            iconOnly = layout.iconOnlyActions,
                            onClick = onNext,
                        )
                    }
                }
                if (layout.showFullscreenToggle) {
                    Spacer(Modifier.width(8.dp))
                    FullscreenButton(
                        isFullscreen = state.isFullscreen,
                        onClick = onToggleFullscreen,
                    )
                }
            }

            state.metadata.currentProgramme?.let {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            state.metadata.nextProgramme?.let {
                Text(
                    text = "À suivre : $it",
                    color = ZyvioTextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Classic expand/collapse icon, always white on the dark overlay. */
@Composable
private fun FullscreenButton(
    isFullscreen: Boolean,
    onClick: () -> Unit,
) {
    OutlinedButton(
        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        onClick = onClick,
    ) {
        Icon(
            imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
            contentDescription = if (isFullscreen) "Quitter le plein écran" else "Plein écran",
            tint = ZyvioTextPrimary,
        )
    }
}

@Composable
private fun PlayerAction(
    profile: DeviceProfile,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    description: String,
    iconOnly: Boolean,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    OutlinedButton(
        modifier = Modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .tvFocusEffect(profile == DeviceProfile.Television, cornerRadiusDp = 10),
        contentPadding = if (iconOnly) PaddingValues(horizontal = 12.dp, vertical = 8.dp) else ButtonDefaults.ContentPadding,
        onClick = onClick,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = if (iconOnly) description else null,
            tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        if (!iconOnly) {
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 200.dp),
            )
        }
    }
}

/** "12:34 / 1:02:03" for the overlay timeline. */
internal object PlayerTimeFormat {
    fun clock(ms: Long): String {
        val totalSeconds = (ms.coerceAtLeast(0L) / 1_000L)
        val hours = totalSeconds / 3_600L
        val minutes = (totalSeconds % 3_600L) / 60L
        val seconds = totalSeconds % 60L
        val mmss = "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
        return if (hours > 0L) "$hours:$mmss" else "$minutes:${seconds.toString().padStart(2, '0')}"
    }

    fun timeline(positionMs: Long, durationMs: Long?): String =
        if (durationMs != null && durationMs > 0L) {
            "${clock(positionMs.coerceAtMost(durationMs))} / ${clock(durationMs)}"
        } else {
            clock(positionMs)
        }
}

@Composable
private fun BufferingOverlay(
    profile: DeviceProfile,
    title: String,
    onBack: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.42f))
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center,
    ) {
        // Retour stays reachable while a slow stream is loading.
        OutlinedButton(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(ZyvioSpace.s4)
                .tvFocusEffect(profile == DeviceProfile.Television, cornerRadiusDp = 12),
            onClick = onBack,
        ) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Retour")
        }
        Column(
            modifier = Modifier.padding(horizontal = ZyvioSpace.s6),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
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
    nextLabel: String?,
    onRetry: () -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(ZyvioBase)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center,
    ) {
        val stacked = maxWidth < PlayerControlsLayout.LABELLED_ACTIONS_MIN_WIDTH_DP.dp
        Column(
            modifier = Modifier
                .padding(horizontal = ZyvioSpace.s6)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
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
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            ErrorActions(stacked = stacked) {
                Button(
                    modifier = Modifier.tvFocusEffect(profile == DeviceProfile.Television, cornerRadiusDp = 10),
                    onClick = onRetry,
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Réessayer")
                }
                if (nextLabel != null) {
                    OutlinedButton(
                        modifier = Modifier.tvFocusEffect(
                            profile == DeviceProfile.Television,
                            cornerRadiusDp = 10,
                        ),
                        onClick = onNext,
                    ) {
                        Icon(Icons.Default.SkipNext, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(nextLabel)
                    }
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

/** Side by side when there is room, stacked full-width on portrait phones. */
@Composable
private fun ErrorActions(stacked: Boolean, content: @Composable () -> Unit) {
    if (stacked) {
        Column(
            modifier = Modifier.widthIn(max = 360.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { content() }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { content() }
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
            .background(ZyvioBase),
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
    audioTracks: List<PlayerTrackUi>,
    subtitleTracks: List<PlayerTrackUi>,
    subtitlesEnabled: Boolean,
    onSelectAudioTrack: (PlayerTrackUi) -> Unit,
    onSelectSubtitleTrack: (PlayerTrackUi) -> Unit,
    onDisableSubtitles: () -> Unit,
) {
    val availableWidth = LocalConfiguration.current.screenWidthDp.dp
    val sidePanelOffset = if (profile == DeviceProfile.Television) {
        (availableWidth * 0.42f).coerceAtMost(520.dp)
    } else {
        0.dp
    }
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                start = sidePanelOffset,
                top = if (profile == DeviceProfile.Television) 0.dp else 120.dp,
            ),
        color = ZyvioSurface1,
    ) {
        Column(Modifier.padding(ZyvioSpace.s6)) {
            Text(
                text = "Audio et sous-titres",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            Spacer(Modifier.height(20.dp))
            Text("AUDIO", color = ZyvioTextSecondary, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(8.dp))

            if (audioTracks.isEmpty()) {
                Text("Aucune piste audio détectée", color = ZyvioTextSecondary)
            } else {
                audioTracks.forEach { track ->
                    TrackChoice(
                        profile = profile,
                        label = track.label,
                        selected = track.selected,
                        onClick = { onSelectAudioTrack(track) },
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            Text("SOUS-TITRES", color = ZyvioTextSecondary, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(8.dp))

            TrackChoice(
                profile = profile,
                label = "Désactivés",
                selected = !subtitlesEnabled,
                onClick = onDisableSubtitles,
            )

            subtitleTracks.forEach { track ->
                TrackChoice(
                    profile = profile,
                    label = track.label,
                    selected = subtitlesEnabled && track.selected,
                    onClick = { onSelectSubtitleTrack(track) },
                )
            }

            if (subtitleTracks.isEmpty()) {
                Text(
                    text = "Aucun sous-titre détecté",
                    modifier = Modifier.padding(top = 8.dp),
                    color = ZyvioTextSecondary,
                )
            }

            Spacer(Modifier.height(18.dp))
            Text(
                text = "Les pistes affichées proviennent du flux en cours.",
                color = ZyvioTextSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun TrackChoice(
    profile: DeviceProfile,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    OutlinedButton(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .tvFocusEffect(profile == DeviceProfile.Television, cornerRadiusDp = 10),
        onClick = onClick,
    ) {
        Text(
            text = if (selected) "✓  " + label else label,
            modifier = Modifier.fillMaxWidth(),
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ResumePanel(
    profile: DeviceProfile,
    title: String,
    timeline: PlayerTimelineUi,
    onRestartFromBeginning: () -> Unit,
    onResumePlayback: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = if (profile == DeviceProfile.Television) 520.dp else 400.dp),
            color = ZyvioSurface1,
            shape = RoundedCornerShape(18.dp),
        ) {
            Column(Modifier.padding(ZyvioSpace.s6)) {
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
                    OutlinedButton(
                        modifier = Modifier.tvFocusEffect(
                            profile == DeviceProfile.Television,
                            cornerRadiusDp = 10,
                        ),
                        onClick = onRestartFromBeginning,
                    ) {
                        Text("Depuis le début")
                    }
                    Button(
                        modifier = Modifier.tvFocusEffect(
                            profile == DeviceProfile.Television,
                            cornerRadiusDp = 10,
                        ),
                        onClick = onResumePlayback,
                    ) {
                        Text("Reprendre")
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelNumberPanel(
    profile: DeviceProfile,
    input: String,
) {
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
            Column(Modifier.padding(ZyvioSpace.s5)) {
                Text(
                    text = "ALLER À LA CHAÎNE",
                    color = ZyvioTextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                )
                Text(
                    text = if (input.isBlank()) "_" else input + "_",
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


private fun keyCodeToDigit(keyCode: Int): Int? = when (keyCode) {
    AndroidKeyEvent.KEYCODE_0 -> 0
    AndroidKeyEvent.KEYCODE_1 -> 1
    AndroidKeyEvent.KEYCODE_2 -> 2
    AndroidKeyEvent.KEYCODE_3 -> 3
    AndroidKeyEvent.KEYCODE_4 -> 4
    AndroidKeyEvent.KEYCODE_5 -> 5
    AndroidKeyEvent.KEYCODE_6 -> 6
    AndroidKeyEvent.KEYCODE_7 -> 7
    AndroidKeyEvent.KEYCODE_8 -> 8
    AndroidKeyEvent.KEYCODE_9 -> 9
    else -> null
}
