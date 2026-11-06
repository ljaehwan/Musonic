package app.musonic

import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import androidx.test.platform.app.InstrumentationRegistry
import app.musonic.audio.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class AudioSettingsTest {
    @Test fun migratesLegacyQualityAndPersistsCustomSound() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val prefix = "audio-test-${UUID.randomUUID()}-"
        val context = object : ContextWrapper(base) {
            override fun getSharedPreferences(name: String, mode: Int) = base.getSharedPreferences(prefix + name, mode)
        }
        try {
            context.getSharedPreferences("musonic_private", 0).edit().putBoolean("transcode", true).commit()
            val settings = AudioSettings.isolated(context)
            assertEquals(StreamQuality.BALANCED, settings.state.value.quality)
            settings.quality(StreamQuality.HIGH)
            settings.enabled(true)
            settings.preset(EqPreset.BASS)
            settings.band(1000, -400, listOf(EqBand(100, 0), EqBand(1000, 0), EqBand(10000, 0)))
            val restored = AudioSettings.isolated(context)
            assertEquals(settings.state.value, restored.state.value)
            assertEquals(EqPreset.CUSTOM, restored.state.value.preset)
            assertEquals(-400, restored.state.value.level(1000))
            restored.reset()
            assertEquals(StreamQuality.HIGH, restored.state.value.quality)
            assertTrue(restored.state.value.eqEnabled)
            assertEquals(0, restored.state.value.level(1000))
        } finally { base.deleteSharedPreferences(prefix + "musonic_private"); base.deleteSharedPreferences(prefix + "musonic_audio") }
    }

    @Test fun softwareEqualizerHasTenBandsAndRestoresSettings() {
        var state = EqualizerState()
        val effect = PlaybackEqualizer { state = it }
        try {
            assertTrue(state.available)
            assertEquals(10, state.bands.size)
            val options = AudioOptions(eqEnabled = true, preset = EqPreset.BASS, preampDb = -3f)
            effect.apply(options)
            assertTrue(state.enabled)
            assertEquals(-3.0, effect.engine.parameters.preampDb, 0.0)
            state.bands.forEach { assertEquals(options.level(it.frequencyHz), it.level) }
            effect.apply(options.copy(eqEnabled = false))
            assertFalse(state.enabled)
        } finally { effect.close() }
        assertFalse(state.available)
        effect.apply(AudioOptions(eqEnabled = true))
        assertFalse(state.available)
    }

    @Test fun applyingBuiltInCustomAndSavedPresetsEnablesAndPersistsEq() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val prefix = "preset-apply-" + UUID.randomUUID() + "-"
        val context = object : ContextWrapper(base) {
            override fun getSharedPreferences(name: String, mode: Int) = base.getSharedPreferences(prefix + name, mode)
        }
        try {
            val settings = AudioSettings.isolated(context)
            settings.preset(EqPreset.BASS, enable = true)
            assertTrue(settings.state.value.eqEnabled)
            settings.enabled(false)
            settings.custom(mapOf(1000 to -1200), enable = true)
            assertTrue(settings.state.value.eqEnabled)
            val saved = settings.savePreset("My sound")!!
            settings.enabled(false)
            settings.preset(EqPreset.FLAT)
            settings.selectSaved(saved, enable = true)
            assertTrue(settings.state.value.eqEnabled)
            assertEquals(-1200, settings.state.value.level(1000))
            assertEquals(settings.state.value, AudioSettings.isolated(context).state.value)
        } finally { base.deleteSharedPreferences(prefix + "musonic_audio"); base.deleteSharedPreferences(prefix + "musonic_private") }
    }
}
