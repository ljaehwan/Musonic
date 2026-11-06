package app.musonic.ui

import androidx.compose.runtime.Composable
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import app.musonic.R

@Composable internal fun PrivacyLink() {
    val handler = LocalUriHandler.current
    TextButton(onClick = { handler.openUri("https://ljaehwan.github.io/Musonic-releases/privacy/") }) {
        Text(stringResource(R.string.privacy_policy))
    }
}
