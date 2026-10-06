package fr.zyviotv.player.ui.player

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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

@Composable
fun NativeVideoPlayer(
    request: PlaybackRequest,
    modifier: Modifier = Modifier,
    onStateChanged: (PlaybackState) -> Unit = {},
    onError: (String) -> Unit = {},
    onPositionChanged: (Long) -> Unit = {},
    onDurationChanged: (Long?) -> Unit = {},
    onIsPlayingChanged: (Boolean) -> Unit = {},
    showNativeControls: Boolean = true,
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
