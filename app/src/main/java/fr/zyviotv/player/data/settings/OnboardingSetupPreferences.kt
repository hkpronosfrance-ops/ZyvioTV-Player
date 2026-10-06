package fr.zyviotv.player.data.settings

import android.content.Context

data class ProfileMediaPreferencesSnapshot(
    val audioLanguage: String,
    val subtitleLanguage: String,
    val autoNextEpisode: Boolean,
)

data class DevicePreferencesSnapshot(
    val playbackQuality: String,
    val interfaceLanguage: String,
)

class OnboardingSetupPreferences(context: Context) {
    private val preferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun profile(profileId: String): ProfileMediaPreferencesSnapshot =
        ProfileMediaPreferencesSnapshot(
            audioLanguage = preferences.getString(
                profileKey(profileId, "audio"),
                "Auto",
            ) ?: "Auto",
            subtitleLanguage = preferences.getString(
                profileKey(profileId, "subtitles"),
                "Auto",
            ) ?: "Auto",
            autoNextEpisode = preferences.getBoolean(
                profileKey(profileId, "auto_next"),
                true,
            ),
        )

    fun saveProfile(
        profileId: String,
        snapshot: ProfileMediaPreferencesSnapshot,
    ) {
        preferences.edit()
            .putString(profileKey(profileId, "audio"), snapshot.audioLanguage)
            .putString(profileKey(profileId, "subtitles"), snapshot.subtitleLanguage)
            .putBoolean(profileKey(profileId, "auto_next"), snapshot.autoNextEpisode)
            .apply()
    }

    fun device(): DevicePreferencesSnapshot =
        DevicePreferencesSnapshot(
            playbackQuality = preferences.getString(KEY_DEVICE_QUALITY, "Auto") ?: "Auto",
            interfaceLanguage = preferences.getString(KEY_INTERFACE_LANGUAGE, "Système") ?: "Système",
        )

    fun saveDevice(snapshot: DevicePreferencesSnapshot) {
        preferences.edit()
            .putString(KEY_DEVICE_QUALITY, snapshot.playbackQuality)
            .putString(KEY_INTERFACE_LANGUAGE, snapshot.interfaceLanguage)
            .apply()
    }

    private fun profileKey(profileId: String, suffix: String): String =
        "profile_" + profileId + "_" + suffix

    private companion object {
        const val PREFS_NAME = "zyviotv_onboarding_setup_preferences"
        const val KEY_DEVICE_QUALITY = "device_quality"
        const val KEY_INTERFACE_LANGUAGE = "interface_language"
    }
}
