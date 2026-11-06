package app.musonic.update

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.musonic.R

// The Play binary contains no APK downloader, installer, or update worker.
@Composable internal fun UpdateDialog() = Unit
@Composable internal fun UpdateCard() {
    Text(stringResource(R.string.play_updates), modifier = Modifier.padding(vertical = 8.dp),
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
