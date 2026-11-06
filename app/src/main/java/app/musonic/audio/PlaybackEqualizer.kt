package app.musonic.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Service-owned software DSP. No global or device audiofx Equalizer is attached. */
class PlaybackEqualizer(private val publish: (EqualizerState) -> Unit = { runtime.value = it }) {
    val engine = DspEngine()
    private var closed = false
    private var options = AudioOptions()
    private var volume: Double? = null
    fun updateVolume(fraction: Double?) {
        val value = fraction?.takeIf { it.isFinite() }?.coerceIn(0.0, 1.0)
        if (closed || volume == value) return
        volume = value
        musicVolume.value = value
        apply(options)
    }
    init { apply(AudioOptions()) }
    fun apply(value: AudioOptions) {
        if (closed) return
        options = value
        val bands = DspBands.frequencies.map { EqBand(it, value.level(it).coerceIn(-1500, 1500)) }
        engine.parameters = DspParameters(value.eqEnabled, bands.map { it.level / 100.0 }, value.preampDb.toDouble(),
            value.balanceEnabled, value.leftGainDb.toDouble(), value.rightGainDb.toDouble(),
            if (value.loudnessEnabled && volume != null) LoudnessContour.gains(volume!!, value.loudnessThreshold.toDouble()) else emptyList())
        publish(EqualizerState(true, value.eqEnabled, bands, -1500, 1500, null))
    }
    fun close() { closed = true; engine.parameters = DspParameters(); publish(EqualizerState()); musicVolume.value = null }
    companion object {
        private val musicVolume = MutableStateFlow<Double?>(null)
        val volumeState = musicVolume.asStateFlow()
        private val runtime = MutableStateFlow(EqualizerState())
        val state = runtime.asStateFlow()
    }
}
