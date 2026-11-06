package app.musonic.data

import app.musonic.R
import app.musonic.settings.*

enum class AuthMode { TOKEN, API_KEY, LEGACY }

// Never log Profile: it contains the reusable authentication secret.
class Profile(
    val id: String,
    val name: String,
    val server: String,
    val username: String,
    val secret: String,
    val authMode: AuthMode = AuthMode.TOKEN,
    val allowHttp: Boolean = false,
) {
    override fun toString() = "Profile(<redacted>)"
}

data class Album(val id: String, val name: String, val artist: String, val coverArt: String?, val songCount: Int = 0, val year: Int = 0)
data class Artist(val id: String, val name: String, val albumCount: Int)
data class Song(val id: String, val title: String, val artist: String, val album: String, val coverArt: String?, val duration: Int, val starred: Boolean = false)
data class Playlist(val id: String, val name: String, val songCount: Int, val coverArt: String?)
data class SearchResult(val albums: List<Album> = emptyList(), val artists: List<Artist> = emptyList(), val songs: List<Song> = emptyList())
class SubsonicException(val code: Int, val text: UiText) : Exception("Subsonic error $code")
