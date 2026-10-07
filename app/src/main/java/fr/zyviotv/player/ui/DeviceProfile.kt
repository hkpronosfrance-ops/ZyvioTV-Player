package fr.zyviotv.player.ui

import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

enum class DeviceProfile {
    Mobile,
    Tablet,
    Television,
}

fun resolveDeviceProfile(
    screenWidthDp: Int,
    uiModeType: Int,
    hasTelevisionFeature: Boolean = false,
): DeviceProfile = when {
    uiModeType == Configuration.UI_MODE_TYPE_TELEVISION || hasTelevisionFeature ->
        DeviceProfile.Television
    screenWidthDp >= TABLET_MIN_WIDTH_DP -> DeviceProfile.Tablet
    else -> DeviceProfile.Mobile
}

@Composable
fun rememberDeviceProfile(): DeviceProfile {
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val uiModeType = configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
    val packageManager = context.packageManager
    val hasTelevisionFeature =
        packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
            packageManager.hasSystemFeature(PackageManager.FEATURE_TELEVISION)

    return resolveDeviceProfile(
        screenWidthDp = configuration.screenWidthDp,
        uiModeType = uiModeType,
        hasTelevisionFeature = hasTelevisionFeature,
    )
}

private const val TABLET_MIN_WIDTH_DP = 600
