package app.musonic.update

import app.musonic.R

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.musonic.BuildConfig

@Composable
internal fun UpdateCard(vm: UpdateViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    val strings = androidx.compose.ui.platform.LocalContext.current

    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(strings.getString(R.string.app_updates), style = MaterialTheme.typography.titleMedium)
            Text(strings.getString(R.string.current_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodyMedium)
            Text(state.release?.let { strings.getString(R.string.version_available, it.name) } ?: state.message?.resolve(strings) ?: strings.getString(R.string.update_notify_hint), style = MaterialTheme.typography.bodyMedium)
            Row {
                TextButton(onClick = { if (state.release != null) vm.open() else vm.check() }, enabled = !state.busy) {
                    Text(if (state.busy) strings.getString(R.string.checking) else if (state.release != null) strings.getString(R.string.view_update) else strings.getString(R.string.check_update))
                }
                TextButton(onClick = {
                    if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                        permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                }) { Text(strings.getString(R.string.notification_settings)) }
            }
        }
    }
}

@Composable
internal fun UpdateDialog(vm: UpdateViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    val strings = androidx.compose.ui.platform.LocalContext.current

    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val prefs = remember { context.getSharedPreferences("musonic_updates", 0) }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && !prefs.getBoolean("notification_asked", false)) {
            prefs.edit().putBoolean("notification_asked", true).apply()
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val unknownSources = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (context.packageManager.canRequestPackageInstalls()) vm.install(context)
    }
    if (state.show && state.release != null) AlertDialog(onDismissRequest = vm::dismiss, containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(strings.getString(R.string.new_musonic)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(strings.getString(R.string.version_available_dialog, state.release!!.name))
            Text(state.message?.resolve(strings) ?: strings.getString(R.string.update_preserves_settings))
            state.progress?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth()); Text("${(it * 100).toInt()}%") }
        } },
        confirmButton = { TextButton(enabled = !state.busy, onClick = {
            if (state.file == null) vm.download()
            else if (context.packageManager.canRequestPackageInstalls()) vm.install(context)
            else unknownSources.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
        }) { Text(if (state.busy) strings.getString(R.string.working) else if (state.file != null) strings.getString(R.string.install) else strings.getString(R.string.download)) } },
        dismissButton = { TextButton(onClick = vm::dismiss, enabled = !state.busy) { Text(strings.getString(R.string.later)) } })
}
