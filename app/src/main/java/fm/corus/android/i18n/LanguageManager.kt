package fm.corus.android.i18n

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * In-app language picker — mirrors iOS LanguageManager.
 *
 * Source of truth is [AppCompatDelegate.getApplicationLocales]:
 *   - Empty list  → System default (Android resolves: pt-* → values-pt, else default English)
 *   - "en"        → English
 *   - "pt-BR"     → Brazilian Portuguese
 *
 * AppCompat persists the selection across launches (platform LocaleManager on API 33+,
 * its own SharedPreferences on lower APIs) and re-applies it before the activity is created,
 * so callers never need to re-apply on startup.
 */
enum class AppLanguage(val tag: String, val displayName: String) {
    SYSTEM("", "System Default"),
    ENGLISH("en", "English"),
    PORTUGUESE_BR("pt-BR", "Português (Brasil)");

    companion object {
        fun fromTag(tag: String?): AppLanguage = when (tag) {
            null, "" -> SYSTEM
            "en" -> ENGLISH
            "pt-BR", "pt", "pt_BR" -> PORTUGUESE_BR
            else -> SYSTEM
        }
    }
}

object LanguageManager {
    fun current(): AppLanguage {
        val locales = AppCompatDelegate.getApplicationLocales()
        return if (locales.isEmpty) AppLanguage.SYSTEM
        else AppLanguage.fromTag(locales[0]?.toLanguageTag())
    }

    fun set(language: AppLanguage) {
        val list = if (language == AppLanguage.SYSTEM) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(language.tag)
        }
        AppCompatDelegate.setApplicationLocales(list)
    }
}
