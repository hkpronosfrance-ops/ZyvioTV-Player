package fr.zyviotv.player.ui.player

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import fr.zyviotv.player.data.settings.OnboardingSetupPreferences
import fr.zyviotv.player.data.settings.PlayerPreferences
import fr.zyviotv.player.data.settings.ProfilePreferences
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.delay
import fr.zyviotv.player.shared.playback.PlaybackRequest
import fr.zyviotv.player.shared.playback.PlaybackState
import fr.zyviotv.player.ui.DeviceProfile

@UnstableApi
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
    onProgressChanged: (Long, Long?) -> Unit = { _, _ -> },
    onPlaybackExit: (Long, Long?) -> Unit = { _, _ -> },
    onPlaybackEnded: (Long, Long?) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val playerPreferences = remember(context.applicationContext) {
        PlayerPreferences(context.applicationContext)
    }
    val profilePreferences = remember(context.applicationContext) {
        ProfilePreferences(context.applicationContext)
    }
    val setupPreferences = remember(context.applicationContext) {
        OnboardingSetupPreferences(context.applicationContext)
    }
    val activeProfileId = profilePreferences.selectedProfileId()
    val profileMediaPreferences = remember(activeProfileId, effectiveKey(request.streamUrl)) {
        activeProfileId?.let(setupPreferences::profile)
    }
    val devicePreferences = remember(effectiveKey(request.streamUrl)) {
        setupPreferences.device()
    }
    val preferenceSnapshot = remember(effectiveKey(request.streamUrl)) {
        playerPreferences.read()
    }
    val initialAudioLanguage = when (profileMediaPreferences?.audioLanguage) {
        "Français" -> "fr"
        "Original", "Auto" -> null
        null -> preferenceSnapshot.preferredAudioLanguage
        else -> null
    }
    val initialSubtitleLanguage = when (profileMediaPreferences?.subtitleLanguage) {
        "Français" -> "fr"
        "Auto", "Désactivés" -> null
        null -> preferenceSnapshot.preferredSubtitleLanguage
        else -> null
    }
    val initialSubtitlesEnabled = when (profileMediaPreferences?.subtitleLanguage) {
        "Désactivés" -> false
        "Auto", "Français" -> true
        null -> preferenceSnapshot.subtitlesEnabled
        else -> true
    }

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
    val shouldPromptResume = request.kind != fr.zyviotv.player.shared.playback.PlaybackKind.Live &&
        request.resumePositionMs >= RESUME_PROMPT_MIN_MS
    var resumePromptPending by remember(effectiveRequest.streamUrl) {
        mutableStateOf(shouldPromptResume)
    }
    var panel by remember(effectiveRequest.streamUrl) {
        mutableStateOf(if (shouldPromptResume) PlayerPanel.Resume else PlayerPanel.None)
    }
    var tracks by remember(effectiveRequest.streamUrl) { mutableStateOf(NativeTrackCatalog()) }
    var selectedAudioLanguage by remember(effectiveRequest.streamUrl) {
        mutableStateOf(initialAudioLanguage)
    }
    var selectedSubtitleLanguage by remember(effectiveRequest.streamUrl) {
        mutableStateOf(initialSubtitleLanguage)
    }
    // Explicit picks from the tracks panel (Media3 "group:track" ids); they
    // also reach tracks that carry no language tag.
    var selectedAudioTrackId by remember(effectiveRequest.streamUrl) { mutableStateOf<String?>(null) }
    var selectedSubtitleTrackId by remember(effectiveRequest.streamUrl) { mutableStateOf<String?>(null) }
    // Kept across channels/episodes and rotation; reset when the player closes.
    var fullscreenRequested by rememberSaveable { mutableStateOf(false) }
    var orientationRequest by rememberSaveable { mutableStateOf(PlayerOrientationRequest.FollowDevice) }
    var scaleMode by rememberSaveable { mutableStateOf(PlayerScaleMode.Fit) }
    var subtitlesEnabled by remember(effectiveRequest.streamUrl) {
        mutableStateOf(initialSubtitlesEnabled)
    }
    var command by remember { mutableStateOf(NativePlayerCommand.None) }
    var commandToken by remember { mutableLongStateOf(0L) }
    var channelDigits by remember(effectiveRequest.streamUrl) { mutableStateOf("") }
    var endedHandled by remember(effectiveRequest.streamUrl) { mutableStateOf(false) }

    LaunchedEffect(playbackState, effectiveRequest.streamUrl) {
        if (playbackState == PlaybackState.Ended && !endedHandled) {
            endedHandled = true
            onPlaybackEnded(positionMs, durationMs)
        }
    }

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

    // Phone vs tablet from the smallest width: a Pixel 7 turned sideways is
    // more than 600 dp wide and resolves to the Tablet profile, but it is
    // still a phone.
    val configuration = LocalConfiguration.current
    val isTelevision = profile == DeviceProfile.Television
    val isCompactDevice = configuration.smallestScreenWidthDp < COMPACT_DEVICE_MAX_SMALLEST_WIDTH_DP
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val windowState = PlayerWindowState(fullscreenRequested, orientationRequest)
    val immersive = PlayerFullscreenPolicy.isImmersive(windowState, isTelevision, isCompactDevice, isLandscape)
    val toggleFullscreen: () -> Unit = {
        val next = PlayerFullscreenPolicy.toggle(windowState, isTelevision, isCompactDevice, isLandscape)
        fullscreenRequested = next.userFullscreen
        orientationRequest = next.orientation
    }
    if (!isTelevision) {
        PlayerWindowEffect(immersive = immersive, orientation = orientationRequest)
    }

    // The on-screen Retour and the Android back gesture/button take the same path.
    val handleBack: () -> Unit = {
        when (PlayerBackPolicy.onBack(panel, immersive)) {
            PlayerBackAction.ExitFullscreen -> toggleFullscreen()
            PlayerBackAction.ExitPlayer -> {
                onPlaybackExit(positionMs, durationMs)
                onBack()
            }
            PlayerBackAction.CancelChannelNumber -> {
                channelDigits = ""
                panel = PlayerPanel.None
            }
            PlayerBackAction.ClosePanel -> panel = PlayerPanel.None
        }
    }
    BackHandler(onBack = handleBack)

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
        audioLabel = tracks.audio.firstOrNull { it.selected }?.label,
        subtitlesLabel = if (!subtitlesEnabled) {
            "Désactivés"
        } else {
            tracks.subtitles.firstOrNull { it.selected }?.label
        },
        audioTracks = tracks.audio.map {
            PlayerTrackUi(
                id = it.id,
                label = it.label,
                language = it.language,
                selected = it.selected,
            )
        },
        subtitleTracks = tracks.subtitles.map {
            PlayerTrackUi(
                id = it.id,
                label = it.label,
                language = it.language,
                selected = it.selected,
            )
        },
        subtitlesEnabled = subtitlesEnabled,
        errorMessage = errorMessage,
        channelNumberInput = channelDigits.takeIf { it.isNotBlank() },
        isFullscreen = immersive,
        scaleMode = scaleMode,
    )

    ParentalPlaybackGuard(
        streamUrl = effectiveRequest.streamUrl,
        playbackKind = effectiveRequest.kind.name,
        playbackState = playbackState,
        isPlaying = isPlaying,
        onBlockPlayback = {
            sendCommand(NativePlayerCommand.Pause)
        },
        onResumePlayback = {
            sendCommand(NativePlayerCommand.Play)
        },
    )

    PlayerScreen(
        profile = profile,
        state = uiState,
        onBack = handleBack,
        onTogglePlayPause = { sendCommand(NativePlayerCommand.TogglePlayPause) },
        onSeekBack = { sendCommand(NativePlayerCommand.SeekBack10) },
        onSeekForward = { sendCommand(NativePlayerCommand.SeekForward10) },
        onRetry = {
            errorMessage = null
            playbackState = PlaybackState.Idle
            sendCommand(NativePlayerCommand.Retry)
        },
        onNext = {
            if (request.kind == fr.zyviotv.player.shared.playback.PlaybackKind.Live) {
                onNextChannel()
            } else if (request.kind == fr.zyviotv.player.shared.playback.PlaybackKind.Episode) {
                onNext()
            }
        },
        onResumePlayback = {
            resumePromptPending = false
            panel = PlayerPanel.None
            sendCommand(NativePlayerCommand.Play)
        },
        onRestartFromBeginning = {
            resumePromptPending = false
            positionMs = 0L
            panel = PlayerPanel.None
            sendCommand(NativePlayerCommand.RestartFromBeginning)
        },
        onOpenTracks = { panel = PlayerPanel.Tracks },
        onOpenGuide = onOpenGuide,
        onToggleFavorite = onToggleFavorite,
        onSelectAudioTrack = { track ->
            selectedAudioTrackId = track.id
            track.language?.let {
                selectedAudioLanguage = it
                playerPreferences.setPreferredAudioLanguage(it)
            }
        },
        onSelectSubtitleTrack = { track ->
            selectedSubtitleTrackId = track.id
            subtitlesEnabled = true
            playerPreferences.setSubtitlesEnabled(true)
            track.language?.let {
                selectedSubtitleLanguage = it
                playerPreferences.setPreferredSubtitleLanguage(it)
            }
        },
        onDisableSubtitles = {
            subtitlesEnabled = false
            playerPreferences.setSubtitlesEnabled(false)
        },
        onChannelUp = onNextChannel,
        onChannelDown = onPreviousChannel,
        onChannelDigit = { digit ->
            if (request.kind == fr.zyviotv.player.shared.playback.PlaybackKind.Live) {
                channelDigits = (channelDigits + digit.toString()).takeLast(MAX_CHANNEL_DIGITS)
                panel = PlayerPanel.ChannelNumber
            }
        },
        onToggleFullscreen = toggleFullscreen,
        onToggleScaleMode = { scaleMode = scaleMode.toggled() },
        videoContent = {
            NativeVideoPlayer(
                request = effectiveRequest,
                modifier = Modifier.fillMaxSize(),
                onStateChanged = { playbackState = it },
                onError = { errorMessage = it },
                onPositionChanged = {
                    positionMs = it
                    onPositionChanged(it)
                    onProgressChanged(it, durationMs)
                },
                onDurationChanged = {
                    durationMs = it
                    onProgressChanged(positionMs, it)
                },
                onIsPlayingChanged = { isPlaying = it },
                onTracksChanged = { tracks = it },
                selectedAudioLanguage = selectedAudioLanguage,
                selectedSubtitleLanguage = selectedSubtitleLanguage,
                selectedAudioTrackId = selectedAudioTrackId,
                selectedSubtitleTrackId = selectedSubtitleTrackId,
                subtitlesEnabled = subtitlesEnabled,
                playbackQuality = devicePreferences.playbackQuality,
                scaleMode = scaleMode,
                showNativeControls = false,
                autoPlay = !resumePromptPending,
                command = command,
                commandToken = commandToken,
            )
        },
    )
}

private fun effectiveKey(streamUrl: String): String = streamUrl

private const val RESUME_BACKOFF_MS = 5_000L
private const val RESUME_PROMPT_MIN_MS = 30_000L
private const val CHANNEL_NUMBER_CONFIRM_DELAY_MS = 1_500L
private const val MAX_CHANNEL_DIGITS = 4
private const val COMPACT_DEVICE_MAX_SMALLEST_WIDTH_DP = 600
