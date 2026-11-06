package app.musonic.playback

import app.musonic.audio.AudioSettings
import app.musonic.audio.PlaybackEqualizer
import android.media.AudioManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import app.musonic.audio.DspAudioSink
import app.musonic.car.CarLibrary
import android.app.PendingIntent
import android.content.Intent
import android.os.Process
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.DefaultMediaNotificationProvider
import app.musonic.R
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import app.musonic.MainActivity
import app.musonic.data.Transport
import app.musonic.data.RecentPlayStore
import app.musonic.data.Song
import androidx.media3.common.Player
import androidx.media3.common.MediaItem
import kotlinx.coroutines.*

@androidx.annotation.OptIn(UnstableApi::class)
class PlaybackService : MediaLibraryService() {
    private var session: MediaLibrarySession? = null
    private val audioScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val equalizer = PlaybackEqualizer()
    private val historyScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private var recordedItem: MediaItem? = null
    override fun onCreate() {
        super.onCreate()
        setMediaNotificationProvider(DefaultMediaNotificationProvider.Builder(this).build().apply {
            setSmallIcon(R.drawable.ic_musonic_notification)
        })
        val audioManager = getSystemService(AudioManager::class.java)
        fun readVolume(): Double? = runCatching {
            if (audioManager.isVolumeFixed) null else {
                val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                if (max <= 0) null else audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toDouble() / max
            }
        }.getOrNull()
        equalizer.updateVolume(readVolume())
        equalizer.apply(AudioSettings.get(this).state.value)
        audioScope.launch {
            while (isActive) {
                val volume = withContext(Dispatchers.IO) { readVolume() }
                equalizer.updateVolume(volume)
                delay(500)
            }
        }
        val renderers = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink =
                DspAudioSink(DefaultAudioSink.Builder(context).setEnableFloatOutput(true).setEnableAudioTrackPlaybackParams(true).build(), equalizer.engine)
        }
        val player = ExoPlayer.Builder(this, renderers)
            .setMediaSourceFactory(DefaultMediaSourceFactory(OkHttpDataSource.Factory(Transport.http)))
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            .setHandleAudioBecomingNoisy(true).setWakeMode(C.WAKE_MODE_NETWORK).build()
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { recordedItem = null }
            override fun onEvents(player: Player, events: Player.Events) {
                val item = player.currentMediaItem ?: return
                if (!player.isPlaying || recordedItem === item) return
                val metadata = item.mediaMetadata
                val account = metadata.extras?.getString("profileId") ?: return
                recordedItem = item
                val song = Song(item.mediaId, metadata.title?.toString().orEmpty(), metadata.artist?.toString().orEmpty(),
                    metadata.albumTitle?.toString().orEmpty(), metadata.extras?.getString("coverId"),
                    ((metadata.durationMs ?: 0L) / 1000L).coerceIn(0, Int.MAX_VALUE.toLong()).toInt(), metadata.extras?.getBoolean("starred") ?: false)
                historyScope.launch { runCatching { RecentPlayStore.get(this@PlaybackService).record(account, song) } }
            }
        })
        audioScope.launch { AudioSettings.get(this@PlaybackService).state.collect { equalizer.apply(it) } }
        val intent = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        session = MediaLibrarySession.Builder(this, player, CarLibrary(this, audioScope, ::canConnect)).setSessionActivity(intent).build()
    }
    // Legacy browser binding uses a placeholder identity; authorize the real caller in onConnect.
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session
    private fun canConnect(info: MediaSession.ControllerInfo) = info.uid == Process.myUid() || info.isTrusted || googleCarHost(info)
    private fun googleCarHost(info: MediaSession.ControllerInfo): Boolean =
        info.packageName in setOf("com.google.android.projection.gearhead", "com.google.android.googlequicksearchbox") &&
            packageManager.getPackagesForUid(info.uid)?.contains(info.packageName) == true &&
            packageManager.checkSignatures(info.packageName, "com.google.android.gms") == PackageManager.SIGNATURE_MATCH
    override fun onDestroy() {
        audioScope.cancel()
        equalizer.close()
        session?.run { player.release(); release() }
        session = null
        (historyScope.coroutineContext[Job] as? CompletableJob)?.complete()
        super.onDestroy()
    }
}
