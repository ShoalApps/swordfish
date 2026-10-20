@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.aaronparry.tube.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.WatchLater
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.MaterialShapes
import com.aaronparry.tube.ui.components.CookieAvatar
import com.aaronparry.tube.ui.components.ShapeIcon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aaronparry.tube.AppGraph
import com.aaronparry.tube.data.Entry
import com.aaronparry.tube.data.VideoItem
import com.aaronparry.tube.ui.components.Avatar
import com.aaronparry.tube.ui.components.EmptyState
import com.aaronparry.tube.ui.components.ErrorState
import com.aaronparry.tube.ui.components.LocalBottomPadding
import com.aaronparry.tube.ui.components.ShelfCard
import com.aaronparry.tube.ui.components.SkeletonCards
import com.aaronparry.tube.ui.components.VideoCard
import com.aaronparry.tube.ui.components.listPadding
import com.aaronparry.tube.ui.components.staggeredReveal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// ---------------- Home ----------------

val homeChips = listOf("All", "Music", "Gaming", "News", "Live", "Sport", "Tech", "Learning", "Comedy")

class HomeViewModel : ViewModel() {
    var chip by mutableStateOf("All")
        private set
    var items by mutableStateOf<List<VideoItem>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var refreshing by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var generation by mutableStateOf(0)
        private set
    private var job: Job? = null
    private var loadedSignedIn = AppGraph.store.signedIn

    init { load() }

    fun select(c: String) {
        if (c == chip && items.isNotEmpty()) return
        chip = c
        load()
    }

    fun refresh() = load(pull = true)

    fun reloadIfAccountChanged() {
        if (loadedSignedIn != AppGraph.store.signedIn) load()
    }

    private fun load(pull: Boolean = false) {
        job?.cancel()
        loadedSignedIn = AppGraph.store.signedIn
        if (pull) refreshing = true else { loading = true; items = emptyList() }
        error = null
        job = viewModelScope.launch {
            try {
                items = if (chip == "All") allFeed() else search(chip)
                generation++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: e.javaClass.simpleName
            } finally {
                loading = false
                refreshing = false
            }
        }
    }

    private suspend fun allFeed(): List<VideoItem> {
        if (AppGraph.store.signedIn) {
            val feed = runCatching { AppGraph.account.home().videos }.getOrDefault(emptyList())
            if (feed.isNotEmpty()) return feed
        }
        val subs = AppGraph.store.subs.value
        if (subs.isNotEmpty()) {
            val feed = AppGraph.repo.feed(subs)
            if (feed.isNotEmpty()) return feed.take(80)
        }
        val trending = runCatching { AppGraph.repo.trending() }.getOrDefault(emptyList())
        return trending.ifEmpty { search("trending") }
    }

    private suspend fun search(q: String) =
        AppGraph.repo.search(q).items.filterIsInstance<Entry.Video>().map { it.item }.filterNot { it.isShort }
}

@Composable
fun HomeScreen(
    onVideo: (VideoItem) -> Unit,
    onChannel: (String) -> Unit,
    onSearch: () -> Unit,
    vm: HomeViewModel = viewModel(),
) {
    val cookie by AppGraph.store.cookie.collectAsState()
    LaunchedEffect(cookie) { vm.reloadIfAccountChanged() }
    val scroll = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                scrollBehavior = scroll,
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.SmartDisplay,
                            null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(30.dp),
                        )
                        Text("Tube", fontWeight = FontWeight.Black, modifier = Modifier.padding(start = 4.dp))
                    }
                },
                actions = {
                    IconButton(onClick = onSearch) { Icon(Icons.Rounded.Search, "Search") }
                },
            )
        },
    ) { pad ->
        val ptr = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = vm.refreshing,
            onRefresh = vm::refresh,
            state = ptr,
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    state = ptr,
                    isRefreshing = vm.refreshing,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            },
            modifier = Modifier.fillMaxSize().padding(top = pad.calculateTopPadding()),
        ) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = listPadding(LocalBottomPadding.current)) {
                item(key = "chips") { Chips(homeChips, vm.chip, vm::select) }
                when {
                    vm.loading -> item(key = "loading") { SkeletonCards() }
                    vm.error != null -> item(key = "error") { ErrorState(vm.error!!, vm::refresh) }
                    else -> itemsIndexed(vm.items, key = { _, v -> "${vm.generation}-${v.id}" }) { i, v ->
                        VideoCard(
                            v,
                            onClick = { onVideo(v) },
                            onChannel = onChannel,
                            modifier = Modifier.animateItem().staggeredReveal(i),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Chips(chips: List<String>, selected: String, onSelect: (String) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(chips) { c ->
            FilterChip(
                selected = c == selected,
                onClick = { onSelect(c) },
                label = { Text(c) },
                shape = RoundedCornerShape(8.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.inverseSurface,
                    selectedLabelColor = MaterialTheme.colorScheme.inverseOnSurface,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                border = null,
            )
        }
    }
}

// ---------------- Subscriptions ----------------

class SubsViewModel : ViewModel() {
    var items by mutableStateOf<List<VideoItem>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var refreshing by mutableStateOf(false)
        private set
    private var loadedFor: Int = -1

    fun loadIfNeeded() {
        val key = AppGraph.store.subs.value.hashCode() * 31 + AppGraph.store.signedIn.hashCode()
        if (key != loadedFor) load(false)
    }

    fun load(pull: Boolean) {
        val subs = AppGraph.store.subs.value
        loadedFor = subs.hashCode() * 31 + AppGraph.store.signedIn.hashCode()
        if (pull) refreshing = true else loading = true
        viewModelScope.launch {
            val fromAccount = if (AppGraph.store.signedIn) {
                runCatching { AppGraph.account.subscriptionFeed() }.getOrDefault(emptyList())
            } else emptyList()
            items = fromAccount.ifEmpty { runCatching { AppGraph.repo.feed(subs) }.getOrDefault(emptyList()) }
            loading = false
            refreshing = false
        }
    }
}

@Composable
fun SubscriptionsScreen(
    onVideo: (VideoItem) -> Unit,
    onChannel: (String) -> Unit,
    vm: SubsViewModel = viewModel(),
) {
    val subs by AppGraph.store.subs.collectAsState()
    val cookie by AppGraph.store.cookie.collectAsState()
    LaunchedEffect(subs, cookie) { vm.loadIfNeeded() }
    val scroll = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0),
        topBar = { TopAppBar(title = { Text("Subscriptions") }, scrollBehavior = scroll) },
    ) { pad ->
        val ptr = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = vm.refreshing,
            onRefresh = { vm.load(true) },
            state = ptr,
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    state = ptr,
                    isRefreshing = vm.refreshing,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            },
            modifier = Modifier.fillMaxSize().padding(top = pad.calculateTopPadding()),
        ) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = listPadding(LocalBottomPadding.current)) {
                if (subs.isEmpty() && !AppGraph.store.signedIn) {
                    item {
                        EmptyState(
                            Icons.Rounded.Subscriptions,
                            "No subscriptions yet",
                            "Sign in from the You tab, or search for creators and tap Subscribe.",
                        )
                    }
                    return@LazyColumn
                }
                item(key = "avatars") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(subs, key = { it.url }) { s ->
                            Column(
                                Modifier.width(64.dp).clickable { onChannel(s.url) },
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Avatar(s.avatar, 56.dp)
                                Text(
                                    s.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                    }
                }
                if (vm.loading) item(key = "loading") { SkeletonCards() }
                else itemsIndexed(vm.items, key = { _, v -> v.id }) { i, v ->
                    VideoCard(v, onClick = { onVideo(v) }, onChannel = onChannel, modifier = Modifier.animateItem().staggeredReveal(i))
                }
            }
        }
    }
}

// ---------------- You ----------------

class YouViewModel : ViewModel() {
    var liked by mutableStateOf<List<VideoItem>>(emptyList())
        private set
    var watchLater by mutableStateOf<List<VideoItem>>(emptyList())
        private set
    var ytHistory by mutableStateOf<List<VideoItem>>(emptyList())
        private set
    private var loadedFor: String? = "none"

    fun load() {
        val cookie = AppGraph.store.cookie.value
        if (cookie == loadedFor) return
        loadedFor = cookie
        if (!AppGraph.store.signedIn) {
            liked = emptyList(); watchLater = emptyList(); ytHistory = emptyList()
            return
        }
        viewModelScope.launch { ytHistory = runCatching { AppGraph.account.history() }.getOrDefault(emptyList()) }
        viewModelScope.launch { liked = runCatching { AppGraph.account.likedVideos() }.getOrDefault(emptyList()) }
        viewModelScope.launch { watchLater = runCatching { AppGraph.account.watchLater() }.getOrDefault(emptyList()) }
    }
}

@Composable
fun YouScreen(
    onVideo: (VideoItem) -> Unit,
    onSettings: () -> Unit,
    onSignIn: () -> Unit,
    onPlaylist: (String) -> Unit,
    onSwitchChannel: () -> Unit,
    vm: YouViewModel = viewModel(),
) {
    val account by AppGraph.store.account.collectAsState()
    val cookie by AppGraph.store.cookie.collectAsState()
    val localHistory by AppGraph.store.history.collectAsState()
    LaunchedEffect(cookie) { vm.load() }
    val signedIn = cookie?.contains("SAPISID") == true
    val history = if (signedIn && vm.ytHistory.isNotEmpty()) vm.ytHistory else localHistory

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { },
                actions = { IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, "Settings") } },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(top = pad.calculateTopPadding()),
            contentPadding = listPadding(LocalBottomPadding.current),
        ) {
            item(key = "header") {
                if (signedIn) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        CookieAvatar(account?.avatar, 80.dp)
                        Column(Modifier.padding(start = 16.dp).weight(1f)) {
                            Text(account?.name ?: "Signed in", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            account?.handle?.let {
                                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = onSwitchChannel, contentPadding = PaddingValues(0.dp)) {
                                Text("Switch channel")
                            }
                        }
                    }
                } else {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        ShapeIcon(Icons.Rounded.AccountCircle, 112.dp, MaterialShapes.Clover8Leaf, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("Enjoy your favourite videos", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
                        Text(
                            "Sign in to see your feed, subscriptions, likes and Watch later.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                        )
                        Button(onClick = onSignIn) { Text("Sign in") }
                    }
                }
            }

            item(key = "history-head") {
                SectionHeader("History", if (history.isNotEmpty() && !signedIn) "Clear" else null) { AppGraph.store.clearHistory() }
            }
            item(key = "history") {
                if (history.isEmpty()) {
                    Text(
                        "Videos you watch will appear here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                } else {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(history.take(30), key = { "h" + it.id }) { v -> ShelfCard(v) { onVideo(v) } }
                    }
                }
            }

            if (signedIn) {
                item(key = "pl-head") { SectionHeader("Playlists", null) {} }
                item(key = "pl") {
                    Column {
                        PlaylistEntry(Icons.Rounded.WatchLater, "Watch later", vm.watchLater.size) { onPlaylist("VLWL") }
                        PlaylistEntry(Icons.Rounded.ThumbUp, "Liked videos", vm.liked.size) { onPlaylist("VLLL") }
                    }
                }
            }

            item(key = "links") {
                Column(Modifier.padding(top = 16.dp)) {
                    ListItem(
                        headlineContent = { Text("Settings") },
                        leadingContent = { Icon(Icons.Rounded.Settings, null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable(onClick = onSettings),
                    )
                    if (signedIn) {
                        ListItem(
                            headlineContent = { Text("Sign out") },
                            leadingContent = { Icon(Icons.AutoMirrored.Rounded.Logout, null) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable { AppGraph.signOut() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, action: String?, onAction: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (action != null) OutlinedButton(onClick = onAction) { Text(action) }
    }
}

@Composable
private fun PlaylistEntry(icon: ImageVector, title: String, count: Int, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { if (count > 0) Text("$count videos") },
        leadingContent = { Icon(icon, null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

// ---------------- A signed-in playlist (Watch later, Liked) ----------------

class PlaylistViewModel(private val browseId: String) : ViewModel() {
    var items by mutableStateOf<List<VideoItem>>(emptyList())
        private set
    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    init { load() }

    fun load() {
        loading = true; error = null
        viewModelScope.launch {
            runCatching { AppGraph.account.playlist(browseId) }
                .onSuccess { items = it }
                .onFailure { error = it.message ?: "Couldn't load this playlist" }
            loading = false
        }
    }
}

@Composable
fun PlaylistScreen(
    browseId: String,
    onBack: () -> Unit,
    onVideo: (VideoItem) -> Unit,
    vm: PlaylistViewModel = viewModel(key = browseId) { PlaylistViewModel(browseId) },
) {
    val title = when (browseId) {
        "VLWL" -> "Watch later"
        "VLLL" -> "Liked videos"
        else -> "Playlist"
    }
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                    }
                },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(top = pad.calculateTopPadding()),
            contentPadding = listPadding(LocalBottomPadding.current),
        ) {
            when {
                vm.loading -> item { SkeletonCards() }
                vm.error != null -> item { ErrorState(vm.error!!, vm::load) }
                vm.items.isEmpty() -> item { EmptyState(Icons.Rounded.History, "Nothing here yet", "Videos you add will show up here.") }
                else -> itemsIndexed(vm.items, key = { _, v -> v.id }) { i, v ->
                    com.aaronparry.tube.ui.components.VideoRow(v, { onVideo(v) }, Modifier.animateItem().staggeredReveal(i))
                }
            }
        }
    }
}
