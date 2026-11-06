package app.musonic

import android.content.ComponentName
import android.os.Bundle
import android.media.browse.MediaBrowser as LegacyBrowser
import android.media.session.MediaController as LegacyController
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import androidx.test.platform.app.InstrumentationRegistry
import app.musonic.audio.AudioSettings
import app.musonic.data.*
import app.musonic.playback.PlaybackService
import okhttp3.mockwebserver.*
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class CarLibraryTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private fun <T> main(block: () -> T): T {
        var value: T? = null
        instrumentation.runOnMainSync { value = block() }
        @Suppress("UNCHECKED_CAST") return value as T
    }
    private fun until(test: () -> Boolean) {
        val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
        while (System.nanoTime() < end) { if (test()) return; Thread.sleep(100) }
        fail("Timed out waiting for car playback")
    }
    @Test fun modernAndLegacyCarBrowsersBrowseSearchPlayQueuesAndRejectStaleAccounts() {
        val context = instrumentation.targetContext
        val store = ProfileStore(context)
        val oldProfiles = store.read(); val oldActive = store.activeId
        val audio = instrumentation.context.assets.open("tone-64.mp3").use { it.readBytes() }
        val songs = """[{"id":"s1","title":"Night Drive","artist":"Demo","album":"After Hours","duration":15},{"id":"s2","title":"Second Song","artist":"Demo","duration":15}]"""
        val server = MockWebServer().apply { dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val endpoint = request.requestUrl!!.encodedPath.substringAfterLast('/')
                if (endpoint == "stream.view") return MockResponse().setHeader("Content-Type", "audio/mpeg").setBody(Buffer().write(audio))
                val body = when(endpoint) {
                    "getAlbumList2.view" -> """, "albumList2":{"album":[{"id":"a1","name":"After Hours","artist":"Demo"}]}"""
                    "getAlbum.view" -> """, "album":{"song":$songs}"""
                    "search3.view" -> """, "searchResult3":{"song":$songs}"""
                    "getStarred2.view" -> """, "starred2":{"song":$songs}"""
                    "getPlaylists.view" -> """, "playlists":{"playlist":[]}"""
                    "getSong.view" -> """, "song":{"id":"s1","title":"Night Drive","artist":"Demo","duration":15}"""
                    else -> return MockResponse().setResponseCode(404)
                }
                return MockResponse().setHeader("Content-Type", "application/json").setBody("""{"subsonic-response":{"status":"ok","version":"1.16.1"$body}}""")
            }
        } }
        server.start()
        val profile = Profile("car-fixture", "Demo", server.url("/").toString(), "demo", "fixture", allowHttp = true)
        store.write(listOf(profile)); store.activeId = profile.id
        // A real car host is foreground. Give this same-UID test client a visible Activity
        // so API 35 audio-focus hardening does not reject playback before the service starts.
        val visibleApp = instrumentation.startActivitySync(android.content.Intent(context, MainActivity::class.java)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        instrumentation.waitForIdleSync()
        val component = ComponentName(context, PlaybackService::class.java)
        val future = main { MediaBrowser.Builder(context, SessionToken(context, component)).buildAsync() }
        val browser = future.get(10, TimeUnit.SECONDS)
        var legacy: LegacyBrowser? = null
        try {
            val root = main { browser.getLibraryRoot(null) }.get(10, TimeUnit.SECONDS)
            assertEquals(0, root.resultCode)
            val categories = main { browser.getChildren(root.value!!.mediaId, 0, 100, null) }.get(10, TimeUnit.SECONDS).value!!
            assertEquals(listOf("앨범", "즐겨찾기", "최근 재생", "재생목록"), categories.map { it.mediaMetadata.title.toString() })
            val albums = main { browser.getChildren(categories[0].mediaId, 0, 100, null) }.get(10, TimeUnit.SECONDS).value!!
            val tracks = main { browser.getChildren(albums[0].mediaId, 0, 100, null) }.get(10, TimeUnit.SECONDS).value!!
            assertEquals(2, tracks.size)
            assertNull("Catalog entries must not contain stream URLs", tracks[0].localConfiguration)
            assertEquals(0, main { browser.search("night", null) }.get(10, TimeUnit.SECONDS).resultCode)
            assertEquals(2, main { browser.getSearchResult("night", 0, 100, null) }.get(10, TimeUnit.SECONDS).value!!.size)
            main { browser.setMediaItem(tracks[1]); browser.prepare(); browser.play() }
            until { main { browser.isPlaying && browser.currentMediaItem?.mediaId == "s2" } }
            assertEquals(2, main { browser.mediaItemCount })
            val connected = CountDownLatch(1)
            legacy = main { LegacyBrowser(context, component, object : LegacyBrowser.ConnectionCallback() {
                override fun onConnected() { connected.countDown() }
            }, null).also { it.connect() } }
            assertTrue("Legacy Android Auto browser must connect", connected.await(10, TimeUnit.SECONDS))
            val children = CountDownLatch(1)
            var legacyTitles = emptyList<String>()
            main { legacy!!.subscribe("root", object : LegacyBrowser.SubscriptionCallback() {
                override fun onChildrenLoaded(parentId: String, childrenList: MutableList<LegacyBrowser.MediaItem>) {
                    legacyTitles = childrenList.map { it.description.title.toString() }; children.countDown()
                }
            }) }
            assertTrue(children.await(10, TimeUnit.SECONDS))
            assertEquals(4, legacyTitles.size)
            main { LegacyController(context, legacy!!.sessionToken).transportControls.playFromMediaId(tracks[0].mediaId, null) }
            until { main { browser.isPlaying && browser.currentMediaItem?.mediaId == "s1" } }
            assertEquals(2, main { browser.mediaItemCount })
            main { browser.stop(); browser.clearMediaItems() }
            main { LegacyController(context, legacy!!.sessionToken).transportControls.playFromSearch("night", null) }
            until { main { browser.isPlaying && browser.currentMediaItem?.mediaId == "s1" } }
            assertEquals(2, main { browser.mediaItemCount })
            main { browser.stop(); browser.clearMediaItems() }
            context.startActivity(android.content.Intent(context, MainActivity::class.java)
                .setAction(android.provider.MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
                .putExtra(android.app.SearchManager.QUERY, "night")
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            until { main { browser.isPlaying && browser.currentMediaItem?.mediaId == "s1" } }
            until { main { androidx.lifecycle.ViewModelProvider(visibleApp)[MusicViewModel::class.java].state.value.queue.size == 2 } }
            store.activeId = "different-account"
            assertNotEquals(0, main { browser.getChildren(albums[0].mediaId, 0, 100, null) }.get(10, TimeUnit.SECONDS).resultCode)
            val empty = main { browser.getChildren("root", 0, 100, null) }.get(10, TimeUnit.SECONDS)
            assertEquals("휴대폰에서 서버를 연결해 주세요", empty.value!!.first().mediaMetadata.title.toString())
        } finally {
            main { legacy?.disconnect(); browser.stop(); browser.clearMediaItems(); browser.release() }
            main { visibleApp.finish() }
            store.write(oldProfiles); store.activeId = oldActive
            RecentPlayStore.get(context).remove(profile.id)
            server.shutdown()
        }
    }
}
