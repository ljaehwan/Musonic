package app.musonic

import app.musonic.settings.AppPreferences
import android.content.res.Configuration
import android.app.NotificationManager
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.ui.test.*
import app.musonic.audio.*
import java.util.Collections
import app.musonic.data.ProfileStore
import app.musonic.data.RecentPlayStore
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.media3.common.C
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.platform.app.InstrumentationRegistry
import app.musonic.playback.PlaybackService
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit

/** Entirely synthetic server/library/audio. Never uses a user's music or credentials. */
class ListeningFlowTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules: org.junit.rules.RuleChain = org.junit.rules.RuleChain.outerRule(object : org.junit.rules.ExternalResource() {
        override fun before() {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            context.getSharedPreferences(AppPreferences.STORE, 0).edit().clear().commit()
            context.getSharedPreferences("musonic_private", 0).edit().clear().commit()
            context.getSharedPreferences("musonic_updates", 0).edit().clear()
                .putBoolean("notification_asked", true).putLong("checked", System.currentTimeMillis()).commit()
        }
    }).around(compose)

    @Test fun connectBrowsePlayAndKeepPlayingInBackground() = listeningFlow(false)

    @Test fun unknownLengthStreamUsesServerDurationAndAdvancesProgress() = listeningFlow(true)

    @Test fun languageAndThemeChangesKeepPlaybackAndSurviveRecreation() = listeningFlow(false, true)

    private fun listeningFlow(unknownLength: Boolean, appearance: Boolean = false) {
        val settings = AudioSettings.get(InstrumentationRegistry.getInstrumentation().targetContext)
        settings.quality(StreamQuality.ORIGINAL); settings.enabled(false); settings.reset(); settings.balance(false); settings.loudness(false)
        val streamRequests = Collections.synchronizedList(mutableListOf<Pair<String?, String?>>())
        val server = MockWebServer()
        val audio = if (unknownLength) InstrumentationRegistry.getInstrumentation().context.assets.open("unknown-length.aac").use { it.readBytes() } else wav()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.requestUrl!!
                if (url.encodedPath.endsWith("stream.view")) streamRequests.add(url.queryParameter("format") to url.queryParameter("maxBitRate"))
                val body = when (url.encodedPath.substringAfterLast('/')) {
                    "ping.view" -> ""
                    "getAlbumList2.view" -> """, "albumList2":{"album":[
                        {"id":"a1","name":"After Hours","artist":"The Midnight Club","coverArt":"c1","songCount":3,"year":2026},
                        {"id":"a2","name":"Soft Focus","artist":"Mira & The Waves","coverArt":"c2","songCount":3},
                        {"id":"a3","name":"Blue Sunday","artist":"August Radio","coverArt":"c3","songCount":3},
                        {"id":"a4","name":"Daylight","artist":"Sol Collective","coverArt":"c4","songCount":3},
                        {"id":"a5","name":"Moonlight Radio","artist":"The Midnight Club","coverArt":"c1","songCount":3},
                        {"id":"a6","name":"Slow Motion","artist":"Mira & The Waves","coverArt":"c2","songCount":3},
                        {"id":"a7","name":"Ocean Drive","artist":"August Radio","coverArt":"c3","songCount":3},
                        {"id":"a8","name":"Velvet Skies","artist":"Sol Collective","coverArt":"c4","songCount":3}]}"""
                    "getArtists.view" -> """, "artists":{"index":[{"name":"M","artist":[{"id":"ar1","name":"The Midnight Club","albumCount":1}]}]}"""
                    "getPlaylists.view" -> """, "playlists":{"playlist":[{"id":"p1","name":"Late night favorites","songCount":3,"coverArt":"c1"}]}"""
                    "getAlbum.view" -> """, "album":{"id":"a1","name":"After Hours","song":$songs}"""
                    "search3.view" -> """, "searchResult3":{"song":$songs}"""
                    "getStarred2.view" -> """, "starred2":{"song":[]}"""
                    "star.view", "unstar.view" -> ""
                    "getCoverArt.view" -> return MockResponse().setHeader("Content-Type", "image/png").setBody(Buffer().write(cover(url.queryParameter("id").orEmpty())))
                    "stream.view" -> return if (unknownLength) MockResponse().setHeader("Content-Type", "audio/aac")
                        .setChunkedBody(Buffer().write(audio), 1024).throttleBody(1024, 250, TimeUnit.MILLISECONDS)
                    else MockResponse().setHeader("Content-Type", "audio/wav").setHeader("Accept-Ranges", "bytes").setBody(Buffer().write(audio))
                    else -> return MockResponse().setResponseCode(404)
                }
                return MockResponse().setHeader("Content-Type", "application/json").setBody("""{"subsonic-response":{"status":"ok","version":"1.16.1","type":"Test fixture"$body}}""")
            }
        }
        server.start()
        try {
            compose.waitForIdle()
            screenshot("01-connect")
            compose.onNodeWithText("서버 주소").performTextInput(server.url("/").toString())
            compose.onNodeWithText("서버 이름 (선택)").performTextInput("Musonic demo library")
            compose.onNodeWithText("사용자 이름").performScrollTo().performTextInput("demo")
            compose.onNodeWithText("비밀번호").performScrollTo().performTextInput("fixture-password")
            compose.onNodeWithText("고급 설정").performScrollTo().performClick()
            compose.onNode(isToggleable()).performScrollTo().performClick()
            compose.onNodeWithText("서버 연결하기").performScrollTo().performClick()
            compose.waitUntil(20_000) { compose.onAllNodesWithText("After Hours").fetchSemanticsNodes().isNotEmpty() }
            val stored = InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences("musonic_private", 0).getString("profiles", "")!!
            assertTrue(stored.isNotEmpty())
            assertFalse("Reusable credentials must not be stored as plaintext", stored.contains("fixture-password"))
            compose.waitForIdle()
            Thread.sleep(1000) // Let cover requests finish for evidence screenshots.
            screenshot("02-library")
            compose.onNodeWithTag("bottom-navigation").assertIsDisplayed()
            // The final album must scroll completely above the overlay, not remain hidden behind it.
            compose.onNodeWithTag("album-library").performScrollToNode(hasText("Velvet Skies"))
            compose.onNodeWithTag("album-library").performScrollToIndex(6)
            val lastAlbum = compose.onNodeWithText("Velvet Skies").fetchSemanticsNode().boundsInRoot
            val dock = compose.onNodeWithTag("bottom-navigation").fetchSemanticsNode().boundsInRoot
            assertTrue("Bottom content must be reachable above the dock", lastAlbum.bottom <= dock.top)
            compose.onNodeWithTag("album-library").performScrollToIndex(0)
            compose.onNodeWithText("After Hours").performClick()
            compose.waitUntil(15_000) { compose.onAllNodesWithText("전체 재생").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("전체 재생").performScrollTo().performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("mini-player").fetchSemanticsNodes().isNotEmpty() }
            if (!unknownLength && !appearance) {
                compose.waitUntil(10_000) { streamRequests.any { it.first == "raw" && it.second == null } }
                compose.onNode(hasText("설정") and hasAnyAncestor(hasTestTag("bottom-navigation"))).performClick()
                compose.onNodeWithTag("settings-list").performScrollToIndex(2)
                compose.onNodeWithTag("quick-equalizer").performClick()
                compose.onNodeWithTag("quick-equalizer").assertIsOn()
                compose.onNodeWithTag("audio-settings-sheet").assertDoesNotExist()
                compose.onNodeWithText("이퀄라이저").performClick()
                compose.onNodeWithTag("equalizer-switch").assertIsOn().performClick()
                compose.onNodeWithText("완료").performClick()
                compose.onNodeWithTag("quick-equalizer").assertIsOff()
                for ((quick, title, detail) in listOf(Triple("quick-balance", "채널 밸런스", "balance-switch"), Triple("quick-loudness", "라우드니스 보정", "loudness-switch"))) {
                    compose.onNodeWithTag(quick).performScrollTo().performClick()
                    compose.onNodeWithTag(quick).assertIsOn()
                    compose.onNodeWithTag("audio-settings-sheet").assertDoesNotExist()
                    compose.onNodeWithText(title).performClick()
                    compose.onNodeWithTag(detail).assertIsOn().performClick()
                    compose.onNodeWithText("완료").performClick()
                    compose.onNodeWithTag(quick).assertIsOff()
                }
                screenshot("08-sound-settings")
                compose.onNodeWithText("이퀄라이저").performClick()
                compose.waitUntil(10_000) { PlaybackEqualizer.state.value.available }
                compose.onNodeWithTag("equalizer-switch").assertIsOff()
                compose.onNodeWithTag("dsp-preamp").performScrollTo().assertIsNotEnabled()
                compose.onNodeWithTag("preset-BASS").assertDoesNotExist()
                compose.onNodeWithTag("all-presets").assertDoesNotExist()
                compose.onNodeWithTag("preset-setting").performScrollTo().performClick()
                compose.onNodeWithTag("choose-BASS").performClick()
                assertFalse("Previewing a preset must not enable EQ", settings.state.value.eqEnabled)
                screenshot("18-preset-list")
                compose.onNodeWithText("취소").performClick()
                assertFalse(settings.state.value.eqEnabled)
                compose.onNodeWithTag("preset-setting").performClick()
                compose.onNodeWithTag("choose-BASS").performClick()
                compose.onNodeWithTag("confirm-preset").performClick()
                compose.waitUntil(5000) { PlaybackEqualizer.state.value.enabled && PlaybackEqualizer.state.value.bands.first().level > 0 }
                val beforeEdit = settings.state.value
                compose.onNodeWithTag("edit-equalizer").performScrollTo().performClick()
                compose.onNodeWithTag("eq-band-0").performTouchInput { swipe(center, androidx.compose.ui.geometry.Offset(centerX, height * .3f), 500) }
                assertTrue("Vertical dragging must adjust the draft", compose.onNodeWithTag("eq-band-0").fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current > 0)
                compose.onNodeWithTag("eq-band-0").performSemanticsAction(SemanticsActions.SetProgress) { it(4f) }
                assertEquals("Draft must not change the playing sound", beforeEdit, settings.state.value)
                screenshot("11-equalizer-editor")
                compose.onNodeWithText("취소").performClick()
                assertEquals(beforeEdit, settings.state.value)
                compose.onNodeWithTag("edit-equalizer").performScrollTo().performClick()
                compose.onNodeWithTag("eq-band-0").performSemanticsAction(SemanticsActions.SetProgress) { it(4f) }
                compose.onNodeWithTag("apply-equalizer").performClick()
                compose.waitUntil(5000) { PlaybackEqualizer.state.value.bands.first().level == 400 }
                assertEquals(EqPreset.CUSTOM, settings.state.value.preset)
                compose.onNodeWithTag("audio-settings-sheet").performScrollToIndex(0)
                screenshot("09-equalizer")
                compose.onNodeWithText("완료").performScrollTo().performClick()
                compose.activityRule.scenario.recreate()
                compose.waitUntil(10_000) { compose.onAllNodesWithTag("mini-player").fetchSemanticsNodes().isNotEmpty() }
                compose.onNode(hasText("설정") and hasAnyAncestor(hasTestTag("bottom-navigation"))).performClick()
                compose.onNodeWithTag("settings-list").performScrollToIndex(2)
                compose.onNodeWithText("직접 조절").assertExists()
                compose.onNodeWithText("음원 품질").performClick()
                compose.onNodeWithTag("quality-HIGH").performClick()
                assertEquals(StreamQuality.HIGH, settings.state.value.quality)
                compose.onNodeWithTag("quality-HIGH").assertIsSelected()
                screenshot("10-audio-quality")
                compose.onNodeWithText("선택한 음질로 다시 재생").performScrollTo().performClick()
                compose.waitUntil(10_000) { streamRequests.any { it.first == "mp3" && it.second == "320" } }
            }
            if (appearance) verifyAppearanceDuringPlayback()
            compose.onNodeWithText("보관함").performClick()
            screenshot("07-mini-player")
            compose.onNodeWithTag("mini-player").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("now-playing").fetchSemanticsNodes().isNotEmpty() }
            Thread.sleep(1000)
            if (!unknownLength) {
                compose.onNodeWithTag("stream-info").assertTextContains("PCM", substring = true)
                assertFalse("Requested MP3 320 must never masquerade as the actual WAV format",
                    compose.onNodeWithTag("stream-info").fetchSemanticsNode().config[SemanticsProperties.Text].any { it.text.contains("320 kbps") })
            }
            screenshot("03-player")
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val context = instrumentation.targetContext
            val future = MediaController.Builder(context, SessionToken(context, ComponentName(context, PlaybackService::class.java))).buildAsync()
            val controller = future.get(10, TimeUnit.SECONDS)
            try {
                compose.waitUntil(15_000) {
                    var playing = false
                    instrumentation.runOnMainSync { playing = controller.isPlaying }
                    playing
                }
                val notifications = context.getSystemService(NotificationManager::class.java)
                compose.waitUntil(10_000) {
                    notifications.activeNotifications.any { it.notification.smallIcon?.resId == R.drawable.ic_musonic_notification }
                }
                val history = RecentPlayStore.get(context)
                val profile = ProfileStore(context).activeId!!
                compose.waitUntil(10_000) { history.read(profile).firstOrNull()?.id == "s1" }
                assertEquals("Queued songs must not count as listened", listOf("s1"), history.read(profile).map { it.id })
                if (unknownLength) instrumentation.runOnMainSync {
                    assertEquals("Fixture must reproduce an unknown engine duration", C.TIME_UNSET, controller.duration)
                }
                compose.onNode(hasText("1:00") and hasAnyAncestor(hasTestTag("now-playing"))).assertExists()
                val progressMatcher = SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo) and hasAnyAncestor(hasTestTag("now-playing"))
                fun progress() = compose.onNode(progressMatcher).fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current
                val initialProgress = progress()
                compose.waitUntil(8000) { progress() > initialProgress }
                if (!unknownLength) {
                    compose.onNodeWithTag("playback-slider").performSemanticsAction(SemanticsActions.SetProgress) { it(30_000f) }
                    compose.waitUntil(5000) { var position = 0L; instrumentation.runOnMainSync { position = controller.currentPosition }; position >= 29_000 }
                    compose.onNodeWithTag("playback-slider").performTouchInput { swipe(center, androidx.compose.ui.geometry.Offset(width * .65f, centerY), 500) }
                    compose.waitUntil(5000) { var position = 0L; instrumentation.runOnMainSync { position = controller.currentPosition }; position >= 36_000 }
                    screenshot("03-player")
                }
                if (unknownLength) {
                    screenshot("05-unknown-duration")
                    compose.activityRule.scenario.recreate()
                    compose.waitUntil(15_000) { compose.onAllNodesWithTag("mini-player").fetchSemanticsNodes().isNotEmpty() }
                    if (compose.onAllNodesWithTag("now-playing").fetchSemanticsNodes().isEmpty()) compose.onNodeWithTag("mini-player").performClick()
                    compose.waitUntil(10_000) { compose.onAllNodesWithTag("now-playing").fetchSemanticsNodes().isNotEmpty() }
                    compose.onNode(hasText("1:00") and hasAnyAncestor(hasTestTag("now-playing"))).assertExists()
                    instrumentation.runOnMainSync {
                        assertEquals(60_000L, controller.currentMediaItem!!.mediaMetadata.durationMs)
                        assertTrue("Recreating the UI must preserve playback", controller.isPlaying)
                    }
                }
                var before = 0L
                instrumentation.runOnMainSync { before = controller.currentPosition }
                compose.activityRule.scenario.onActivity { it.moveTaskToBack(true) }
                Thread.sleep(2000)
                instrumentation.runOnMainSync {
                    assertTrue("Service must keep playing in background", controller.isPlaying)
                    assertTrue("Playback position must advance in background", controller.currentPosition > before + 500)
                    controller.pause()
                    assertEquals(3, controller.mediaItemCount)
                    assertEquals("Night Drive", controller.currentMediaItem!!.mediaMetadata.title.toString())
                    controller.seekToNextMediaItem()
                    controller.play()
                }
                compose.waitUntil(10_000) { history.read(profile).firstOrNull()?.id == "s2" }
                assertEquals("Background playback must update history", listOf("s2", "s1"), history.read(profile).map { it.id })
            } finally { instrumentation.runOnMainSync { controller.stop(); controller.clearMediaItems(); controller.release() } }
        } finally { settings.quality(StreamQuality.ORIGINAL); settings.enabled(false); settings.reset(); server.shutdown() }
    }

    private fun verifyAppearanceDuringPlayback() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val controller = MediaController.Builder(context, SessionToken(context, ComponentName(context, PlaybackService::class.java)))
            .buildAsync().get(10, TimeUnit.SECONDS)
        try {
            var before = 0L
            compose.waitUntil(15_000) { var playing = false; instrumentation.runOnMainSync { playing = controller.isPlaying }; playing }
            instrumentation.runOnMainSync { before = controller.currentPosition }
            compose.onNode(hasText("설정") and hasAnyAncestor(hasTestTag("bottom-navigation"))).performClick()
            compose.onNodeWithTag("theme-light").performScrollTo().assertIsSelected()
            compose.onNodeWithTag("theme-dark").performClick()
            compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("theme-dark") and isSelected()).fetchSemanticsNodes().isNotEmpty() }
            assertEquals(Configuration.UI_MODE_NIGHT_YES, compose.activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK)
            for ((tag, heading) in listOf("en" to "Appearance and language", "ja" to "表示と言語", "zh-CN" to "外观与语言", "es" to "Apariencia e idioma", "en" to "Appearance and language")) {
                compose.onNodeWithTag("language-setting").performScrollTo().performClick()
                compose.onNodeWithTag("language-$tag").performClick()
                compose.waitUntil(10_000) { compose.onAllNodesWithText(heading).fetchSemanticsNodes().isNotEmpty() }
                assertEquals(tag, AppPreferences.language(context))
                compose.onNodeWithTag("theme-dark").assertIsSelected()
                instrumentation.runOnMainSync { assertTrue("Language changes must keep playback", controller.isPlaying) }
            }
            screenshot("13-dark-settings")
            compose.onNodeWithText("Library").performClick()
            screenshot("14-dark-library")
            compose.onNodeWithTag("mini-player").performClick()
            screenshot("15-dark-player")
            instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            compose.waitForIdle()
            compose.onNode(hasText("Settings") and hasAnyAncestor(hasTestTag("bottom-navigation"))).performClick()
            compose.onNodeWithTag("settings-list").performScrollToIndex(2)
            compose.onNodeWithText("Equalizer").performClick()
            screenshot("16-dark-equalizer")
            compose.onNodeWithText("Done").performClick()
            compose.onNodeWithTag("theme-light").performScrollTo().performClick()
            compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("theme-light") and isSelected()).fetchSemanticsNodes().isNotEmpty() }
            compose.activityRule.scenario.recreate()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Appearance and language").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("theme-light").assertIsSelected()
            assertEquals(Configuration.UI_MODE_NIGHT_NO, compose.activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK)
            assertEquals("en", AppPreferences.language(context))
            assertFalse(AppPreferences.dark(context))
            instrumentation.runOnMainSync {
                assertTrue(controller.isPlaying)
                assertTrue("Playback must advance across preference changes", controller.currentPosition > before)
                controller.seekTo(0)
            }
            screenshot("17-light-settings")
            compose.onNodeWithTag("language-setting").performClick()
            compose.onNodeWithTag("language-ko").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("화면 및 언어").fetchSemanticsNodes().isNotEmpty() }
        } finally { instrumentation.runOnMainSync { controller.release() } }
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        Thread.sleep(150) // Let the platform compositor present the latest Compose frame.
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val screenshot = instrumentation.uiAutomation.takeScreenshot() ?: return
        val output = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?.let(::File)
            ?: instrumentation.targetContext.getExternalFilesDir(null)!!
        output.mkdirs()
        File(output, "$name.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    private val songs = """[
        {"id":"s1","title":"Night Drive","artist":"The Midnight Club","album":"After Hours","coverArt":"c1","duration":60},
        {"id":"s2","title":"Neon Slowdance","artist":"The Midnight Club","album":"After Hours","coverArt":"c1","duration":60},
        {"id":"s3","title":"Home Before Dawn","artist":"The Midnight Club","album":"After Hours","coverArt":"c1","duration":60}]
    """
    private fun wav(): ByteArray {
        val count = 44100 * 60
        return ByteBuffer.allocate(44 + count * 2).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + count * 2); put("WAVEfmt ".toByteArray()); putInt(16)
            putShort(1); putShort(1); putInt(44100); putInt(88200); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(count * 2)
            repeat(count) { i -> putShort((kotlin.math.sin(i * 2 * Math.PI * 220 / 44100) * 500).toInt().toShort()) }
        }.array()
    }
    private fun cover(id: String): ByteArray {
        val bitmap = Bitmap.createBitmap(600, 600, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val pair = when(id) { "c2" -> Color.rgb(177, 129, 109) to Color.rgb(60, 77, 76); "c3" -> Color.rgb(99, 155, 173) to Color.rgb(25, 54, 96); "c4" -> Color.rgb(225, 178, 91) to Color.rgb(108, 68, 36); else -> Color.rgb(132, 88, 124) to Color.rgb(24, 31, 66) }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(0f, 0f, 600f, 600f, pair.first, pair.second, Shader.TileMode.CLAMP) }
        canvas.drawRect(0f, 0f, 600f, 600f, paint)
        paint.shader = null; paint.color = Color.argb(60, 255, 255, 240); paint.style = Paint.Style.STROKE; paint.strokeWidth = 3f
        repeat(8) { canvas.drawCircle(300f, 270f, 55f + it * 26f, paint) }
        paint.style = Paint.Style.FILL; paint.color = Color.rgb(245, 242, 228); paint.textSize = 44f; paint.isFakeBoldText = true
        canvas.drawText(when(id) { "c2" -> "SOFT FOCUS"; "c3" -> "BLUE SUNDAY"; "c4" -> "DAYLIGHT"; else -> "AFTER HOURS" }, 40f, 505f, paint)
        paint.textSize = 16f; paint.isFakeBoldText = false; canvas.drawText("MUSONIC / SYNTHETIC TEST LIBRARY", 42f, 540f, paint)
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }
}
