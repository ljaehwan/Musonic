package app.musonic

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.exoplayer.audio.AudioSink
import androidx.test.platform.app.InstrumentationRegistry
import app.musonic.audio.*
import java.lang.reflect.Proxy
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class DspAudioSinkTest {
    @Test fun highResolutionPcmSurvivesAndPartialWritesAreNotProcessedTwice() {
        var configured: Format? = null
        val output = mutableListOf<Float>()
        var writes = 0
        val downstream = Proxy.newProxyInstance(AudioSink::class.java.classLoader, arrayOf(AudioSink::class.java)) { _, method, args ->
            when (method.name) {
                "getFormatSupport" -> AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY
                "configure" -> { configured = args!![0] as Format; null }
                "handleBuffer" -> {
                    val buffer = args!![0] as ByteBuffer
                    output += buffer.float
                    writes++
                    !buffer.hasRemaining()
                }
                else -> null
            }
        } as AudioSink
        val sink = DspAudioSink(downstream, DspEngine())
        assertFalse(sink.supportsFormat(Format.Builder().setSampleMimeType("audio/ac3").build()))
        sink.configure(Format.Builder().setSampleMimeType("audio/raw").setSampleRate(96000).setChannelCount(2).setPcmEncoding(C.ENCODING_PCM_24BIT).build(), 0, null)
        assertEquals(C.ENCODING_PCM_FLOAT, configured!!.pcmEncoding)
        assertEquals(96000, configured!!.sampleRate)
        val pcm = ByteBuffer.wrap(byteArrayOf(1, 0, 0, -1, -1, -1)).order(ByteOrder.LITTLE_ENDIAN)
        assertFalse(sink.handleBuffer(pcm, 0, 1))
        assertEquals("Input must remain available until downstream accepts it", 0, pcm.position())
        assertTrue(sink.handleBuffer(pcm, 0, 1))
        assertEquals(6, pcm.position())
        assertEquals(2, writes)
        assertEquals(1f / 8388608, output[0], 0f)
        assertEquals(-1f / 8388608, output[1], 0f)
        sink.flush()
    }

    @Test fun controlsChangeDecodedPcmAtTheOutputAndBypassRestoresIt() {
        val report = java.io.File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "dsp-measurements.txt")
        report.writeText("Measurements at DSP output, before Android routing; steady-state dB relative to bypass.\n")
        for (rate in listOf(44100, 48000, 96000)) for (encoding in listOf(C.ENCODING_PCM_16BIT, C.ENCODING_PCM_24BIT, C.ENCODING_PCM_FLOAT)) {
            var output = FloatArray(0)
            val downstream = Proxy.newProxyInstance(AudioSink::class.java.classLoader, arrayOf(AudioSink::class.java)) { _, method, args ->
                when (method.name) {
                    "configure" -> null
                    "handleBuffer" -> {
                        val buffer = args!![0] as ByteBuffer
                        output = FloatArray(buffer.remaining() / 4) { buffer.float }
                        true
                    }
                    else -> null
                }
            } as AudioSink
            val effect = PlaybackEqualizer { }
            val sink = DspAudioSink(downstream, effect.engine)
            sink.configure(Format.Builder().setSampleMimeType("audio/raw").setSampleRate(rate).setChannelCount(2).setPcmEncoding(encoding).build(), 0, null)
            fun measure(options: AudioOptions, hz: Double = 1000.0): Pair<Double, Double> {
                effect.apply(options) // Update the running processor without configure/flush/restarting the track.
                val bytes = if (encoding == C.ENCODING_PCM_16BIT) 2 else if (encoding == C.ENCODING_PCM_24BIT) 3 else 4
                val pcm = ByteBuffer.allocateDirect(rate * 2 * bytes).order(ByteOrder.LITTLE_ENDIAN)
                for (i in 0 until rate) {
                    val value = .05 * kotlin.math.sin(2 * Math.PI * hz * i / rate)
                    repeat(2) {
                        when (encoding) {
                            C.ENCODING_PCM_16BIT -> pcm.putShort((value * 32768).toInt().toShort())
                            C.ENCODING_PCM_24BIT -> { val v = (value * 8388608).toInt(); pcm.put(v.toByte()); pcm.put((v shr 8).toByte()); pcm.put((v shr 16).toByte()) }
                            else -> pcm.putFloat(value.toFloat())
                        }
                    }
                }
                pcm.flip()
                assertTrue(sink.handleBuffer(pcm, 0, 1))
                assertEquals(rate * 2, output.size)
                fun rms(channel: Int): Double {
                    var energy = 0.0
                    for (i in rate / 2 until rate) { val v = output[i * 2 + channel].toDouble(); energy += v * v }
                    return kotlin.math.sqrt(energy / (rate - rate / 2))
                }
                return rms(0) to rms(1)
            }
            fun db(value: Double, base: Double) = 20 * kotlin.math.log10(value / base)
            val bypass = measure(AudioOptions())
            val cut = measure(AudioOptions(eqEnabled = true, preset = EqPreset.CUSTOM,
                customLevels = DspBands.frequencies.associateWith { if (it == 1000) -1200 else 0 }))
            val cutDb = db(cut.first, bypass.first)
            assertEquals("1 kHz EQ cut at output", -12.0, cutDb, .06)
            val preamp = measure(AudioOptions(eqEnabled = true, preampDb = -12f))
            val preampDb = db(preamp.first, bypass.first)
            assertEquals("Preamp must attenuate both channels", -12.0, preampDb, .01)
            assertEquals(preamp.first, preamp.second, 1e-8)
            val balance = measure(AudioOptions(balanceEnabled = true, leftGainDb = -6f, rightGainDb = -12f))
            val leftDb = db(balance.first, bypass.first); val rightDb = db(balance.second, bypass.second)
            assertEquals(-6.0, leftDb, .01); assertEquals(-12.0, rightDb, .01)
            val bassBase = measure(AudioOptions(), 63.0).first
            val bassLow = measure(AudioOptions(eqEnabled = true, preset = EqPreset.BASS), 63.0).first
            val bassMid = measure(AudioOptions(eqEnabled = true, preset = EqPreset.BASS)).first
            val contrast = db(bassLow, bassBase) - db(bassMid, bypass.first)
            assertTrue("Bass preset must alter tone despite automatic headroom", contrast > 4.0)
            val restored = measure(AudioOptions(preampDb = -12f, leftGainDb = -30f, rightGainDb = -30f))
            assertEquals("Disabled controls must bypass stored gains", 0.0, db(restored.first, bypass.first), .001)
            report.appendText("rate=" + rate + " encoding=" + encoding + " eq=" + cutDb + " preamp=" + preampDb + " left=" + leftDb + " right=" + rightDb + " bassContrast=" + contrast + " dB\n")
            effect.close()
        }
    }
}
