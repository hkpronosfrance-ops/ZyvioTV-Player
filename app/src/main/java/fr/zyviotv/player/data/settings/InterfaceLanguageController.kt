package fr.zyviotv.player.data.settings

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import java.util.Locale

object InterfaceLanguageController {
    const val SYSTEM = "Système"
    const val FRENCH = "Français"
    const val ENGLISH = "English"

    fun apply(context: Context, preference: String) {
        val languageTag = when (preference) {
            FRENCH -> "fr"
            ENGLISH -> "en"
            else -> ""
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val localeManager = context.getSystemService(LocaleManager::class.java)
            localeManager.applicationLocales = LocaleList.forLanguageTags(languageTag)
            return
        }

        if (languageTag.isBlank()) return

        val locale = Locale.forLanguageTag(languageTag)
        Locale.setDefault(locale)
        val configuration = context.resources.configuration
        configuration.setLocale(locale)
        @Suppress("DEPRECATION")
        context.resources.updateConfiguration(
            configuration,
            context.resources.displayMetrics,
        )
    }
}
