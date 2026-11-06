package app.musonic

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import app.musonic.data.Profile
import app.musonic.data.RecentPlayStore
import app.musonic.data.Song
import androidx.compose.ui.geometry.Offset
import app.musonic.data.ProfileStore
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean

class LibraryPagingTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val server = MockWebServer()
    private val offsets = Collections.synchronizedList(mutableListOf<Int>())
    private val failed = AtomicBoolean(false)
    @get:Rule val rules: RuleChain = RuleChain.outerRule(object : ExternalResource() {
        override fun before() {
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val url = request.requestUrl!!
                    val payload = when (url.encodedPath.substringAfterLast('/')) {
                        "getAlbumList2.view" -> {
                            val offset = url.queryParameter("offset")!!.toInt()
                            offsets.add(offset)
                            if (offset == 40 && !failed.getAndSet(true)) return MockResponse().setResponseCode(503)
                            val range = when (offset) { 0 -> 0..39; 40 -> 39..78; 80 -> 79..84; else -> IntRange.EMPTY }
                            val albums = range.joinToString(",") { """{"id":"a$it","name":"Album $it","artist":"Fixture"}""" }
                            """, "albumList2":{"album":[$albums]}"""
                        }
                        "getArtists.view" -> """, "artists":{"index":[]}"""
                        "getPlaylists.view" -> """, "playlists":{"playlist":[]}"""
                        else -> ""
                    }
                    return MockResponse().setHeader("Content-Type", "application/json").setBody("""{"subsonic-response":{"status":"ok","version":"1.16.1"$payload}}""")
                }
            }
            server.start()
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            context.getSharedPreferences("musonic_updates", 0).edit().clear().putBoolean("notification_asked", true).putLong("checked", System.currentTimeMillis()).commit()
            val profile = Profile("paging-fixture", "Paging", server.url("/").toString(), "fixture", "fixture", allowHttp = true)
            ProfileStore(context).apply { write(listOf(profile)); activeId = profile.id }
        }
        override fun after() { server.shutdown() }
    }).around(compose)
    @Test fun pullRefreshAndRecentHistorySurviveRecreation() {
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Album 0").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("새로고침").assertDoesNotExist()
        compose.onNodeWithTag("album-library").performTouchInput { swipe(Offset(centerX, 120f), Offset(centerX, height * .8f), 650) }
        compose.waitUntil(10_000) { offsets.count { it == 0 } == 2 }
        val store = RecentPlayStore.get(InstrumentationRegistry.getInstrumentation().targetContext)
        store.remove("paging-fixture")
        repeat(60) { store.record("paging-fixture", Song("recent-$it", "Recent $it", "Fixture", "History", null, 60)) }
        store.record("paging-fixture", Song("recent-3", "Recent 3", "Fixture", "History", null, 60))
        compose.onNodeWithText("최근 재생").performClick()
        compose.onNodeWithText("재생목록").assertDoesNotExist()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Recent 3").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Recent 3").assertIsDisplayed()
        compose.onNodeWithText("Recent 59").assertIsDisplayed()
        compose.onNodeWithTag("album-library").performScrollToNode(hasText("Recent 0"))
        compose.onNodeWithText("Recent 0").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Recent 3").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Recent 3").assertIsDisplayed()
        store.remove("paging-fixture")
    }
    @Test fun scrollingLoadsPagesAutomaticallyRetriesFailuresAndStopsAtEnd() {
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Album 0").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("앨범 더 보기").assertDoesNotExist()
        compose.onNodeWithTag("album-library").performScrollToIndex(20)
        compose.waitUntil(10_000) { offsets.contains(40) }
        compose.onNodeWithTag("album-library").performScrollToIndex(22)
        compose.waitUntil(10_000) { compose.onAllNodesWithText("다시 시도").fetchSemanticsNodes().isNotEmpty() }
        Thread.sleep(700)
        assertEquals(listOf(0, 40), offsets.toList())
        compose.onNodeWithText("다시 시도").performScrollTo().performClick()
        compose.waitUntil(10_000) { offsets.size == 3 && compose.onAllNodesWithText("다시 시도").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("album-library").performScrollToIndex(40)
        compose.waitUntil(10_000) { offsets.contains(80) }
        compose.onNodeWithTag("album-library").performScrollToNode(hasText("Album 84"))
        compose.onNodeWithText("Album 84").assertIsDisplayed()
        Thread.sleep(700)
        assertEquals("Offsets must count server entries, including duplicate albums", listOf(0, 40, 40, 80), offsets.toList())
    }
}
