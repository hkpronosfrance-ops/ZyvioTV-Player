package fr.zyviotv.player.data.settings

import android.content.Context

class ProfilePreferences(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun selectedProfileId(): String? =
        preferences.getString(KEY_SELECTED_PROFILE_ID, null)

    fun setSelectedProfileId(profileId: String?) {
        preferences.edit().putString(KEY_SELECTED_PROFILE_ID, profileId).apply()
    }

    fun defaultProfileId(): String? =
        preferences.getString(KEY_DEFAULT_PROFILE_ID, null)

    fun setDefaultProfileId(profileId: String?) {
        preferences.edit().putString(KEY_DEFAULT_PROFILE_ID, profileId).apply()
    }

    private companion object {
        const val PREFS_NAME = "zyviotv_profile_preferences"
        const val KEY_SELECTED_PROFILE_ID = "selected_profile_id"
        const val KEY_DEFAULT_PROFILE_ID = "default_profile_id"
    }
}
