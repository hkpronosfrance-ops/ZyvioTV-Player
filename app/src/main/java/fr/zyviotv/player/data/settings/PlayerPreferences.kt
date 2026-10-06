package fr.zyviotv.player.data.settings

import android.content.Context

data class PlayerPreferencesSnapshot(
    val preferredAudioLanguage: String?,
    val preferredSubtitleLanguage: String?,
    val subtitlesEnabled: Boolean,
    val autoplayNextEpisode: Boolean,
    val dataSaverEnabled: Boolean,
    val mobileQualityLimit: String,
)

class PlayerPreferences(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun read(): PlayerPreferencesSnapshot =
        PlayerPreferencesSnapshot(
            preferredAudioLanguage = preferences.getString(KEY_AUDIO_LANGUAGE, null),
            preferredSubtitleLanguage = preferences.getString(KEY_SUBTITLE_LANGUAGE, null),
            subtitlesEnabled = preferences.getBoolean(KEY_SUBTITLES_ENABLED, true),
            autoplayNextEpisode = preferences.getBoolean(KEY_AUTOPLAY_NEXT, true),
            dataSaverEnabled = preferences.getBoolean(KEY_DATA_SAVER, false),
            mobileQualityLimit = preferences.getString(KEY_MOBILE_QUALITY, "Auto") ?: "Auto",
        )

    fun setPreferredAudioLanguage(value: String?) {
        preferences.edit().putString(KEY_AUDIO_LANGUAGE, value?.takeIf { it.isNotBlank() }).apply()
    }

    fun setPreferredSubtitleLanguage(value: String?) {
        preferences.edit().putString(KEY_SUBTITLE_LANGUAGE, value?.takeIf { it.isNotBlank() }).apply()
    }

    fun setSubtitlesEnabled(value: Boolean) {
        preferences.edit().putBoolean(KEY_SUBTITLES_ENABLED, value).apply()
    }

    fun setAutoplayNextEpisode(value: Boolean) {
        preferences.edit().putBoolean(KEY_AUTOPLAY_NEXT, value).apply()
    }

    fun setDataSaverEnabled(value: Boolean) {
        preferences.edit().putBoolean(KEY_DATA_SAVER, value).apply()
    }

    fun setMobileQualityLimit(value: String) {
        preferences.edit().putString(KEY_MOBILE_QUALITY, value).apply()
    }

    private companion object {
        const val PREFS_NAME = "zyviotv_player_preferences"
        const val KEY_AUDIO_LANGUAGE = "preferred_audio_language"
        const val KEY_SUBTITLE_LANGUAGE = "preferred_subtitle_language"
        const val KEY_SUBTITLES_ENABLED = "subtitles_enabled"
        const val KEY_AUTOPLAY_NEXT = "autoplay_next_episode"
        const val KEY_DATA_SAVER = "data_saver_enabled"
        const val KEY_MOBILE_QUALITY = "mobile_quality_limit"
    }
}
