package app.musonic.settings

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale

/** App-only appearance preferences. Audio and server profiles use separate stores. */
object AppPreferences {
    const val STORE = "musonic_appearance"
    val languages = linkedMapOf("ko" to "한국어", "en" to "English", "ja" to "日本語", "zh-CN" to "简体中文", "es" to "Español")
    fun language(context: Context): String = context.getSharedPreferences(STORE, 0).getString("language", "ko")
        ?.takeIf { it in languages } ?: "ko"
    fun dark(context: Context) = context.getSharedPreferences(STORE, 0).getBoolean("dark", false)
    fun setLanguage(context: Context, language: String) {
        require(language in languages)
        context.getSharedPreferences(STORE, 0).edit().putString("language", language).apply()
    }
    fun setDark(context: Context, dark: Boolean) {
        context.getSharedPreferences(STORE, 0).edit().putBoolean("dark", dark).apply()
    }
    fun localizedContext(context: Context): Context {
        val config = Configuration(context.resources.configuration).apply {
            val locale = Locale.forLanguageTag(language(context))
            setLocales(LocaleList(locale))
            setLayoutDirection(locale)
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (dark(context)) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
        return context.createConfigurationContext(config)
    }
}
