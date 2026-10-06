package fr.zyviotv.player.ui.player

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import fr.zyviotv.player.shared.playback.PlaybackRequest
import fr.zyviotv.player.shared.playback.PlaybackState
import fr.zyviotv.player.ui.DeviceProfile

@Composable
fun PlayerHost(
    profile: DeviceProfile,
    request: PlaybackRequest,
    metadata: PlayerMetadataUi = PlayerMetadataUi(
        title = request.title,
        kind = request.kind,
    ),
    onBack: () -> Unit = {},
    onNext: () -> Unit = {},
    onOpenGuide: () -> Unit = {},
    onToggleFavorite: () -> Unit = {},
    onPositionChanged: (Long) -> Unit = {},
) {
    val effectiveRequest = remember(request) {
        request.copy(
            resumePositionMs = if (request.resumePositionMs > 0L) {
                (request.resumePositionMs - RESUME_BACKOFF_MS).coerceAtLeast(0L)
            } else {
                0L
            },
        )
    }

    var playbackState by remember(effectiveRequest.streamUrl) { mutableStateOf(PlaybackState.Idle) }
    var isPlaying by remember(effectiveRequest.streamUrl) { mutableStateOf(true) }
    var positionMs by remember(effectiveRequest.streamUrl) {
        mutableLongStateOf(effectiveRequest.resumePositionMs)
    }
    var durationMs by remember(effectiveRequest.streamUrl) { mutableStateOf<Long?>(null) }
    var errorMessage by remember(effectiveRequest.streamUrl) { mutableStateOf<String?>(null) }
    var panel by remember(effectiveRequest.streamUrl) { mutableStateOf(PlayerPanel.None) }
    var command by remember { mutableStateOf(NativePlayerCommand.None) }
    var commandToken by remember { mutableLongStateOf(0L) }

    fun sendCommand(next: NativePlayerCommand) {
        command = next
        commandToken += 1L
    }

    val uiState = PlayerScreenUiState(
        playbackState = playbackState,
        isPlaying = isPlaying,
        controlsVisible = true,
        panel = panel,
        metadata = metadata,
        timeline = PlayerTimelineUi(
            positionMs = positionMs,
            durationMs = durationMs,
        ),
        errorMessage = errorMessage,
    )

    PlayerScreen(
        profile = profile,
        state = uiState,
        onBack = {
            when (panel) {
                PlayerPanel.None -> onBack()
                else -> panel = PlayerPanel.None
            }
        },
        onTogglePlayPause = { sendCommand(NativePlayerCommand.TogglePlayPause) },
        onSeekBack = { sendCommand(NativePlayerCommand.SeekBack10) },
        onSeekForward = { sendCommand(NativePlayerCommand.SeekForward10) },
        onRetry = {
            errorMessage = null
            playbackState = PlaybackState.Idle
            sendCommand(NativePlayerCommand.Retry)
        },
        onNext = onNext,
        onOpenTracks = { panel = PlayerPanel.Tracks },
        onOpenGuide = onOpenGuide,
        onToggleFavorite = onToggleFavorite,
        videoContent = {
            NativeVideoPlayer(
                request = effectiveRequest,
                modifier = Modifier.fillMaxSize(),
                onStateChanged = { playbackState = it },
                onError = { errorMessage = it },
                onPositionChanged = {
                    positionMs = it
                    onPositionChanged(it)
                },
                onDurationChanged = { durationMs = it },
                onIsPlayingChanged = { isPlaying = it },
                showNativeControls = false,
                command = command,
                commandToken = commandToken,
            )
        },
    )
}

private const val RESUME_BACKOFF_MS = 5_000L
