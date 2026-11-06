package app.musonic.artwork

import android.content.Context
import app.musonic.data.Profile
import app.musonic.data.SubsonicClient
import app.musonic.data.Transport
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.ImageRequest
import coil.size.Precision
import okhttp3.Headers
import java.io.File
import java.security.MessageDigest

/** Only image bytes and MIME metadata are persisted, never credential-bearing request URLs. */
object ArtworkCache {
    @Volatile private var shared: ImageLoader? = null
    fun loader(context: Context): ImageLoader = shared ?: synchronized(this) {
        shared ?: ImageLoader.Builder(context.applicationContext)
            .okHttpClient(Transport.http.newBuilder().addInterceptor { chain ->
                val response = chain.proceed(chain.request())
                val headers = Headers.Builder()
                response.header("Content-Type")?.let { headers.add("Content-Type", it) }
                response.newBuilder().headers(headers.build()).build()
            }.build())
            .memoryCache { MemoryCache.Builder(context).maxSizePercent(0.18).build() }
            .diskCache { DiskCache.Builder().directory(File(context.cacheDir, "album-artwork")).maxSizeBytes(160L * 1024 * 1024).build() }
            .respectCacheHeaders(false).crossfade(false).build().also { shared = it }
    }
    fun key(profile: Profile, coverId: String, size: Int, revision: Long): String {
        val source = listOf(profile.id, profile.server, profile.username, coverId, size.toString(), revision.toString())
            .joinToString("") { "${it.length}:$it" }
        return MessageDigest.getInstance("SHA-256").digest(source.toByteArray()).joinToString("") { "%02x".format(it) }
    }
    fun request(context: Context, api: SubsonicClient, coverId: String?, size: Int, revision: Long): ImageRequest? {
        if (coverId == null) return null
        val key = key(api.profile, coverId, size, revision)
        return ImageRequest.Builder(context).data(api.cover(coverId, size))
            .memoryCacheKey(key).diskCacheKey(key).size(size).precision(Precision.EXACT).crossfade(false).build()
    }
}
