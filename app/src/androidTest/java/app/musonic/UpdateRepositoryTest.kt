package app.musonic

import androidx.test.platform.app.InstrumentationRegistry
import app.musonic.update.ReleaseInfo
import app.musonic.update.UpdateRepository
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/** Supply an APK with the same signing key and a higher versionCode via -e updateApk. */
class UpdateRepositoryTest {
    @Test fun checksDownloadsVerifiesAndReusesSignedUpdateWithoutMusicCredentials() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val fixture = InstrumentationRegistry.getArguments().getString("updateApk")
        assumeTrue("Pass -e updateApk with a signed higher-version APK", fixture != null)
        val context = instrumentation.targetContext
        val bytes = File(fixture!!).readBytes()
        @Suppress("DEPRECATION")
        val pkg = context.packageManager.getPackageArchiveInfo(fixture, 0)!!
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        @Suppress("DEPRECATION")
        val info = ReleaseInfo(pkg.versionCode, pkg.versionName!!,
            "https://github.com/ljaehwan/Musonic-releases/releases/download/v-test/Musonic-test.apk", digest, bytes.size.toLong())
        val prefs = context.getSharedPreferences("musonic_updates", 0)
        prefs.edit().clear().commit()
        val updates = File(context.filesDir, "updates")
        updates.listFiles()?.forEach { it.delete() }
        val server = MockWebServer()
        server.start()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            assertNull(chain.request().header("Authorization"))
            assertNull(chain.request().url.queryParameter("u"))
            chain.proceed(chain.request().newBuilder().url(server.url(chain.request().url.encodedPath)).build())
        }.build()
        val repository = UpdateRepository(context, client)
        try {
            server.enqueue(MockResponse().setBody("""{"draft":false,"prerelease":false,"assets":[{"name":"update.json","browser_download_url":"https://github.com/ljaehwan/Musonic-releases/releases/download/v-test/update.json"},{"name":"Musonic-test.apk","browser_download_url":"${info.url}","size":${info.size}}]}"""))
            server.enqueue(MockResponse().setBody(info.json()))
            assertEquals(info, repository.checkForUpdate(true))
            assertEquals(info, repository.checkForUpdate())
            assertEquals("Second check uses the six-hour cache", 2, server.requestCount)
            server.enqueue(MockResponse().setBody(Buffer().write(bytes)))
            var progress = 0f
            val downloaded = repository.download(info) { progress = it }
            assertTrue(downloaded.exists())
            assertEquals(1f, progress)
            repository.verify(downloaded, info)
            assertEquals(downloaded, repository.download(info) { })
            assertEquals("Verified file is reused", 3, server.requestCount)
            assertThrows(IllegalArgumentException::class.java) { repository.verify(downloaded, info.copy(sha256 = "0".repeat(64))) }
            assertThrows(IllegalArgumentException::class.java) { repository.verify(downloaded, info.copy(code = BuildConfig.VERSION_CODE)) }
            downloaded.writeBytes(byteArrayOf(0, 1, 2))
            assertThrows(IllegalArgumentException::class.java) { repository.verify(downloaded, info) }
            server.enqueue(MockResponse().setBody(Buffer().write(bytes)))
            repository.verify(repository.download(info) { }, info)
            assertEquals("Corrupt cached files are downloaded again", 4, server.requestCount)
            InstrumentationRegistry.getArguments().getString("wrongSignerApk")?.let { path ->
                val untrusted = File(path)
                val hash = MessageDigest.getInstance("SHA-256").digest(untrusted.readBytes()).joinToString("") { "%02x".format(it) }
                assertThrows(IllegalArgumentException::class.java) {
                    repository.verify(untrusted, info.copy(sha256 = hash, size = untrusted.length()))
                }
            }
            server.enqueue(MockResponse().setResponseCode(503))
            try { repository.checkForUpdate(true); fail("Server failure must not report up-to-date") } catch (_: java.io.IOException) { }
            assertEquals("Transient failures preserve the known release", info, repository.cached())
        } finally {
            server.shutdown()
            updates.listFiles()?.forEach { it.delete() }
            prefs.edit().clear().putBoolean("notification_asked", true).putLong("checked", System.currentTimeMillis()).commit()
        }
    }
}
