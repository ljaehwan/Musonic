package app.musonic.audio

import kotlin.math.*

/** Ten octave-spaced peaking filters. Coefficients and per-channel history use Double precision. */
object DspBands { val frequencies = listOf(31, 63, 125, 250, 500, 1000, 2000, 4000, 8000, 16000) }

data class DspParameters(
    val enabled: Boolean = false,
    val gains: List<Double> = List(10) { 0.0 },
    val preampDb: Double = 0.0,
    val balanceEnabled: Boolean = false,
    val leftGainDb: Double = 0.0,
    val rightGainDb: Double = 0.0,
    val loudnessGains: List<Double> = emptyList(),
) {
    val filtering: Boolean get() = enabled || loudnessGains.any { it.isFinite() && it != 0.0 }
    val processing: Boolean get() = filtering || balanceEnabled
}
private fun Double.safeDb(min: Double, max: Double) = if (isFinite()) coerceIn(min, max) else 0.0

internal data class Biquad(val b0: Double, val b1: Double, val b2: Double, val a1: Double, val a2: Double) {
    fun magnitude(hz: Double, rate: Int): Double {
        val w = 2 * PI * hz / rate
        val nr = b0 + b1 * cos(w) + b2 * cos(2 * w)
        val ni = -b1 * sin(w) - b2 * sin(2 * w)
        val dr = 1 + a1 * cos(w) + a2 * cos(2 * w)
        val di = -a1 * sin(w) - a2 * sin(2 * w)
        return sqrt((nr * nr + ni * ni) / (dr * dr + di * di))
    }
    companion object {
        // RBJ peaking-EQ formula, Q = sqrt(2), normalized a0.
        fun peak(hz: Int, db: Double, rate: Int): Biquad {
            if (hz >= rate * .475 || abs(db) < .000001) return Biquad(1.0, 0.0, 0.0, 0.0, 0.0)
            val a = 10.0.pow(db / 40)
            val w = 2 * PI * hz / rate
            val alpha = sin(w) / (2 * sqrt(2.0))
            val a0 = 1 + alpha / a
            return Biquad((1 + alpha * a) / a0, -2 * cos(w) / a0, (1 - alpha * a) / a0,
                -2 * cos(w) / a0, (1 - alpha / a) / a0)
        }
    }
}

class DspEngine {
    @Volatile var parameters = DspParameters()
    private var rate = 48000
    private var channels = 2
    private var applied = DspParameters()
    private var active = Bank(applied, rate, channels)
    private var previous: Bank? = null
    private var fade = 0
    private var fadeLength = 2400
    private var limiter = 1.0
    private var release = exp(-1.0 / (rate * .08))
    private var frame = DoubleArray(channels)

    fun configure(sampleRate: Int, channelCount: Int) {
        require(sampleRate > 0 && channelCount in 1..32)
        rate = sampleRate; channels = channelCount
        fadeLength = (rate * .05).roundToInt().coerceAtLeast(1)
        release = exp(-1.0 / (rate * .08))
        frame = DoubleArray(channels)
        reset()
    }
    fun reset() {
        applied = parameters
        active = Bank(applied, rate, channels); previous = null; fade = 0; limiter = 1.0
    }
    /** Interleaved, complete frames only. No per-frame allocations, no resampling. */
    fun process(samples: DoubleArray, count: Int = samples.size) {
        require(count % channels == 0 && count <= samples.size)
        val next = parameters
        if (previous == null && next != applied) {
            previous = active; active = Bank(next, rate, channels); applied = next; fade = 0
        }
        val processing = applied.processing
        var offset = 0
        while (offset < count) {
            val old = previous
            val mix = if (old == null) 1.0 else (fade + 1).toDouble() / fadeLength
            var peak = 0.0
            for (channel in 0 until channels) {
                val input = samples[offset + channel].takeIf { it.isFinite() } ?: 0.0
                val output = active.sample(input, channel)
                frame[channel] = if (old == null) output else old.sample(input, channel) * (1 - mix) + output * mix
                peak = max(peak, abs(frame[channel]))
            }
            val protect = processing || old != null
            val target = if (protect && peak > .98) .98 / peak else 1.0
            limiter = if (target < limiter) target else 1 - (1 - limiter) * release
            for (channel in 0 until channels) {
                val value = frame[channel] * (if (protect) limiter else 1.0)
                samples[offset + channel] = if (value.isFinite()) value.coerceIn(-1.0, 1.0) else 0.0
            }
            if (old != null && ++fade >= fadeLength) previous = null
            offset += channels
        }
    }

    private class Bank(val settings: DspParameters, rate: Int, private val channels: Int) {
        private val filters = DspBands.frequencies.mapIndexed { i, hz -> Biquad.peak(hz,
            (if (settings.enabled) (settings.gains.getOrNull(i) ?: 0.0).safeDb(-15.0, 15.0) else 0.0) +
                (settings.loudnessGains.getOrNull(i) ?: 0.0).safeDb(0.0, 6.0), rate) }
        private val z1 = DoubleArray(filters.size * channels)
        private val z2 = DoubleArray(filters.size * channels)
        private val gain: Double
        private val filtering = settings.filtering
        private val left = if (settings.balanceEnabled) 10.0.pow(settings.leftGainDb.safeDb(-30.0, 0.0) / 20) else 1.0
        private val right = if (settings.balanceEnabled) 10.0.pow(settings.rightGainDb.safeDb(-30.0, 0.0) / 20) else 1.0
        init {
            // Estimate the maximum of the combined response, not just the largest slider.
            var maximum = 1.0
            if (filtering) for (i in 0..1024) {
                val hz = 10.0 * (rate * .499 / 10).pow(i / 1024.0)
                var response = 1.0
                filters.forEach { response *= it.magnitude(hz, rate) }
                maximum = max(maximum, response)
            }
            gain = (if (settings.enabled) 10.0.pow(settings.preampDb.safeDb(-12.0, 0.0) / 20) else 1.0) / maximum
        }
        fun sample(input: Double, channel: Int): Double {
            var value = input * gain
            if (filtering) filters.forEachIndexed { i, f ->
                val index = channel * filters.size + i
                val output = f.b0 * value + z1[index]
                z1[index] = f.b1 * value - f.a1 * output + z2[index]
                z2[index] = f.b2 * value - f.a2 * output
                value = output
            }
            return value * (if (channels < 2) 1.0 else when (channel) { 0 -> left; 1 -> right; else -> 1.0 })
        }
    }
}
