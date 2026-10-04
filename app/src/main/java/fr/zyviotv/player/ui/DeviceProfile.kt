package fr.zyviotv.player.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

enum class DeviceProfile {
    Mobile,
    Tablet,
    Television,
}

@Composable
fun rememberDeviceProfile(): DeviceProfile {
    val configuration = LocalConfiguration.current
    val isTelevision =
        configuration.uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION

    return when {
        isTelevision -> DeviceProfile.Television
        configuration.screenWidthDp >= 600 -> DeviceProfile.Tablet
        else -> DeviceProfile.Mobile
    }
}
