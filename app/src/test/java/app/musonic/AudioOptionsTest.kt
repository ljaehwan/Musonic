package app.musonic

import app.musonic.audio.*
import org.junit.Assert.*
import org.junit.Test

class AudioOptionsTest {
    @Test fun `gain curves interpolate in octaves and clamp at boundaries`() {
        val curve = mapOf(100 to -600, 400 to 600)
        assertEquals(0, interpolateGain(curve, 200))
        assertEquals(-600, interpolateGain(curve, 40))
        assertEquals(600, interpolateGain(curve, 16000))
        assertEquals(0, interpolateGain(emptyMap(), 1000))
    }
    @Test fun `presets work across device band counts without extreme gain`() {
        val frequencies = listOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
        for (preset in EqPreset.entries) {
            val values = frequencies.map { AudioOptions(preset = preset).level(it) }
            assertTrue(values.all { it in -600..600 })
            if (preset == EqPreset.FLAT) assertTrue(values.all { it == 0 })
        }
        assertTrue(AudioOptions(preset = EqPreset.BASS).level(60) > AudioOptions(preset = EqPreset.BASS).level(14000))
        assertTrue(AudioOptions(preset = EqPreset.TREBLE).level(14000) > AudioOptions(preset = EqPreset.TREBLE).level(60))
    }
}
