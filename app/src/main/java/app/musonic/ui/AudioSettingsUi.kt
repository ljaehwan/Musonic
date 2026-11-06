@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
package app.musonic.ui

import app.musonic.R

import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.musonic.MusicViewModel
import app.musonic.audio.*

@Composable
fun AudioSettingsCard(vm: MusicViewModel) {
    val strings = androidx.compose.ui.platform.LocalContext.current

    val options by vm.audioSettings.state.collectAsStateWithLifecycle()
    val equalizer by vm.equalizerState.collectAsStateWithLifecycle()
    val music by vm.state.collectAsStateWithLifecycle()
    var sheet by rememberSaveable { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(strings.getString(R.string.sound), style = MaterialTheme.typography.titleLarge)
        Surface(color = Grouped, contentColor = TextPrimary, shape = RoundedCornerShape(20.dp)) {
            Column {
                AudioSettingRow(strings.getString(R.string.audio_quality), if (options.quality == StreamQuality.ORIGINAL) strings.getString(R.string.original_unchanged) else strings.getString(options.quality.detail),
                    Icons.Rounded.GraphicEq) { sheet = "quality" }
                HorizontalDivider(Modifier.padding(start = 60.dp), color = Separator.copy(alpha = .6f))
                AudioSettingRow(strings.getString(R.string.equalizer), if (options.eqEnabled) (options.presetTitle ?: strings.getString(options.preset.title)) else strings.getString(R.string.off),
                    Icons.Rounded.Tune, options.eqEnabled, vm.audioSettings::enabled, "quick-equalizer") { sheet = "equalizer" }
                HorizontalDivider(Modifier.padding(start = 60.dp), color = Separator.copy(alpha = .6f))
                AudioSettingRow(strings.getString(R.string.channel_balance), if (options.balanceEnabled) strings.getString(R.string.balance_active) else strings.getString(R.string.off), Icons.Rounded.Headphones, options.balanceEnabled, vm.audioSettings::balance, "quick-balance") { sheet = "balance" }
                HorizontalDivider(Modifier.padding(start = 60.dp), color = Separator.copy(alpha = .6f))
                AudioSettingRow(strings.getString(R.string.loudness), if (options.loudnessEnabled) strings.getString(R.string.loudness_detail) else strings.getString(R.string.off), Icons.Rounded.Waves, options.loudnessEnabled, vm.audioSettings::loudness, "quick-loudness") { sheet = "loudness" }
            }
        }
    }
    sheet?.let { selected ->
        ModalBottomSheet(onDismissRequest = { sheet = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = CanvasColor) {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 680.dp).testTag("audio-settings-sheet"),
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                stickyHeader { Surface(color = CanvasColor, contentColor = TextPrimary) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(when (selected) { "quality" -> strings.getString(R.string.audio_quality); "balance" -> strings.getString(R.string.channel_balance); "loudness" -> strings.getString(R.string.loudness); else -> strings.getString(R.string.equalizer) }, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { sheet = null }) { Text(strings.getString(R.string.done)) }
                } } }
                if (selected == "quality") {
                    item { Text(strings.getString(R.string.quality_hint), color = Muted) }
                    item { Surface(color = Grouped, contentColor = TextPrimary, shape = RoundedCornerShape(20.dp)) { Column {
                        StreamQuality.entries.forEachIndexed { index, quality ->
                            Surface(color = Grouped, contentColor = TextPrimary, modifier = Modifier.testTag("quality-${quality.name}").selectable(selected = options.quality == quality, role = Role.RadioButton, onClick = { vm.audioSettings.quality(quality) })) {
                                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Text(strings.getString(quality.title), fontWeight = FontWeight.SemiBold)
                                        Text(strings.getString(quality.detail), color = Muted, fontSize = 13.sp)
                                    }
                                    RadioButton(selected = options.quality == quality, onClick = null)
                                }
                            }
                            if (index < StreamQuality.entries.lastIndex) HorizontalDivider(Modifier.padding(horizontal = 18.dp), color = Separator.copy(alpha = .6f))
                        }
                    } } }
                    item { Text(strings.getString(R.string.quality_explanation), color = Muted, style = MaterialTheme.typography.bodyMedium) }
                    if (music.current != null) item {
                        Button(onClick = { vm.replayWithQuality(); sheet = null }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) {
                            Text(strings.getString(R.string.replay_quality))
                        }
                        Text(strings.getString(R.string.replay_hint), color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                } else if (selected == "balance") {
                    item { ChannelBalancePanel(options, vm.audioSettings) }
                } else if (selected == "loudness") {
                    item { LoudnessPanel(options, vm.audioSettings) }
                } else {
                    item { Surface(color = Grouped, contentColor = TextPrimary, shape = RoundedCornerShape(20.dp)) {
                        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(strings.getString(R.string.equalizer_enable), fontWeight = FontWeight.SemiBold)
                                Text(if (options.eqEnabled && equalizer.enabled) strings.getString(R.string.equalizer_live) else strings.getString(R.string.equalizer_hint), color = Muted, fontSize = 13.sp)
                            }
                            Switch(options.eqEnabled, vm.audioSettings::enabled,
                                modifier = Modifier.testTag("equalizer-switch").semantics { contentDescription = strings.getString(R.string.equalizer_enable) })
                        }
                    } }
                    equalizer.message?.let { message -> item { Text(strings.getString(message), color = Muted, style = MaterialTheme.typography.bodyMedium) } }
                    if (equalizer.available) {
                        item { EqualizerPanel(options, equalizer, vm.audioSettings) }
                        item { Text(if (options.eqEnabled) strings.getString(R.string.equalizer_distortion) else strings.getString(R.string.equalizer_off_hint), color = Muted, style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioSettingRow(title: String, detail: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean? = null, toggle: ((Boolean) -> Unit)? = null, tag: String = "", open: () -> Unit) {
    val strings = androidx.compose.ui.platform.LocalContext.current

    // Sibling targets keep changing the switch separate from opening the detail sheet.
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Surface(onClick = open, color = Grouped, contentColor = TextPrimary, modifier = Modifier.weight(1f)) {
            Row(Modifier.padding(start = 18.dp, end = if (checked == null) 18.dp else 8.dp, top = 18.dp, bottom = 18.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Icon(icon, null, tint = Accent, modifier = Modifier.size(26.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, fontWeight = FontWeight.SemiBold)
                    Text(detail, color = Muted, fontSize = 13.sp)
                }
                if (checked == null) Icon(Icons.Rounded.ChevronRight, null, tint = Muted, modifier = Modifier.size(20.dp))
            }
        }
        if (checked != null && toggle != null) Switch(checked, toggle,
            modifier = Modifier.padding(end = 18.dp).testTag(tag).semantics { contentDescription = strings.getString(R.string.enable_named, title) })
    }
}
