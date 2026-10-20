@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.aaronparry.tube.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aaronparry.tube.AppGraph
import com.aaronparry.tube.data.VideoItem
import com.aaronparry.tube.ui.components.Avatar
import com.aaronparry.tube.ui.components.EmptyState
import com.aaronparry.tube.ui.components.ErrorState
import com.aaronparry.tube.ui.components.LocalBottomPadding
import com.aaronparry.tube.ui.components.SkeletonCards
import com.aaronparry.tube.ui.components.VideoCard
import com.aaronparry.tube.ui.components.VideoRow
import com.aaronparry.tube.ui.components.listPadding
import com.aaronparry.tube.data.Entry
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
    private var job: Job? = null

    init { load() }

    fun select(c: String) {
        if (c == chip && items.isNotEmpty()) return
        chip = c
        load()
    }

    fun refresh() = load(pull = true)

    private fun load(pull: Boolean = false) {
        job?.cancel()
        if (pull) refreshing = true else { loading = true; items = emptyList() }
        error = null
        job = viewModelScope.launch {
            try {
                items = if (chip == "All") allFeed() else search(chip)
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
                        Text(
                            "Tube",
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onSearch) { Icon(Icons.Rounded.Search, "Search") }
                },
            )
        },
    ) { pad ->
        PullToRefreshBox(
            isRefreshing = vm.refreshing,
            onRefresh = vm::refresh,
            modifier = Modifier.fillMaxSize().padding(top = pad.calculateTopPadding()),
        ) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = listPadding(LocalBottomPadding.current)) {
                item(key = "chips") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(homeChips) { c ->
                            FilterChip(
                                selected = c == vm.chip,
                                onClick = { vm.select(c) },
                                label = { Text(c) },
                                shape = RoundedCornerShape(12.dp),
                            )
                        }
                    }
                }
                when {
                    vm.loading -> item(key = "loading") { SkeletonCards() }
                    vm.error != null -> item(key = "error") { ErrorState(vm.error!!, vm::refresh) }
                    else -> items(vm.items, key = { it.id }) { v ->
                        VideoCard(v, onClick = { onVideo(v) }, onChannel = onChannel, modifier = Modifier.animateItem())
                    }
                }
            }
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
        val subs = AppGraph.store.subs.value
        if (subs.hashCode() != loadedFor) load(false)
    }

    fun load(pull: Boolean) {
        val subs = AppGraph.store.subs.value
        loadedFor = subs.hashCode()
        if (pull) refreshing = true else loading = true
        viewModelScope.launch {
            items = runCatching { AppGraph.repo.feed(subs) }.getOrDefault(emptyList())
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
    LaunchedEffect(subs) { vm.loadIfNeeded() }
    val scroll = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0),
        topBar = { TopAppBar(title = { Text("Subscriptions") }, scrollBehavior = scroll) },
    ) { pad ->
        PullToRefreshBox(
            isRefreshing = vm.refreshing,
            onRefresh = { vm.load(true) },
            modifier = Modifier.fillMaxSize().padding(top = pad.calculateTopPadding()),
        ) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = listPadding(LocalBottomPadding.current)) {
                if (subs.isEmpty()) {
                    item {
                        EmptyState(
                            Icons.Rounded.Subscriptions,
                            "No subscriptions yet",
                            "Search for creators you like and tap Subscribe. Their new videos will show up here.",
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
                                Modifier.width(72.dp).clickable { onChannel(s.url) },
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
                else items(vm.items, key = { it.id }) { v ->
                    VideoCard(v, onClick = { onVideo(v) }, onChannel = onChannel, modifier = Modifier.animateItem())
                }
            }
        }
    }
}

// ---------------- You (library) ----------------

@Composable
fun LibraryScreen(onVideo: (VideoItem) -> Unit, onSettings: () -> Unit) {
    val history by AppGraph.store.history.collectAsState()
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text("You") },
                actions = { IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, "Settings") } },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(top = pad.calculateTopPadding()),
            contentPadding = listPadding(LocalBottomPadding.current),
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.History, null)
                    Text(
                        "History",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(start = 12.dp).weight(1f),
                    )
                    if (history.isNotEmpty()) {
                        TextButton(onClick = { AppGraph.store.clearHistory() }) {
                            Icon(Icons.Rounded.DeleteSweep, null, Modifier.size(18.dp))
                            Text("Clear", Modifier.padding(start = 6.dp))
                        }
                    }
                }
            }
            if (history.isEmpty()) {
                item { EmptyState(Icons.Rounded.History, "Nothing watched yet", "Videos you watch will appear here.") }
            }
            items(history, key = { "h" + it.id }) { v ->
                VideoRow(v, onClick = { onVideo(v) }, modifier = Modifier.animateItem())
            }
        }
    }
}
