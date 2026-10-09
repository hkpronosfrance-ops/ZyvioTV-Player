package fr.zyviotv.player.ui.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Applies the player's window state to the activity (phones and tablets):
 * system bars hidden while immersive (a swipe shows them briefly) and the
 * requested orientation. The orientation the activity had when the player
 * opened is restored, with the bars, when the player leaves the screen, so
 * the catalogue never stays locked in landscape.
 *
 * The activity declares orientation/screenSize config changes, so rotating
 * never recreates it: the Media3 player and its stream keep running.
 */
@Composable
fun PlayerWindowEffect(
    immersive: Boolean,
    orientation: PlayerOrientationRequest,
) {
    val activity = LocalContext.current.findActivity() ?: return
    val openingOrientation = remember(activity) { activity.requestedOrientation }

    LaunchedEffect(activity, immersive) {
        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        if (immersive) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    LaunchedEffect(activity, orientation) {
        activity.requestedOrientation = when (orientation) {
            PlayerOrientationRequest.FollowDevice -> openingOrientation
            PlayerOrientationRequest.Landscape -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            PlayerOrientationRequest.Portrait -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        }
    }

    DisposableEffect(activity) {
        onDispose {
            WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                .show(WindowInsetsCompat.Type.systemBars())
            activity.requestedOrientation = openingOrientation
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
