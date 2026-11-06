package app.musonic

import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import app.musonic.audio.*
import app.musonic.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

class SoundControlsTest {
    @get:Rule val compose = createComposeRule()
    @Test fun presetsSaveCancelDeleteAndIndependentControls() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val prefix = "sound-ui-${UUID.randomUUID()}-"
        val context = object : ContextWrapper(base) {
            override fun getSharedPreferences(name: String, mode: Int) = base.getSharedPreferences(prefix + name, mode)
        }
        val settings = AudioSettings.isolated(context)
        val effect = PlaybackEqualizer {}
        var page by mutableStateOf("presets")
        var open by mutableStateOf(true)
        settings.preset(EqPreset.BASS)
        try {
            compose.setContent {
                val options by settings.state.collectAsState()
                MusonicTheme { Surface {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
                        if (page == "presets" && open) PresetPicker(options, settings) { open = false }
                        if (page == "balance") { Text("채널 밸런스", style = MaterialTheme.typography.headlineMedium); Spacer(Modifier.height(24.dp)); ChannelBalancePanel(options, settings) }
                        if (page == "loudness") { Text("라우드니스 보정", style = MaterialTheme.typography.headlineMedium); Spacer(Modifier.height(24.dp)); LoudnessPanel(options, settings) }
                    }
                } }
            }
            compose.onNodeWithTag("preset-list").performScrollToNode(hasTestTag("choose-W_SHAPE"))
            compose.onNodeWithTag("choose-W_SHAPE").performClick()
            assertEquals(EqPreset.BASS, settings.state.value.preset)
            screenshot("12-sound-presets")
            compose.onNodeWithText("취소").performClick()
            assertEquals(EqPreset.BASS, settings.state.value.preset)
            compose.runOnIdle { open = true }
            compose.onNodeWithTag("save-sound").performClick()
            compose.onNodeWithTag("preset-name").performTextInput("나의 저음")
            compose.onNodeWithTag("confirm-save-sound").performClick()
            val id = settings.state.value.savedPresets.single().id
            compose.onNodeWithTag("confirm-preset").performClick()
            assertEquals("나의 저음", settings.state.value.presetTitle)
            compose.runOnIdle { open = true }
            compose.onNodeWithTag("preset-list").performScrollToNode(hasTestTag("saved-$id"))
            compose.onNodeWithContentDescription("나의 저음 삭제").performClick()
            compose.onNodeWithText("삭제", substring = false).performClick()
            assertTrue(settings.state.value.savedPresets.isEmpty())
            assertEquals(500, settings.state.value.level(31))
            compose.onNodeWithTag("confirm-preset").performClick()
            compose.runOnIdle { page = "balance" }
            compose.onNodeWithTag("balance-switch").performClick()
            compose.onNodeWithTag("left-gain").performSemanticsAction(SemanticsActions.SetProgress) { it(-6f) }
            compose.onNodeWithTag("right-gain").performSemanticsAction(SemanticsActions.SetProgress) { it(-2f) }
            assertEquals(-6f, settings.state.value.leftGainDb, 0f)
            assertEquals(-2f, settings.state.value.rightGainDb, 0f)
            assertFalse(settings.state.value.eqEnabled)
            screenshot("13-channel-balance")
            compose.runOnIdle { page = "loudness"; effect.updateVolume(.2) }
            compose.onNodeWithTag("loudness-switch").performClick()
            compose.onNodeWithTag("loudness-threshold").performSemanticsAction(SemanticsActions.SetProgress) { it(70f) }
            assertEquals(.7f, settings.state.value.loudnessThreshold, .0001f)
            compose.onNodeWithText("휴대폰 볼륨 20%").assertExists()
            screenshot("14-loudness")
            compose.runOnIdle { effect.updateVolume(.9) }
            compose.onNodeWithText("시작 볼륨 이상이라 보정하지 않아요").assertExists()
        } finally {
            effect.close(); base.deleteSharedPreferences(prefix + "musonic_audio"); base.deleteSharedPreferences(prefix + "musonic_private")
        }
    }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.takeScreenshot().useBitmap { bitmap ->
            File(instrumentation.targetContext.getExternalFilesDir(null), "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
    private fun Bitmap.useBitmap(block: (Bitmap) -> Unit) { try { block(this) } finally { recycle() } }
}
