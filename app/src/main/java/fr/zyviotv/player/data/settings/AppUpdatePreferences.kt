package fr.zyviotv.player.data.settings

import android.content.Context

class AppUpdatePreferences(context: Context) {
    private val preferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun shouldShowOptionalUpdate(
        latestVersionCode: Long,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): Boolean {
        val dismissedVersion = preferences.getLong(KEY_DISMISSED_VERSION, -1L)
        val dismissedAt = preferences.getLong(KEY_DISMISSED_AT, 0L)

        if (dismissedVersion != latestVersionCode) return true
        return nowEpochMillis - dismissedAt >= OPTIONAL_UPDATE_REMINDER_MS
    }

    fun dismissOptionalUpdate(
        latestVersionCode: Long,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ) {
        preferences.edit()
            .putLong(KEY_DISMISSED_VERSION, latestVersionCode)
            .putLong(KEY_DISMISSED_AT, nowEpochMillis)
            .apply()
    }

    fun clearDismissal() {
        preferences.edit()
            .remove(KEY_DISMISSED_VERSION)
            .remove(KEY_DISMISSED_AT)
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "zyviotv_app_update_preferences"
        const val KEY_DISMISSED_VERSION = "dismissed_optional_version"
        const val KEY_DISMISSED_AT = "dismissed_optional_at"
        const val OPTIONAL_UPDATE_REMINDER_MS = 7L * 24L * 60L * 60L * 1000L
    }
}
