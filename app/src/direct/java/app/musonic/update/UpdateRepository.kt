package app.musonic.update

import app.musonic.R
import app.musonic.settings.*

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import app.musonic.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

internal class UpdateRepository(private val context: Context,
    private val client: OkHttpClient = OkHttpClient.Builder().followSslRedirects(false)
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(45, TimeUnit.SECONDS).build()) {
    private val strings get() = AppPreferences.localizedContext(context)
    private val prefs = context.getSharedPreferences("musonic_updates", Context.MODE_PRIVATE)
    fun cached(): ReleaseInfo? = runCatching { ReleaseInfo.parse(prefs.getString("release", null) ?: return null) }.getOrNull()?.takeIf { it.code > BuildConfig.VERSION_CODE }
    suspend fun checkForUpdate(force: Boolean = false): ReleaseInfo? = withContext(Dispatchers.IO) {
        checkLock.withLock {
            val elapsed = System.currentTimeMillis() - prefs.getLong("checked", 0)
            if (!force && elapsed in 0 until TimeUnit.HOURS.toMillis(6)) return@withLock cached()
            val raw = getText("https://api.github.com/repos/$RELEASE_REPO/releases/latest")
            val release = JSONObject(raw)
            require(!release.optBoolean("draft") && !release.optBoolean("prerelease"))
            val assets = release.getJSONArray("assets")
            val manifestUrl = (0 until assets.length()).map { assets.getJSONObject(it) }
                .firstOrNull { it.optString("name") == "update.json" }?.getString("browser_download_url")
                ?: throw IOException(strings.getString(R.string.update_info_missing))
            require(validAssetUrl(manifestUrl))
            val info = ReleaseInfo.parse(getText(manifestUrl))
            require((0 until assets.length()).map { assets.getJSONObject(it) }.any {
                it.optString("browser_download_url") == info.url && it.optLong("size") == info.size
            })
            prefs.edit().putString("release", info.json()).putLong("checked", System.currentTimeMillis()).apply()
            info.takeIf { it.code > BuildConfig.VERSION_CODE }
        }
    }
    private fun getText(url: String): String = client.newCall(Request.Builder().url(url)
        .header("Accept", "application/vnd.github+json").header("User-Agent", "Musonic/${BuildConfig.VERSION_NAME}").build()).execute().use {
        if (!it.isSuccessful) throw IOException(if (it.code == 404) strings.getString(R.string.update_none) else strings.getString(R.string.update_server_failed))
        val bytes = it.body?.byteStream()?.use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (output.size() <= 512_000) {
                val count = input.read(buffer, 0, minOf(buffer.size, 512_001 - output.size()))
                if (count < 0) break
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        } ?: throw IOException(strings.getString(R.string.empty_response))
        require(bytes.size <= 512_000)
        bytes.toString(Charsets.UTF_8)
    }
    suspend fun download(info: ReleaseInfo, progress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        downloadLock.withLock {
            val dir = File(context.filesDir, "updates").apply { mkdirs() }
            val target = File(dir, "Musonic-${info.code}.apk")
            if (target.exists()) {
                if (runCatching { verify(target, info) }.isSuccess) return@withLock target
                target.delete()
            }
            val partial = File(dir, "download.part")
            try {
                client.newCall(Request.Builder().url(info.url).build()).execute().use { response ->
                    if (!response.isSuccessful) throw IOException(strings.getString(R.string.download_failed))
                    val body = response.body ?: throw IOException(strings.getString(R.string.empty_file))
                    body.byteStream().use { input -> partial.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var total = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            require(total <= info.size)
                            output.write(buffer, 0, count)
                            progress(total.toFloat() / info.size)
                        }
                    } }
                }
                verify(partial, info)
                kotlin.check(partial.renameTo(target))
                dir.listFiles()?.filter { it != target }?.forEach { it.delete() }
                target
            } finally { partial.delete() }
        }
    }
    @Suppress("DEPRECATION")
    fun verify(file: File, info: ReleaseInfo) {
        require(file.length() == info.size) { strings.getString(R.string.file_size_mismatch) }
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> val bytes = ByteArray(65536); while (true) { val n = input.read(bytes); if (n < 0) break; digest.update(bytes, 0, n) } }
        require(digest.digest().joinToString("") { "%02x".format(it) } == info.sha256) { strings.getString(R.string.file_verification_failed) }
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val apk = context.packageManager.getPackageArchiveInfo(file.path, flags) ?: error(strings.getString(R.string.invalid_apk))
        val installed = context.packageManager.getPackageInfo(context.packageName, flags)
        val code = if (Build.VERSION.SDK_INT >= 28) apk.longVersionCode else apk.versionCode.toLong()
        require(apk.packageName == context.packageName && code == info.code.toLong() && code > BuildConfig.VERSION_CODE)
        fun signatures(p: PackageInfo) = (if (Build.VERSION.SDK_INT >= 28) p.signingInfo?.apkContentsSigners else p.signatures)
            ?.map { it.toCharsString() }?.toSet().orEmpty()
        require(signatures(installed).isNotEmpty() && signatures(installed) == signatures(apk)) { strings.getString(R.string.signature_mismatch) }
    }
    companion object { private val checkLock = Mutex(); private val downloadLock = Mutex() }
}
