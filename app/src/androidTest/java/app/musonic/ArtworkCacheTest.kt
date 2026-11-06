package app.musonic

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import app.musonic.artwork.ArtworkCache
import app.musonic.data.*
import coil.decode.DataSource
import coil.request.SuccessResult
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

class ArtworkCacheTest {
    @Test fun artworkSurvivesNewAuthUrlsAndMemoryEvictionWithoutSharingAccounts(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val server = MockWebServer()
        server.start()
        val bytes = ByteArrayOutputStream().also { out -> Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }.compress(Bitmap.CompressFormat.PNG, 100, out) }.toByteArray()
        val profile = Profile(UUID.randomUUID().toString(), "Fixture", server.url("/").toString(), "cache-user", "cache-secret-fixture", allowHttp = true)
        val api = SubsonicClient(profile)
        val loader = ArtworkCache.loader(context)
        fun response() = MockResponse().setHeader("Content-Type", "image/png").setHeader("Cache-Control", "no-store")
            .setHeader("X-Echo-Private-Url", api.cover("cover1")!!).setBody(Buffer().write(bytes))
        try {
            server.enqueue(response())
            val first = ArtworkCache.request(context, api, "cover1", 64, 0)!!
            assertEquals(DataSource.NETWORK, (loader.execute(first) as SuccessResult).dataSource)
            val second = ArtworkCache.request(context, api, "cover1", 64, 0)!!
            assertNotEquals("Salted auth URLs should differ", first.data, second.data)
            assertEquals(DataSource.MEMORY_CACHE, (loader.execute(second) as SuccessResult).dataSource)
            loader.memoryCache!!.clear()
            assertEquals(DataSource.DISK, (loader.execute(ArtworkCache.request(context, api, "cover1", 64, 0)!!) as SuccessResult).dataSource)
            assertEquals(1, server.requestCount)
            val another = SubsonicClient(Profile("other-account", "Other", profile.server, "other-user", "different-secret", allowHttp = true))
            server.enqueue(response())
            assertEquals(DataSource.NETWORK, (loader.execute(ArtworkCache.request(context, another, "cover1", 64, 0)!!) as SuccessResult).dataSource)
            server.enqueue(response())
            assertEquals("Refresh revision must fetch changed artwork", DataSource.NETWORK, (loader.execute(ArtworkCache.request(context, api, "cover1", 64, 1)!!) as SuccessResult).dataSource)
            val compressed = ByteArrayOutputStream().also { out -> java.util.zip.GZIPOutputStream(out).use { it.write(bytes) } }.toByteArray()
            server.enqueue(MockResponse().setHeader("Content-Type", "image/png").setHeader("Content-Encoding", "gzip").setBody(Buffer().write(compressed)))
            assertEquals("Compressed artwork responses must decode before metadata filtering", DataSource.NETWORK,
                (loader.execute(ArtworkCache.request(context, api, "gzipped-cover", 64, 0)!!) as SuccessResult).dataSource)
            assertEquals(4, server.requestCount)
            File(context.cacheDir, "album-artwork").listFiles()?.filter { it.extension == "0" || it.name == "journal" }?.forEach {
                val text = it.readText()
                assertFalse("Cache metadata must not contain request URLs", text.contains("getCoverArt.view"))
                assertFalse(text.contains("cache-secret-fixture"))
                assertFalse(text.contains("cache-user"))
            }
        } finally { server.shutdown() }
    }
}
