package fr.zyviotv.player.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

enum class DeviceProfile {
    Mobile,
    Tablet,
    Television,
}

fun resolveDeviceProfile(
    screenWidthDp: Int,
    uiModeType: Int,
): DeviceProfile = when {
    uiModeType == Configuration.UI_MODE_TYPE_TELEVISION -> DeviceProfile.Television
    screenWidthDp >= TABLET_MIN_WIDTH_DP -> DeviceProfile.Tablet
    else -> DeviceProfile.Mobile
}

@Composable
fun rememberDeviceProfile(): DeviceProfile {
    val configuration = LocalConfiguration.current
    val uiModeType = configuration.uiMode and Configuration.UI_MODE_TYPE_MASK

    return resolveDeviceProfile(
        screenWidthDp = configuration.screenWidthDp,
        uiModeType = uiModeType,
    )
}

private const val TABLET_MIN_WIDTH_DP = 600
