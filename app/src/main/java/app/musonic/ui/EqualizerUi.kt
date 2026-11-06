@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package app.musonic.ui

import app.musonic.R

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import app.musonic.audio.*
import java.util.Locale
import kotlin.math.roundToInt

@Composable
internal fun EqualizerPanel(options: AudioOptions, state: EqualizerState, settings: AudioSettings) {
    val strings = androidx.compose.ui.platform.LocalContext.current

    var presets by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf(false) }
    val levels = state.bands.map { options.level(it.frequencyHz).coerceIn(state.minimum, state.maximum) }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Surface(color = Grouped, contentColor = TextPrimary, shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text((options.presetTitle ?: strings.getString(options.preset.title)), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                        Text(strings.getString(R.string.dsp_bands, state.bands.size), color = Muted, fontSize = 12.sp)
                    }
                    Text(if (state.enabled) strings.getString(R.string.active) else strings.getString(R.string.off), color = if (state.enabled) Accent else Muted, fontSize = 12.sp)
                }
                EqualizerGraph(state, levels, enabled = options.eqEnabled)
            }
        }
        Surface(onClick = { presets = true }, color = Grouped, contentColor = TextPrimary,
            shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().testTag("preset-setting")) {
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Icon(Icons.Rounded.Tune, null, tint = Accent)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(strings.getString(R.string.preset_setting), fontWeight = FontWeight.SemiBold)
                    Text(options.presetTitle ?: strings.getString(options.preset.title), color = Muted, fontSize = 13.sp)
                }
                Icon(Icons.Rounded.ChevronRight, null, tint = Muted, modifier = Modifier.size(20.dp))
            }
        }
        Surface(color = Grouped, contentColor = TextPrimary, shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(strings.getString(R.string.output_level), fontWeight = FontWeight.SemiBold)
                    Text(String.format(Locale.US, "%.1f dB", options.preampDb), color = Accent, fontSize = 13.sp)
                }
                Slider(options.preampDb, { settings.preamp((it * 2).roundToInt() / 2f) }, valueRange = -12f..0f, enabled = options.eqEnabled,
                    modifier = Modifier.testTag("dsp-preamp").semantics { contentDescription = strings.getString(R.string.output_level) },
                    thumb = { Box(Modifier.size(18.dp).background(if (options.eqEnabled) Accent else Muted, RoundedCornerShape(50))) },
                    track = { SliderDefaults.Track(it, modifier = Modifier.height(4.dp), enabled = options.eqEnabled, thumbTrackGapSize = 0.dp, drawStopIndicator = null) })
                Text(strings.getString(if (options.eqEnabled) R.string.headroom_hint else R.string.equalizer_off_hint), color = Muted, fontSize = 12.sp)
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = settings::reset) { Text(strings.getString(R.string.reset), color = Muted) }
            Spacer(Modifier.weight(1f))
            FilledTonalButton(onClick = { editing = true }, modifier = Modifier.testTag("edit-equalizer"),
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = Accent.copy(alpha = .09f), contentColor = Accent),
                shape = RoundedCornerShape(14.dp)) {
                Icon(Icons.Rounded.Tune, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(strings.getString(R.string.preset_custom))
            }
        }
    }
    if (presets) PresetPicker(options, settings) { presets = false }
    if (editing) {
        // Drafts never affect playback until Apply; cancel/back leaves the original preset intact.
        var draft by rememberSaveable(state.bands.map { it.frequencyHz }, state.minimum, state.maximum) { mutableStateOf(ArrayList(levels)) }
        Dialog(onDismissRequest = { editing = false }) {
            Surface(color = CanvasColor, contentColor = TextPrimary, shape = RoundedCornerShape(28.dp)) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(strings.getString(R.string.custom_sound), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text(strings.getString(R.string.band_hint), color = Muted, fontSize = 13.sp)
                        if (!options.eqEnabled) Text(strings.getString(R.string.preset_enables_eq), color = Accent, fontSize = 12.sp)
                    }
                    EqualizerGraph(state, draft, enabled = true, edit = { index, value ->
                        draft = ArrayList(draft).also { it[index] = value }
                    })
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { draft = ArrayList(List(draft.size) { 0 }) }) { Text(strings.getString(R.string.flatten), color = Muted) }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { editing = false }) { Text(strings.getString(R.string.cancel), color = Muted) }
                        Button(onClick = {
                            settings.custom(state.bands.mapIndexed { i, band -> band.frequencyHz to draft[i] }.toMap(), enable = true)
                            editing = false
                        }, shape = RoundedCornerShape(14.dp), modifier = Modifier.testTag("apply-equalizer")) { Text(strings.getString(R.string.apply)) }
                    }
                }
            }
        }
    }
}

/** The curve visualizes band gains; it is not a measurement of the device's frequency response. */
@Composable
internal fun EqualizerGraph(state: EqualizerState, levels: List<Int>, enabled: Boolean, edit: ((Int, Int) -> Unit)? = null) {
    val strings = androidx.compose.ui.platform.LocalContext.current

    val gridColor = Separator
    val tint = if (enabled) Accent else Muted
    val range = (state.maximum - state.minimum).coerceAtLeast(1).toFloat()
    val plotHeight = if (edit == null) 180.dp else 250.dp
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Column(Modifier.width(28.dp).padding(top = if (edit == null) 0.dp else 28.dp).height(plotHeight), verticalArrangement = Arrangement.SpaceBetween) {
            listOf(state.maximum, (state.maximum + state.minimum) / 2, state.minimum).forEach {
                Text(String.format(Locale.US, "%+.0f", it / 100f), color = Muted.copy(alpha = .75f), fontSize = 10.sp)
            }
        }
        BoxWithConstraints(Modifier.weight(1f)) {
            val plotWidth = if (edit == null) maxWidth else maxOf(maxWidth, (state.bands.size * 48).dp)
            Column(Modifier.horizontalScroll(rememberScrollState())) {
                if (edit != null) Row(Modifier.width(plotWidth).height(28.dp)) {
                    levels.forEach { Text(String.format(Locale.US, "%+.1f", it / 100f), color = tint, fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)) }
                }
                Box(Modifier.width(plotWidth).height(plotHeight)) {
                    Canvas(Modifier.fillMaxSize()) {
                        val pad = 10.dp.toPx()
                        val height = size.height - 2 * pad
                        fun y(level: Int) = pad + height * (state.maximum - level.coerceIn(state.minimum, state.maximum)) / range
                        val zero = y(0)
                        listOf(state.maximum, state.maximum / 2, 0, state.minimum / 2, state.minimum).distinct().forEach { level ->
                            drawLine(gridColor.copy(alpha = if (level == 0) .85f else .4f), Offset(0f, y(level)), Offset(size.width, y(level)), 1.dp.toPx())
                        }
                        val bandWidth = size.width / levels.size.coerceAtLeast(1)
                        val points = levels.mapIndexed { i, level -> Offset(bandWidth * (i + .5f), y(level)) }
                        if (points.isNotEmpty()) {
                            val curve = Path().apply {
                                moveTo(0f, points.first().y)
                                lineTo(points.first().x, points.first().y)
                                points.zipWithNext().forEach { (a, b) ->
                                    val mid = (a.x + b.x) / 2
                                    cubicTo(mid, a.y, mid, b.y, b.x, b.y)
                                }
                                lineTo(size.width, points.last().y)
                            }
                            val fill = Path().apply { addPath(curve); lineTo(size.width, size.height); lineTo(0f, size.height); close() }
                            drawPath(fill, Brush.verticalGradient(listOf(tint.copy(alpha = .16f), tint.copy(alpha = .015f))))
                            if (edit != null) points.forEach { point ->
                                drawLine(tint.copy(alpha = .12f), Offset(point.x, pad), Offset(point.x, size.height - pad), 3.dp.toPx(), StrokeCap.Round)
                                drawLine(tint.copy(alpha = .35f), Offset(point.x, zero), point, 3.dp.toPx(), StrokeCap.Round)
                            }
                            drawPath(curve, tint, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
                            points.forEach {
                                drawCircle(tint, (if (edit == null) 4 else 9).dp.toPx(), it)
                                if (edit != null) drawCircle(Color.White, 3.dp.toPx(), it)
                            }
                        }
                    }
                    if (edit != null) Row(Modifier.fillMaxSize()) {
                        state.bands.forEachIndexed { index, band ->
                            val onEdit by rememberUpdatedState(edit)
                            Box(Modifier.weight(1f).fillMaxHeight().testTag("eq-band-$index")
                                .semantics {
                                    contentDescription = strings.getString(R.string.adjust_frequency, frequencyLabel(band.frequencyHz))
                                    stateDescription = String.format(Locale.US, "%+.1f dB", levels[index] / 100f)
                                    progressBarRangeInfo = ProgressBarRangeInfo(levels[index] / 100f, state.minimum / 100f..state.maximum / 100f)
                                    setProgress { value ->
                                        if (!value.isFinite()) false else {
                                            onEdit(index, ((value * 2).roundToInt() * 50).coerceIn(state.minimum, state.maximum)); true
                                        }
                                    }
                                }
                                .pointerInput(index, state.minimum, state.maximum) {
                                    val pad = 10.dp.toPx()
                                    fun update(y: Float) {
                                        val value = state.maximum - ((y - pad) / (size.height - 2 * pad).coerceAtLeast(1f)).coerceIn(0f, 1f) * range
                                        onEdit(index, ((value / 50).roundToInt() * 50).coerceIn(state.minimum, state.maximum))
                                    }
                                    detectVerticalDragGestures(onDragStart = { update(it.y) }) { change, _ -> change.consume(); update(change.position.y) }
                                }
                                .pointerInput(index, state.minimum, state.maximum) {
                                    detectTapGestures { point ->
                                        val pad = 10.dp.toPx()
                                        val value = state.maximum - ((point.y - pad) / (size.height - 2 * pad).coerceAtLeast(1f)).coerceIn(0f, 1f) * range
                                        onEdit(index, ((value / 50).roundToInt() * 50).coerceIn(state.minimum, state.maximum))
                                    }
                                })
                        }
                    }
                }
                Row(Modifier.width(plotWidth).padding(top = 10.dp)) {
                    state.bands.forEach { Text(frequencyLabel(it.frequencyHz), modifier = Modifier.weight(1f), textAlign = TextAlign.Center, color = Muted, fontSize = 10.sp) }
                }
            }
        }
    }
}

private fun frequencyLabel(hz: Int) = if (hz >= 1000) "${(hz / 1000.0).toString().removeSuffix(".0")}k" else "$hz"
