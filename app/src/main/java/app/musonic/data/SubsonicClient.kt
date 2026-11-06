package app.musonic.data

import app.musonic.R
import app.musonic.settings.*

import app.musonic.audio.StreamQuality

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

object Transport {
    // Never forward credential-bearing URLs to redirect destinations or log requests.
    val http = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
}

class SubsonicClient(val profile: Profile, private val http: OkHttpClient = Transport.http,
    private val label: (Int) -> String = { when (it) {
        R.string.untitled -> "Untitled"; R.string.unknown_artist -> "Unknown artist"; else -> "Playlists"
    } }) {
    private val base = normalizeServer(profile.server, profile.allowHttp)

    fun url(endpoint: String, params: Map<String, String> = emptyMap()): HttpUrl {
        require(endpoint.matches(Regex("[A-Za-z0-9]+")))
        val builder = base.newBuilder().addPathSegment("rest").addPathSegment("$endpoint.view")
            .addQueryParameter("u", profile.username).addQueryParameter("v", "1.16.1")
            .addQueryParameter("c", "Musonic").addQueryParameter("f", "json")
        when (profile.authMode) {
            AuthMode.TOKEN -> {
                val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }.hex()
                builder.addQueryParameter("t", token(profile.secret, salt)).addQueryParameter("s", salt)
            }
            AuthMode.API_KEY -> builder.addQueryParameter("apiKey", profile.secret)
            AuthMode.LEGACY -> builder.addQueryParameter("p", "enc:" + profile.secret.toByteArray(Charsets.UTF_8).hex())
        }
        params.forEach { (key, value) -> builder.addQueryParameter(key, value) }
        return builder.build()
    }

    suspend fun request(endpoint: String, params: Map<String, String> = emptyMap()): JSONObject = withContext(Dispatchers.IO) {
        try {
            http.newCall(Request.Builder().url(url(endpoint, params)).build()).execute().use { response ->
                if (response.isRedirect) throw SubsonicException(-1, uiText(R.string.server_redirect))
                if (!response.isSuccessful) throw SubsonicException(response.code, uiText(R.string.server_http_error, response.code))
                val envelope = try { JSONObject(response.body?.string().orEmpty()).getJSONObject("subsonic-response") }
                    catch (_: Exception) { throw SubsonicException(-2, uiText(R.string.server_not_subsonic)) }
                if (envelope.optString("status") != "ok") {
                    val code = envelope.optJSONObject("error")?.optInt("code") ?: 0
                    val message = when (code) {
                        40 -> uiText(R.string.auth_invalid)
                        41 -> uiText(R.string.auth_legacy_required)
                        42, 43, 44 -> uiText(R.string.auth_check)
                        50 -> uiText(R.string.permission_denied)
                        60 -> uiText(R.string.api_permission)
                        70 -> uiText(R.string.track_not_found)
                        20, 30 -> uiText(R.string.api_incompatible)
                        else -> uiText(R.string.server_code_error, code)
                    }
                    throw SubsonicException(code, message)
                }
                envelope
            }
        } catch (error: SubsonicException) { throw error }
        catch (_: javax.net.ssl.SSLException) { throw SubsonicException(-3, uiText(R.string.certificate_error)) }
        catch (_: IOException) { throw SubsonicException(-4, uiText(R.string.connection_failed)) }
    }

    suspend fun ping() = request("ping").optString("type", "Subsonic")
    suspend fun albums(offset: Int = 0) = request("getAlbumList2", mapOf("type" to "newest", "size" to "40", "offset" to "$offset"))
        .optJSONObject("albumList2")?.optJSONArray("album").objects().map(::album)
    suspend fun song(id: String) = song(request("getSong", mapOf("id" to id)).getJSONObject("song"))
    suspend fun albumSongs(id: String) = request("getAlbum", mapOf("id" to id)).optJSONObject("album")?.optJSONArray("song").objects().map(::song)
    suspend fun artists() = request("getArtists").optJSONObject("artists")?.optJSONArray("index").objects()
        .flatMap { it.optJSONArray("artist").objects() }.map(::artist)
    suspend fun artistAlbums(id: String) = request("getArtist", mapOf("id" to id)).optJSONObject("artist")?.optJSONArray("album").objects().map(::album)
    suspend fun playlists() = request("getPlaylists").optJSONObject("playlists")?.optJSONArray("playlist").objects().map {
        Playlist(it.getString("id"), it.optString("name").ifBlank { label(R.string.playlists) }, it.optInt("songCount"), it.optional("coverArt"))
    }
    suspend fun playlistSongs(id: String) = request("getPlaylist", mapOf("id" to id)).optJSONObject("playlist")?.optJSONArray("entry").objects().map(::song)
    suspend fun search(query: String): SearchResult {
        val result = request("search3", mapOf("query" to query, "songCount" to "100", "albumCount" to "40", "artistCount" to "40")).optJSONObject("searchResult3")
        return SearchResult(result?.optJSONArray("album").objects().map(::album), result?.optJSONArray("artist").objects().map(::artist), result?.optJSONArray("song").objects().map(::song))
    }
    suspend fun favorites() = request("getStarred2").optJSONObject("starred2")?.optJSONArray("song").objects().map(::song)
    suspend fun star(id: String, starred: Boolean) { request(if (starred) "star" else "unstar", mapOf("id" to id)) }
    fun cover(id: String?, size: Int = 600) = id?.let { url("getCoverArt", mapOf("id" to it, "size" to size.toString())).toString() }
    fun stream(id: String, quality: StreamQuality = StreamQuality.ORIGINAL) = url("stream", buildMap {
        put("id", id)
        putAll(quality.parameters())
    }).toString()

    private fun album(j: JSONObject) = Album(j.getString("id"), j.optString("name").ifBlank { label(R.string.untitled) }, j.optString("artist").ifBlank { label(R.string.unknown_artist) }, j.optional("coverArt"), j.optInt("songCount"), j.optInt("year"))
    private fun artist(j: JSONObject) = Artist(j.getString("id"), j.optString("name").ifBlank { label(R.string.unknown_artist) }, j.optInt("albumCount"))
    private fun song(j: JSONObject) = Song(j.getString("id"), j.optString("title").ifBlank { label(R.string.untitled) }, j.optString("artist").ifBlank { label(R.string.unknown_artist) }, j.optString("album"), j.optional("coverArt"), j.optInt("duration"), !j.optional("starred").isNullOrEmpty())

    companion object {
        fun normalizeServer(input: String, allowHttp: Boolean = false): HttpUrl {
            val parsed = input.trim().trimEnd('/').toHttpUrlOrNull() ?: throw LocalizedInputException(uiText(R.string.invalid_url))
            if (!(parsed.scheme == "https" || (allowHttp && app.musonic.BuildConfig.ALLOW_HTTP))) throw LocalizedInputException(uiText(R.string.http_disabled))
            if (!(parsed.username.isEmpty() && parsed.password.isEmpty())) throw LocalizedInputException(uiText(R.string.url_credentials))
            if (!(parsed.query == null && parsed.fragment == null)) throw LocalizedInputException(uiText(R.string.url_query))
            // HttpUrl keeps a root slash; remove it before appending /rest without losing subpaths.
            return parsed.newBuilder().encodedPath(parsed.encodedPath.trimEnd('/').ifBlank { "/" }).build()
        }
        fun token(password: String, salt: String) = MessageDigest.getInstance("MD5").digest((password + salt).toByteArray(Charsets.UTF_8)).hex()

    }
}
private fun ByteArray.hex() = joinToString("") { "%02x".format(it.toInt() and 255) }
private fun JSONObject.optional(key: String): String? = if (isNull(key)) null else optString(key).ifBlank { null }
private fun JSONArray?.objects() = if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }
