package fr.zyviotv.player.ui.player

import android.os.Build
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecDecoderException
import androidx.media3.exoplayer.mediacodec.MediaCodecRenderer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import fr.zyviotv.player.shared.playback.PlaybackKind
import fr.zyviotv.player.shared.playback.PlaybackMediaType
import fr.zyviotv.player.shared.playback.PlaybackMediaTypeResolver
import fr.zyviotv.player.shared.playback.PlaybackRequest
import fr.zyviotv.player.shared.playback.PlaybackSource
import fr.zyviotv.player.shared.playback.PlaybackState
import fr.zyviotv.player.shared.playback.PlaybackValidationResult
import fr.zyviotv.player.shared.playback.PlaybackValidator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@UnstableApi
@Composable
fun NativeVideoPlayer(
    request: PlaybackRequest,
    modifier: Modifier = Modifier,
    onStateChanged: (PlaybackState) -> Unit = {},
    onError: (String) -> Unit = {},
    onPositionChanged: (Long) -> Unit = {},
    onDurationChanged: (Long?) -> Unit = {},
    onIsPlayingChanged: (Boolean) -> Unit = {},
    onTracksChanged: (NativeTrackCatalog) -> Unit = {},
    selectedAudioLanguage: String? = null,
    selectedSubtitleLanguage: String? = null,
    selectedAudioTrackId: String? = null,
    selectedSubtitleTrackId: String? = null,
    subtitlesEnabled: Boolean = true,
    playbackQuality: String = "Auto",
    scaleMode: PlayerScaleMode = PlayerScaleMode.Fit,
    showNativeControls: Boolean = true,
    autoPlay: Boolean = true,
    command: NativePlayerCommand = NativePlayerCommand.None,
    commandToken: Long = 0L,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val validation = remember(request) { PlaybackValidator.validate(request) }

    // Effects outlive a single composition: always call the latest callbacks.
    val stateChanged by rememberUpdatedState(onStateChanged)
    val errorReported by rememberUpdatedState(onError)
    val positionChanged by rememberUpdatedState(onPositionChanged)
    val durationChanged by rememberUpdatedState(onDurationChanged)
    val isPlayingChanged by rememberUpdatedState(onIsPlayingChanged)
    val tracksChanged by rememberUpdatedState(onTracksChanged)

    if (validation !is PlaybackValidationResult.Valid) {
        // Report through an effect: callbacks must not mutate caller state
        // during composition.
        LaunchedEffect(validation) {
            stateChanged(PlaybackState.Error)
            errorReported(
                (validation as? PlaybackValidationResult.Invalid)?.message
                    ?: "Le flux vidéo est invalide.",
            )
        }
        return
    }

    val mediaAttempts = remember(request.streamUrl, request.kind) {
        PlaybackMediaTypeResolver.attempts(request)
    }
    val attemptIndex = remember(request.streamUrl, request.resumePositionMs) { intArrayOf(0) }

    val player = remember(request.streamUrl, request.resumePositionMs) {
        PlaybackDiagnostics.launch(request)
        PlaybackDiagnostics.attempt(mediaAttempts[0], 0)
        ExoPlayer.Builder(
            context,
            // A decoder that fails to initialise (frequent for HEVC on the
            // emulator) falls back to the next one, e.g. the software codec.
            DefaultRenderersFactory(context).setEnableDecoderFallback(true),
        )
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(
                    DefaultDataSource.Factory(
                        context,
                        providerHttpDataSourceFactory(PlaybackSource.parse(request.streamUrl).headers),
                    ),
                ),
            )
            .build()
            .apply {
                setHandleAudioBecomingNoisy(true)
                setMediaItem(buildMediaItem(request, mediaAttempts[0]))
                if (request.resumePositionMs > 0L) {
                    seekTo(request.resumePositionMs)
                }
                prepare()
                playWhenReady = autoPlay
            }
    }
    // Commands sent before this player existed belong to the previous one.
    val commandGate = remember(player) { PlayerCommandGate(commandToken) }

    LaunchedEffect(
        player,
        selectedAudioLanguage,
        selectedSubtitleLanguage,
        selectedAudioTrackId,
        selectedSubtitleTrackId,
        subtitlesEnabled,
        playbackQuality,
    ) {
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .apply {
                selectedAudioLanguage?.let { setPreferredAudioLanguage(it) }
                selectedSubtitleLanguage?.let { setPreferredTextLanguage(it) }
                setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !subtitlesEnabled)
                // Explicit track choice: works for IPTV tracks without a
                // language tag, which preferred-language selection cannot reach.
                applyTrackOverride(player.currentTracks, C.TRACK_TYPE_AUDIO, selectedAudioTrackId)
                if (subtitlesEnabled) {
                    applyTrackOverride(player.currentTracks, C.TRACK_TYPE_TEXT, selectedSubtitleTrackId)
                }
                when (playbackQuality) {
                    "4K" -> setMaxVideoSize(3840, 2160)
                    "FHD" -> setMaxVideoSize(1920, 1080)
                    "HD" -> setMaxVideoSize(1280, 720)
                    "SD" -> setMaxVideoSize(854, 480)
                    else -> clearVideoSizeConstraints()
                }
            }
            .build()
    }

    // The overlay timeline needs the position while playing; 1 Hz keeps the
    // recomposition cost negligible.
    LaunchedEffect(player) {
        while (true) {
            delay(POSITION_TICK_MS)
            if (player.isPlaying) {
                positionChanged(player.currentPosition.coerceAtLeast(0L))
            }
        }
    }

    LaunchedEffect(player, commandToken) {
        if (!commandGate.accept(commandToken)) return@LaunchedEffect
        when (command) {
            NativePlayerCommand.None -> Unit
            NativePlayerCommand.TogglePlayPause -> {
                if (player.isPlaying) {
                    player.pause()
                } else {
                    if (player.playbackState == Player.STATE_ENDED) player.seekTo(0L)
                    player.play()
                }
            }
            NativePlayerCommand.Pause -> player.pause()
            NativePlayerCommand.Play -> player.play()
            NativePlayerCommand.SeekBack10 -> {
                player.seekTo((player.currentPosition - SEEK_STEP_MS).coerceAtLeast(0L))
                positionChanged(player.currentPosition.coerceAtLeast(0L))
            }
            NativePlayerCommand.SeekForward10 -> {
                val duration = player.duration.takeIf { it > 0L && it != C.TIME_UNSET }
                val target = player.currentPosition + SEEK_STEP_MS
                player.seekTo(if (duration != null) target.coerceAtMost(duration) else target)
                positionChanged(player.currentPosition.coerceAtLeast(0L))
            }
            NativePlayerCommand.RestartFromBeginning -> {
                player.seekTo(0L)
                player.play()
            }
            NativePlayerCommand.Retry -> {
                val position = if (request.kind == PlaybackKind.Live) {
                    0L
                } else {
                    player.currentPosition.coerceAtLeast(0L)
                }
                player.stop()
                player.setMediaItem(buildMediaItem(request, mediaAttempts[attemptIndex[0]]))
                if (position > 0L) player.seekTo(position)
                player.prepare()
                player.playWhenReady = true
            }
        }
    }

    // Background/foreground: pause on stop, resume only what was playing.
    DisposableEffect(player, lifecycleOwner) {
        val resumePolicy = PlayerResumePolicy()
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    resumePolicy.onStop(wasPlaying = player.isPlaying || player.playWhenReady)
                    positionChanged(player.currentPosition.coerceAtLeast(0L))
                    player.pause()
                }
                Lifecycle.Event.ON_START -> {
                    if (resumePolicy.onStart() && player.playbackState != Player.STATE_ENDED) {
                        player.play()
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        onDispose { lifecycleOwner.lifecycle.removeObserver(lifecycleObserver) }
    }

    // One owner for the player's lifetime: released exactly once, when the
    // player leaves the screen or is replaced by the next channel/episode.
    DisposableEffect(player) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        var bufferingJob: Job? = null
        var silentRetryUsed = false

        fun publishDuration() {
            val duration = player.duration.takeIf { it > 0L && it != C.TIME_UNSET }
            durationChanged(duration)
        }

        fun failPlayback(message: String) {
            bufferingJob?.cancel()
            PlaybackDiagnostics.state("error")
            stateChanged(PlaybackState.Error)
            errorReported(message)
        }

        fun reload(mediaType: PlaybackMediaType) {
            val position = if (request.kind == PlaybackKind.Live) {
                0L
            } else {
                player.currentPosition.coerceAtLeast(0L)
            }
            player.stop()
            player.setMediaItem(buildMediaItem(request, mediaType))
            if (position > 0L) {
                player.seekTo(position)
            }
            player.prepare()
            player.playWhenReady = true
        }

        fun silentRetryOrFail(message: String) {
            bufferingJob?.cancel()
            if (!silentRetryUsed) {
                silentRetryUsed = true
                reload(mediaAttempts[attemptIndex[0]])
            } else {
                failPlayback(message)
            }
        }

        fun handlePlayerError(error: PlaybackException) {
            val httpStatus = (error.cause as? HttpDataSource.InvalidResponseCodeException)
                ?.responseCode
            val failure = PlaybackErrorClassifier.classify(
                errorCode = error.errorCode,
                httpStatus = httpStatus,
                decoder = error.decoderDiagnosis(),
            )
            val nextAttempt = attemptIndex[0] + 1
            when {
                failure.kind == PlaybackErrorKind.BehindLiveWindow -> {
                    PlaybackDiagnostics.failure(failure, error.errorCodeName, terminal = false)
                    player.seekToDefaultPosition()
                    player.prepare()
                }
                failure.canTryNextContainer && nextAttempt < mediaAttempts.size -> {
                    PlaybackDiagnostics.failure(failure, error.errorCodeName, terminal = false)
                    attemptIndex[0] = nextAttempt
                    PlaybackDiagnostics.attempt(mediaAttempts[nextAttempt], nextAttempt)
                    bufferingJob?.cancel()
                    reload(mediaAttempts[nextAttempt])
                }
                failure.isTransient && !silentRetryUsed -> {
                    PlaybackDiagnostics.failure(failure, error.errorCodeName, terminal = false)
                    silentRetryOrFail(failure.userMessage)
                }
                else -> {
                    PlaybackDiagnostics.failure(failure, error.errorCodeName, terminal = true)
                    failPlayback(failure.userMessage)
                }
            }
        }

        fun beginBufferingWatch() {
            bufferingJob?.cancel()
            bufferingJob = scope.launch {
                delay(BUFFERING_INDICATOR_DELAY_MS)
                if (player.playbackState != Player.STATE_BUFFERING) return@launch

                stateChanged(PlaybackState.Buffering)

                delay(BUFFERING_ERROR_TIMEOUT_MS - BUFFERING_INDICATOR_DELAY_MS)
                if (player.playbackState == Player.STATE_BUFFERING) {
                    silentRetryOrFail(
                        "Le flux met trop de temps à répondre. Réessayez ou choisissez un autre contenu.",
                    )
                }
            }
        }

        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> beginBufferingWatch()
                    Player.STATE_READY -> {
                        bufferingJob?.cancel()
                        silentRetryUsed = false
                        PlaybackDiagnostics.state("ready")
                        stateChanged(PlaybackState.Ready)
                        positionChanged(player.currentPosition.coerceAtLeast(0L))
                        publishDuration()
                        tracksChanged(player.currentTracks.toNativeTrackCatalog())
                    }
                    Player.STATE_ENDED -> {
                        bufferingJob?.cancel()
                        stateChanged(PlaybackState.Ended)
                        positionChanged(player.currentPosition.coerceAtLeast(0L))
                        publishDuration()
                    }
                    else -> {
                        bufferingJob?.cancel()
                        stateChanged(PlaybackState.Idle)
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                isPlayingChanged(isPlaying)
            }

            override fun onTracksChanged(tracks: Tracks) {
                tracksChanged(tracks.toNativeTrackCatalog())
            }

            override fun onPlayerError(error: PlaybackException) {
                handlePlayerError(error)
            }
        }

        player.addListener(listener)

        onDispose {
            bufferingJob?.cancel()
            scope.cancel()
            positionChanged(player.currentPosition.coerceAtLeast(0L))
            player.removeListener(listener)
            player.release()
            PlaybackDiagnostics.released("dispose")
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { viewContext ->
            PlayerView(viewContext).apply {
                this.player = player
                useController = showNativeControls
                keepScreenOn = true
                setShutterBackgroundColor(android.graphics.Color.BLACK)
                resizeMode = scaleMode.resizeMode()
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            }
        },
        update = { view ->
            if (view.player !== player) view.player = player
            view.useController = showNativeControls
            view.resizeMode = scaleMode.resizeMode()
        },
        // Detach the surface before the player is released.
        onRelease = { view -> view.player = null },
    )
}

@UnstableApi
private fun PlayerScaleMode.resizeMode(): Int = when (this) {
    PlayerScaleMode.Fit -> AspectRatioFrameLayout.RESIZE_MODE_FIT
    PlayerScaleMode.Zoom -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
}

@UnstableApi
private fun TrackSelectionParameters.Builder.applyTrackOverride(
    tracks: Tracks,
    trackType: Int,
    trackId: String?,
) {
    if (trackId == null) return
    val groupIndex = trackId.substringBefore(':').toIntOrNull() ?: return
    val trackIndex = trackId.substringAfter(':').toIntOrNull() ?: return
    val group = tracks.groups.getOrNull(groupIndex) ?: return
    if (group.type != trackType || trackIndex !in 0 until group.length) return
    setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, trackIndex))
}

/** Codec facts for diagnostics only; never touches the stream URL. */
@UnstableApi
private fun PlaybackException.decoderDiagnosis(): DecoderDiagnosis? {
    val initialization = cause as? MediaCodecRenderer.DecoderInitializationException
    val decoding = cause as? MediaCodecDecoderException
    val rendererMime = (this as? ExoPlaybackException)?.rendererFormat?.sampleMimeType
    val mimeType = initialization?.mimeType
        ?: decoding?.codecInfo?.mimeType
        ?: rendererMime
    val codecName = initialization?.codecInfo?.name ?: decoding?.codecInfo?.name
    if (mimeType == null && codecName == null) return null
    return DecoderDiagnosis(
        mimeType = mimeType,
        codecName = codecName,
        isEmulator = DecoderDiagnosis.isEmulator(
            fingerprint = Build.FINGERPRINT.orEmpty(),
            hardware = Build.HARDWARE.orEmpty(),
            product = Build.PRODUCT.orEmpty(),
        ),
    )
}

data class NativeTrackOption(
    val id: String,
    val label: String,
    val language: String?,
    val selected: Boolean,
)

data class NativeTrackCatalog(
    val audio: List<NativeTrackOption> = emptyList(),
    val subtitles: List<NativeTrackOption> = emptyList(),
)

@UnstableApi
private fun Tracks.toNativeTrackCatalog(): NativeTrackCatalog {
    val audio = mutableListOf<NativeTrackOption>()
    val subtitles = mutableListOf<NativeTrackOption>()

    groups.forEachIndexed { groupIndex, group ->
        val target = when (group.type) {
            C.TRACK_TYPE_AUDIO -> audio
            C.TRACK_TYPE_TEXT -> subtitles
            else -> null
        } ?: return@forEachIndexed

        for (trackIndex in 0 until group.length) {
            if (!group.isTrackSupported(trackIndex)) continue

            val format = group.mediaTrackGroup.getFormat(trackIndex)
            val language = format.language
            val fallback = if (group.type == C.TRACK_TYPE_AUDIO) {
                "Piste audio " + (audio.size + 1)
            } else {
                "Sous-titre " + (subtitles.size + 1)
            }
            val label = format.label?.takeIf { it.isNotBlank() }
                ?: language?.takeIf { it.isNotBlank() }?.uppercase()
                ?: fallback

            target += NativeTrackOption(
                id = groupIndex.toString() + ":" + trackIndex,
                label = label,
                language = language,
                selected = group.isTrackSelected(trackIndex),
            )
        }
    }

    return NativeTrackCatalog(
        audio = audio,
        subtitles = subtitles,
    )
}

enum class NativePlayerCommand {
    None,
    TogglePlayPause,
    Pause,
    Play,
    SeekBack10,
    SeekForward10,
    RestartFromBeginning,
    Retry,
}

/**
 * Provider HTTP stack shared by every stream: IPTV panels commonly redirect
 * between HTTP and HTTPS hosts (load balancers, tokenised edges), which the
 * Media3 default refuses. Cleartext stays governed by the app network policy.
 */
@UnstableApi
private fun providerHttpDataSourceFactory(headers: Map<String, String>): HttpDataSource.Factory =
    DefaultHttpDataSource.Factory()
        // A playlist-provided User-Agent/Referer is an access requirement of
        // the provider (M3U `|User-Agent=` or #EXTVLCOPT, bloc #208).
        .setUserAgent(headers[USER_AGENT_HEADER] ?: PROVIDER_USER_AGENT)
        .setDefaultRequestProperties(headers - USER_AGENT_HEADER)
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(PROVIDER_CONNECT_TIMEOUT_MS)
        .setReadTimeoutMs(PROVIDER_READ_TIMEOUT_MS)

private fun buildMediaItem(request: PlaybackRequest, mediaType: PlaybackMediaType): MediaItem {
    val mimeType = when (mediaType) {
        PlaybackMediaType.Hls -> MimeTypes.APPLICATION_M3U8
        PlaybackMediaType.TransportStream -> MimeTypes.VIDEO_MP2T
        PlaybackMediaType.Progressive,
        PlaybackMediaType.Unknown,
        -> null
    }

    return MediaItem.Builder()
        .setUri(PlaybackSource.parse(request.streamUrl).url)
        .apply {
            if (mimeType != null) setMimeType(mimeType)
        }
        .build()
}


private const val USER_AGENT_HEADER = "User-Agent"
private const val PROVIDER_USER_AGENT = "ZYVIOTV-Player/0.1 (Android)"
private const val PROVIDER_CONNECT_TIMEOUT_MS = 15_000
private const val PROVIDER_READ_TIMEOUT_MS = 20_000
private const val BUFFERING_INDICATOR_DELAY_MS = 500L
private const val BUFFERING_ERROR_TIMEOUT_MS = 15_000L
private const val SEEK_STEP_MS = 10_000L
private const val POSITION_TICK_MS = 1_000L
