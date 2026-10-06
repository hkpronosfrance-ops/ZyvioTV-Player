package fr.zyviotv.player.ui.player

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
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
    onPreviousChannel: () -> Unit = {},
    onNextChannel: () -> Unit = {},
    onChannelNumberEntered: (String) -> Unit = {},
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
    var channelDigits by remember(effectiveRequest.streamUrl) { mutableStateOf("") }

    fun sendCommand(next: NativePlayerCommand) {
        command = next
        commandToken += 1L
    }

    LaunchedEffect(channelDigits) {
        if (channelDigits.isNotBlank()) {
            delay(CHANNEL_NUMBER_CONFIRM_DELAY_MS)
            val confirmed = channelDigits
            channelDigits = ""
            panel = PlayerPanel.None
            onChannelNumberEntered(confirmed)
        }
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
        channelNumberInput = channelDigits.takeIf { it.isNotBlank() },
    )

    PlayerScreen(
        profile = profile,
        state = uiState,
        onBack = {
            when (panel) {
                PlayerPanel.None -> onBack()
                PlayerPanel.ChannelNumber -> {
                    channelDigits = ""
                    panel = PlayerPanel.None
                }
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
        onChannelUp = onNextChannel,
        onChannelDown = onPreviousChannel,
        onChannelDigit = { digit ->
            if (request.kind == fr.zyviotv.player.shared.playback.PlaybackKind.Live) {
                channelDigits = (channelDigits + digit.toString()).takeLast(MAX_CHANNEL_DIGITS)
                panel = PlayerPanel.ChannelNumber
            }
        },
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
private const val CHANNEL_NUMBER_CONFIRM_DELAY_MS = 1_500L
private const val MAX_CHANNEL_DIGITS = 4
