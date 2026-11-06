package app.musonic

import android.content.res.Configuration
import androidx.test.platform.app.InstrumentationRegistry
import app.musonic.settings.AppPreferences
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test

class AppearancePreferencesTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    @Before fun clear() { context.getSharedPreferences(AppPreferences.STORE, 0).edit().clear().commit() }
    @After fun restore() = clear()

    @Test fun defaultsToLightEvenWhenDeviceIsDarkAndPersistsChoice() {
        val systemDark = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
        })
        assertFalse(AppPreferences.dark(systemDark))
        assertEquals(Configuration.UI_MODE_NIGHT_NO, AppPreferences.localizedContext(systemDark).resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK)
        AppPreferences.setDark(context, true)
        assertTrue(AppPreferences.dark(context.applicationContext))
        assertEquals(Configuration.UI_MODE_NIGHT_YES, AppPreferences.localizedContext(context.applicationContext).resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK)
    }

    @Test fun allLanguagesResolveAndFormatMessagesFromPersistedChoice() {
        for ((tag, title) in mapOf("ko" to "설정", "en" to "Settings", "ja" to "設定", "zh-CN" to "设置", "es" to "Ajustes")) {
            AppPreferences.setLanguage(context, tag)
            assertEquals(tag, AppPreferences.language(context.applicationContext))
            val localized = AppPreferences.localizedContext(context.applicationContext)
            assertEquals(title, localized.getString(R.string.settings))
            assertTrue(localized.getString(R.string.phone_volume, 50).contains("50%"))
            val summary = localized.getString(R.string.track_duration_summary, 3, 12)
            assertTrue(summary.contains("3") && summary.contains("12"))
            assertFalse(summary.contains("%"))
            assertTrue(localized.getString(R.string.remove_connection_detail, "My Server").contains("My Server"))
            assertFalse(localized.getString(R.string.equalizer).isBlank())
        }
    }
}
