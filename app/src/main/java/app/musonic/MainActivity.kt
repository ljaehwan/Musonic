package app.musonic

import android.os.Bundle
import android.content.Intent
import android.app.SearchManager
import android.provider.MediaStore
import androidx.lifecycle.ViewModelProvider
import android.content.Context
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.musonic.settings.AppPreferences
import app.musonic.ui.MusonicApp
import app.musonic.ui.MusonicTheme

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppPreferences.localizedContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bars = if (AppPreferences.dark(this)) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        val music = ViewModelProvider(this)[MusicViewModel::class.java]
        setContent { MusonicTheme { MusonicApp(music) } }
        if (savedInstanceState == null) handleVoice(intent)
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleVoice(intent)
    }
    private fun handleVoice(intent: Intent) {
        if (intent.action == MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH) {
            val query = intent.getStringExtra(SearchManager.QUERY)
                ?: intent.getStringExtra(MediaStore.EXTRA_MEDIA_TITLE).orEmpty()
            intent.action = null
            ViewModelProvider(this)[MusicViewModel::class.java].playVoiceSearch(query)
        }
    }
}
