package app.musonic.audio

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.AudioOffloadSupport
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Processes decoded PCM before DefaultAudioSink can quantize it. Always decodes compressed audio. */
@androidx.annotation.OptIn(UnstableApi::class)
class DspAudioSink(sink: AudioSink, private val engine: DspEngine) : ForwardingAudioSink(sink) {
    private var encoding = C.ENCODING_INVALID
    private var channels = 0
    private var sampleBytes = 0
    private var pending: ByteBuffer? = null
    private var input: ByteBuffer? = null
    private var output = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())
    private var samples = DoubleArray(0)
    private fun bytes(encoding: Int) = when (encoding) {
        C.ENCODING_PCM_8BIT -> 1
        C.ENCODING_PCM_16BIT, C.ENCODING_PCM_16BIT_BIG_ENDIAN -> 2
        C.ENCODING_PCM_24BIT, C.ENCODING_PCM_24BIT_BIG_ENDIAN -> 3
        C.ENCODING_PCM_32BIT, C.ENCODING_PCM_32BIT_BIG_ENDIAN, C.ENCODING_PCM_FLOAT -> 4
        else -> 0
    }
    override fun supportsFormat(format: Format) = getFormatSupport(format) != AudioSink.SINK_FORMAT_UNSUPPORTED
    override fun getFormatSupport(format: Format): Int = if (format.sampleMimeType == "audio/raw" && bytes(format.pcmEncoding) > 0)
        super.getFormatSupport(format.buildUpon().setPcmEncoding(C.ENCODING_PCM_FLOAT).build()) else AudioSink.SINK_FORMAT_UNSUPPORTED
    override fun getFormatOffloadSupport(format: Format): AudioOffloadSupport = AudioOffloadSupport.DEFAULT_UNSUPPORTED
    override fun configure(inputFormat: Format, specifiedBufferSize: Int, outputChannels: IntArray?) {
        if (inputFormat.sampleMimeType != "audio/raw" || bytes(inputFormat.pcmEncoding) == 0)
            throw AudioSink.ConfigurationException("Unsupported PCM format", inputFormat)
        encoding = inputFormat.pcmEncoding; channels = inputFormat.channelCount; sampleBytes = bytes(encoding)
        engine.configure(inputFormat.sampleRate, channels)
        pending = null; input = null
        super.configure(inputFormat.buildUpon().setPcmEncoding(C.ENCODING_PCM_FLOAT).build(), specifiedBufferSize, outputChannels)
    }
    override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean {
        if (pending == null) {
            if (!buffer.hasRemaining()) return true
            check(buffer.remaining() % (sampleBytes * channels) == 0) { "Incomplete PCM frame" }
            val count = buffer.remaining() / sampleBytes
            if (samples.size < count) samples = DoubleArray(count)
            val big = encoding == C.ENCODING_PCM_16BIT_BIG_ENDIAN || encoding == C.ENCODING_PCM_24BIT_BIG_ENDIAN || encoding == C.ENCODING_PCM_32BIT_BIG_ENDIAN
            val source = buffer.duplicate().order(if (big) ByteOrder.BIG_ENDIAN else ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until count) samples[i] = when (sampleBytes) {
                1 -> ((source.get().toInt() and 255) - 128) / 128.0
                2 -> source.short / 32768.0
                3 -> {
                    val a = source.get().toInt() and 255; val b = source.get().toInt() and 255; val c = source.get().toInt() and 255
                    val raw = if (big) (a shl 16) or (b shl 8) or c else (c shl 16) or (b shl 8) or a
                    ((raw shl 8) shr 8) / 8388608.0
                }
                else -> if (encoding == C.ENCODING_PCM_FLOAT) source.float.toDouble() else source.int / 2147483648.0
            }
            engine.process(samples, count)
            if (output.capacity() < count * 4) output = ByteBuffer.allocateDirect(count * 4).order(ByteOrder.nativeOrder())
            output.clear()
            for (i in 0 until count) output.putFloat(samples[i].toFloat())
            output.flip(); pending = output; input = buffer
        }
        check(input === buffer) { "PCM input changed before drain" }
        if (!super.handleBuffer(pending!!, presentationTimeUs, encodedAccessUnitCount)) return false
        buffer.position(buffer.limit()); pending = null; input = null
        return true
    }
    override fun flush() { super.flush(); pending = null; input = null; engine.reset() }
    override fun reset() { super.reset(); pending = null; input = null; engine.reset() }
}
