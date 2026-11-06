package app.musonic

import app.musonic.audio.*
import kotlin.math.*
import org.junit.Assert.*
import org.junit.Test

class DspEngineTest {
    private fun tone(rate: Int, hz: Double, amplitude: Double = .1, channels: Int = 2) = DoubleArray(rate * channels) {
        if (it % channels == 0) amplitude * sin(2 * PI * hz * (it / channels) / rate) else 0.0
    }
    private fun rms(samples: DoubleArray, channel: Int = 0, channels: Int = 2): Double {
        var energy = 0.0; var count = 0
        for (i in samples.size / 2 + channel until samples.size step channels) { energy += samples[i] * samples[i]; count++ }
        return sqrt(energy / count)
    }
    @Test fun disabledAndFlatPreserveSamplesAndStereoSeparation() {
        for (enabled in listOf(false, true)) {
            val engine = DspEngine().apply { parameters = DspParameters(enabled); configure(48000, 2) }
            val audio = tone(48000, 1000.0); val original = audio.copyOf()
            engine.process(audio)
            assertArrayEquals(original, audio, 1e-12)
        }
    }
    @Test fun tenFiltersProvideFrequencySelectiveGainAtMultipleSampleRates() {
        for (rate in listOf(44100, 48000, 96000)) {
            fun measure(hz: Double): Double {
                val engine = DspEngine().apply { parameters = DspParameters(true, DspBands.frequencies.map { if (it == 1000) -12.0 else 0.0 }); configure(rate, 2) }
                val audio = tone(rate, hz); val original = rms(audio)
                engine.process(audio)
                assertEquals("No left-to-right crosstalk", 0.0, rms(audio, 1), 0.0)
                return 20 * log10(rms(audio) / original)
            }
            assertEquals(-12.0, measure(1000.0), .05)
            assertTrue("Distant frequencies should remain nearly unchanged", measure(100.0) > -.3)
        }
    }
    @Test fun boostHeadroomAndLinkedPeakProtectionStayFiniteAndBounded() {
        val engine = DspEngine().apply { parameters = DspParameters(true, List(10) { 15.0 }); configure(48000, 2) }
        val samples = DoubleArray(96000) { if (it % 2 == 0) (if (it % 4 == 0) 1.0 else -1.0) else .2 }
        engine.process(samples)
        assertTrue(samples.all { it.isFinite() && abs(it) <= .981 })
        assertTrue(samples.any { abs(it) > .0001 })
    }
    @Test fun changesCrossfadeAndResetDoesNotCarryPreviousSongTail() {
        val engine = DspEngine().apply { configure(48000, 1) }
        val audio = DoubleArray(4800) { .2 }
        engine.parameters = DspParameters(true, List(10) { 0.0 }, -12.0)
        engine.process(audio)
        assertTrue(abs(audio.first() - .2) < .001)
        assertEquals(.2 * 10.0.pow(-12.0 / 20), audio.last(), 1e-9)
        engine.parameters = DspParameters(true, List(10) { 12.0 })
        engine.process(DoubleArray(4800) { .1 })
        engine.reset()
        val silence = DoubleArray(4800); engine.process(silence)
        assertTrue(silence.all { it == 0.0 })
    }
    @Test fun independentBalanceWorksWithoutEqAndLeavesMonoUnchanged() {
        val engine = DspEngine().apply { parameters = DspParameters(balanceEnabled = true, leftGainDb = -6.0, rightGainDb = -12.0); configure(48000, 2) }
        val samples = DoubleArray(48000) { .1 }; engine.process(samples)
        assertEquals(.1 * 10.0.pow(-6.0 / 20), samples[0], 1e-12)
        assertEquals(.1 * 10.0.pow(-12.0 / 20), samples[1], 1e-12)
        engine.configure(48000, 1)
        val mono = DoubleArray(100) { .1 }; engine.process(mono)
        assertTrue(mono.all { abs(it - .1) < 1e-12 })
        engine.parameters = engine.parameters.copy(balanceEnabled = false)
        engine.configure(48000, 2)
        val bypass = DoubleArray(100) { .1 }; engine.process(bypass)
        assertTrue(bypass.all { abs(it - .1) < 1e-12 })
    }
    @Test fun loudnessDependsOnVolumeAndChangesRealFrequencyResponseWithoutEq() {
        fun measure(hz: Double, volume: Double): Double {
            val engine = DspEngine().apply {
                parameters = DspParameters(loudnessGains = LoudnessContour.gains(volume, .6)); configure(48000, 2)
            }
            val audio = tone(48000, hz); val before = rms(audio); engine.process(audio)
            assertEquals(0.0, rms(audio, 1), 0.0)
            return 20 * log10(rms(audio) / before)
        }
        assertEquals(0.0, measure(63.0, .8), 1e-9)
        val lowContrast = measure(63.0, .2) - measure(1000.0, .2)
        val mediumContrast = measure(63.0, .4) - measure(1000.0, .4)
        assertTrue(lowContrast > 3)
        assertTrue(lowContrast > mediumContrast)
        assertTrue(measure(8000.0, .2) > measure(1000.0, .2))
        assertTrue(LoudnessContour.gains(Double.NaN, .6).all { it == 0.0 })
    }
    @Test fun combinedControlsRemainBoundedAndVolumeChangesCrossfade() {
        val engine = DspEngine().apply { configure(48000, 2) }
        engine.parameters = DspParameters(true, List(10) { 15.0 }, balanceEnabled = true, leftGainDb = -6.0,
            loudnessGains = LoudnessContour.gains(.1, .8))
        val samples = DoubleArray(96000) { .1 * sin(it * .08) }; engine.process(samples)
        assertTrue(samples.all { it.isFinite() && abs(it) <= .981 })
        engine.parameters = DspParameters(balanceEnabled = true, rightGainDb = -12.0)
        val end = DoubleArray(9600) { .1 }; engine.process(end)
        assertEquals(.1, end[end.size - 2], 1e-9)
        assertEquals(.1 * 10.0.pow(-12.0 / 20), end.last(), 1e-9)
    }
    @Test fun lowRatesAndInvalidSamplesCannotProduceNanOutput() {
        val engine = DspEngine().apply { parameters = DspParameters(true, List(10) { 15.0 }); configure(8000, 1) }
        val audio = DoubleArray(8000) { if (it % 10 == 0) Double.NaN else if (it % 13 == 0) Double.POSITIVE_INFINITY else .9 }
        engine.process(audio)
        assertTrue(audio.all { it.isFinite() && abs(it) <= 1.0 })
    }
}
