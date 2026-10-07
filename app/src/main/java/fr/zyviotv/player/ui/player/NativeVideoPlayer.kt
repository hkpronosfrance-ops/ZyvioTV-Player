package fr.zyviotv.player.ui.player

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import fr.zyviotv.player.shared.playback.PlaybackMediaType
import fr.zyviotv.player.shared.playback.PlaybackMediaTypeResolver
import fr.zyviotv.player.shared.playback.PlaybackRequest
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
    subtitlesEnabled: Boolean = true,
    playbackQuality: String = "Auto",
    showNativeControls: Boolean = true,
    command: NativePlayerCommand = NativePlayerCommand.None,
    commandToken: Long = 0L,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val validation = remember(request) { PlaybackValidator.validate(request) }

    if (validation !is PlaybackValidationResult.Valid) {
        onStateChanged(PlaybackState.Error)
        onError(
            (validation as? PlaybackValidationResult.Invalid)?.message
                ?: "Le flux vidéo est invalide.",
        )
        return
    }

    val player = remember(request.streamUrl, request.resumePositionMs) {
        ExoPlayer.Builder(context).build().apply {
            setHandleAudioBecomingNoisy(true)
            setMediaItem(buildMediaItem(request))
            if (request.resumePositionMs > 0L) {
                seekTo(request.resumePositionMs)
            }
            prepare()
            playWhenReady = true
        }
    }

    LaunchedEffect(
        player,
        selectedAudioLanguage,
        selectedSubtitleLanguage,
        subtitlesEnabled,
        playbackQuality,
    ) {
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .apply {
                selectedAudioLanguage?.let { setPreferredAudioLanguage(it) }
                selectedSubtitleLanguage?.let { setPreferredTextLanguage(it) }
                setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !subtitlesEnabled)
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

    LaunchedEffect(player, commandToken) {
        when (command) {
            NativePlayerCommand.None -> Unit
            NativePlayerCommand.TogglePlayPause -> {
                if (player.isPlaying) player.pause() else player.play()
            }
            NativePlayerCommand.Pause -> player.pause()
            NativePlayerCommand.Play -> player.play()
            NativePlayerCommand.SeekBack10 -> {
                player.seekTo((player.currentPosition - SEEK_STEP_MS).coerceAtLeast(0L))
            }
            NativePlayerCommand.SeekForward10 -> {
                val duration = player.duration.takeIf { it > 0L && it != C.TIME_UNSET }
                val target = player.currentPosition + SEEK_STEP_MS
                player.seekTo(if (duration != null) target.coerceAtMost(duration) else target)
            }
            NativePlayerCommand.Retry -> {
                val position = player.currentPosition.coerceAtLeast(0L)
                player.stop()
                player.setMediaItem(buildMediaItem(request))
                if (position > 0L) player.seekTo(position)
                player.prepare()
                player.playWhenReady = true
            }
        }
    }

    DisposableEffect(player, lifecycleOwner) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        var bufferingJob: Job? = null
        var silentRetryUsed = false

        fun publishDuration() {
            val duration = player.duration.takeIf { it > 0L && it != C.TIME_UNSET }
            onDurationChanged(duration)
        }

        fun failPlayback(message: String) {
            bufferingJob?.cancel()
            onStateChanged(PlaybackState.Error)
            onError(message)
        }

        fun silentRetryOrFail(message: String) {
            bufferingJob?.cancel()
            if (!silentRetryUsed) {
                silentRetryUsed = true
                val position = player.currentPosition.coerceAtLeast(0L)
                player.stop()
                player.setMediaItem(buildMediaItem(request))
                if (position > 0L) {
                    player.seekTo(position)
                }
                player.prepare()
                player.playWhenReady = true
            } else {
                failPlayback(message)
            }
        }

        fun beginBufferingWatch() {
            bufferingJob?.cancel()
            bufferingJob = scope.launch {
                delay(BUFFERING_INDICATOR_DELAY_MS)
                if (player.playbackState != Player.STATE_BUFFERING) return@launch

                onStateChanged(PlaybackState.Buffering)

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
                        onStateChanged(PlaybackState.Ready)
                        onPositionChanged(player.currentPosition.coerceAtLeast(0L))
                        publishDuration()
                        onTracksChanged(player.currentTracks.toNativeTrackCatalog())
                    }
                    Player.STATE_ENDED -> {
                        bufferingJob?.cancel()
                        onStateChanged(PlaybackState.Ended)
                        onPositionChanged(player.currentPosition.coerceAtLeast(0L))
                        publishDuration()
                    }
                    else -> {
                        bufferingJob?.cancel()
                        onStateChanged(PlaybackState.Idle)
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                onIsPlayingChanged(isPlaying)
            }

            override fun onTracksChanged(tracks: Tracks) {
                onTracksChanged(tracks.toNativeTrackCatalog())
            }

            override fun onPlayerError(error: PlaybackException) {
                silentRetryOrFail(
                    "Impossible de lire ce flux. Réessayez ou choisissez un autre contenu.",
                )
            }
        }

        val lifecycleObserver = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    onPositionChanged(player.currentPosition.coerceAtLeast(0L))
                    player.pause()
                }
                Lifecycle.Event.ON_START -> {
                    if (player.playbackState != Player.STATE_ENDED) {
                        player.play()
                    }
                }
                else -> Unit
            }
        }

        player.addListener(listener)
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)

        onDispose {
            bufferingJob?.cancel()
            scope.cancel()
            onPositionChanged(player.currentPosition.coerceAtLeast(0L))
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            player.removeListener(listener)
            player.release()
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = {
            PlayerView(context).apply {
                this.player = player
                useController = showNativeControls
                keepScreenOn = true
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            }
        },
        update = { view ->
            view.player = player
            view.useController = showNativeControls
        },
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
    Retry,
}

private fun buildMediaItem(request: PlaybackRequest): MediaItem {
    val mimeType = when (PlaybackMediaTypeResolver.resolve(request)) {
        PlaybackMediaType.Hls -> MimeTypes.APPLICATION_M3U8
        PlaybackMediaType.TransportStream -> MimeTypes.VIDEO_MP2T
        PlaybackMediaType.Progressive,
        PlaybackMediaType.Unknown,
        -> null
    }

    return MediaItem.Builder()
        .setUri(request.streamUrl)
        .apply {
            if (mimeType != null) setMimeType(mimeType)
        }
        .build()
}


private const val BUFFERING_INDICATOR_DELAY_MS = 500L
private const val BUFFERING_ERROR_TIMEOUT_MS = 15_000L
private const val SEEK_STEP_MS = 10_000L
