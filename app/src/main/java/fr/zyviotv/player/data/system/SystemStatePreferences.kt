package fr.zyviotv.player.data.system

import android.content.Context

class SystemStatePreferences(context: Context) {
    private val preferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun shouldShowPlannedMaintenance(nowEpochMillis: Long = System.currentTimeMillis()): Boolean {
        val today = nowEpochMillis / DAY_MS
        return preferences.getLong(KEY_LAST_MAINTENANCE_DAY, -1L) != today
    }

    fun dismissPlannedMaintenance(nowEpochMillis: Long = System.currentTimeMillis()) {
        preferences.edit()
            .putLong(KEY_LAST_MAINTENANCE_DAY, nowEpochMillis / DAY_MS)
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "zyviotv_system_state_preferences"
        const val KEY_LAST_MAINTENANCE_DAY = "last_maintenance_day"
        const val DAY_MS = 24L * 60L * 60L * 1000L
    }
}
