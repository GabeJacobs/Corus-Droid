package fm.corus.android.i18n

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/**
 * In-app language picker — mirrors iOS LanguageManager.
 *
 * Default Android resource resolution handles fresh installs: pt-* devices pick
 * `values-pt-rBR/`, everyone else falls back to default `values/` (English).
 *
 * Once the user explicitly picks a language, we persist it in SharedPreferences and
 * apply it via:
 *   - [Activity.attachBaseContext] for the next cold start (wraps Configuration)
 *   - [Activity.recreate] for the current session (UI updates immediately)
 *   - [AppCompatDelegate.setApplicationLocales] so the platform's per-app locale
 *     setting in system settings reflects our choice on Android 13+.
 *
 * MainActivity extends ComponentActivity, not AppCompatActivity, so we cannot rely
 * on AppCompatActivity's automatic locale-on-attach behavior — we wrap the
 * Configuration ourselves in [wrapContext].
 */
enum class AppLanguage(val tag: String, val displayName: String) {
    SYSTEM("", "System Default"),
    ENGLISH("en", "English"),
    PORTUGUESE_BR("pt-BR", "Português (Brasil)"),
    SPANISH("es", "Español");

    companion object {
        fun fromTag(tag: String?): AppLanguage = when (tag) {
            null, "" -> SYSTEM
            "en" -> ENGLISH
            "pt-BR", "pt", "pt_BR" -> PORTUGUESE_BR
            "es", "es_ES", "es_419" -> SPANISH
            else -> SYSTEM
        }
    }
}

object LanguageManager {
    private const val PREFS = "corus_locale"
    private const val KEY_TAG = "tag"

    fun current(context: Context): AppLanguage {
        val tag = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_TAG, "") ?: ""
        return AppLanguage.fromTag(tag)
    }

    /**
     * Persist the selection and apply it to the running activity.
     * Pass the calling Activity so we can recreate it to refresh the UI.
     */
    fun set(activity: Activity, language: AppLanguage) {
        activity.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TAG, language.tag)
            .apply()

        // Keep platform per-app locale (Android 13+ system settings) in sync.
        val list = if (language == AppLanguage.SYSTEM) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(language.tag)
        }
        AppCompatDelegate.setApplicationLocales(list)

        // Force the activity to recreate so the new resources apply now.
        activity.recreate()
    }

    /**
     * Wrap the base context with the persisted locale.
     * Called from MainActivity.attachBaseContext.
     */
    fun wrapContext(base: Context): Context {
        val tag = base.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_TAG, "") ?: ""
        if (tag.isEmpty()) return base

        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)

        val config = Configuration(base.resources.configuration)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val localeList = LocaleList(locale)
            LocaleList.setDefault(localeList)
            config.setLocales(localeList)
        } else {
            @Suppress("DEPRECATION")
            config.locale = locale
        }
        return base.createConfigurationContext(config)
    }
}
