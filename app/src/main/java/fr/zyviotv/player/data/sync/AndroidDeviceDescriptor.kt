package fr.zyviotv.player.data.sync

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.provider.Settings
import fr.zyviotv.player.BuildConfig
import fr.zyviotv.player.shared.sync.DevicePlatform
import fr.zyviotv.player.shared.sync.DeviceRegistration

object AndroidDeviceDescriptor {
    fun current(context: Context): DeviceRegistration {
        val configuration = context.resources.configuration
        val type = configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
        val platform = when {
            type == Configuration.UI_MODE_TYPE_TELEVISION -> DevicePlatform.AndroidTv
            configuration.screenWidthDp >= 600 -> DevicePlatform.AndroidTablet
            else -> DevicePlatform.AndroidPhone
        }

        val deviceUid = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID,
        ) ?: "unknown-device"

        val manufacturer = Build.MANUFACTURER.orEmpty().trim()
        val model = Build.MODEL.orEmpty().trim()
        val displayName = listOf(manufacturer, model)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { "Appareil Android" }

        return DeviceRegistration(
            deviceUid = deviceUid,
            displayName = displayName,
            platform = platform,
            appVersion = BuildConfig.VERSION_NAME,
        )
    }
}
