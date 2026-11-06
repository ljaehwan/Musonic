package app.musonic.ui

import app.musonic.R

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CarSettingsCard() {
    val strings = androidx.compose.ui.platform.LocalContext.current

    var help by rememberSaveable { mutableStateOf(false) }
    Surface(onClick = { help = true }, color = Grouped, contentColor = TextPrimary, shape = RoundedCornerShape(20.dp)) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(Icons.Rounded.DirectionsCar, null, tint = Accent)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Android Auto", fontWeight = FontWeight.SemiBold)
                Text(strings.getString(R.string.car_continue), color = Muted, fontSize = 13.sp)
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = Muted)
        }
    }
    if (help) AlertDialog(onDismissRequest = { help = false }, title = { Text(strings.getString(R.string.car_title)) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(strings.getString(R.string.car_instructions))
            Text(strings.getString(R.string.car_sideload))
            Text(strings.getString(R.string.car_launcher))
        }
    }, confirmButton = { TextButton(onClick = { help = false }) { Text(strings.getString(R.string.ok)) } })
}
