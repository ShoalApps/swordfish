@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, UnstableApi::class)

package com.aaronateataco.materialtube.ui.screens

import android.view.TextureView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.aaronateataco.materialtube.AppGraph
import com.aaronateataco.materialtube.data.FeedFilter
import com.aaronateataco.materialtube.data.Sub
import com.aaronateataco.materialtube.data.VideoItem
import com.aaronateataco.materialtube.data.formatCount
import com.aaronateataco.materialtube.player.PlayerHolder
import com.aaronateataco.materialtube.player.StreamPicker
import com.aaronateataco.materialtube.ui.components.Avatar
import com.aaronateataco.materialtube.ui.components.ErrorState
import com.aaronateataco.materialtube.ui.components.ShapeIcon
import com.aaronateataco.materialtube.ui.components.SubscribeButton
import com.aaronateataco.materialtube.ui.components.rememberMorphShape
import com.aaronateataco.materialtube.ui.components.shareVideo
import com.aaronateataco.materialtube.ui.components.toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.stream.StreamInfo

/** A Short tapped elsewhere (e.g. the Home shelf) that the Shorts tab should open on. */
object ShortsLaunch {
    @Volatile var start: VideoItem? = null
}

/**
 * Shorts feed. Three players are kept warm (previous, current, next) and the two
 * Shorts above and below have their details and thumbnails fetched ahead of time.
 */
class ShortsViewModel : ViewModel() {
    private val pool = List(3) { ExoPlayer.Builder(AppGraph.app).build().apply { repeatMode = Player.REPEAT_MODE_ONE } }
    /** slot -> page index it's holding */
    private val slotIndex = IntArray(3) { -1 }
    private val slotJobs = arrayOfNulls<Job>(3)

    var items by mutableStateOf<List<VideoItem>>(emptyList())
        private set
    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var current by mutableIntStateOf(0)
        private set
    var paused by mutableStateOf(false)
        private set
    var progress by mutableFloatStateOf(0f)
        private set
    var limitReached by mutableStateOf<String?>(null)
        private set
    /** Page the pager should jump to (set when opening a specific Short). */
    var jumpTo by mutableStateOf<Int?>(null)

    /** Bumped whenever players move between pages, so the UI re-reads [playerFor]. */
    var slotVersion by mutableIntStateOf(0)
        private set

    /** page index -> details, first-frame flag, aspect ratio */
    val infos = mutableStateMapOf<Int, StreamInfo>()
    val ready = mutableStateMapOf<Int, Boolean>()
    val aspects = mutableStateMapOf<Int, Float>()

    private val infoCache = mutableMapOf<String, StreamInfo>()
    private var active = false
    private var viewedThisSession = mutableSetOf<Int>()

    init {
        pool.forEachIndexed { slot, p ->
            p.addListener(object : Player.Listener {
                override fun onRenderedFirstFrame() {
                    slotIndex[slot].takeIf { it >= 0 }?.let { ready[it] = true }
                }
                override fun onVideoSizeChanged(size: VideoSize) {
                    val i = slotIndex[slot]
                    if (i >= 0 && size.width > 0 && size.height > 0) aspects[i] = size.width.toFloat() / size.height
                }
            })
        }
        viewModelScope.launch {
            var tick = 0
            while (true) {
                val p = playerFor(current)
                val awake = active && p?.isPlaying == true
                if (com.aaronateataco.materialtube.ui.ScreenAwake.shorts != awake) com.aaronateataco.materialtube.ui.ScreenAwake.shorts = awake
                if (p != null) {
                    val d = p.duration
                    progress = if (d > 0) (p.currentPosition.toFloat() / d).coerceIn(0f, 1f) else 0f
                }
                if (++tick % 10 == 0 && active && p?.isPlaying == true) {
                    AppGraph.store.addShortsMs(1000)
                    checkDailyLimit()
                }
                delay(100)
            }
        }
        load()
    }

    fun load() {
        loading = true; error = null
        viewModelScope.launch {
            try {
                val fromAccount = if (AppGraph.store.signedIn) {
                    runCatching { AppGraph.account.home().shorts }.getOrDefault(emptyList())
                } else emptyList()
                val more = if (fromAccount.size >= 20) emptyList() else AppGraph.repo.shorts(AppGraph.store.subs.value)
                items = FeedFilter.apply((fromAccount + more).distinctBy { it.id })
                val launch = ShortsLaunch.start
                if (launch != null) {
                    ShortsLaunch.start = null
                    items = listOf(launch) + items.filter { it.id != launch.id }
                }
                if (items.isEmpty()) error = "Couldn't find any Shorts right now"
                else onPage(0)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: e.javaClass.simpleName
            } finally {
                loading = false
            }
        }
    }

    fun playerFor(index: Int): ExoPlayer? {
        slotVersion
        val slot = slotIndex.indexOf(index)
        return if (slot >= 0) pool[slot] else null
    }

    /** Which slot is showing a page; used to rebuild the video view when it changes. */
    fun slotOf(index: Int): Int {
        slotVersion
        return slotIndex.indexOf(index)
    }

    private suspend fun info(item: VideoItem): StreamInfo =
        infoCache[item.id] ?: AppGraph.repo.stream("https://www.youtube.com/watch?v=${item.id}").also { infoCache[item.id] = it }

    fun onPage(index: Int) {
        current = index
        paused = false
        viewedThisSession += index
        checkSessionLimit()

        val wanted = listOf(index, index + 1, index - 1).filter { it in items.indices }
        // Free slots holding pages we no longer need.
        for (slot in 0 until 3) {
            if (slotIndex[slot] !in wanted) {
                slotJobs[slot]?.cancel()
                pool[slot].stop()
                pool[slot].clearMediaItems()
                slotIndex[slot] = -1
            }
        }
        // Give each wanted page a slot and start preparing it.
        for (i in wanted) {
            if (slotIndex.contains(i)) continue
            val slot = slotIndex.indexOf(-1).takeIf { it >= 0 } ?: continue
            slotIndex[slot] = i
            ready.remove(i)
            slotJobs[slot] = viewModelScope.launch {
                runCatching {
                    val inf = info(items[i])
                    infos[i] = inf
                    val src = StreamPicker.buildSource(StreamPicker.pick(inf, 1080), inf) ?: return@runCatching
                    if (slotIndex[slot] != i) return@runCatching
                    pool[slot].setMediaSource(src)
                    pool[slot].playWhenReady = false
                    pool[slot].prepare()
                    applyPlayState()
                }
            }
        }
        slotVersion++
        applyPlayState()

        // Look further ahead: details and thumbnails for 2 above and 2 below.
        for (i in (index - 2)..(index + 2)) {
            val item = items.getOrNull(i) ?: continue
            viewModelScope.launch { runCatching { infos[i] = info(item) } }
            SingletonImageLoader.get(AppGraph.app).enqueue(ImageRequest.Builder(AppGraph.app).data(item.thumbnail).build())
        }
    }

    private fun applyPlayState() {
        for (slot in 0 until 3) {
            val p = pool[slot]
            if (slotIndex[slot] == current) {
                p.playWhenReady = active && !paused && limitReached == null
            } else {
                p.playWhenReady = false
                if (p.currentPosition > 0) p.seekTo(0)
            }
        }
    }

    fun togglePause() {
        paused = !paused
        applyPlayState()
    }

    /** Put a Short opened from elsewhere at the front and jump to it. */
    private fun takeLaunch() {
        val start = ShortsLaunch.start ?: return
        if (items.isEmpty()) return
        ShortsLaunch.start = null
        val existing = items.indexOfFirst { it.id == start.id }
        val target = if (existing >= 0) existing else {
            items = items.take(current + 1) + start + items.drop(current + 1)
            current + 1
        }
        jumpTo = target
    }

    fun setActive(on: Boolean) {
        active = on
        if (on) {
            takeLaunch()
            PlayerHolder.player.pause()
            viewedThisSession = mutableSetOf(current)
            checkDailyLimit()
        }
        applyPlayState()
    }

    private fun checkSessionLimit() {
        val limit = AppGraph.store.settings.value.shortsSessionLimit
        if (limit > 0 && viewedThisSession.size > limit) {
            limitReached = "You've watched $limit Shorts this time."
            applyPlayState()
        }
    }

    private fun checkDailyLimit() {
        val minutes = AppGraph.store.settings.value.shortsDailyMinutes
        if (minutes > 0 && AppGraph.store.shortsMsToday() >= minutes * 60_000L) {
            limitReached = "You've reached today's $minutes-minute Shorts limit."
            applyPlayState()
        }
    }

    override fun onCleared() {
        com.aaronateataco.materialtube.ui.ScreenAwake.shorts = false
        pool.forEach { it.release() }
    }
}

@Composable
fun ShortsScreen(onChannel: (String) -> Unit, onExit: () -> Unit, vm: ShortsViewModel = viewModel()) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        vm.setActive(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> vm.setActive(true)
                Lifecycle.Event.ON_PAUSE -> vm.setActive(false)
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            vm.setActive(false)
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        val limit = vm.limitReached
        when {
            limit != null -> LimitReached(limit, onExit)
            vm.loading -> LoadingIndicator(Modifier.align(Alignment.Center).size(64.dp))
            vm.error != null -> ErrorState(vm.error!!, vm::load, Modifier.align(Alignment.Center))
            else -> {
                val pager = rememberPagerState { vm.items.size }
                LaunchedEffect(pager.settledPage, vm.items) { vm.onPage(pager.settledPage) }
                LaunchedEffect(vm.jumpTo) {
                    vm.jumpTo?.let { pager.scrollToPage(it); vm.jumpTo = null }
                }
                VerticalPager(
                    state = pager,
                    beyondViewportPageCount = 2,
                    key = { vm.items[it].id },
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    ShortPage(vm, page, isCurrent = page == pager.settledPage, onChannel = onChannel)
                }
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(120.dp)
                .background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), Color.Transparent))),
        )
        Text(
            "Shorts",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.statusBarsPadding().padding(16.dp),
        )
    }
}

@Composable
private fun LimitReached(message: String, onExit: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        ShapeIcon(Icons.Rounded.HourglassBottom, 120.dp, MaterialShapes.Cookie12Sided, MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
        Text("Time for a break", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 20.dp))
        Text(
            "$message You can change this in Settings > Shorts.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
        )
        Button(onClick = onExit) { Text("Back to Home") }
    }
}

@Composable
private fun ShortPage(vm: ShortsViewModel, page: Int, isCurrent: Boolean, onChannel: (String) -> Unit) {
    val context = LocalContext.current
    val item = vm.items[page]
    val info = vm.infos[page]
    val player = vm.playerFor(page)
    var liked by remember(item.id) { mutableStateOf<Boolean?>(null) }
    val subs by AppGraph.store.subs.collectAsState()
    val surface = MaterialTheme.colorScheme.surface
    val onSurface = MaterialTheme.colorScheme.onSurface

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(item.id) { detectTapGestures(onTap = { if (isCurrent) vm.togglePause() }) },
    ) {
        AsyncImage(
            model = item.thumbnail,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (player != null) {
            val isReady = vm.ready[page] == true
            // Fill the whole screen, cropping the edges if needed, like the official app.
            BoxWithConstraints(Modifier.fillMaxSize().clipToBounds(), contentAlignment = Alignment.Center) {
                val videoRatio = vm.aspects[page] ?: (9f / 16f)
                val boxRatio = maxWidth / maxHeight
                val w = if (videoRatio > boxRatio) maxHeight * videoRatio else maxWidth
                val h = if (videoRatio > boxRatio) maxHeight else maxWidth / videoRatio
                key(vm.slotOf(page)) {
                    AndroidView(
                        factory = { ctx -> TextureView(ctx).also { player.setVideoTextureView(it) } },
                        onRelease = { player.clearVideoTextureView(it) },
                        modifier = Modifier
                            .requiredSize(w, h)
                            .graphicsLayer { alpha = if (isReady) 1f else 0f },
                    )
                }
            }
        }

        AnimatedVisibility(
            isCurrent && vm.paused,
            enter = fadeIn() + scaleIn(initialScale = 1.4f),
            exit = fadeOut() + scaleOut(targetScale = 0.8f),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f)) {
                Icon(Icons.Rounded.PlayArrow, "Play", Modifier.padding(16.dp).size(48.dp))
            }
        }

        // Bottom fade into the app's own background colour, then details on top.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(280.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, surface.copy(alpha = 0.92f)))),
        )
        Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 16.dp, end = 8.dp, bottom = 20.dp), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                val channelUrl = info?.uploaderUrl ?: item.channelUrl
                val channelName = info?.uploaderName ?: item.channelName
                if (channelName.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(
                            info?.uploaderAvatars?.firstOrNull()?.url ?: item.channelAvatar,
                            32.dp,
                            Modifier.clickable(enabled = channelUrl != null) { channelUrl?.let(onChannel) },
                        )
                        Text(
                            if (channelName.startsWith("@")) channelName else "@$channelName",
                            color = onSurface,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 8.dp).weight(1f, fill = false),
                        )
                        if (channelUrl != null) {
                            SubscribeButton(subs.any { it.url == channelUrl }) {
                                AppGraph.toggleSubscribe(Sub(channelUrl, channelName, item.channelAvatar))
                            }
                        }
                    }
                }
                Text(
                    info?.name ?: item.title,
                    color = onSurface,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                val likes = info?.likeCount?.takeIf { it >= 0 }
                ShortAction(Icons.Rounded.ThumbUp, likes?.let(::formatCount) ?: "Like", liked == true) {
                    liked = if (liked == true) null else true
                    rateShort(context, item.id, liked)
                }
                ShortAction(Icons.Rounded.ThumbDown, "Dislike", liked == false) {
                    liked = if (liked == false) null else false
                    rateShort(context, item.id, liked)
                }
                ShortAction(Icons.Rounded.Share, "Share", false) {
                    shareVideo(context, FeedFilter.shareUrl(item.id, item.url))
                }
            }
        }
        if (isCurrent && player != null) {
            LinearProgressIndicator(
                progress = { vm.progress },
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp),
                drawStopIndicator = {},
                gapSize = 0.dp,
            )
        }
    }
}

private fun rateShort(context: android.content.Context, id: String, liked: Boolean?) {
    if (!AppGraph.store.signedIn) {
        toast(context, "Sign in to save likes to YouTube")
        return
    }
    val action = when (liked) {
        true -> "like"
        false -> "dislike"
        null -> "removelike"
    }
    AppGraph.scope.launch { runCatching { AppGraph.account.rate(id, action) } }
}

@Composable
private fun ShortAction(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    val scale by animateFloatAsState(if (active) 1.15f else 1f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "pop")
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            shape = rememberMorphShape(MaterialShapes.Circle, MaterialShapes.Cookie9Sided, if (active) 1f else 0f, rotateBy = 40f),
            color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f),
            contentColor = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(48.dp).graphicsLayer { scaleX = scale; scaleY = scale },
        ) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, label) }
        }
        Text(label, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
    }
}
