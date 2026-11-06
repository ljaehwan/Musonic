package app.musonic

import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import app.musonic.audio.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class SavedSoundTest {
    @Test fun savedSoundsAndIndependentControlsSurviveRestart() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val prefix = "saved-sound-test-${UUID.randomUUID()}-"
        val context = object : ContextWrapper(base) {
            override fun getSharedPreferences(name: String, mode: Int) = base.getSharedPreferences(prefix + name, mode)
        }
        try {
            val settings = AudioSettings.isolated(context)
            settings.preset(EqPreset.W_SHAPE); settings.preamp(-3f)
            val id = settings.savePreset("  밤에 듣기  ")!!
            val original = settings.state.value.savedPresets.single()
            settings.preset(EqPreset.BASS); settings.preamp(-6f)
            settings.selectSaved(id)
            settings.balance(true); settings.channel(true, -6f); settings.channel(false, -2f)
            settings.loudness(true); settings.threshold(.7f)
            val restored = AudioSettings.isolated(context)
            assertEquals(settings.state.value, restored.state.value)
            assertEquals("밤에 듣기", restored.state.value.presetTitle)
            assertEquals(-3f, restored.state.value.preampDb, 0f)
            DspBands.frequencies.forEach { assertEquals(original.levels[it], restored.state.value.level(it)) }
            val effect = PlaybackEqualizer {}
            effect.apply(restored.state.value)
            assertTrue(effect.engine.parameters.balanceEnabled)
            assertFalse(effect.engine.parameters.enabled)
            assertTrue(effect.engine.parameters.loudnessGains.isEmpty())
            effect.updateVolume(.2)
            assertTrue(effect.engine.parameters.loudnessGains.first() > 0)
            effect.updateVolume(.9)
            assertTrue(effect.engine.parameters.loudnessGains.all { it == 0.0 })
            effect.updateVolume(null)
            assertTrue(effect.engine.parameters.loudnessGains.isEmpty())
            effect.close()
            restored.custom(mapOf(63 to -200))
            assertNull(restored.state.value.selectedPresetId)
            restored.selectSaved(id)
            val curve = DspBands.frequencies.map(restored.state.value::level)
            restored.deletePreset(id)
            assertEquals(curve, DspBands.frequencies.map(restored.state.value::level))
            assertTrue(AudioSettings.isolated(context).state.value.savedPresets.isEmpty())
            assertNull(restored.savePreset("   "))
        } finally { base.deleteSharedPreferences(prefix + "musonic_audio"); base.deleteSharedPreferences(prefix + "musonic_private") }
    }
}
