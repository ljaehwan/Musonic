package app.musonic.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.musonic.R
import app.musonic.settings.AppPreferences

@Composable
internal fun AppearanceSettingsCard() {
    val context = LocalContext.current
    val dark = AppPreferences.dark(context)
    val language = AppPreferences.language(context)
    var choosingLanguage by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(context.getString(R.string.appearance), style = MaterialTheme.typography.titleLarge)
        Surface(color = Grouped, contentColor = TextPrimary, shape = RoundedCornerShape(20.dp)) {
            Column {
                Text(context.getString(R.string.theme), fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 18.dp, top = 18.dp, bottom = 10.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(false, true).forEach { choice ->
                        val selected = dark == choice
                        Surface(color = if (selected) SelectedBackground else Grouped, contentColor = TextPrimary,
                            shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f)) {
                            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag(if (choice) "theme-dark" else "theme-light")
                                .selectable(selected, role = Role.RadioButton, onClick = {
                                    if (choice != dark) { AppPreferences.setDark(context, choice); context.activity()?.recreate() }
                                }).padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(if (choice) Icons.Rounded.DarkMode else Icons.Rounded.LightMode, null,
                                    tint = if (selected) Accent else Muted, modifier = Modifier.size(22.dp))
                                Text(context.getString(if (choice) R.string.dark_mode else R.string.light_mode),
                                    modifier = Modifier.weight(1f), fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                                if (selected) Icon(Icons.Rounded.Check, null, tint = Accent, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(top = 16.dp, start = 18.dp, end = 18.dp), color = Separator.copy(alpha = .6f))
                Surface(onClick = { choosingLanguage = true }, color = Grouped, contentColor = TextPrimary,
                    modifier = Modifier.testTag("language-setting")) {
                    Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Rounded.Language, null, tint = Accent)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(context.getString(R.string.app_language), fontWeight = FontWeight.SemiBold)
                            Text(AppPreferences.languages.getValue(language), color = Muted, style = MaterialTheme.typography.bodyMedium)
                        }
                        Icon(Icons.Rounded.ChevronRight, null, tint = Muted)
                    }
                }
            }
        }
    }
    if (choosingLanguage) AlertDialog(onDismissRequest = { choosingLanguage = false },
        title = { Text(context.getString(R.string.app_language)) },
        text = { Column(Modifier.selectableGroup()) {
            AppPreferences.languages.forEach { (tag, name) ->
                Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("language-$tag")
                    .selectable(language == tag, role = Role.RadioButton, onClick = {
                        choosingLanguage = false
                        if (tag != language) { AppPreferences.setLanguage(context, tag); context.activity()?.recreate() }
                    }), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = tag == language, onClick = null)
                    Text(name, Modifier.padding(start = 8.dp))
                }
            }
        } }, confirmButton = { TextButton(onClick = { choosingLanguage = false }) { Text(context.getString(R.string.cancel)) } })
}

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}
