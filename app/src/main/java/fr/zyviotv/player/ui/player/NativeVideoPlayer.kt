package fr.zyviotv.player.ui.player

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import fr.zyviotv.player.shared.playback.PlaybackRequest
import fr.zyviotv.player.shared.playback.PlaybackValidationResult
import fr.zyviotv.player.shared.playback.PlaybackValidator

@Composable
fun NativeVideoPlayer(
    request: PlaybackRequest,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val validation = remember(request) { PlaybackValidator.validate(request) }

    if (validation !is PlaybackValidationResult.Valid) {
        return
    }

    val player = remember(request.streamUrl) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(request.streamUrl))
            if (request.resumePositionMs > 0L) {
                seekTo(request.resumePositionMs)
            }
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(player) {
        onDispose {
            player.release()
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = {
            PlayerView(context).apply {
                this.player = player
                useController = true
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
