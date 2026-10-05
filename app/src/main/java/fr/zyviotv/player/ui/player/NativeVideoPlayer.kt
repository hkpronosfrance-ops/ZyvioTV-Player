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

@Composable
fun NativeVideoPlayer(
    request: PlaybackRequest,
    modifier: Modifier = Modifier,
    onStateChanged: (PlaybackState) -> Unit = {},
    onError: (String) -> Unit = {},
    onPositionChanged: (Long) -> Unit = {},
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
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                val state = when (playbackState) {
                    Player.STATE_BUFFERING -> PlaybackState.Buffering
                    Player.STATE_READY -> PlaybackState.Ready
                    Player.STATE_ENDED -> PlaybackState.Ended
                    else -> PlaybackState.Idle
                }
                onStateChanged(state)
                if (state == PlaybackState.Ready || state == PlaybackState.Ended) {
                    onPositionChanged(player.currentPosition.coerceAtLeast(0L))
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                onStateChanged(PlaybackState.Error)
                onError("Impossible de lire ce flux. Réessayez ou choisissez un autre contenu.")
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
                useController = true
                keepScreenOn = true
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            }
        },
        update = { view ->
            view.player = player
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
