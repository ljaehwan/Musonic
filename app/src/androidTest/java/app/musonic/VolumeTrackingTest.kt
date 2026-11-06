package app.musonic

import android.content.ComponentName
import android.media.AudioManager
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import app.musonic.audio.PlaybackEqualizer
import app.musonic.playback.PlaybackService
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit
import kotlin.math.abs

class VolumeTrackingTest {
    @Test fun serviceFollowsRealSystemMediaVolumeChanges() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val manager = context.getSystemService(AudioManager::class.java)
        val original = manager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val maximum = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        assertFalse("Emulator must expose adjustable volume", manager.isVolumeFixed)
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { it.volumeControlStream = AudioManager.STREAM_MUSIC }
        val controller = MediaController.Builder(context, SessionToken(context, ComponentName(context, PlaybackService::class.java)))
            .buildAsync().get(10, TimeUnit.SECONDS)
        instrumentation.uiAutomation.adoptShellPermissionIdentity(android.Manifest.permission.MODIFY_AUDIO_SETTINGS)
        fun press(up: Boolean) {
            manager.setStreamVolume(AudioManager.STREAM_MUSIC, manager.getStreamVolume(AudioManager.STREAM_MUSIC) + if (up) 1 else -1, 0)
        }
        fun awaitVolume(index: Int) {
            val expected = index.toDouble() / maximum
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while ((PlaybackEqualizer.volumeState.value?.let { abs(it - expected) < .00001 } != true) && System.nanoTime() < deadline) Thread.sleep(100)
            assertEquals(index, manager.getStreamVolume(AudioManager.STREAM_MUSIC))
            assertEquals(expected, PlaybackEqualizer.volumeState.value ?: -1.0, .00001)
        }
        val increase = original < maximum
        try {
            press(increase)
            awaitVolume(original + if (increase) 1 else -1)
            press(!increase)
            awaitVolume(original)
        } finally {
            repeat(maximum) {
                val current = manager.getStreamVolume(AudioManager.STREAM_MUSIC)
                if (current != original) { press(current < original); Thread.sleep(100) }
            }
            instrumentation.runOnMainSync { controller.release() }
            scenario.close()
            instrumentation.uiAutomation.dropShellPermissionIdentity()
        }
    }
}
