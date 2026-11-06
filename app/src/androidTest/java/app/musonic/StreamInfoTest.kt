package app.musonic

import android.content.ComponentName
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.platform.app.InstrumentationRegistry
import app.musonic.audio.StreamInfo
import app.musonic.playback.PlaybackService
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class StreamInfoTest {
    @Test fun unknownAndPeakOnlyBitratesAreNeverInvented() {
        val unknown = StreamInfo.from(Format.Builder().setSampleMimeType("audio/flac").setSampleRate(44100).setPeakBitrate(900000).build())
        assertNull(unknown.averageBitrate)
        assertEquals("FLAC   비트레이트 정보 없음   44.1 kHz", unknown.label(app.musonic.settings.AppPreferences.localizedContext(InstrumentationRegistry.getInstrumentation().targetContext)))
        assertEquals("AAC   192 kbps   48 kHz", StreamInfo.from(Format.Builder().setSampleMimeType("audio/mp4a-latm")
            .setAverageBitrate(192000).setSampleRate(48000).build()).label(app.musonic.settings.AppPreferences.localizedContext(InstrumentationRegistry.getInstrumentation().targetContext)))
    }

    @Test fun controllerReportsIncomingMp3InsteadOfRequestedRateAndClearsOnReplacement() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val server = MockWebServer()
        val audio = instrumentation.context.assets.open("tone-64.mp3").use { it.readBytes() }
        server.enqueue(MockResponse().setHeader("Content-Type", "audio/mpeg").setBody(Buffer().write(audio)))
        server.enqueue(MockResponse().setResponseCode(404))
        server.start()
        val firstUrl = server.url("/stream?format=mp3&maxBitRate=320").toString()
        val secondUrl = server.url("/missing").toString()
        val future = MediaController.Builder(context, SessionToken(context, ComponentName(context, PlaybackService::class.java))).buildAsync()
        val controller = future.get(10, TimeUnit.SECONDS)
        try {
            instrumentation.runOnMainSync {
                controller.setMediaItem(MediaItem.Builder().setMediaId("synthetic64").setUri(firstUrl).build())
                controller.prepare(); controller.play()
            }
            var info: StreamInfo? = null
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
            while (info == null && System.nanoTime() < deadline) {
                instrumentation.runOnMainSync { info = StreamInfo.current(controller) }
                Thread.sleep(100)
            }
            assertNotNull("Selected stream format must reach the MediaController", info)
            assertEquals(64000, info!!.averageBitrate)
            assertEquals("MP3   64 kbps   48 kHz", info!!.label(app.musonic.settings.AppPreferences.localizedContext(InstrumentationRegistry.getInstrumentation().targetContext)))
            instrumentation.runOnMainSync {
                assertEquals(C.TRACK_TYPE_AUDIO, controller.currentTracks.groups.first().type)
                controller.setMediaItem(MediaItem.Builder().setMediaId("replacement").setUri(secondUrl).build())
                assertNull("Replacing a track must hide the previous format immediately", StreamInfo.current(controller))
            }
        } finally {
            instrumentation.runOnMainSync { controller.stop(); controller.clearMediaItems(); controller.release() }
            server.shutdown()
        }
    }
}
