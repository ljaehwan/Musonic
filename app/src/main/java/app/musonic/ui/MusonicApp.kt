@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package app.musonic.ui

import app.musonic.R

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import app.musonic.update.*
import androidx.lifecycle.viewmodel.compose.viewModel
import app.musonic.BuildConfig
import app.musonic.MusicState
import app.musonic.MusicViewModel
import app.musonic.data.*
import coil.compose.AsyncImage
import java.util.UUID
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged

// Scroll content extends behind the dock; bottom content padding keeps its last item reachable.
private val LocalBottomOverlay = compositionLocalOf { 0.dp }

@Composable
private fun pageContentPadding() = PaddingValues(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 24.dp + LocalBottomOverlay.current)

@Composable
fun MusonicApp(vm: MusicViewModel) {
    val strings = androidx.compose.ui.platform.LocalContext.current

    UpdateDialog()
    val state by vm.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var addingServer by rememberSaveable { mutableStateOf(false) }
    var playerOpen by rememberSaveable { mutableStateOf(false) }
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(state.error) { state.error?.let { snack.showSnackbar(it.resolve(strings)); vm.clearError() } }
    var previousProfile by rememberSaveable { mutableStateOf(state.active?.id) }
    LaunchedEffect(state.active?.id) {
        if (previousProfile != state.active?.id) { tab = 0; playerOpen = false; previousProfile = state.active?.id }
    }
    BackHandler(state.detailTitle != null && !playerOpen) { vm.closeDetail() }
    val dockVisible = state.active != null && !addingServer
    val dockEdgeHeight = with(LocalDensity.current) { 16.dp.toPx() }
    Scaffold(containerColor = CanvasColor, snackbarHost = { SnackbarHost(snack) }, bottomBar = {
        if (dockVisible) Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(
            0f to CanvasColor.copy(alpha = 0f),
            .5f to CanvasColor.copy(alpha = .60f),
            1f to CanvasColor.copy(alpha = .94f),
            endY = dockEdgeHeight))) {
            state.current?.let { MiniPlayer(state, vm) { playerOpen = true } }
            Surface(color = Color.Transparent, tonalElevation = 0.dp,
                modifier = Modifier.testTag("bottom-navigation")) {
                Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 12.dp, end = 12.dp, top = if (state.current != null) 2.dp else 18.dp, bottom = 6.dp).selectableGroup()) {
                    listOf(strings.getString(R.string.library) to Icons.Outlined.LibraryMusic, strings.getString(R.string.search) to Icons.Outlined.Search,
                        strings.getString(R.string.liked) to Icons.Outlined.FavoriteBorder, strings.getString(R.string.settings) to Icons.Outlined.Settings).forEachIndexed { index, (label, icon) ->
                        val color = if (tab == index) Accent else Muted
                        Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).selectable(selected = tab == index, role = Role.Tab,
                            onClick = { tab = index; vm.closeDetail(); if (index == 2) vm.loadFavorites() }).padding(vertical = 7.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Icon(icon, null, tint = color, modifier = Modifier.size(25.dp))
                            Text(label, color = color, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }) { padding ->
        val direction = LocalLayoutDirection.current
        CompositionLocalProvider(LocalBottomOverlay provides if (dockVisible) padding.calculateBottomPadding() else 0.dp) {
            Box(Modifier.fillMaxSize().padding(start = padding.calculateStartPadding(direction), end = padding.calculateEndPadding(direction),
                top = padding.calculateTopPadding(), bottom = if (dockVisible) 0.dp else padding.calculateBottomPadding())) {
                when {
                    addingServer || state.active == null -> ConnectionScreen(state, vm, onConnected = { addingServer = false }, onCancel = if (state.active != null) ({ addingServer = false }) else null)
                    state.detailTitle != null -> DetailScreen(state, vm)
                    tab == 0 -> LibraryScreen(state, vm)
                    tab == 1 -> SearchScreen(state, vm)
                    tab == 2 -> SongPage(strings.getString(R.string.liked), state.favorites, state, vm) { vm.loadFavorites() }
                    else -> ServerScreen(state, vm) { addingServer = true }
                }
            }
        }
    }
    if (playerOpen && state.current != null) ModalBottomSheet(onDismissRequest = { playerOpen = false },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = PlayerBackground, contentColor = TextPrimary,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Muted.copy(alpha = .5f), width = 36.dp, height = 4.dp) }) { NowPlaying(state, vm) }
}

@Composable
private fun ConnectionScreen(state: MusicState, vm: MusicViewModel, onConnected: () -> Unit, onCancel: (() -> Unit)?) {
    val strings = androidx.compose.ui.platform.LocalContext.current

    var name by rememberSaveable { mutableStateOf("") }
    var server by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    // Authentication secrets intentionally never enter saved-instance state.
    var secret by remember { mutableStateOf("") }
    var mode by rememberSaveable { mutableStateOf(AuthMode.TOKEN) }
    var allowHttp by rememberSaveable { mutableStateOf(false) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }
    var validation by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.ic_musonic_logo), "Musonic", modifier = Modifier.width(128.dp).height(34.dp))
            Spacer(Modifier.weight(1f))
            onCancel?.let { TextButton(onClick = it) { Text(strings.getString(R.string.cancel)) } }
        }
        Column(Modifier.padding(top = 24.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(strings.getString(R.string.connect_server), style = MaterialTheme.typography.headlineLarge)
            Text(strings.getString(R.string.connect_subtitle), color = Muted, style = MaterialTheme.typography.bodyLarge)
        }
        if (state.profiles.isNotEmpty() && state.active == null) {
            Group {
                state.profiles.forEach { profile ->
                    Row(Modifier.fillMaxWidth().clickable { vm.activate(profile); onConnected() }.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(profile.name, Modifier.weight(1f)); Icon(Icons.Rounded.ChevronRight, null, tint = Muted)
                    }
                }
            }
        }
        Group {
            FormField(server, { server = it }, strings.getString(R.string.server_address), "https://music.example.com", KeyboardType.Uri)
            FormDivider()
            FormField(name, { name = it }, strings.getString(R.string.server_name), strings.getString(R.string.my_music))
        }
        Group {
            FormField(username, { username = it }, strings.getString(R.string.username), strings.getString(R.string.login_id))
            FormDivider()
            TextField(secret, { secret = it }, label = { Text(if (mode == AuthMode.API_KEY) strings.getString(R.string.api_key) else strings.getString(R.string.password)) },
                modifier = Modifier.fillMaxWidth(), singleLine = true, colors = formColors(),
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = { IconButton(onClick = { visible = !visible }) { Icon(if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, strings.getString(R.string.toggle_password), Modifier.size(20.dp)) } })
        }
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { advanced = !advanced }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(strings.getString(R.string.advanced), Modifier.weight(1f), color = Muted, fontSize = 15.sp)
            Icon(if (advanced) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = Muted, modifier = Modifier.size(20.dp))
        }
        if (advanced) Group {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(strings.getString(R.string.auth_method), style = MaterialTheme.typography.titleMedium)
                Segments(listOf(strings.getString(R.string.preset_flat), strings.getString(R.string.api_key), strings.getString(R.string.legacy)), mode.ordinal) { mode = AuthMode.entries[it] }
                if (BuildConfig.ALLOW_HTTP) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(strings.getString(R.string.allow_http), Modifier.weight(1f), fontSize = 15.sp)
                    Switch(allowHttp, { allowHttp = it })
                }
                if (allowHttp) Text(strings.getString(R.string.http_warning), color = Muted, style = MaterialTheme.typography.bodyMedium)
            }
        }
        validation?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = {
            validation = null
            try {
                val normalized = SubsonicClient.normalizeServer(server, allowHttp)
                require(username.isNotBlank() && secret.isNotEmpty()) { strings.getString(R.string.credentials_required) }
                vm.login(Profile(UUID.randomUUID().toString(), name.trim().ifBlank { normalized.host }, normalized.toString(), username.trim(), secret, mode, allowHttp)) { secret = ""; onConnected() }
            } catch (e: IllegalArgumentException) { validation = (e as? app.musonic.settings.LocalizedInputException)?.text?.resolve(strings) ?: e.message }
        }, enabled = !state.loginBusy, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(14.dp)) {
            if (state.loginBusy) CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
            else Text(strings.getString(R.string.sign_in), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
        Text(strings.getString(R.string.connection_privacy), color = Muted, fontSize = 12.sp, lineHeight = 19.sp)
        PrivacyLink()
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun formColors() = TextFieldDefaults.colors(focusedContainerColor = Grouped, unfocusedContainerColor = Grouped,
    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
    focusedLabelColor = Accent, unfocusedLabelColor = Muted)

@Composable
private fun FormField(value: String, change: (String) -> Unit, label: String, placeholder: String, keyboard: KeyboardType = KeyboardType.Text) {
    val strings = androidx.compose.ui.platform.LocalContext.current

    TextField(value, change, label = { Text(label) }, placeholder = { Text(placeholder, color = Muted) },
        modifier = Modifier.fillMaxWidth(), colors = formColors(), singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard))
}

@Composable
private fun Group(content: @Composable ColumnScope.() -> Unit) {
    val strings = androidx.compose.ui.platform.LocalContext.current

    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Grouped), content = content)
}

@Composable
private fun FormDivider() {
    val strings = androidx.compose.ui.platform.LocalContext.current
 HorizontalDivider(Modifier.padding(start = 16.dp), color = Separator, thickness = .5.dp) }

@Composable
private fun Segments(labels: List<String>, selected: Int, select: (Int) -> Unit) {
    val strings = androidx.compose.ui.platform.LocalContext.current

    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Grouped).padding(3.dp).selectableGroup()) {
        labels.forEachIndexed { index, label ->
            val active = selected == index
            Box(Modifier.weight(1f).then(if (active) Modifier.shadow(2.dp, RoundedCornerShape(8.dp)) else Modifier)
                .clip(RoundedCornerShape(8.dp)).background(if (active) SelectedBackground else Color.Transparent)
                .selectable(selected = active, role = Role.Tab, onClick = { select(index) }).heightIn(min = 42.dp).padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                Text(label, fontSize = 13.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
}

@Composable
private fun PageTitle(title: String, subtitle: String? = null, action: @Composable (() -> Unit)? = null) {
    val strings = androidx.compose.ui.platform.LocalContext.current

    Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, style = MaterialTheme.typography.headlineLarge)
            subtitle?.let { Text(it, color = Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        action?.invoke()
    }
}

@Composable
private fun LibraryScreen(state: MusicState, vm: MusicViewModel) {
    val strings = androidx.compose.ui.platform.LocalContext.current

    var section by rememberSaveable { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    val albumRows = remember(state.albums) { state.albums.chunked(2) }
    LaunchedEffect(state.active?.id, section) { listState.scrollToItem(0) }
    LaunchedEffect(listState, section, state.active?.id, state.albums.size, state.albumsLoading, state.albumPageError, state.artworkRevision) {
        if (section == 0) snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .distinctUntilChanged().collect { last ->
                if (last >= 0) {
                    val nextAlbum = ((last - 3 + 1).coerceAtLeast(0) * 2).coerceAtMost(state.albums.size)
                    vm.prefetchCovers(state.albums.drop(nextAlbum).take(12).map { it.coverArt })
                    if (state.albums.isNotEmpty() && last >= 3 + (state.albums.size + 1) / 2 - 6 &&
                        !state.albumsLoading && state.moreAlbums && state.albumPageError == null) vm.loadMore()
                }
            }
    }
    RefreshContainer(state.refreshing, { if (section == 2) vm.loadRecent(true) else vm.refresh() }) {
        LazyColumn(Modifier.fillMaxSize().testTag("album-library"), state = listState, contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 24.dp + LocalBottomOverlay.current), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            item { PageTitle(strings.getString(R.string.library), state.active?.name) }
            item { Segments(listOf(strings.getString(R.string.albums), strings.getString(R.string.artists), strings.getString(R.string.recently_played)), section) { section = it; if (it == 2) vm.loadRecent() } }
            if (section == 0) {
                item { SectionTitle(strings.getString(R.string.recent_added), "") }
                items(albumRows, key = { it.first().id }) { row -> AlbumRow(row, vm) }
                if (state.albumsLoading && state.albums.isNotEmpty()) item { Loading() }
                state.albumPageError?.let { message -> item {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(message.resolve(strings), color = Muted, style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { if (state.albums.isEmpty()) vm.refresh(false) else vm.loadMore() }) { Text(strings.getString(R.string.retry)) }
                    }
                } }
                if (!state.loading && state.albums.isEmpty()) item { Empty(strings.getString(R.string.no_albums), strings.getString(R.string.refresh_library_hint), Icons.Rounded.Album) }
            } else if (section == 1) {
                item { SectionTitle(strings.getString(R.string.artists), "${state.artists.size}") }
                items(state.artists, key = Artist::id) { artist -> ArtistRow(artist) { vm.openArtist(artist) } }
                if (!state.loading && state.artists.isEmpty()) item { Empty(strings.getString(R.string.no_artists), strings.getString(R.string.scan_hint), Icons.Rounded.PersonOutline) }
            } else {
                item { SectionTitle(strings.getString(R.string.recent_music), "") }
                items(state.recent, key = Song::id) { song -> SongRow(song, state, vm) { vm.play(state.recent, state.recent.indexOf(song)) } }
                if (state.recent.isEmpty()) item { Empty(strings.getString(R.string.recent_empty), strings.getString(R.string.recent_hint), Icons.Rounded.History) }

            }
            if (state.loading && !state.refreshing && section != 2) item { Loading() }
        }
    }
}

@Composable
private fun SearchScreen(state: MusicState, vm: MusicViewModel) {
    val strings = androidx.compose.ui.platform.LocalContext.current

    var query by rememberSaveable(state.active?.id) { mutableStateOf("") }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = pageContentPadding(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { PageTitle(strings.getString(R.string.search)) }
        item { TextField(query, { query = it; vm.search(it) }, placeholder = { Text(strings.getString(R.string.search_placeholder)) }, singleLine = true,
            leadingIcon = { Icon(Icons.Rounded.Search, null) }, trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = ""; vm.search("") }) { Icon(Icons.Rounded.Close, strings.getString(R.string.clear_search)) } },
            colors = formColors(), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) }
        if (state.searching) item { Loading() }
        if (query.isBlank()) item { Empty(strings.getString(R.string.search_music), strings.getString(R.string.search_hint), Icons.Outlined.Search) }
        if (state.search.artists.isNotEmpty()) {
            item { SectionTitle(strings.getString(R.string.artists), "${state.search.artists.size}") }
            items(state.search.artists, key = { "artist:${it.id}" }) { artist -> ArtistRow(artist) { vm.openArtist(artist) } }
        }
        if (state.search.albums.isNotEmpty()) {
            item { SectionTitle(strings.getString(R.string.albums), "${state.search.albums.size}") }
            items(state.search.albums.chunked(2), key = { "album:${it.first().id}" }) { AlbumRow(it, vm) }
        }
        if (state.search.songs.isNotEmpty()) {
            item { SectionTitle(strings.getString(R.string.songs), "${state.search.songs.size}") }
            items(state.search.songs, key = { "song:${it.id}" }) { song -> SongRow(song, state, vm) { vm.play(state.search.songs, state.search.songs.indexOf(song)) } }
        }
        if (query.isNotBlank() && !state.searching && state.search == SearchResult()) item { Empty(strings.getString(R.string.no_results), strings.getString(R.string.search_retry_hint), Icons.Rounded.SearchOff) }
    }
}

@Composable
private fun DetailScreen(state: MusicState, vm: MusicViewModel) {
    val strings = androidx.compose.ui.platform.LocalContext.current

    LazyColumn(Modifier.fillMaxSize(), contentPadding = pageContentPadding(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { IconButton(onClick = vm::closeDetail) { Icon(Icons.Rounded.ChevronLeft, strings.getString(R.string.back_library), tint = Accent, modifier = Modifier.size(30.dp)) } }
        item { Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Artwork(state.detailCover, state.detailTitle.orEmpty(), vm, Modifier.widthIn(max = 300.dp).fillMaxWidth().aspectRatio(1f))
            Text(state.detailTitle.orEmpty(), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            if (state.detailSongs.isNotEmpty()) {
                Text(strings.getString(R.string.track_duration_summary, state.detailSongs.size, state.detailSongs.sumOf { it.duration } / 60), color = Muted)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilledTonalButton(onClick = { vm.play(state.detailSongs) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Icon(Icons.Rounded.PlayArrow, null); Text(strings.getString(R.string.play_all)) }
                    FilledTonalButton(onClick = { vm.play(state.detailSongs.shuffled()) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Icon(Icons.Rounded.Shuffle, null); Text(strings.getString(R.string.shuffle)) }
                }
            }
        } }
        if (state.detailLoading) item { Loading() }
        itemsIndexed(state.detailSongs, key = { index, song -> "song:$index:${song.id}" }) { index, song -> SongRow(song, state, vm) { vm.play(state.detailSongs, index) } }
        items(state.detailAlbums.chunked(2), key = { "album:${it.first().id}" }) { AlbumRow(it, vm) }
        if (!state.detailLoading && state.detailSongs.isEmpty() && state.detailAlbums.isEmpty()) item { Empty(strings.getString(R.string.no_music), strings.getString(R.string.content_permission_hint), Icons.Rounded.MusicNote) }
    }
}

@Composable
private fun SongPage(title: String, songs: List<Song>, state: MusicState, vm: MusicViewModel, refresh: () -> Unit) {
    val strings = androidx.compose.ui.platform.LocalContext.current

    RefreshContainer(state.loading, refresh) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = pageContentPadding(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { PageTitle(title) }
            if (songs.isNotEmpty()) item { Button(onClick = { vm.play(songs) }) { Icon(Icons.Rounded.PlayArrow, null); Text(strings.getString(R.string.play_all)) } }
            items(songs, key = Song::id) { song -> SongRow(song, state, vm) { vm.play(songs, songs.indexOf(song)) } }
            if (!state.loading && songs.isEmpty()) item { Empty(strings.getString(R.string.likes_empty), strings.getString(R.string.likes_hint), Icons.Rounded.FavoriteBorder) }
        }
    }
}

@Composable
private fun ServerScreen(state: MusicState, vm: MusicViewModel, add: () -> Unit) {
    val strings = androidx.compose.ui.platform.LocalContext.current

    var deleting by remember { mutableStateOf<Profile?>(null) }
    LazyColumn(Modifier.testTag("settings-list").fillMaxSize(), contentPadding = pageContentPadding(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { PageTitle(strings.getString(R.string.settings)) }
        item { AppearanceSettingsCard() }
        item { AudioSettingsCard(vm) }
        item { CarSettingsCard() }
        item { SectionTitle(strings.getString(R.string.music_servers), "") }
        items(state.profiles, key = { it.id }) { profile ->
            Surface(color = Grouped, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Dns, null, tint = Accent); Spacer(Modifier.width(10.dp)); Text(profile.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        if (profile.id == state.active?.id) Text(strings.getString(R.string.in_use), color = Accent, fontSize = 12.sp)
                    }
                    Text(profile.server, color = Muted, style = MaterialTheme.typography.bodyMedium)
                    Text(profile.username, style = MaterialTheme.typography.bodyMedium)
                    Row { if (profile.id != state.active?.id) TextButton(onClick = { vm.activate(profile) }) { Text(strings.getString(R.string.switch_server)) }
                        Spacer(Modifier.weight(1f)); TextButton(onClick = { deleting = profile }) { Text(strings.getString(R.string.remove_device), color = Muted) } }
                }
            }
        }
        item { OutlinedButton(onClick = add, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) { Icon(Icons.Rounded.Add, null); Text(strings.getString(R.string.add_server)) } }
        item { UpdateCard() }
        item { PrivacyLink() }
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.ic_musonic_logo), "Musonic", modifier = Modifier.width(112.dp).height(30.dp))
            Spacer(Modifier.weight(1f))
            Text(BuildConfig.VERSION_NAME, color = Muted, style = MaterialTheme.typography.bodyMedium)
        } }
    }
    deleting?.let { profile -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text(strings.getString(R.string.remove_connection)) },
        text = { Text(strings.getString(R.string.remove_connection_detail, profile.name)) },
        confirmButton = { TextButton(onClick = { vm.removeProfile(profile.id); deleting = null }) { Text(strings.getString(R.string.delete)) } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text(strings.getString(R.string.cancel)) } }) }
}

@Composable
private fun RefreshContainer(refreshing: Boolean, refresh: () -> Unit, content: @Composable () -> Unit) {
    val strings = androidx.compose.ui.platform.LocalContext.current

    val pullState = rememberPullToRefreshState()
    val trigger = { if (!refreshing) refresh() }
    PullToRefreshBox(isRefreshing = refreshing, onRefresh = trigger, state = pullState,
        modifier = Modifier.fillMaxSize().testTag("pull-refresh").semantics {
            customActions = listOf(CustomAccessibilityAction(strings.getString(R.string.refresh)) { trigger(); true })
        }, indicator = {
            PullToRefreshDefaults.Indicator(state = pullState, isRefreshing = refreshing,
                modifier = Modifier.align(Alignment.TopCenter).testTag("refresh-indicator"), containerColor = CanvasColor, color = Accent)
        }) { content() }
}

@Composable
private fun MiniPlayer(state: MusicState, vm: MusicViewModel, expand: () -> Unit) {
    val strings = androidx.compose.ui.platform.LocalContext.current

    val song = state.current ?: return
    Surface(onClick = expand, color = MiniBackground, shape = RoundedCornerShape(22.dp),
        border = BorderStroke(.5.dp, Separator.copy(alpha = .45f)), shadowElevation = 5.dp,
        modifier = Modifier.testTag("mini-player").padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 4.dp).fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Artwork(song.coverArt, song.id, vm, Modifier.size(48.dp), size = 128)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Muted, fontSize = 12.sp)
                }
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = vm::togglePlay, modifier = Modifier.size(48.dp).background(TextPrimary, CircleShape)) {
                    if (state.buffering) CircularProgressIndicator(Modifier.size(21.dp), color = OnPlaybackControl, strokeWidth = 2.dp)
                    else Icon(if (state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (state.playing) strings.getString(R.string.pause) else strings.getString(R.string.play), tint = OnPlaybackControl, modifier = Modifier.size(27.dp))
                }
                IconButton(onClick = vm::next, modifier = Modifier.size(48.dp)) { Icon(Icons.Rounded.SkipNext, strings.getString(R.string.next_track), tint = TextPrimary, modifier = Modifier.size(26.dp)) }
            }
            Spacer(Modifier.height(9.dp))
            key(song.id) {
                val progress by animateFloatAsState(if (state.duration > 0) (state.position.toFloat() / state.duration).coerceIn(0f, 1f) else 0f,
                    animationSpec = tween(450), label = "mini-progress")
                Box(Modifier.fillMaxWidth().height(2.dp).clip(CircleShape).background(Separator.copy(alpha = .6f))) {
                    Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(Accent.copy(alpha = .8f)))
                }
            }
        }
    }
}

@Composable
private fun NowPlaying(state: MusicState, vm: MusicViewModel) {
    val strings = androidx.compose.ui.platform.LocalContext.current

    val song = state.current ?: return
    var showQueue by remember { mutableStateOf(false) }
    var seeking by remember(state.active?.id, song.id, state.queueIndex) { mutableStateOf<Float?>(null) }
    val scrubInteraction = remember { MutableInteractionSource() }
    val dragging by scrubInteraction.collectIsDraggedAsState()
    val trackHeight by animateDpAsState(if (dragging) 9.dp else 5.dp, label = "scrub-height")
    LazyColumn(Modifier.testTag("now-playing").fillMaxWidth().navigationBarsPadding(), contentPadding = PaddingValues(horizontal = 32.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        item { Spacer(Modifier.height(12.dp)) }
        item { Artwork(song.coverArt, song.id, vm, Modifier.widthIn(max = 380.dp).fillMaxWidth().aspectRatio(1f).shadow(18.dp, RoundedCornerShape(10.dp)), size = 900) }
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(song.title, style = MaterialTheme.typography.headlineMedium.copy(fontSize = 24.sp), maxLines = 2, overflow = TextOverflow.Ellipsis); Text(song.artist, color = Muted, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            IconButton(onClick = { vm.toggleStar(song) }) { Icon(if (song.starred) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, strings.getString(R.string.toggle_like), tint = if (song.starred) Accent else Muted) }
        } }
        item { Column {
            Slider(value = seeking ?: state.position.toFloat().coerceAtMost(state.duration.toFloat()), onValueChange = { seeking = it },
                onValueChangeFinished = { seeking?.let { vm.seek(it.toLong()) }; seeking = null }, valueRange = 0f..state.duration.coerceAtLeast(1).toFloat(), enabled = state.canSeek, interactionSource = scrubInteraction,
                modifier = Modifier.testTag("playback-slider"),
                colors = SliderDefaults.colors(thumbColor = TextPrimary, activeTrackColor = TextPrimary, inactiveTrackColor = Separator),
                thumb = { Spacer(Modifier.size(0.dp)) },
                track = { SliderDefaults.Track(sliderState = it, modifier = Modifier.height(trackHeight), thumbTrackGapSize = 0.dp, drawStopIndicator = null,
                    colors = SliderDefaults.colors(activeTrackColor = TextPrimary.copy(alpha = .65f), inactiveTrackColor = Separator.copy(alpha = .65f))) })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(time((seeking?.toLong() ?: state.position) / 1000), color = Muted, fontSize = 12.sp); Text(if (state.duration > 0) time(state.duration / 1000) else strings.getString(R.string.duration_unknown), color = Muted, fontSize = 12.sp) }
            Text(state.streamInfo?.label(strings) ?: strings.getString(R.string.stream_loading), color = Muted, fontSize = 11.sp,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 14.dp).testTag("stream-info"))
        } }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = vm::shuffle) { Icon(Icons.Rounded.Shuffle, strings.getString(R.string.toggle_shuffle), tint = if (state.shuffle) Accent else Muted) }
            IconButton(onClick = vm::previous, modifier = Modifier.size(56.dp)) { Icon(Icons.Rounded.FastRewind, strings.getString(R.string.previous_track), Modifier.size(40.dp)) }
            IconButton(onClick = vm::togglePlay, modifier = Modifier.size(76.dp)) {
                if (state.buffering) CircularProgressIndicator(Modifier.size(32.dp), color = TextPrimary) else Icon(if (state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (state.playing) strings.getString(R.string.pause) else strings.getString(R.string.play), Modifier.size(54.dp))
            }
            IconButton(onClick = vm::next, modifier = Modifier.size(56.dp)) { Icon(Icons.Rounded.FastForward, strings.getString(R.string.next_track), Modifier.size(40.dp)) }
            IconButton(onClick = vm::repeat) { Icon(if (state.repeat == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, strings.getString(R.string.toggle_repeat), tint = if (state.repeat != Player.REPEAT_MODE_OFF) Accent else Muted) }
        } }
        item { TextButton(onClick = { showQueue = !showQueue }) { Icon(Icons.AutoMirrored.Rounded.QueueMusic, null); Spacer(Modifier.width(8.dp)); Text(strings.getString(R.string.queue)); Icon(if (showQueue) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null) } }
        if (showQueue) items(state.queue.withIndex().toList(), key = { it.index }) { (index, item) -> SongRow(item, state, vm) { vm.jump(index) } }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun AlbumRow(albums: List<Album>, vm: MusicViewModel) {
    val strings = androidx.compose.ui.platform.LocalContext.current

    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        albums.forEach { album -> Column(Modifier.weight(1f).clickable { vm.openAlbum(album) }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Artwork(album.coverArt, album.id, vm, Modifier.fillMaxWidth().aspectRatio(1f))
            Text(album.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium, fontSize = 15.sp)
            Text(album.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Muted, fontSize = 13.sp)
        } }
        if (albums.size == 1) Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun ArtistRow(artist: Artist, open: () -> Unit) {
    val strings = androidx.compose.ui.platform.LocalContext.current

    Row(Modifier.fillMaxWidth().clickable(onClick = open).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(52.dp).background(Grouped, CircleShape), contentAlignment = Alignment.Center) { Text(artist.name.take(1), color = Accent, fontSize = 23.sp) }
        Column(Modifier.weight(1f)) { Text(artist.name, fontWeight = FontWeight.SemiBold); Text(strings.getString(R.string.album_count, artist.albumCount), color = Muted, fontSize = 12.sp) }
        Icon(Icons.Rounded.ChevronRight, null, tint = Muted)
    }
}

@Composable
private fun SongRow(song: Song, state: MusicState, vm: MusicViewModel, play: () -> Unit) {
    val strings = androidx.compose.ui.platform.LocalContext.current

    Row(Modifier.fillMaxWidth().clickable(onClick = play).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Artwork(song.coverArt, song.id, vm, Modifier.size(52.dp), size = 128)
        Column(Modifier.weight(1f)) { Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (state.current?.id == song.id) Accent else TextPrimary, fontWeight = FontWeight.Medium); Text(song.artist, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        Text(time(song.duration.toLong()), color = Muted, fontSize = 11.sp)
        IconButton(onClick = { vm.toggleStar(song) }, modifier = Modifier.size(44.dp)) { Icon(if (song.starred) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, strings.getString(R.string.toggle_track_like, song.title), tint = if (song.starred) Accent else Muted, modifier = Modifier.size(20.dp)) }
    }
}

@Composable
private fun Artwork(coverId: String?, seed: String, vm: MusicViewModel, modifier: Modifier, size: Int = 512) {
    val strings = androidx.compose.ui.platform.LocalContext.current

    val identity by vm.artworkIdentity.collectAsStateWithLifecycle()
    val request = remember(coverId, identity, size) { vm.artworkRequest(coverId, size) }
    val colors = listOf(Color(0xFF365653), Color(0xFF54506F), Color(0xFF7A5845), Color(0xFF3B5770))
    val tint = colors[(seed.hashCode().toLong().let { if (it < 0) -it else it } % colors.size).toInt()]
    Box(modifier.clip(RoundedCornerShape(8.dp)).background(Brush.linearGradient(listOf(tint, Grouped))), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.Album, null, tint = TextPrimary.copy(alpha = .3f), modifier = Modifier.fillMaxSize(.42f))
        if (request != null) AsyncImage(model = request, imageLoader = vm.artworkLoader,
            contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

@Composable private fun SectionTitle(title: String, count: String) {
    val strings = androidx.compose.ui.platform.LocalContext.current
 Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text(title, style = MaterialTheme.typography.titleLarge); Text(count, color = Muted, fontSize = 12.sp) } }
@Composable private fun Loading() {
    val strings = androidx.compose.ui.platform.LocalContext.current
 Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.dp) } }
@Composable private fun Empty(title: String, subtitle: String, icon: ImageVector) {
    val strings = androidx.compose.ui.platform.LocalContext.current
 Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) { Icon(icon, null, tint = Accent, modifier = Modifier.size(42.dp)); Text(title, fontWeight = FontWeight.SemiBold); Text(subtitle, color = Muted, fontSize = 13.sp, textAlign = TextAlign.Center) } }
private fun time(seconds: Long) = "%d:%02d".format(seconds / 60, seconds % 60)
