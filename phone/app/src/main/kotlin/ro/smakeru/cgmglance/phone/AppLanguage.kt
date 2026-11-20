package ro.smakeru.cgmglance.phone

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * The languages this app ships, plus "whatever the phone uses".
 *
 * [endonym] is each language's own name for itself and is never translated: someone who landed
 * in a language they cannot read has to be able to find their own in the list.
 */
enum class AppLanguage(
    val tag: String?,
    val endonym: String?,
) {
    SYSTEM(null, null),
    ENGLISH("en", "English"),
    ROMANIAN("ro", "Română"),
    GERMAN("de", "Deutsch"),
    FRENCH("fr", "Français"),
    ;

    companion object {
        /** Region and script are ignored: `de-AT` is German. Anything unshipped follows the system. */
        fun fromTag(tag: String?): AppLanguage {
            val language =
                tag
                    ?.trim()
                    ?.substringBefore('-')
                    ?.substringBefore('_')
                    ?.lowercase()
            if (language.isNullOrEmpty()) return SYSTEM
            return entries.firstOrNull { it.tag == language } ?: SYSTEM
        }
    }
}

/**
 * Reads and applies the user's language choice.
 *
 * From Android 13 the platform owns per-app language: it stores the choice, applies it to every
 * context, recreates the activities and lists the app under the system's own language settings,
 * so the choice made there and the one made here are the same value. Below 13 there is no such
 * facility, and this app deliberately has no AppCompat dependency to borrow one from, so the
 * choice is kept in a small preference file and applied by wrapping contexts in [wrap].
 */
object AppLanguageController {
    private const val FILE_NAME = "cgm_glance_language"
    private const val KEY_TAG = "tag"

    fun current(context: Context): AppLanguage =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val locales = context.getSystemService(LocaleManager::class.java).applicationLocales
            if (locales.isEmpty) AppLanguage.SYSTEM else AppLanguage.fromTag(locales[0].toLanguageTag())
        } else {
            AppLanguage.fromTag(prefs(context).getString(KEY_TAG, null))
        }

    /**
     * Stores [language] and makes it take effect.
     *
     * On Android 13 and later the system recreates visible activities by itself. Below that the
     * caller must recreate its activity, and anything drawn outside one (widgets, notifications)
     * must be refreshed through a context passed to [wrap].
     */
    fun apply(
        context: Context,
        language: AppLanguage,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java).applicationLocales =
                language.tag?.let(LocaleList::forLanguageTags) ?: LocaleList.getEmptyLocaleList()
            return
        }
        // commit: the activity is recreated immediately afterwards and reads this back.
        prefs(context).edit().putString(KEY_TAG, language.tag).commit()
        Locale.setDefault(localeFor(language))
    }

    /**
     * A context whose resources speak the chosen language. Returns [base] unchanged on Android 13
     * and later, where the platform has already done this, and when the choice is the system's.
     */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val language = AppLanguage.fromTag(prefs(base).getString(KEY_TAG, null))
        if (language == AppLanguage.SYSTEM) return base

        val locale = localeFor(language)
        // Number formatting reads the default locale, and "7,2" beside German text is the point.
        Locale.setDefault(locale)
        val configuration = Configuration(base.resources.configuration)
        configuration.setLocale(locale)
        return base.createConfigurationContext(configuration)
    }

    private fun localeFor(language: AppLanguage): Locale =
        language.tag?.let(Locale::forLanguageTag) ?: Resources.getSystem().configuration.locales[0]

    private fun prefs(context: Context) = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
}
