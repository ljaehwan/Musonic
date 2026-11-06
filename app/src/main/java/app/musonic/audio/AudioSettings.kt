package app.musonic.audio

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import org.json.JSONArray
import java.util.UUID

class AudioSettings private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("musonic_audio", Context.MODE_PRIVATE)
    init {
        if (!prefs.contains("quality")) {
            val legacy = context.getSharedPreferences("musonic_private", Context.MODE_PRIVATE).getBoolean("transcode", false)
            prefs.edit().putString("quality", if (legacy) StreamQuality.BALANCED.name else StreamQuality.ORIGINAL.name).apply()
        }
    }
    private fun levels(json: JSONObject) = json.keys().asSequence().mapNotNull { key ->
        key.toIntOrNull()?.takeIf { it > 0 }?.let { it to json.getInt(key).coerceIn(-1500, 1500) }
    }.toMap()
    private fun finite(key: String, default: Float, range: ClosedFloatingPointRange<Float>) =
        prefs.getFloat(key, default).takeIf { it.isFinite() }?.coerceIn(range) ?: default
    private fun read() = AudioOptions(
        quality = StreamQuality.entries.firstOrNull { it.name == prefs.getString("quality", null) } ?: StreamQuality.ORIGINAL,
        savedPresets = runCatching {
            val array = JSONArray(prefs.getString("saved_presets", "[]"))
            (0 until array.length()).map { index -> array.getJSONObject(index).let {
                SavedEqPreset(it.getString("id"), it.getString("name").take(40), levels(it.getJSONObject("levels")),
                    it.optDouble("preamp", 0.0).takeIf { db -> db.isFinite() }?.coerceIn(-12.0, 0.0)?.toFloat() ?: 0f)
            } }
        }.getOrDefault(emptyList()),
        selectedPresetId = prefs.getString("selected_preset", null),
        balanceEnabled = prefs.getBoolean("balance_enabled", false),
        leftGainDb = finite("left_gain", 0f, -30f..0f),
        rightGainDb = finite("right_gain", 0f, -30f..0f),
        loudnessEnabled = prefs.getBoolean("loudness_enabled", false),
        loudnessThreshold = finite("loudness_threshold", .6f, .1f..1f),
        eqEnabled = prefs.getBoolean("eq_enabled", false),
        preampDb = prefs.getFloat("preamp_db", 0f).takeIf { it.isFinite() }?.coerceIn(-12f, 0f) ?: 0f,
        preset = EqPreset.entries.firstOrNull { it.name == prefs.getString("eq_preset", null) } ?: EqPreset.FLAT,
        customLevels = runCatching { JSONObject(prefs.getString("eq_levels", "{}")!!).let { json ->
            json.keys().asSequence().mapNotNull { key -> key.toIntOrNull()?.takeIf { it > 0 }?.let { it to json.getInt(key).coerceIn(-1500, 1500) } }.toMap()
        } }.getOrDefault(emptyMap()),
    )
    private val mutable = MutableStateFlow(read())
    val state = mutable.asStateFlow()
    private fun save(value: AudioOptions) {
        val saved = JSONArray()
        value.savedPresets.forEach { preset -> saved.put(JSONObject().put("id", preset.id).put("name", preset.name)
            .put("levels", JSONObject(preset.levels.mapKeys { it.key.toString() })).put("preamp", preset.preampDb.toDouble())) }
        prefs.edit().putString("saved_presets", saved.toString()).putString("selected_preset", value.selectedPresetId)
            .putBoolean("balance_enabled", value.balanceEnabled).putFloat("left_gain", value.leftGainDb).putFloat("right_gain", value.rightGainDb)
            .putBoolean("loudness_enabled", value.loudnessEnabled).putFloat("loudness_threshold", value.loudnessThreshold).putString("quality", value.quality.name).putBoolean("eq_enabled", value.eqEnabled)
            .putFloat("preamp_db", value.preampDb).putString("eq_preset", value.preset.name).putString("eq_levels", JSONObject(value.customLevels.mapKeys { it.key.toString() }).toString()).apply()
        mutable.value = value
    }
    fun quality(value: StreamQuality) = save(state.value.copy(quality = value))
    fun preamp(value: Float) { if (value.isFinite()) save(state.value.copy(preampDb = value.coerceIn(-12f, 0f), selectedPresetId = null)) }
    fun enabled(value: Boolean) = save(state.value.copy(eqEnabled = value))
    fun preset(value: EqPreset, enable: Boolean = false) = save(state.value.copy(preset = value, selectedPresetId = null, eqEnabled = enable || state.value.eqEnabled))
    fun band(frequency: Int, level: Int, bands: List<EqBand>) {
        val current = state.value
        val curve = bands.associate { it.frequencyHz to current.level(it.frequencyHz) } + (frequency to level.coerceIn(-1500, 1500))
        save(current.copy(preset = EqPreset.CUSTOM, selectedPresetId = null, customLevels = curve))
    }
    fun reset() = save(state.value.copy(preset = EqPreset.FLAT, selectedPresetId = null, customLevels = emptyMap(), preampDb = 0f))
    fun custom(levels: Map<Int, Int>, enable: Boolean = false) = save(state.value.copy(preset = EqPreset.CUSTOM, selectedPresetId = null, eqEnabled = enable || state.value.eqEnabled,
        customLevels = levels.filterKeys { it > 0 }.mapValues { it.value.coerceIn(-1500, 1500) }))
    fun savePreset(name: String): String? {
        val trimmed = name.trim().take(40)
        if (trimmed.isBlank()) return null
        val current = state.value
        val preset = SavedEqPreset(UUID.randomUUID().toString(), trimmed,
            DspBands.frequencies.associateWith(current::level), current.preampDb)
        save(current.copy(savedPresets = current.savedPresets + preset, selectedPresetId = preset.id,
            preset = EqPreset.CUSTOM, customLevels = preset.levels))
        return preset.id
    }
    fun selectSaved(id: String, enable: Boolean = false) {
        val preset = state.value.savedPresets.firstOrNull { it.id == id } ?: return
        save(state.value.copy(selectedPresetId = id, preset = EqPreset.CUSTOM, eqEnabled = enable || state.value.eqEnabled, customLevels = preset.levels, preampDb = preset.preampDb))
    }
    fun deletePreset(id: String) = save(state.value.copy(savedPresets = state.value.savedPresets.filterNot { it.id == id },
        selectedPresetId = state.value.selectedPresetId?.takeUnless { it == id }))
    fun balance(enabled: Boolean) = save(state.value.copy(balanceEnabled = enabled))
    fun channel(left: Boolean, db: Float) {
        if (!db.isFinite()) return
        save(if (left) state.value.copy(leftGainDb = db.coerceIn(-30f, 0f)) else state.value.copy(rightGainDb = db.coerceIn(-30f, 0f)))
    }
    fun resetBalance() = save(state.value.copy(leftGainDb = 0f, rightGainDb = 0f))
    fun loudness(enabled: Boolean) = save(state.value.copy(loudnessEnabled = enabled))
    fun threshold(value: Float) { if (value.isFinite()) save(state.value.copy(loudnessThreshold = value.coerceIn(.1f, 1f))) }
    companion object {
        @Volatile private var instance: AudioSettings? = null
        fun get(context: Context): AudioSettings = instance ?: synchronized(this) {
            instance ?: AudioSettings(context.applicationContext).also { instance = it }
        }
        internal fun isolated(context: Context) = AudioSettings(context)
    }
}
