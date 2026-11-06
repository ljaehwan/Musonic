package app.musonic

import app.musonic.update.ReleaseInfo
import app.musonic.update.validAssetUrl
import org.junit.Assert.*
import org.junit.Test

class ReleaseInfoTest {
    private val url = "https://github.com/ljaehwan/Musonic-releases/releases/download/v1/Musonic.apk"
    private fun manifest(code: Int = 10, asset: String = url, hash: String = "a".repeat(64), size: Long = 100) =
        """{"versionCode":$code,"versionName":"0.2.4","apkUrl":"$asset","sha256":"$hash","size":$size}"""
    @Test fun roundTripAndVersionComparison() {
        val release = ReleaseInfo.parse(manifest())
        assertEquals(release, ReleaseInfo.parse(release.json()))
        assertTrue(release.code > 9)
        assertFalse(release.code > 10)
    }
    @Test fun rejectsOtherRepositoriesAndUntrustedUrls() {
        listOf(url.replace("https:", "http:"), url.replace("github.com", "github.com.evil.test"),
            url.replace("ljaehwan", "someone"), url.replace("github.com/", "github.com@evil.test/"),
            "$url?token=secret", "$url#fragment", url.replace("/v1/", "/../"),
            url.replace("/v1/", "/%2e%2e/"), url.replace("github.com", "github.com:8443")).forEach {
            assertFalse(it, validAssetUrl(it))
            assertThrows(IllegalArgumentException::class.java) { ReleaseInfo.parse(manifest(asset = it)) }
        }
    }
    @Test fun rejectsInvalidDigestSizeAndVersion() {
        listOf(manifest(hash = "abc"), manifest(size = 0), manifest(size = 200_000_001), manifest(code = 0), manifest(asset = url.replace(".apk", ".zip"))).forEach {
            assertThrows(IllegalArgumentException::class.java) { ReleaseInfo.parse(it) }
        }
    }
}
