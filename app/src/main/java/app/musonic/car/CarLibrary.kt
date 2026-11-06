package app.musonic.car

import app.musonic.R
import app.musonic.settings.*

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Process
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.*
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import app.musonic.audio.AudioSettings
import app.musonic.data.*
import kotlinx.coroutines.*

/** Account-scoped catalog; all I/O is asynchronous and external URIs are never accepted for playback. */
@androidx.annotation.OptIn(UnstableApi::class)
class CarLibrary(private val context: Context, private val scope: CoroutineScope, private val canConnect: (MediaSession.ControllerInfo) -> Boolean) : MediaLibrarySession.Callback {
    override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult =
        if (canConnect(controller)) MediaSession.ConnectionResult.AcceptedResultBuilder(session).build() else MediaSession.ConnectionResult.reject()
    private val strings get() = AppPreferences.localizedContext(context)
    private val store = ProfileStore(context)
    private suspend fun account(): Profile = withContext(Dispatchers.IO) {
        store.read().firstOrNull { it.id == store.activeId } ?: throw IllegalStateException(strings.getString(R.string.car_connect))
    }
    private suspend fun client(mediaId: String? = null): SubsonicClient {
        val profile = account()
        if (mediaId != null) require(parts(mediaId)[1] == profile.id) { "Server account changed" }
        return SubsonicClient(profile, label = { strings.getString(it) })
    }
    private fun parts(id: String): List<String> {
        require(id.length <= 8192)
        val parts = id.split('/').map(Uri::decode)
        require(parts.size == 5 && parts[0] == "car")
        return parts
    }
    private fun id(profile: Profile, kind: String, key: String = "", parent: String = "") =
        listOf("car", profile.id, kind, key, parent).joinToString("/") { Uri.encode(it) }
    private fun folder(id: String, title: String, subtitle: String? = null, art: String? = null) = MediaItem.Builder().setMediaId(id)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setSubtitle(subtitle).setIsBrowsable(true).setIsPlayable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED).setArtworkUri(art?.let(Uri::parse)).build()).build()
    private fun song(api: SubsonicClient, song: Song, parent: String = "", playable: Boolean = false): MediaItem {
        val metadata = MediaMetadata.Builder().setTitle(song.title).setArtist(song.artist).setAlbumTitle(song.album)
            .setIsBrowsable(false).setIsPlayable(true).setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .setArtworkUri(api.cover(song.coverArt)?.let(Uri::parse)).setDurationMs(song.duration.takeIf { it > 0 }?.toLong()?.times(1000))
            .setExtras(Bundle().apply { putString("profileId", api.profile.id); putString("coverId", song.coverArt); putBoolean("starred", song.starred) }).build()
        return MediaItem.Builder().setMediaId(if (playable) song.id else id(api.profile, "song", song.id, parent)).setMediaMetadata(metadata)
            .apply { if (playable) setUri(api.stream(song.id, AudioSettings.get(context).state.value.quality)) }.build()
    }
    private fun <T> future(block: suspend () -> T): ListenableFuture<T> {
        val result = SettableFuture.create<T>()
        val job = scope.launch { try { result.set(block()) } catch (e: CancellationException) { result.cancel(false) }
            catch (_: Exception) { result.setException(IllegalStateException(strings.getString(R.string.car_load_failed))) } }
        result.addListener({ if (result.isCancelled) job.cancel() }, { it.run() })
        return result
    }
    private fun <T> library(block: suspend () -> LibraryResult<T>): ListenableFuture<LibraryResult<T>> = future<LibraryResult<T>> {
        try { block() } catch (e: CancellationException) { throw e } catch (_: Exception) { errorResult<T>() }
    }
    @Suppress("UNCHECKED_CAST")
    private fun <T> errorResult(): LibraryResult<T> = LibraryResult.ofError<Any>(SessionError.ERROR_UNKNOWN) as LibraryResult<T>
    override fun onGetLibraryRoot(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, params: LibraryParams?) =
        Futures.immediateFuture(LibraryResult.ofItem(folder("root", "Musonic"), LibraryParams.Builder().setExtras(Bundle().apply {
            putBoolean("android.media.browse.SEARCH_SUPPORTED", true)
        }).build()))

    override fun onGetChildren(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, parentId: String,
        page: Int, pageSize: Int, params: LibraryParams?): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = library {
        require(page >= 0 && pageSize > 0)
        if (parentId == "root") {
            val profile = try { account() } catch (_: IllegalStateException) {
                return@library LibraryResult.ofItemList(listOf(MediaItem.Builder().setMediaId("connect-phone").setMediaMetadata(
                    MediaMetadata.Builder().setTitle(strings.getString(R.string.car_connect_short)).setIsBrowsable(false).setIsPlayable(false).build()).build()), params)
            }
            val roots = listOf("albums" to strings.getString(R.string.albums), "favorites" to strings.getString(R.string.favorites), "recent" to strings.getString(R.string.recently_played), "playlists" to strings.getString(R.string.playlists))
                .map { folder(id(profile, it.first), it.second, profile.name) }
            return@library LibraryResult.ofItemList(slice(roots, page, pageSize), params)
        }
        val api = client(parentId)
        val key = parts(parentId)
        val items = when (key[2]) {
            "albums" -> {
                // Car hosts often request all children. Bound each level and expose the next page explicitly.
                val offset = key[3].toIntOrNull()?.coerceAtLeast(0) ?: 0
                val albums = api.albums(offset)
                albums.map { folder(id(api.profile, "album", it.id), it.name, it.artist, api.cover(it.coverArt)) } +
                    if (albums.size == 40) listOf(folder(id(api.profile, "albums", (offset + 40).toString()), strings.getString(R.string.next_albums))) else emptyList()
            }
            "playlists" -> api.playlists().map { folder(id(api.profile, "playlist", it.id), it.name, strings.getString(R.string.song_count, it.songCount), api.cover(it.coverArt)) }
            else -> songs(api, parentId).map { song(api, it, parentId) }
        }
        check(store.activeId == api.profile.id)
        LibraryResult.ofItemList(slice(items, page, pageSize), params)
    }
    private suspend fun songs(api: SubsonicClient, parent: String): List<Song> {
        val key = parts(parent)
        require(key[1] == api.profile.id)
        return when (key[2]) {
            "album" -> api.albumSongs(key[3])
            "playlist" -> api.playlistSongs(key[3])
            "favorites" -> api.favorites()
            "recent" -> withContext(Dispatchers.IO) { RecentPlayStore.get(context).read(api.profile.id).take(200) }
            else -> throw IllegalArgumentException("Unknown folder")
        }
    }
    override fun onGetItem(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, mediaId: String,
    ): ListenableFuture<LibraryResult<MediaItem>> = library {
        if (mediaId == "root") return@library LibraryResult.ofItem(folder("root", "Musonic"), null)
        val api = client(mediaId); val key = parts(mediaId)
        val item = if (key[2] == "song") song(api, api.song(key[3]), key[4]) else folder(mediaId, when (key[2]) {
            "albums" -> strings.getString(R.string.albums); "favorites" -> strings.getString(R.string.favorites); "recent" -> strings.getString(R.string.recently_played); "playlists" -> strings.getString(R.string.playlists); else -> strings.getString(R.string.music)
        })
        check(store.activeId == api.profile.id)
        LibraryResult.ofItem(item, null)
    }
    override fun onSearch(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, query: String, params: LibraryParams?): ListenableFuture<LibraryResult<Void>> = library {
        val api = client(); val items = api.search(query.take(200)).songs
        check(store.activeId == api.profile.id)
        session.notifySearchResultChanged(browser, query, items.size, params)
        LibraryResult.ofVoid(params)
    }
    override fun onGetSearchResult(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, query: String,
        page: Int, pageSize: Int, params: LibraryParams?): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = library {
        val api = client(); val items = api.search(query.take(200)).songs
        check(store.activeId == api.profile.id)
        LibraryResult.ofItemList(slice(items, page, pageSize).map { song(api, it) }, params)
    }
    override fun onAddMediaItems(mediaSession: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: List<MediaItem>): ListenableFuture<List<MediaItem>> = future {
        if (controller.uid == Process.myUid() && mediaItems.all { it.localConfiguration != null }) return@future mediaItems
        val api = client()
        val result = mediaItems.take(200).flatMap { item ->
            val query = item.requestMetadata.searchQuery
            if (!query.isNullOrBlank()) api.search(query.take(200)).songs.map { song(api, it, playable = true) }
            else {
                val key = parts(item.mediaId); require(key[1] == api.profile.id && key[2] == "song")
                listOf(song(api, api.song(key[3]), playable = true))
            }
        }
        check(store.activeId == api.profile.id)
        result
    }
    override fun onSetMediaItems(mediaSession: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: List<MediaItem>,
        startIndex: Int, startPositionMs: Long): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = future {
        if (controller.uid == Process.myUid() && mediaItems.all { it.localConfiguration != null })
            return@future MediaSession.MediaItemsWithStartPosition(mediaItems, startIndex, startPositionMs)
        val api = client()
        val item = mediaItems.firstOrNull() ?: throw IllegalArgumentException("Empty selection")
        val query = item.requestMetadata.searchQuery
        var index = 0
        val tracks = if (!query.isNullOrBlank()) api.search(query.take(200)).songs else {
            val key = parts(item.mediaId); require(key[1] == api.profile.id)
            if (key[2] == "song") {
                val queue = if (key[4].isNotEmpty()) songs(api, key[4]) else listOf(api.song(key[3]))
                index = queue.indexOfFirst { it.id == key[3] }
                require(index >= 0)
                queue
            } else songs(api, item.mediaId)
        }
        require(tracks.isNotEmpty())
        check(store.activeId == api.profile.id)
        MediaSession.MediaItemsWithStartPosition(tracks.map { song(api, it, playable = true) }, index, 0)
    }
    override fun onPlaybackResumption(mediaSession: MediaSession, controller: MediaSession.ControllerInfo): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = future {
        val api = client()
        val recent = withContext(Dispatchers.IO) { RecentPlayStore.get(context).read(api.profile.id).take(100) }
        check(store.activeId == api.profile.id && recent.isNotEmpty())
        MediaSession.MediaItemsWithStartPosition(recent.map { song(api, it, playable = true) }, 0, 0)
    }
    private fun <T> slice(items: List<T>, page: Int, size: Int): List<T> {
        require(page >= 0 && size > 0)
        val from = (page.toLong() * size).coerceAtMost(items.size.toLong()).toInt()
        return items.subList(from, (from.toLong() + size).coerceAtMost(items.size.toLong()).toInt())
    }
}
