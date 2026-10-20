@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.aaronparry.tube.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.NorthWest
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.aaronparry.tube.AppGraph
import com.aaronparry.tube.data.ChannelDetails
import com.aaronparry.tube.data.Entry
import com.aaronparry.tube.data.Sub
import com.aaronparry.tube.data.VideoItem
import com.aaronparry.tube.data.formatCount
import com.aaronparry.tube.ui.components.Avatar
import com.aaronparry.tube.ui.components.CenterLoading
import com.aaronparry.tube.ui.components.ChannelRow
import com.aaronparry.tube.ui.components.ErrorState
import com.aaronparry.tube.ui.components.LocalBottomPadding
import com.aaronparry.tube.ui.components.SkeletonCards
import com.aaronparry.tube.ui.components.SubscribeButton
import com.aaronparry.tube.ui.components.VideoCard
import com.aaronparry.tube.ui.components.listPadding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.Page

// ---------------- Search ----------------

class SearchViewModel : ViewModel() {
    var query by mutableStateOf("")
        private set
    var suggestions by mutableStateOf<List<String>>(emptyList())
        private set
    var submitted by mutableStateOf<String?>(null)
        private set
    var results by mutableStateOf<List<Entry>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var loadingMore by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    private var next: Page? = null
    private var suggestJob: Job? = null
    private var searchJob: Job? = null

    fun onQuery(q: String) {
        query = q
        submitted = null
        suggestJob?.cancel()
        if (q.isBlank()) { suggestions = emptyList(); return }
        suggestJob = viewModelScope.launch {
            delay(200)
            suggestions = AppGraph.repo.suggestions(q)
        }
    }

    fun submit(q: String = query) {
        if (q.isBlank()) return
        query = q
        submitted = q
        suggestJob?.cancel()
        searchJob?.cancel()
        results = emptyList(); error = null; next = null; loading = true
        searchJob = viewModelScope.launch {
            try {
                val page = AppGraph.repo.search(q)
                results = page.items
                next = page.next
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: e.javaClass.simpleName
            } finally {
                loading = false
            }
        }
    }

    fun loadMore() {
        val q = submitted ?: return
        val page = next ?: return
        if (loadingMore || loading) return
        loadingMore = true
        viewModelScope.launch {
            runCatching { AppGraph.repo.search(q, page) }.onSuccess {
                results = results + it.items
                next = it.next
            }
            loadingMore = false
        }
    }
}

@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onVideo: (VideoItem) -> Unit,
    onChannel: (String) -> Unit,
    vm: SearchViewModel = viewModel(),
) {
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) { if (vm.submitted == null) focus.requestFocus() }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                TextField(
                    value = vm.query,
                    onValueChange = vm::onQuery,
                    placeholder = { Text("Search YouTube") },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                    trailingIcon = {
                        if (vm.query.isNotEmpty()) {
                            IconButton(onClick = { vm.onQuery(""); focus.requestFocus() }) {
                                Icon(Icons.Rounded.Close, "Clear")
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { vm.submit(); focusManager.clearFocus() }),
                    modifier = Modifier.weight(1f).padding(end = 8.dp).focusRequester(focus),
                )
            }
        },
    ) { pad ->
        AnimatedContent(
            targetState = vm.submitted == null,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            modifier = Modifier.fillMaxSize().padding(top = pad.calculateTopPadding()),
            label = "search",
        ) { typing ->
            if (typing) {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = listPadding(LocalBottomPadding.current)) {
                    items(vm.suggestions) { s ->
                        ListItem(
                            headlineContent = { Text(s, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingContent = { Icon(Icons.Rounded.Search, null) },
                            trailingContent = {
                                IconButton(onClick = { vm.onQuery("$s ") }) { Icon(Icons.Rounded.NorthWest, "Fill") }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable { vm.submit(s); focusManager.clearFocus() },
                        )
                    }
                }
            } else {
                val listState = rememberLazyListState()
                LaunchedEffect(listState) {
                    snapshotFlow {
                        val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                        last >= listState.layoutInfo.totalItemsCount - 4
                    }.distinctUntilChanged().filter { it }.collect { vm.loadMore() }
                }
                LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = listPadding(LocalBottomPadding.current)) {
                    when {
                        vm.loading -> item { SkeletonCards() }
                        vm.error != null -> item { ErrorState(vm.error!!, { vm.submit() }) }
                        else -> {
                            items(vm.results, key = { e ->
                                when (e) {
                                    is Entry.Video -> "v" + e.item.id
                                    is Entry.Channel -> "c" + e.item.url
                                }
                            }) { e ->
                                when (e) {
                                    is Entry.Video -> VideoCard(e.item, { onVideo(e.item) }, onChannel, Modifier.animateItem())
                                    is Entry.Channel -> ChannelRow(e.item) { onChannel(e.item.url) }
                                }
                            }
                            if (vm.loadingMore) item { CenterLoading() }
                        }
                    }
                }
            }
        }
    }
}

// ---------------- Channel ----------------

class ChannelViewModel(private val url: String) : ViewModel() {
    var details by mutableStateOf<ChannelDetails?>(null)
        private set
    var videos by mutableStateOf<List<VideoItem>>(emptyList())
        private set
    var loading by mutableStateOf(true)
        private set
    var loadingMore by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    private var next: Page? = null

    init { load() }

    fun load() {
        loading = true; error = null
        viewModelScope.launch {
            try {
                val d = AppGraph.repo.channel(url)
                details = d
                d.videosTab?.let { tab ->
                    val page = AppGraph.repo.channelVideos(tab)
                    videos = page.items
                    next = page.next
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: e.javaClass.simpleName
            } finally {
                loading = false
            }
        }
    }

    fun loadMore() {
        val tab = details?.videosTab ?: return
        val page = next ?: return
        if (loadingMore) return
        loadingMore = true
        viewModelScope.launch {
            runCatching { AppGraph.repo.channelVideos(tab, page) }.onSuccess {
                videos = videos + it.items
                next = it.next
            }
            loadingMore = false
        }
    }
}

@Composable
fun ChannelScreen(
    url: String,
    onBack: () -> Unit,
    onVideo: (VideoItem) -> Unit,
    vm: ChannelViewModel = viewModel(key = url) { ChannelViewModel(url) },
) {
    val subs by AppGraph.store.subs.collectAsState()
    val d = vm.details
    val listState = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 4
        }
    }
    LaunchedEffect(nearEnd) { if (nearEnd) vm.loadMore() }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(d?.name ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(top = pad.calculateTopPadding()),
            state = listState,
            contentPadding = listPadding(LocalBottomPadding.current),
        ) {
            if (d != null) {
                item(key = "header") {
                    Column {
                        if (d.banner != null) {
                            AsyncImage(
                                model = d.banner,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .padding(horizontal = 12.dp)
                                    .fillMaxWidth()
                                    .aspectRatio(4.2f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                            )
                        }
                        Row(
                            Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            com.aaronparry.tube.ui.components.CookieAvatar(d.avatar, 80.dp)
                            Column(Modifier.padding(start = 16.dp).weight(1f)) {
                                Text(d.name, style = MaterialTheme.typography.headlineSmall, maxLines = 2)
                                if (d.subscribers >= 0) {
                                    Text(
                                        "${formatCount(d.subscribers)} subscribers",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        if (!d.description.isNullOrBlank()) {
                            Text(
                                d.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                        Box(Modifier.fillMaxWidth().padding(16.dp)) {
                            SubscribeButton(subs.any { it.url == d.url || it.url == url }) {
                                AppGraph.toggleSubscribe(Sub(url, d.name, d.avatar))
                            }
                        }
                    }
                }
            }
            when {
                vm.loading -> item { SkeletonCards() }
                vm.error != null -> item { ErrorState(vm.error!!, vm::load) }
                else -> {
                    items(vm.videos, key = { it.id }) { v ->
                        VideoCard(v, { onVideo(v) }, null, Modifier.animateItem())
                    }
                    if (vm.loadingMore) item { CenterLoading() }
                }
            }
        }
    }
}
