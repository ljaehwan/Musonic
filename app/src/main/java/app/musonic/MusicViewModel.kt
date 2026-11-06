package app.musonic

import app.musonic.R
import app.musonic.settings.*

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.musonic.data.*
import app.musonic.audio.*
import app.musonic.artwork.ArtworkCache
import coil.request.Disposable
import app.musonic.playback.PlaybackService
import app.musonic.playback.resolveDurationMs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MusicState(
    val profiles: List<Profile> = emptyList(), val active: Profile? = null,
    val loginBusy: Boolean = false, val loading: Boolean = false, val error: UiText? = null,
    val albums: List<Album> = emptyList(), val moreAlbums: Boolean = true,
    val albumsLoading: Boolean = false, val albumPageError: UiText? = null, val artworkRevision: Long = 0,
    val artists: List<Artist> = emptyList(), val recent: List<Song> = emptyList(),
    val refreshing: Boolean = false,
    val favorites: List<Song> = emptyList(), val search: SearchResult = SearchResult(),
    val searching: Boolean = false, val detailTitle: String? = null,
    val detailSongs: List<Song> = emptyList(), val detailAlbums: List<Album> = emptyList(),
    val detailCover: String? = null, val detailLoading: Boolean = false,
    val queue: List<Song> = emptyList(), val queueIndex: Int = -1,
    val current: Song? = null, val playing: Boolean = false, val buffering: Boolean = false,
    val position: Long = 0, val duration: Long = 0, val canSeek: Boolean = false, val shuffle: Boolean = false,
    val repeat: Int = Player.REPEAT_MODE_OFF,
    val streamInfo: StreamInfo? = null,
)

class MusicViewModel(application: Application) : AndroidViewModel(application) {
    val audioSettings = AudioSettings.get(application)
    val equalizerState = PlaybackEqualizer.state
    private val store = ProfileStore(application)
    private val recentStore = RecentPlayStore.get(application)
    private var recentLoadJob: Job? = null
    private val mutable = MutableStateFlow(MusicState())
    val state = mutable.asStateFlow()
    val artworkIdentity = state.map { it.active?.id to it.artworkRevision }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null to 0L)
    private var client: SubsonicClient? = null
    private var controller: MediaController? = null
    private var searchJob: Job? = null
    private var libraryJob: Job? = null
    private var albumPageJob: Job? = null
    private var nextAlbumOffset = 0
    private val artworkPrefs = application.getSharedPreferences("musonic_artwork", 0)
    val artworkLoader = ArtworkCache.loader(application)
    private val prefetches = mutableMapOf<String, Disposable>()
    private var detailJob: Job? = null
    private var favoritesJob: Job? = null
    private var generation = 0
    private var searchVersion = 0
    private var detailVersion = 0
    private var libraryVersion = 0
    private val controllerFuture = MediaController.Builder(application,
        SessionToken(application, ComponentName(application, PlaybackService::class.java))).buildAsync()
    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.contains(Player.EVENT_TIMELINE_CHANGED)) {
                val queue = (0 until player.mediaItemCount).map { i -> player.getMediaItemAt(i).let { item ->
                    val meta = item.mediaMetadata
                    Song(item.mediaId, meta.title?.toString().orEmpty(), meta.artist?.toString().orEmpty(),
                        meta.albumTitle?.toString().orEmpty(), meta.extras?.getString("coverId"),
                        ((meta.durationMs ?: 0) / 1000).coerceIn(0, Int.MAX_VALUE.toLong()).toInt(), meta.extras?.getBoolean("starred") ?: false)
                } }
                mutable.update { existing ->
                    if (existing.queue.map { it.id } == queue.map { it.id }) existing else existing.copy(queue = queue)
                }
            }
            syncPlayer()
        }
        override fun onPlayerError(error: PlaybackException) {
            mutable.update { it.copy(error = uiText(R.string.play_failed)) }
        }
    }

    init {
        viewModelScope.launch { recentStore.changes.collect { loadRecent() } }
        try {
            val profiles = store.read()
            mutable.update { it.copy(profiles = profiles) }
            profiles.firstOrNull { it.id == store.activeId }?.let(::activate)
        } catch (_: Exception) {
            mutable.update { it.copy(error = uiText(R.string.account_open_failed)) }
        }
        controllerFuture.addListener({
            try {
                controller = controllerFuture.get().also { it.addListener(playerListener) }
                // Recover display metadata from the service after an Activity/process recreation.
                val c = controller ?: return@addListener
                if (c.mediaItemCount > 0) {
                    val queue = (0 until c.mediaItemCount).map { index -> c.getMediaItemAt(index).let { item ->
                        Song(item.mediaId, item.mediaMetadata.title?.toString().orEmpty(), item.mediaMetadata.artist?.toString().orEmpty(), item.mediaMetadata.albumTitle?.toString().orEmpty(), item.mediaMetadata.extras?.getString("coverId"),
                            ((item.mediaMetadata.durationMs ?: 0L) / 1000L).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt())
                    } }
                    mutable.update { it.copy(queue = queue) }
                }
                syncPlayer()
            } catch (_: Exception) { mutable.update { it.copy(error = uiText(R.string.player_start_failed)) } }
        }, ContextCompat.getMainExecutor(application))
        viewModelScope.launch { while (true) { syncPlayer(); delay(500) } }
    }

    fun login(profile: Profile, onSuccess: () -> Unit) {
        if (state.value.loginBusy) return
        mutable.update { it.copy(loginBusy = true, error = null) }
        viewModelScope.launch {
            try {
                val api = SubsonicClient(profile, label = { AppPreferences.localizedContext(getApplication()).getString(it) })
                api.ping()
                val profiles = state.value.profiles.filterNot { it.id == profile.id } + profile
                store.write(profiles)
                mutable.update { it.copy(profiles = profiles) }
                activate(profile)
                onSuccess()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.update { it.copy(error = safeError(e)) } }
            finally { mutable.update { it.copy(loginBusy = false) } }
        }
    }

    fun activate(profile: Profile) {
        generation++
        searchJob?.cancel(); libraryJob?.cancel(); albumPageJob?.cancel(); detailJob?.cancel(); favoritesJob?.cancel(); clearPrefetch()
        controller?.stop(); controller?.clearMediaItems()
        client = SubsonicClient(profile, label = { AppPreferences.localizedContext(getApplication()).getString(it) })
        nextAlbumOffset = 0
        store.activeId = profile.id
        mutable.update { MusicState(profiles = it.profiles, active = profile, artworkRevision = artworkPrefs.getLong(profile.id, 0)) }
        loadRecent()
        refresh(false)
    }

    fun removeProfile(id: String) {
        val profiles = state.value.profiles.filterNot { it.id == id }
        try { store.write(profiles) } catch (_: Exception) { mutable.update { it.copy(error = uiText(R.string.account_delete_failed)) }; return }
        mutable.update { it.copy(profiles = profiles) }
        viewModelScope.launch(Dispatchers.IO) { recentStore.remove(id) }
        if (state.value.active?.id == id) {
            generation++
            libraryJob?.cancel(); albumPageJob?.cancel(); searchJob?.cancel(); detailJob?.cancel(); favoritesJob?.cancel(); clearPrefetch()
            controller?.stop(); controller?.clearMediaItems(); client = null; store.activeId = null
            mutable.update { MusicState(profiles = profiles) }
        }
    }

    fun loadRecent(showRefreshing: Boolean = false) {
        val profile = state.value.active ?: return
        val stamp = generation
        recentLoadJob?.cancel()
        if (showRefreshing) mutable.update { it.copy(refreshing = true) }
        recentLoadJob = viewModelScope.launch {
            try {
                val songs = withContext(Dispatchers.IO) { recentStore.read(profile.id) }
                if (stamp == generation) mutable.update { it.copy(recent = songs) }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { if (stamp == generation) mutable.update { it.copy(error = uiText(R.string.recent_failed)) } }
            finally { if (stamp == generation && showRefreshing) mutable.update { it.copy(refreshing = false) } }
        }
    }

    fun refresh(invalidateArtwork: Boolean = true) {
        val api = client ?: return
        val stamp = generation
        val version = ++libraryVersion
        libraryJob?.cancel(); albumPageJob?.cancel(); clearPrefetch()
        if (invalidateArtwork) {
            val revision = state.value.artworkRevision + 1
            artworkPrefs.edit().putLong(api.profile.id, revision).apply()
            mutable.update { it.copy(artworkRevision = revision) }
        }
        mutable.update { it.copy(loading = true, refreshing = invalidateArtwork, albumsLoading = true, albumPageError = null, error = null) }
        libraryJob = viewModelScope.launch {
            var albumsLoaded = false
            try {
                val albums = api.albums()
                if (stamp == generation && version == libraryVersion) {
                    albumsLoaded = true
                    nextAlbumOffset = albums.size
                    mutable.update { it.copy(albums = albums.distinctBy(Album::id), moreAlbums = albums.size == 40, albumsLoading = false) }
                }
                val artists = api.artists()
                if (stamp == generation && version == libraryVersion) mutable.update { it.copy(artists = artists) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (stamp == generation && version == libraryVersion) mutable.update {
                it.copy(error = safeError(e), albumPageError = if (!albumsLoaded && it.albums.isEmpty()) safeError(e) else it.albumPageError)
            } }
            finally { if (stamp == generation && version == libraryVersion) mutable.update { it.copy(loading = false, refreshing = false, albumsLoading = if (albumsLoaded) it.albumsLoading else false) } }
        }
    }

    fun loadMore() {
        if (state.value.albumsLoading || !state.value.moreAlbums) return
        val api = client ?: return
        val stamp = generation
        val version = libraryVersion
        val offset = nextAlbumOffset
        mutable.update { it.copy(albumsLoading = true, albumPageError = null) }
        albumPageJob = viewModelScope.launch {
            try {
                val next = api.albums(offset)
                if (stamp == generation && version == libraryVersion) {
                    nextAlbumOffset = offset + next.size
                    mutable.update {
                        val merged = (it.albums + next).distinctBy(Album::id)
                        it.copy(albums = merged, moreAlbums = next.size == 40 && merged.size > it.albums.size)
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (stamp == generation && version == libraryVersion) mutable.update { it.copy(albumPageError = safeError(e)) } }
            finally { if (stamp == generation && version == libraryVersion) mutable.update { it.copy(albumsLoading = false) } }
        }
    }

    fun artworkRequest(id: String?, size: Int = 512) = client?.let {
        ArtworkCache.request(getApplication(), it, id, size, state.value.artworkRevision)
    }
    fun prefetchCovers(ids: List<String?>) {
        val api = client ?: return
        val covers = ids.filterNotNull().distinct().take(16)
        val keys = covers.associateWith { ArtworkCache.key(api.profile, it, 512, state.value.artworkRevision) }
        keys.forEach { (id, key) ->
            if (key !in prefetches) artworkRequest(id)?.let { prefetches[key] = artworkLoader.enqueue(it) }
        }
        while (prefetches.size > 32) prefetches.remove(prefetches.keys.first())?.dispose()
    }
    private fun clearPrefetch() { prefetches.values.forEach { it.dispose() }; prefetches.clear() }

    fun search(query: String) {
        val version = ++searchVersion
        searchJob?.cancel()
        mutable.update { it.copy(search = SearchResult(), searching = query.isNotBlank()) }
        if (query.isBlank()) return
        val api = client ?: return
        val stamp = generation
        searchJob = viewModelScope.launch {
            delay(350)
            try {
                val result = api.search(query.trim())
                if (stamp == generation && version == searchVersion) mutable.update { it.copy(search = result) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (stamp == generation) mutable.update { it.copy(error = safeError(e)) } }
            finally { if (stamp == generation && version == searchVersion) mutable.update { it.copy(searching = false) } }
        }
    }

    fun openAlbum(album: Album) = detail(album.name, album.coverArt) { it.albumSongs(album.id) to emptyList() }
    fun openArtist(artist: Artist) = detail(artist.name, null) { emptyList<Song>() to it.artistAlbums(artist.id) }
    private fun detail(title: String, cover: String?, block: suspend (SubsonicClient) -> Pair<List<Song>, List<Album>>) {
        val api = client ?: return
        val version = ++detailVersion
        val stamp = generation
        detailJob?.cancel()
        mutable.update { it.copy(detailTitle = title, detailCover = cover, detailSongs = emptyList(), detailAlbums = emptyList(), detailLoading = true) }
        detailJob = viewModelScope.launch {
            try { val (songs, albums) = block(api); if (stamp == generation && version == detailVersion) mutable.update { it.copy(detailSongs = songs, detailAlbums = albums) } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (stamp == generation && version == detailVersion) mutable.update { it.copy(error = safeError(e)) } }
            finally { if (stamp == generation && version == detailVersion) mutable.update { it.copy(detailLoading = false) } }
        }
    }
    fun closeDetail() { detailVersion++; detailJob?.cancel(); mutable.update { it.copy(detailTitle = null, detailLoading = false) } }
    fun loadFavorites() {
        val api = client ?: return
        favoritesJob?.cancel()
        favoritesJob = viewModelScope.launch {
            mutable.update { it.copy(loading = true) }
            try { val songs = api.favorites(); mutable.update { it.copy(favorites = songs) } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.update { it.copy(error = safeError(e)) } }
            finally { mutable.update { it.copy(loading = false) } }
        }
    }
    fun toggleStar(song: Song) {
        val api = client ?: return
        val stamp = generation
        viewModelScope.launch {
            try {
                api.star(song.id, !song.starred)
                if (stamp != generation) return@launch
                fun List<Song>.updated() = map { if (it.id == song.id) it.copy(starred = !song.starred) else it }
                mutable.update { it.copy(detailSongs = it.detailSongs.updated(), queue = it.queue.updated(), recent = it.recent.updated(),
                    favorites = if (song.starred) it.favorites.filterNot { s -> s.id == song.id } else (it.favorites + song.copy(starred = true)).distinctBy(Song::id),
                    search = it.search.copy(songs = it.search.songs.updated())) }
                syncPlayer()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (stamp == generation) mutable.update { it.copy(error = safeError(e)) } }
        }
    }
    fun cover(id: String?) = client?.cover(id)
    fun play(songs: List<Song>, index: Int = 0) {
        val api = client ?: return
        val c = controller ?: run { mutable.update { it.copy(error = uiText(R.string.player_preparing)) }; return }
        if (songs.isEmpty() || index !in songs.indices) return
        val items = songs.map { song -> MediaItem.Builder().setMediaId(song.id).setUri(api.stream(song.id, audioSettings.state.value.quality))
            .setMediaMetadata(MediaMetadata.Builder().setTitle(song.title).setArtist(song.artist).setAlbumTitle(song.album)
                .setArtworkUri(api.cover(song.coverArt)?.let(Uri::parse))
                .setDurationMs(song.duration.toLong().takeIf { it > 0 }?.times(1000L))
                .setExtras(android.os.Bundle().apply { putString("coverId", song.coverArt); putString("profileId", api.profile.id); putBoolean("starred", song.starred) }).build()).build() }
        mutable.update { it.copy(queue = songs) }
        c.setMediaItems(items, index, 0); c.prepare(); c.play()
    }
    fun togglePlay() { controller?.let { if (it.isPlaying) it.pause() else { if (it.playbackState == Player.STATE_IDLE) it.prepare(); it.play() } } }
    fun next() { controller?.seekToNextMediaItem() }
    fun previous() { controller?.seekToPrevious() }
    fun seek(position: Long) {
        val c = controller ?: return
        if (state.value.duration > 0 && c.isCurrentMediaItemSeekable && c.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)) {
            c.seekTo(position.coerceIn(0L, state.value.duration))
        }
    }
    fun jump(index: Int) { controller?.seekTo(index, 0); controller?.play() }
    fun shuffle() { controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } }
    fun repeat() { controller?.let { it.repeatMode = when (it.repeatMode) { Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL; Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE; else -> Player.REPEAT_MODE_OFF } } }
    fun playVoiceSearch(query: String) {
        val api = client ?: run { mutable.update { it.copy(error = uiText(R.string.connect_first)) }; return }
        val stamp = generation
        viewModelScope.launch {
            try {
                kotlinx.coroutines.withTimeout(10_000) { while (controller == null) delay(50) }
                val songs = if (query.isBlank()) withContext(Dispatchers.IO) { recentStore.read(api.profile.id).take(100) }
                    else api.search(query.take(200)).songs
                if (stamp != generation) return@launch
                if (songs.isEmpty()) mutable.update { it.copy(error = uiText(R.string.no_playable_music)) } else play(songs)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (stamp == generation) mutable.update { it.copy(error = safeError(e)) } }
        }
    }
    fun replayWithQuality() { val snapshot = state.value; play(snapshot.queue, snapshot.queueIndex) }
    fun clearError() { mutable.update { it.copy(error = null) } }
    private fun syncPlayer() {
        val c = controller ?: return
        val item = c.currentMediaItem
        val index = c.currentMediaItemIndex
        mutable.update {
            val current = it.queue.getOrNull(index)?.takeIf { song -> song.id == item?.mediaId }
            val duration = resolveDurationMs(c.duration, current?.duration ?: 0, item?.mediaMetadata?.durationMs)
            it.copy(current = current, queueIndex = index,
                streamInfo = StreamInfo.current(c),
                playing = c.isPlaying, buffering = c.playbackState == Player.STATE_BUFFERING,
                position = c.currentPosition.coerceAtLeast(0), duration = duration,
                canSeek = duration > 0 && c.isCurrentMediaItemSeekable && c.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM),
                shuffle = c.shuffleModeEnabled, repeat = c.repeatMode)
        }
    }
    override fun onCleared() { clearPrefetch(); controller?.removeListener(playerListener); MediaController.releaseFuture(controllerFuture); super.onCleared() }
    private fun safeError(error: Exception) = when(error) {
        is SubsonicException -> error.text
        is LocalizedInputException -> error.text
        is IllegalArgumentException -> uiText(R.string.request_failed)
        else -> uiText(R.string.request_retry)
    }
}
