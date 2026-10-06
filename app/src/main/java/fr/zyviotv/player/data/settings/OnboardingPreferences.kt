package fr.zyviotv.player.data.settings

import android.content.Context

class OnboardingPreferences(context: Context) {
    private val preferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isCompleted(): Boolean =
        preferences.getBoolean(KEY_COMPLETED, false)

    fun markStarted() {
        preferences.edit()
            .putBoolean(KEY_STARTED, true)
            .apply()
    }

    fun hasStarted(): Boolean =
        preferences.getBoolean(KEY_STARTED, false)

    fun markCompleted() {
        preferences.edit()
            .putBoolean(KEY_STARTED, true)
            .putBoolean(KEY_COMPLETED, true)
            .apply()
    }

    fun reset() {
        preferences.edit().clear().apply()
    }

    private companion object {
        const val PREFS_NAME = "zyviotv_onboarding"
        const val KEY_STARTED = "started"
        const val KEY_COMPLETED = "completed"
    }
}
