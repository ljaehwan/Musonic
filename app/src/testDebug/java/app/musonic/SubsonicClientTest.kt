package app.musonic

import app.musonic.data.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SubsonicClientTest {
    private lateinit var server: MockWebServer
    @Before fun setup() { server = MockWebServer(); server.start() }
    @After fun teardown() { server.shutdown() }
    private fun client(mode: AuthMode = AuthMode.TOKEN, secret: String = "sesame", path: String = "/") =
        SubsonicClient(Profile("test", "Test", server.url(path).toString(), "joe", secret, mode, true))
    private fun ok(body: String = "") = MockResponse().setHeader("Content-Type", "application/json")
        .setBody("""{"subsonic-response":{"status":"ok","version":"1.16.1"$body}}""")

    @Test fun `matches published Subsonic authentication vector`() {
        assertEquals("26719a1196d2a940705a59634eb18eab", SubsonicClient.token("sesame", "c19b2d"))
    }
    @Test fun `password never appears in token URL and salt changes`() {
        val a = client().url("ping"); val b = client().url("ping")
        assertNull(a.queryParameter("p")); assertFalse(a.toString().contains("sesame"))
        assertNotEquals(a.queryParameter("s"), b.queryParameter("s"))
        assertEquals(SubsonicClient.token("sesame", a.queryParameter("s")!!), a.queryParameter("t"))
    }
    @Test fun `root and reverse proxy subpaths are preserved`() {
        assertEquals("/rest/ping.view", client().url("ping").encodedPath)
        assertEquals("/music/rest/ping.view", client(path = "/music/").url("ping").encodedPath)
        assertEquals("/music/rest/ping.view", client(path = "/music").url("ping").encodedPath)
    }
    @Test fun `API key auth and legacy auth do not mix credentials`() {
        val api = client(AuthMode.API_KEY, "a+b &한글").url("ping")
        assertEquals("a+b &한글", api.queryParameter("apiKey")); assertNull(api.queryParameter("t")); assertNull(api.queryParameter("p"))
        val legacy = client(AuthMode.LEGACY, "päss").url("ping")
        assertEquals("enc:70c3a47373", legacy.queryParameter("p")); assertNull(legacy.queryParameter("t"))
    }
    @Test fun `requires explicit cleartext consent and rejects URL credentials`() {
        listOf("http://example.com", "https://joe:password@example.com", "https://example.com?token=secret", "https://example.com/#app").forEach { url ->
            assertThrows(IllegalArgumentException::class.java) { SubsonicClient.normalizeServer(url) }
        }
        assertEquals("http", SubsonicClient.normalizeServer("http://192.0.2.1:4533", true).scheme)
    }
    @Test fun `redirects never send authentication to a second destination`() = runTest {
        MockWebServer().use { destination ->
            destination.start()
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", destination.url("/rest/ping.view")))
            try { client().ping(); fail("Expected redirect rejection") } catch (e: SubsonicException) { assertEquals(-1, e.code) }
            assertEquals(0, destination.requestCount)
        }
    }
    @Test fun `HTML login page is not treated as success`() = runTest {
        server.enqueue(MockResponse().setBody("<html>Login</html>"))
        try { client().ping(); fail("Expected invalid server") } catch (e: SubsonicException) { assertEquals(-2, e.code) }
    }
    @Test fun `API authentication errors override HTTP success and do not echo secrets`() = runTest {
        server.enqueue(MockResponse().setBody("""{"subsonic-response":{"status":"failed","error":{"code":40,"message":"bad sesame"}}}"""))
        try { client().ping(); fail("Expected authentication rejection") } catch (e: SubsonicException) { assertEquals(40, e.code); assertFalse(e.message!!.contains("sesame")) }
    }
    @Test fun `album pagination and missing metadata are supported`() = runTest {
        server.enqueue(ok(""", "albumList2":{"album":[{"id":"a-1","name":"앨범","coverArt":null}]}"""))
        val albums = client().albums(40)
        assertEquals("앨범", albums.single().name); assertNull(albums.single().coverArt)
        assertEquals("40", server.takeRequest().requestUrl!!.queryParameter("offset"))
    }
    @Test fun `empty library and incomplete search categories are valid`() = runTest {
        server.enqueue(ok(""", "albumList2":{}""")); assertTrue(client().albums().isEmpty())
        server.enqueue(ok(""", "searchResult3":{"song":[{"id":"s1","title":"노래","duration":123,"starred":"2025-01-01"}]}"""))
        val result = client().search("한글 & space")
        assertTrue(result.albums.isEmpty()); assertTrue(result.songs.single().starred)
        server.takeRequest(); assertEquals("한글 & space", server.takeRequest().requestUrl!!.queryParameter("query"))
    }
    @Test fun `stream choices are explicit and song ids are encoded`() {
        for (quality in app.musonic.audio.StreamQuality.entries) {
            val url = client().stream("track+&1", quality).toHttpUrl()
            assertEquals("track+&1", url.queryParameter("id"))
            assertEquals(if (quality.kbps == null) "raw" else "mp3", url.queryParameter("format"))
            assertEquals(quality.kbps?.toString(), url.queryParameter("maxBitRate"))
        }
    }
    @Test fun `star operation uses the chosen endpoint and track id`() = runTest {
        server.enqueue(ok()); server.enqueue(ok())
        client().star("song 1", true); client().star("song 1", false)
        assertEquals("/rest/star.view", server.takeRequest().requestUrl!!.encodedPath)
        val unstar = server.takeRequest().requestUrl!!
        assertEquals("/rest/unstar.view", unstar.encodedPath); assertEquals("song 1", unstar.queryParameter("id"))
    }
}
