package app.musonic.update

import org.json.JSONObject
import java.net.URI

internal const val RELEASE_REPO = "ljaehwan/Musonic-releases"
internal const val RELEASE_PREFIX = "https://github.com/$RELEASE_REPO/releases/download/"
internal data class ReleaseInfo(val code: Int, val name: String, val url: String, val sha256: String, val size: Long) {
    fun json(): String = JSONObject().put("versionCode", code).put("versionName", name)
        .put("apkUrl", url).put("sha256", sha256).put("size", size).toString()
    companion object {
        fun parse(raw: String): ReleaseInfo {
            val j = JSONObject(raw)
            return ReleaseInfo(j.getInt("versionCode"), j.getString("versionName"), j.getString("apkUrl"),
                j.getString("sha256").lowercase(), j.getLong("size")).also {
                require(it.code > 0 && it.name.isNotBlank() && it.name.length <= 80)
                require(validAssetUrl(it.url) && it.url.endsWith(".apk"))
                require(it.sha256.matches(Regex("[a-f0-9]{64}")))
                require(it.size in 1..200_000_000L)
            }
        }
    }
}
internal fun validAssetUrl(url: String): Boolean = runCatching {
    val uri = URI(url)
    url.startsWith(RELEASE_PREFIX) && uri.scheme == "https" && uri.host == "github.com" &&
        uri.userInfo == null && uri.port == -1 && uri.query == null && uri.fragment == null &&
        uri.normalize() == uri && !uri.rawPath.contains('%') && uri.path.removePrefix("/$RELEASE_REPO/releases/download/").split('/').size == 2
}.getOrDefault(false)
