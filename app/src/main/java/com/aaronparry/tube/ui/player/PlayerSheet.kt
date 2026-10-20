@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.aaronparry.tube.ui.player

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.TextureView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.aaronparry.tube.AppGraph
import com.aaronparry.tube.data.SbCategories
import com.aaronparry.tube.data.SbSegment
import com.aaronparry.tube.data.Sub
import com.aaronparry.tube.data.VideoItem
import com.aaronparry.tube.data.formatCount
import com.aaronparry.tube.data.formatDuration
import com.aaronparry.tube.player.PlayerViewModel
import com.aaronparry.tube.ui.components.Avatar
import com.aaronparry.tube.ui.components.ErrorState
import com.aaronparry.tube.ui.components.SubscribeButton
import com.aaronparry.tube.ui.components.VideoCard
import com.aaronparry.tube.ui.components.rememberBranding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private fun lerpF(a: Float, b: Float, t: Float) = a + (b - a) * t

private val MiniHeight = 64.dp
private val NavBarHeight = 80.dp

@Composable
fun PlayerSheet(
    vm: PlayerViewModel,
    bottomBarVisible: Boolean,
    onChannel: (String) -> Unit,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Animate the sheet whenever the view model asks (open / collapse).
    LaunchedEffect(vm.sheetNonce) { vm.expand.animateTo(vm.sheetTarget, spatial) }

    val p = vm.expand.value
    val isFullscreen = vm.fullscreen || (landscape && p > 0.99f)
    FullscreenEffect(isFullscreen, vm)

    if (isFullscreen) {
        BackHandler { vm.fullscreen = false }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            VideoSurface(vm, Modifier.fillMaxSize())
            PlayerControls(vm, fullscreen = true, onCollapse = { vm.fullscreen = false })
        }
        return
    }

    BackHandler(enabled = p > 0.5f) { vm.collapse() }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val fullH = constraints.maxHeight.toFloat()
        val statusTop = WindowInsets.statusBars.getTop(density).toFloat()
        val navBottom = WindowInsets.navigationBars.getBottom(density).toFloat()
        val miniH = with(density) { MiniHeight.toPx() }
        val barH = if (bottomBarVisible) with(density) { NavBarHeight.toPx() } + navBottom else navBottom
        val miniTop = fullH - barH - miniH - with(density) { 8.dp.toPx() }
        val travel = miniTop.coerceAtLeast(1f)

        val dragState = rememberDraggableState { delta ->
            scope.launch { vm.expand.snapTo((vm.expand.value - delta / travel).coerceIn(0f, 1f)) }
        }
        val settle: suspend (Float) -> Unit = { velocity ->
            val target = when {
                velocity < -1200f -> 1f
                velocity > 1200f -> 0f
                vm.expand.value > 0.5f -> 1f
                else -> 0f
            }
            vm.expand.animateTo(target, spatial, initialVelocity = -velocity / travel)
        }

        val top = lerpF(miniTop, 0f, p)
        val height = lerpF(miniH, fullH, p)
        val sidePad = lerp(8.dp, 0.dp, p)
        val corner = lerp(18.dp, 0.dp, p)
        val bg = lerp(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.surface, p)
        val videoWidthFull = maxWidth
        val videoW = lerp(MiniHeight * 16f / 9f, videoWidthFull, p)
        val videoH = lerp(MiniHeight, videoWidthFull * 9f / 16f, p)
        val statusPad = with(density) { (statusTop * p).toDp() }

        Surface(
            color = bg,
            shape = RoundedCornerShape(corner),
            shadowElevation = lerp(6.dp, 0.dp, p),
            modifier = Modifier
                .offset { IntOffset(0, top.roundToInt()) }
                .padding(horizontal = sidePad)
                .fillMaxWidth()
                .height(with(density) { height.toDp() }),
        ) {
            Column(Modifier.fillMaxSize()) {
                Spacer(Modifier.height(statusPad))
                Box {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(videoH)
                            .draggable(
                                state = dragState,
                                orientation = Orientation.Vertical,
                                onDragStopped = { v -> settle(v) },
                            )
                            .clickable(enabled = p < 0.05f) { vm.showSheet(1f) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .width(videoW)
                                .height(videoH)
                                .clip(RoundedCornerShape(lerp(12.dp, 0.dp, p)))
                                .background(Color.Black),
                        ) {
                            VideoSurface(vm, Modifier.fillMaxSize())
                            if (p > 0.6f) {
                                PlayerControls(
                                    vm,
                                    fullscreen = false,
                                    onCollapse = { vm.collapse() },
                                    modifier = Modifier.alpha(((p - 0.6f) / 0.4f).coerceIn(0f, 1f)),
                                )
                            }
                        }
                        if (p < 0.4f) MiniInfo(vm, Modifier.alpha(1f - p / 0.4f).weight(1f))
                    }
                    // Thin progress line on the mini player
                    if (p < 0.2f && vm.durationMs > 0) {
                        val frac = (vm.positionMs.toFloat() / vm.durationMs).coerceIn(0f, 1f)
                        val primary = MaterialTheme.colorScheme.primary
                        Canvas(Modifier.fillMaxWidth().height(2.dp).align(Alignment.BottomStart).alpha(1f - p / 0.2f)) {
                            drawRect(primary, size = Size(size.width * frac, size.height))
                        }
                    }
                }
                if (p > 0.02f) {
                    WatchDetails(vm, onChannel, Modifier.weight(1f).alpha(p))
                }
            }
        }
    }
}

@Composable
private fun MiniInfo(vm: PlayerViewModel, modifier: Modifier) {
    val item = vm.current
    Row(modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                item?.title.orEmpty(),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                item?.channelName.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = vm::togglePlay) {
            AnimatedContent(vm.isPlaying, transitionSpec = { scaleIn() + fadeIn() togetherWith scaleOut() + fadeOut() }, label = "mini") {
                Icon(if (it) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (it) "Pause" else "Play")
            }
        }
        IconButton(onClick = vm::close) { Icon(Icons.Rounded.Close, "Close") }
    }
}

/** Draws video onto a TextureView so it animates smoothly with the sheet. */
@Composable
fun VideoSurface(vm: PlayerViewModel, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { ctx -> TextureView(ctx).also { vm.player.setVideoTextureView(it) } },
            onRelease = { vm.player.clearVideoTextureView(it) },
            modifier = Modifier.fillMaxSize().wrapContentSize().aspectRatio(vm.aspect),
        )
    }
}

@Composable
private fun FullscreenEffect(fullscreen: Boolean, vm: PlayerViewModel) {
    val view = LocalView.current
    val activity = LocalContext.current as? Activity ?: return
    LaunchedEffect(vm.fullscreen) {
        if (vm.fullscreen) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else if (activity.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            delay(2500)
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
    DisposableEffect(fullscreen) {
        val controller = WindowCompat.getInsetsController(activity.window, view)
        if (fullscreen) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { }
    }
}

// ---------------- Controls ----------------

@Composable
fun PlayerControls(
    vm: PlayerViewModel,
    fullscreen: Boolean,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var visible by remember { mutableStateOf(true) }
    var poke by remember { mutableIntStateOf(0) }
    var seekHint by remember { mutableStateOf<String?>(null) }
    var showOptions by remember { mutableStateOf(false) }

    LaunchedEffect(visible, vm.isPlaying, poke) {
        if (visible && vm.isPlaying) {
            delay(3000)
            visible = false
        }
    }
    LaunchedEffect(seekHint) {
        if (seekHint != null) { delay(700); seekHint = null }
    }

    Box(
        modifier
            .fillMaxSize()
            .pointerInputTaps(
                onTap = { visible = !visible; poke++ },
                onDoubleTap = { left ->
                    vm.seekBy(if (left) -10_000 else 10_000)
                    seekHint = if (left) "« 10s" else "10s »"
                },
            ),
    ) {
        AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.42f))) {
                // Top row
                Row(
                    Modifier.fillMaxWidth().align(Alignment.TopStart).padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onCollapse) {
                        Icon(
                            if (fullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.KeyboardArrowDown,
                            "Minimise",
                            tint = Color.White,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { showOptions = true; poke++ }) {
                        Icon(Icons.Rounded.Tune, "Quality and speed", tint = Color.White)
                    }
                }

                // Centre row
                Row(
                    Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { vm.seekBy(-10_000); poke++ }) {
                        Icon(Icons.Rounded.Replay10, "Back 10 seconds", tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                    PlayPauseButton(vm.isPlaying, vm.buffering) { vm.togglePlay(); poke++ }
                    IconButton(onClick = { vm.seekBy(10_000); poke++ }) {
                        Icon(Icons.Rounded.Forward10, "Forward 10 seconds", tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                }

                // Bottom row
                Column(Modifier.fillMaxWidth().align(Alignment.BottomStart).padding(horizontal = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${formatDuration(vm.positionMs / 1000)} / ${formatDuration(vm.durationMs / 1000)}",
                            color = Color.White,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { vm.fullscreen = !fullscreen; poke++ }) {
                            Icon(
                                if (fullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                                "Fullscreen",
                                tint = Color.White,
                            )
                        }
                    }
                    SeekBar(
                        positionMs = vm.positionMs,
                        durationMs = vm.durationMs,
                        bufferedMs = vm.bufferedMs,
                        segments = vm.segments,
                        onSeek = { vm.seekTo(it); poke++ },
                        onDragging = { poke++ },
                    )
                }
            }
        }

        if (vm.buffering && !visible) {
            LoadingIndicator(Modifier.align(Alignment.Center).size(48.dp), color = Color.White)
        }

        // Double-tap hint
        AnimatedVisibility(
            seekHint != null,
            enter = fadeIn() + scaleIn(initialScale = 0.7f),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Surface(shape = RoundedCornerShape(50), color = Color.Black.copy(alpha = 0.6f)) {
                Text(seekHint.orEmpty(), color = Color.White, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
        }

        // SponsorBlock "Skipped" pill
        AnimatedVisibility(
            vm.skipped != null,
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 48.dp),
        ) {
            val seg = vm.skipped
            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.secondaryContainer) {
                Row(Modifier.padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Skipped ${seg?.let { SbCategories.of(it.category)?.label?.lowercase() } ?: "segment"}",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    TextButton(onClick = vm::undoSkip) { Text("Undo") }
                }
            }
        }
    }

    if (showOptions) PlayerOptionsSheet(vm) { showOptions = false }
}

private fun Modifier.pointerInputTaps(onTap: () -> Unit, onDoubleTap: (left: Boolean) -> Unit): Modifier =
    this.then(
        Modifier.pointerInput(Unit) {
            detectTapGestures(
                onTap = { onTap() },
                onDoubleTap = { offset -> onDoubleTap(offset.x < size.width / 2f) },
            )
        },
    )

/** Big play button whose shape morphs between circle (paused) and rounded square (playing). */
@Composable
private fun PlayPauseButton(playing: Boolean, buffering: Boolean, onClick: () -> Unit) {
    val corner by animateDpAsState(
        if (playing) 22.dp else 36.dp,
        MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "playShape",
    )
    FilledIconButton(
        onClick = onClick,
        shape = RoundedCornerShape(corner),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        modifier = Modifier.size(72.dp),
    ) {
        if (buffering) {
            LoadingIndicator(Modifier.size(40.dp))
        } else {
            AnimatedContent(
                playing,
                transitionSpec = { (scaleIn(initialScale = 0.5f) + fadeIn()) togetherWith (scaleOut(targetScale = 0.5f) + fadeOut()) },
                label = "playIcon",
            ) {
                Icon(if (it) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (it) "Pause" else "Play", Modifier.size(36.dp))
            }
        }
    }
}

@Composable
private fun SeekBar(
    positionMs: Long,
    durationMs: Long,
    bufferedMs: Long,
    segments: List<SbSegment>,
    onSeek: (Long) -> Unit,
    onDragging: () -> Unit,
) {
    var drag by remember { mutableStateOf<Float?>(null) }
    val frac = drag ?: if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val bufFrac = if (durationMs > 0) (bufferedMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val thumb by animateDpAsState(if (drag != null) 9.dp else 6.dp, label = "thumb")
    val track by animateDpAsState(if (drag != null) 6.dp else 4.dp, label = "track")
    val primary = MaterialTheme.colorScheme.primary

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(28.dp)
            .pointerInput(durationMs) {
                detectHorizontalDragGestures(
                    onDragStart = { drag = (it.x / size.width).coerceIn(0f, 1f); onDragging() },
                    onDragEnd = { drag?.let { onSeek((it * durationMs).toLong()) }; drag = null },
                    onDragCancel = { drag = null },
                ) { change, _ ->
                    drag = (change.position.x / size.width).coerceIn(0f, 1f)
                    onDragging()
                }
            }
            .pointerInput(durationMs) {
                detectTapGestures { onSeek(((it.x / size.width).coerceIn(0f, 1f) * durationMs).toLong()) }
            },
    ) {
        val h = track.toPx()
        val y = size.height / 2f - h / 2f
        val r = CornerRadius(h / 2f)
        drawRoundRect(Color.White.copy(alpha = 0.25f), Offset(0f, y), Size(size.width, h), r)
        drawRoundRect(Color.White.copy(alpha = 0.45f), Offset(0f, y), Size(size.width * bufFrac, h), r)
        if (durationMs > 0) {
            val durSec = durationMs / 1000f
            segments.forEach { s ->
                val color = SbCategories.of(s.category)?.color ?: Color.Green
                val x0 = (s.startSec.toFloat() / durSec).coerceIn(0f, 1f) * size.width
                val x1 = (s.endSec.toFloat() / durSec).coerceIn(0f, 1f) * size.width
                drawRect(color, Offset(x0, y), Size((x1 - x0).coerceAtLeast(2f), h))
            }
        }
        drawRoundRect(primary, Offset(0f, y), Size(size.width * frac, h), r)
        drawCircle(primary, thumb.toPx(), Offset(size.width * frac, size.height / 2f))
    }
}

@Composable
private fun PlayerOptionsSheet(vm: PlayerViewModel, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text("Quality", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(vm.qualities.ifEmpty { listOf(vm.quality) }) { q ->
                androidx.compose.material3.FilterChip(
                    selected = q == vm.quality,
                    onClick = { vm.changeQuality(q); onDismiss() },
                    label = { Text("${q}p") },
                )
            }
        }
        Text("Speed", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 8.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)) { s ->
                androidx.compose.material3.FilterChip(
                    selected = abs(s - vm.speed) < 0.01f,
                    onClick = { vm.changeSpeed(s); onDismiss() },
                    label = { Text(if (s == 1f) "Normal" else "${s}x") },
                )
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

// ---------------- Watch page details ----------------

@Composable
private fun WatchDetails(vm: PlayerViewModel, onChannel: (String) -> Unit, modifier: Modifier) {
    val item = vm.current
    val info = vm.info
    val subs by AppGraph.store.subs.collectAsState()
    val context = LocalContext.current

    LazyColumn(modifier.fillMaxWidth(), contentPadding = PaddingValues(bottom = 24.dp)) {
        if (item != null) {
            item(key = "head") {
                val branding = rememberBranding(item)
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        branding?.title ?: item.title,
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 19.sp, lineHeight = 25.sp),
                        fontWeight = FontWeight.SemiBold,
                    )
                    val meta = listOfNotNull(
                        info?.viewCount?.takeIf { it >= 0 }?.let { "${formatCount(it)} views" },
                        info?.textualUploadDate ?: item.uploaded,
                    ).joinToString(" · ")
                    if (meta.isNotBlank()) {
                        Text(
                            meta,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
            item(key = "channel") {
                val channelUrl = info?.uploaderUrl ?: item.channelUrl
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = channelUrl != null) { channelUrl?.let(onChannel) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(item.channelAvatar, 40.dp)
                    Column(Modifier.padding(horizontal = 12.dp).weight(1f)) {
                        Text(item.channelName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val subCount = info?.uploaderSubscriberCount ?: -1L
                        if (subCount >= 0) {
                            Text(
                                "${formatCount(subCount)} subscribers",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (channelUrl != null) {
                        SubscribeButton(subs.any { it.url == channelUrl }) {
                            AppGraph.store.toggleSub(Sub(channelUrl, item.channelName, item.channelAvatar))
                        }
                    }
                }
            }
            item(key = "actions") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                            Row(Modifier.padding(horizontal = 16.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.ThumbUp, "Likes", Modifier.size(18.dp))
                                val likes = info?.likeCount?.takeIf { it >= 0 } ?: vm.votes?.likes
                                Text(likes?.let(::formatCount) ?: "", Modifier.padding(start = 8.dp, end = 12.dp), style = MaterialTheme.typography.labelLarge)
                                Box(Modifier.width(1.dp).height(18.dp).background(MaterialTheme.colorScheme.outlineVariant))
                                Icon(Icons.Rounded.ThumbDown, "Dislikes", Modifier.padding(start = 12.dp).size(18.dp))
                                AnimatedContent(vm.votes?.dislikes, label = "dislikes") { d ->
                                    Text(d?.let(::formatCount) ?: "", Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                    item {
                        FilledTonalButton(onClick = {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, item.url)
                            }
                            context.startActivity(Intent.createChooser(send, "Share video"))
                        }) {
                            Icon(Icons.Rounded.Share, null, Modifier.size(18.dp))
                            Text("Share", Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
            item(key = "desc") {
                var expanded by remember(item.id) { mutableStateOf(false) }
                val html = info?.description?.content
                if (!html.isNullOrBlank()) {
                    val text = remember(html) {
                        runCatching { AnnotatedString.fromHtml(html.replace("\n", "<br>")) }
                            .getOrElse { AnnotatedString(html) }
                    }
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { expanded = !expanded }
                            .animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()),
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(
                                text,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = if (expanded) Int.MAX_VALUE else 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                if (expanded) "Show less" else "…more",
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                }
            }
        }
        when {
            vm.error != null -> item(key = "err") { ErrorState(vm.error!!, vm::retry) }
            vm.loading -> item(key = "load") {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    LoadingIndicator(Modifier.size(56.dp))
                }
            }
        }
        if (vm.related.isNotEmpty()) {
            item(key = "upnext") {
                Text(
                    "Up next",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                )
            }
            items(vm.related, key = { "r" + it.id }) { v: VideoItem ->
                VideoCard(v, onClick = { vm.open(v) }, onChannel = onChannel, modifier = Modifier.animateItem())
            }
        }
    }
}
