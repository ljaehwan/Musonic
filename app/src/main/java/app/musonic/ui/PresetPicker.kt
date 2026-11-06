package app.musonic.ui

import app.musonic.R

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import app.musonic.audio.*

@Composable
internal fun PresetPicker(options: AudioOptions, settings: AudioSettings, dismiss: () -> Unit) {
    val strings = androidx.compose.ui.platform.LocalContext.current

    var selected by rememberSaveable { mutableStateOf(options.selectedPresetId ?: options.preset.name) }
    var naming by rememberSaveable { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<SavedEqPreset?>(null) }
    Dialog(onDismissRequest = dismiss) {
        Surface(color = CanvasColor, contentColor = TextPrimary, shape = RoundedCornerShape(28.dp)) {
            Column(Modifier.padding(20.dp).heightIn(max = 610.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(strings.getString(R.string.preset_setting), fontSize = 23.sp, fontWeight = FontWeight.Bold)
                if (!options.eqEnabled) Text(strings.getString(R.string.preset_enables_eq), color = Muted, fontSize = 13.sp)
                LazyColumn(Modifier.weight(1f, fill = false).testTag("preset-list"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    item { Text(strings.getString(R.string.built_in_presets), color = Muted, fontSize = 12.sp, modifier = Modifier.padding(8.dp)) }
                    items(EqPreset.entries.filter { it != EqPreset.CUSTOM }, key = { it.name }) { preset ->
                        PresetChoice(strings.getString(preset.title), selected == preset.name, "choose-${preset.name}", { selected = preset.name })
                    }
                    item { Text(strings.getString(R.string.my_sounds), color = Muted, fontSize = 12.sp, modifier = Modifier.padding(8.dp)) }
                    items(options.savedPresets, key = { it.id }) { preset ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) { PresetChoice(preset.name, selected == preset.id, "saved-${preset.id}", { selected = preset.id }) }
                            IconButton(onClick = { deleting = preset }) { Icon(Icons.Rounded.DeleteOutline, strings.getString(R.string.delete_named, preset.name), tint = Muted) }
                        }
                    }
                    if (options.savedPresets.isEmpty()) item {
                        Text(strings.getString(R.string.save_sound_hint), color = Muted, fontSize = 13.sp, modifier = Modifier.padding(8.dp))
                    }
                }
                TextButton(onClick = { naming = true }, modifier = Modifier.fillMaxWidth().testTag("save-sound")) { Text(strings.getString(R.string.save_current_sound)) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = dismiss) { Text(strings.getString(R.string.cancel), color = Muted) }
                    Button(onClick = {
                        EqPreset.entries.firstOrNull { it.name == selected }?.let { settings.preset(it, enable = true) } ?: settings.selectSaved(selected, enable = true)
                        dismiss()
                    }, shape = RoundedCornerShape(14.dp), modifier = Modifier.testTag("confirm-preset")) { Text(strings.getString(R.string.apply)) }
                }
            }
        }
    }
    if (naming) {
        var name by rememberSaveable { mutableStateOf("") }
        AlertDialog(onDismissRequest = { naming = false }, title = { Text(strings.getString(R.string.save_sound)) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(strings.getString(R.string.save_sound_detail), color = Muted)
                OutlinedTextField(name, { name = it.take(40) }, singleLine = true, label = { Text(strings.getString(R.string.name)) }, modifier = Modifier.testTag("preset-name"))
            } },
            confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = {
                settings.savePreset(name)?.let { selected = it }; naming = false
            }, modifier = Modifier.testTag("confirm-save-sound")) { Text(strings.getString(R.string.save)) } },
            dismissButton = { TextButton(onClick = { naming = false }) { Text(strings.getString(R.string.cancel)) } })
    }
    deleting?.let { preset ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text(strings.getString(R.string.delete_sound)) },
            text = { Text(strings.getString(R.string.delete_sound_detail, preset.name)) },
            confirmButton = { TextButton(onClick = {
                settings.deletePreset(preset.id)
                if (selected == preset.id) selected = EqPreset.CUSTOM.name
                deleting = null
            }) { Text(strings.getString(R.string.delete)) } }, dismissButton = { TextButton(onClick = { deleting = null }) { Text(strings.getString(R.string.cancel)) } })
    }
}

@Composable
private fun PresetChoice(title: String, selected: Boolean, tag: String, choose: () -> Unit) {
    val strings = androidx.compose.ui.platform.LocalContext.current

    Surface(color = if (selected) Accent.copy(alpha = .08f) else CanvasColor, shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.fillMaxWidth().testTag(tag).selectable(selected, role = Role.RadioButton, onClick = choose)
            .padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = if (selected) Accent else TextPrimary, modifier = Modifier.weight(1f), fontSize = 15.sp)
            RadioButton(selected, onClick = null, modifier = Modifier.size(24.dp))
        }
    }
}
