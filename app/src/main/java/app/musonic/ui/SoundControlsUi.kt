@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package app.musonic.ui

import app.musonic.R

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.musonic.audio.*
import java.util.Locale
import kotlin.math.roundToInt

@Composable
internal fun ChannelBalancePanel(options: AudioOptions, settings: AudioSettings) {
    val strings = androidx.compose.ui.platform.LocalContext.current

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SoundToggle(strings.getString(R.string.balance_volumes), strings.getString(R.string.balance_hint), options.balanceEnabled, settings::balance, "balance-switch")
        SoundCard {
            SoundSlider(strings.getString(R.string.left), options.leftGainDb, -30f..0f, db(options.leftGainDb), "left-gain", options.balanceEnabled) { settings.channel(true, it) }
            HorizontalDivider(color = Separator.copy(alpha = .5f))
            SoundSlider(strings.getString(R.string.right), options.rightGainDb, -30f..0f, db(options.rightGainDb), "right-gain", options.balanceEnabled) { settings.channel(false, it) }
            TextButton(onClick = settings::resetBalance, modifier = Modifier.align(Alignment.End)) { Text(strings.getString(R.string.center_balance)) }
        }
        Text(strings.getString(R.string.balance_explanation), color = Muted, fontSize = 13.sp)
    }
}

@Composable
internal fun LoudnessPanel(options: AudioOptions, settings: AudioSettings) {
    val strings = androidx.compose.ui.platform.LocalContext.current

    val volume by PlaybackEqualizer.volumeState.collectAsStateWithLifecycle()
    val gains = if (options.loudnessEnabled && volume != null) LoudnessContour.gains(volume!!, options.loudnessThreshold.toDouble()) else List(10) { 0.0 }
    val levels = gains.map { (it * 100).roundToInt() }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SoundToggle(strings.getString(R.string.low_volume_correction), strings.getString(R.string.low_volume_detail), options.loudnessEnabled, settings::loudness, "loudness-switch")
        SoundCard {
            SoundSlider(strings.getString(R.string.correction_threshold), options.loudnessThreshold * 100, 10f..100f,
                "${(options.loudnessThreshold * 100).roundToInt()}%", "loudness-threshold", options.loudnessEnabled, step = 5f) { settings.threshold(it / 100) }
            Text(strings.getString(R.string.threshold_hint), color = Muted, fontSize = 12.sp)
        }
        Surface(color = Grouped, contentColor = TextPrimary, shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(if (volume == null) strings.getString(R.string.volume_unavailable) else strings.getString(R.string.phone_volume, (volume!! * 100).roundToInt()), fontWeight = FontWeight.SemiBold)
                    Text(when {
                        !options.loudnessEnabled -> strings.getString(R.string.correction_off)
                        volume == null -> strings.getString(R.string.correction_paused)
                        gains.all { it == 0.0 } -> strings.getString(R.string.above_threshold)
                        else -> strings.getString(R.string.bass_compensating, db(gains.max().toFloat()))
                    }, color = Accent, fontSize = 13.sp)
                }
                EqualizerGraph(EqualizerState(available = true, bands = DspBands.frequencies.map { EqBand(it, 0) }, minimum = -600, maximum = 600), levels,
                    enabled = options.loudnessEnabled && volume != null)
                Text(strings.getString(R.string.band_correction), color = Muted, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 20.dp))
            }
        }
        Text(strings.getString(R.string.loudness_explanation), color = Muted, fontSize = 13.sp)
        Text(strings.getString(R.string.headroom_volume_hint), color = Muted, fontSize = 13.sp)
    }
}

@Composable
private fun SoundToggle(title: String, detail: String, checked: Boolean, change: (Boolean) -> Unit, tag: String) {
    val strings = androidx.compose.ui.platform.LocalContext.current

    SoundCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(detail, color = Muted, fontSize = 12.sp)
            }
            Switch(checked, change, modifier = Modifier.testTag(tag).semantics { contentDescription = title })
        }
    }
}
@Composable
private fun SoundCard(content: @Composable ColumnScope.() -> Unit) {
    val strings = androidx.compose.ui.platform.LocalContext.current

    Surface(color = Grouped, contentColor = TextPrimary, shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}
@Composable
private fun SoundSlider(title: String, value: Float, range: ClosedFloatingPointRange<Float>, label: String, tag: String,
    enabled: Boolean, step: Float = .5f, change: (Float) -> Unit) {
    val strings = androidx.compose.ui.platform.LocalContext.current

    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(label, color = if (enabled) Accent else Muted, fontSize = 13.sp)
        }
        Slider(value, { change((it / step).roundToInt() * step) }, enabled = enabled, valueRange = range,
            modifier = Modifier.testTag(tag).semantics { contentDescription = title },
            thumb = { Box(Modifier.size(18.dp).background(if (enabled) Accent else Muted, RoundedCornerShape(50))) },
            track = { SliderDefaults.Track(it, modifier = Modifier.height(4.dp), enabled = enabled, thumbTrackGapSize = 0.dp, drawStopIndicator = null) })
    }
}
private fun db(value: Float) = String.format(Locale.US, "%+.1f dB", value)
