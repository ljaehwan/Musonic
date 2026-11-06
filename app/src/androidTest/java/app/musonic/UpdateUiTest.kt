package app.musonic

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import app.musonic.update.ReleaseInfo
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import java.io.File

class UpdateUiTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val info = ReleaseInfo(Int.MAX_VALUE, "0.2.4-test",
        "https://github.com/ljaehwan/Musonic-releases/releases/download/v-test/Musonic.apk", "a".repeat(64), 100)
    @get:Rule val rules: RuleChain = RuleChain.outerRule(object : ExternalResource() {
        override fun before() {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            context.getSharedPreferences("musonic_private", 0).edit().clear().commit()
            context.getSharedPreferences("musonic_updates", 0).edit().clear().putString("release", info.json())
                .putBoolean("notification_asked", true).putInt("notified", Int.MAX_VALUE)
                .putLong("checked", System.currentTimeMillis()).commit()
        }
        override fun after() {
            InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences("musonic_updates", 0)
                .edit().clear().putBoolean("notification_asked", true).putLong("checked", System.currentTimeMillis()).commit()
        }
    }).around(compose)
    @Test fun cachedNewVersionShowsDownloadPromptAndCanBeDismissed() {
        compose.waitUntil(10_000) { compose.onAllNodesWithText("새로운 Musonic").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("${info.name} 버전을 사용할 수 있습니다.").assertIsDisplayed()
        compose.onNodeWithText("다운로드", useUnmergedTree = true).assertIsDisplayed()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val screenshot = instrumentation.uiAutomation.takeScreenshot()
        screenshot?.let { File(instrumentation.targetContext.getExternalFilesDir(null), "06-update.png").outputStream().use { stream -> it.compress(Bitmap.CompressFormat.PNG, 100, stream) } }
        compose.onNodeWithText("나중에").performClick()
        compose.onNodeWithText("새로운 Musonic").assertDoesNotExist()
        compose.onNodeWithText("서버 주소").assertExists()
    }
}
