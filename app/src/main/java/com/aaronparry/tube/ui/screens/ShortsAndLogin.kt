@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, UnstableApi::class)

package com.aaronparry.tube.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.TextureView
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import coil3.compose.AsyncImage
import com.aaronparry.tube.AppGraph
import com.aaronparry.tube.data.VideoItem
import com.aaronparry.tube.data.formatCount
import com.aaronparry.tube.player.PlayerHolder
import com.aaronparry.tube.player.StreamPicker
import com.aaronparry.tube.ui.components.Avatar
import com.aaronparry.tube.ui.components.EmptyState
import com.aaronparry.tube.ui.components.ErrorState
import com.aaronparry.tube.ui.components.SubscribeButton
import com.aaronparry.tube.ui.components.shareVideo
import com.aaronparry.tube.ui.components.toast
import com.aaronparry.tube.data.Sub
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.stream.StreamInfo

// ---------------- Shorts ----------------

class ShortsViewModel : ViewModel() {
    val player: ExoPlayer = ExoPlayer.Builder(AppGraph.app).build().apply {
        repeatMode = Player.REPEAT_MODE_ONE
    }
    var items by mutableStateOf<List<VideoItem>>(emptyList())
        private set
    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var playingId by mutableStateOf<String?>(null)
        private set
    var ready by mutableStateOf(false)
        private set
    var paused by mutableStateOf(false)
        private set
    var aspect by mutableFloatStateOf(9f / 16f)
        private set
    var progress by mutableFloatStateOf(0f)
        private set
    var infoFor by mutableStateOf<StreamInfo?>(null)
        private set

    private val infos = mutableMapOf<String, StreamInfo>()
    private var playJob: Job? = null
    private var active = false

    init {
        player.addListener(object : Player.Listener {
            override fun onRenderedFirstFrame() { ready = true }
            override fun onVideoSizeChanged(size: VideoSize) {
                if (size.width > 0 && size.height > 0) aspect = size.width.toFloat() / size.height
            }
        })
        viewModelScope.launch {
            while (true) {
                val d = player.duration
                progress = if (d > 0) (player.currentPosition.toFloat() / d).coerceIn(0f, 1f) else 0f
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
                items = (fromAccount + AppGraph.repo.shorts()).distinctBy { it.id }
                if (items.isEmpty()) error = "Couldn't find any Shorts right now"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: e.javaClass.simpleName
            } finally {
                loading = false
            }
        }
    }

    private suspend fun info(item: VideoItem): StreamInfo =
        infos[item.id] ?: AppGraph.repo.stream("https://www.youtube.com/watch?v=${item.id}").also { infos[item.id] = it }

    fun playAt(index: Int) {
        val item = items.getOrNull(index) ?: return
        if (playingId == item.id) return
        playingId = item.id
        ready = false
        paused = false
        playJob?.cancel()
        player.stop()
        playJob = viewModelScope.launch {
            runCatching {
                val i = info(item)
                infoFor = i
                val source = StreamPicker.buildSource(StreamPicker.pick(i, 1080), i) ?: return@runCatching
                player.setMediaSource(source)
                player.prepare()
                if (active) player.play()
                // Warm up the next one so swiping feels instant.
                items.getOrNull(index + 1)?.let { next -> runCatching { info(next) } }
                Unit
            }
        }
    }

    fun togglePause() {
        paused = !paused
        if (paused) player.pause() else player.play()
    }

    fun setActive(on: Boolean) {
        active = on
        if (on) {
            PlayerHolder.player.pause()
            if (!paused) player.play()
        } else player.pause()
    }

    override fun onCleared() {
        player.release()
    }
}

@Composable
fun ShortsScreen(onChannel: (String) -> Unit, vm: ShortsViewModel = viewModel()) {
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

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when {
            vm.loading -> LoadingIndicator(Modifier.align(Alignment.Center).size(64.dp), color = Color.White)
            vm.error != null -> ErrorState(vm.error!!, vm::load, Modifier.align(Alignment.Center))
            else -> {
                val pager = rememberPagerState { vm.items.size }
                LaunchedEffect(pager.settledPage, vm.items) { vm.playAt(pager.settledPage) }
                VerticalPager(
                    state = pager,
                    beyondViewportPageCount = 1,
                    key = { vm.items[it].id },
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    ShortPage(vm, vm.items[page], isCurrent = page == pager.settledPage, onChannel = onChannel)
                }
            }
        }
        Text(
            "Shorts",
            color = Color.White,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.statusBarsPadding().padding(16.dp),
        )
    }
}

@Composable
private fun ShortPage(vm: ShortsViewModel, item: VideoItem, isCurrent: Boolean, onChannel: (String) -> Unit) {
    val context = LocalContext.current
    val showVideo = isCurrent && vm.playingId == item.id
    val info = vm.infoFor?.takeIf { it.id == item.id && showVideo }
    var liked by remember(item.id) { mutableStateOf<Boolean?>(null) }
    val subs = AppGraph.store.subs.value

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(item.id) { detectTapGestures(onTap = { if (showVideo) vm.togglePause() }) },
    ) {
        AsyncImage(
            model = item.thumbnail,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (showVideo) {
            Box(Modifier.fillMaxSize().background(if (vm.ready) Color.Black else Color.Transparent), contentAlignment = Alignment.Center) {
                AndroidView(
                    factory = { ctx ->
                        TextureView(ctx).also {
                            it.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                            vm.player.setVideoTextureView(it)
                        }
                    },
                    onRelease = { vm.player.clearVideoTextureView(it) },
                    modifier = Modifier.fillMaxSize().wrapContentSize().aspectRatio(vm.aspect),
                )
            }
        }

        // Pause indicator
        AnimatedVisibility(
            showVideo && vm.paused,
            enter = fadeIn() + scaleIn(initialScale = 1.4f),
            exit = fadeOut() + scaleOut(targetScale = 0.8f),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Surface(shape = MaterialTheme.shapes.extraLarge, color = Color.Black.copy(alpha = 0.5f)) {
                Icon(Icons.Rounded.PlayArrow, "Play", Modifier.padding(16.dp).size(48.dp), tint = Color.White)
            }
        }

        // Bottom gradient + details
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(260.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)))),
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
                            "@$channelName".replace("@@", "@"),
                            color = Color.White,
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
                    color = Color.White,
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
                ShortAction(Icons.Rounded.Share, "Share", false) { shareVideo(context, item.url) }
            }
        }
        if (showVideo) {
            LinearProgressIndicator(
                progress = { vm.progress },
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(2.dp),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.25f),
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
    val scale by animateFloatAsState(
        if (active) 1.2f else 1f,
        MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "pop",
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            shape = MaterialTheme.shapes.extraLarge,
            color = Color.White.copy(alpha = if (active) 0.9f else 0.18f),
            modifier = Modifier.size(48.dp).graphicsLayer { scaleX = scale; scaleY = scale },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, label, tint = if (active) Color.Black else Color.White)
            }
        }
        Text(label, color = Color.White, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
    }
}

// ---------------- Sign in ----------------

private const val LOGIN_URL =
    "https://accounts.google.com/ServiceLogin?service=youtube&passive=true" +
        "&continue=https%3A%2F%2Fwww.youtube.com%2Fsignin%3Faction_handle_signin%3Dtrue%26app%3Dm%26next%3Dhttps%253A%252F%252Fm.youtube.com%252F"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginScreen(onDone: () -> Unit, onBack: () -> Unit) {
    var loading by remember { mutableStateOf(true) }
    var finished by remember { mutableStateOf(false) }
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Sign in to YouTube")
                        Text(
                            "Uses an unofficial app, so a spare account is safest",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(top = pad.calculateTopPadding())) {
            AndroidView(
                factory = { ctx ->
                    CookieManager.getInstance().setAcceptCookie(true)
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                                loading = true
                            }

                            override fun onPageFinished(view: WebView, url: String?) {
                                loading = false
                                if (finished) return
                                if (url != null && (url.startsWith("https://m.youtube.com") || url.startsWith("https://www.youtube.com"))) {
                                    val cookie = CookieManager.getInstance().getCookie("https://www.youtube.com")
                                    if (cookie != null && cookie.contains("SAPISID")) {
                                        finished = true
                                        CookieManager.getInstance().flush()
                                        AppGraph.store.setCookie(cookie)
                                        AppGraph.syncAccount()
                                        onDone()
                                    }
                                }
                            }
                        }
                        loadUrl(LOGIN_URL)
                    }
                },
                onRelease = { it.stopLoading(); it.destroy() },
                modifier = Modifier.fillMaxSize(),
            )
            AnimatedVisibility(loading, enter = fadeIn(tween(150)), exit = fadeOut(tween(300)), modifier = Modifier.align(Alignment.TopCenter)) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
    }
}
