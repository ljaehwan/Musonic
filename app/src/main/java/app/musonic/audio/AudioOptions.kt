package app.musonic.audio

import app.musonic.R
import app.musonic.settings.*

import kotlin.math.ln
import kotlin.math.roundToInt

enum class StreamQuality(val title: Int, val detail: Int, val kbps: Int?) {
    ORIGINAL(R.string.quality_original, R.string.quality_original_detail, null),
    HIGH(R.string.quality_high, R.string.quality_mp3_320, 320),
    BALANCED(R.string.quality_balanced, R.string.quality_mp3_192, 192),
    SAVER(R.string.quality_saver, R.string.quality_mp3_128, 128),
    LOW(R.string.quality_low, R.string.quality_mp3_64, 64);
    fun parameters(): Map<String, String> = if (kbps == null) mapOf("format" to "raw")
        else mapOf("format" to "mp3", "maxBitRate" to kbps.toString())
}

enum class EqPreset(val title: Int, val gains: List<Int>) {
    FLAT(R.string.preset_flat, listOf(0, 0, 0, 0, 0)),
    BASS(R.string.preset_bass, listOf(500, 300, 0, -100, -100)),
    VOCAL(R.string.preset_vocal, listOf(-200, -100, 300, 300, 0)),
    TREBLE(R.string.preset_treble, listOf(-100, -100, 0, 300, 500)),
    DARK(R.string.preset_dark, listOf(200, 150, 50, -150, -300)),
    BRIGHT(R.string.preset_bright, listOf(-200, -100, 0, 200, 350)),
    BASS_CUT(R.string.preset_bass_cut, listOf(-500, -300, 0, 0, 0)),
    TREBLE_CUT(R.string.preset_treble_cut, listOf(0, 0, 0, -300, -500)),
    M_SHAPE(R.string.preset_m, listOf(0, 100, 300, 150, -100, -200, 150, 300, 100, 0)),
    U_SHAPE(R.string.preset_u, listOf(400, 300, 150, 0, -150, -150, 0, 150, 300, 400)),
    V_SHAPE(R.string.preset_v, listOf(500, 350, 200, 50, -100, -150, 0, 200, 350, 500)),
    W_SHAPE(R.string.preset_w, listOf(350, 100, -200, -100, 200, 300, 0, -200, 100, 350)),
    CUSTOM(R.string.preset_custom, emptyList());
}

data class SavedEqPreset(val id: String, val name: String, val levels: Map<Int, Int>, val preampDb: Float)

/** A gentle volume-index-based contour, not a calibrated equal-loudness/SPL model. */
object LoudnessContour {
    fun gains(volume: Double, threshold: Double): List<Double> {
        val safeVolume = if (volume.isFinite()) volume.coerceIn(0.0, 1.0) else 1.0
        val safeThreshold = if (threshold.isFinite()) threshold.coerceIn(.1, 1.0) else .6
        val amount = ((safeThreshold - safeVolume) / safeThreshold).coerceIn(0.0, 1.0)
        return listOf(6.0, 5.0, 3.0, 1.0, 0.0, 0.0, 0.0, .5, 2.0, 2.5).map { it * amount }
    }
}

data class AudioOptions(
    val quality: StreamQuality = StreamQuality.ORIGINAL,
    val eqEnabled: Boolean = false,
    val preampDb: Float = 0f,
    val preset: EqPreset = EqPreset.FLAT,
    val customLevels: Map<Int, Int> = emptyMap(),
    val savedPresets: List<SavedEqPreset> = emptyList(),
    val selectedPresetId: String? = null,
    val balanceEnabled: Boolean = false,
    val leftGainDb: Float = 0f,
    val rightGainDb: Float = 0f,
    val loudnessEnabled: Boolean = false,
    val loudnessThreshold: Float = .6f,
) {
    val presetTitle: String? get() = savedPresets.firstOrNull { it.id == selectedPresetId }?.name
    fun level(frequencyHz: Int): Int {
        val curve = if (preset == EqPreset.CUSTOM) customLevels else
            (if (preset.gains.size == 10) DspBands.frequencies else listOf(60, 230, 910, 3600, 14000)).zip(preset.gains).toMap()
        return interpolateGain(curve, frequencyHz)
    }
}

/** Gains are millibels; interpolate on a logarithmic frequency axis for different device bands. */
fun interpolateGain(curve: Map<Int, Int>, frequencyHz: Int): Int {
    val points = curve.filterKeys { it > 0 }.toSortedMap().entries.toList()
    if (points.isEmpty()) return 0
    if (frequencyHz <= points.first().key) return points.first().value
    if (frequencyHz >= points.last().key) return points.last().value
    val (left, right) = points.zipWithNext().first { frequencyHz in it.first.key..it.second.key }
    val mix = ln(frequencyHz.toDouble() / left.key) / ln(right.key.toDouble() / left.key)
    return (left.value + (right.value - left.value) * mix).roundToInt()
}

data class EqBand(val frequencyHz: Int, val level: Int)
data class EqualizerState(
    val available: Boolean = false,
    val enabled: Boolean = false,
    val bands: List<EqBand> = emptyList(),
    val minimum: Int = -1500,
    val maximum: Int = 1500,
    val message: Int? = R.string.audio_preparing,
)
