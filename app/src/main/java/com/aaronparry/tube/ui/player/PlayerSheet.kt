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
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import coil3.compose.AsyncImage
import com.aaronparry.tube.data.Comment
import com.aaronparry.tube.player.Rating
import com.aaronparry.tube.ui.Links
import com.aaronparry.tube.ui.components.CenterLoading
import com.aaronparry.tube.ui.components.PolygonShape
import com.aaronparry.tube.ui.components.rememberMorphShape
import com.aaronparry.tube.ui.components.rememberSpin
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.foundation.interaction.collectIsPressedAsState
import com.aaronparry.tube.ui.components.LaunchOrigin
import com.aaronparry.tube.ui.components.pressScale
import com.aaronparry.tube.ui.components.shareVideo
import com.aaronparry.tube.ui.components.staggeredReveal
import com.aaronparry.tube.ui.components.toast
import kotlinx.coroutines.CancellationException
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
    val slowSpatial = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Container transform: the tapped thumbnail grows into the player.
    val launch = remember { Animatable(1f) }
    var launchFrom by remember { mutableStateOf<Rect?>(null) }
    // Predictive back: the page shrinks as you swipe back.
    val back = remember { Animatable(0f) }

    LaunchedEffect(vm.sheetNonce) {
        val origin = if (vm.sheetTarget == 1f && vm.expand.value < 0.5f) LaunchOrigin.take(vm.current?.id) else LaunchOrigin.take(null)
        if (origin != null) {
            launchFrom = origin
            vm.expand.snapTo(1f)
            launch.snapTo(0f)
            launch.animateTo(1f, slowSpatial)
            launchFrom = null
        } else {
            vm.expand.animateTo(vm.sheetTarget, spatial)
        }
    }

    val p = vm.expand.value
    val isFullscreen = vm.fullscreen || (landscape && p > 0.99f)
    FullscreenEffect(isFullscreen, vm)

    PredictiveBackHandler(enabled = p > 0.5f && !isFullscreen) { events ->
        try {
            events.collect { e -> back.snapTo(e.progress) }
            vm.collapse()
            scope.launch { back.animateTo(0f, spatial) }
        } catch (e: CancellationException) {
            scope.launch { back.animateTo(0f, spatial) }
            throw e
        }
    }

    if (isFullscreen) {
        BackHandler { vm.fullscreen = false }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            VideoSurface(vm, Modifier.fillMaxSize())
            PlayerControls(vm, fullscreen = true, onCollapse = { vm.fullscreen = false })
        }
        return
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val fullH = constraints.maxHeight.toFloat()
        val fullW = constraints.maxWidth.toFloat()
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

        val b = back.value
        val top = lerpF(miniTop, 0f, p)
        val height = lerpF(miniH, fullH, p)
        val sidePad = lerp(8.dp, 0.dp, p)
        val corner = maxOf(lerp(18.dp, 0.dp, p), lerp(0.dp, 32.dp, b))
        val bg = lerp(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.surface, p)
        val videoWidthFull = maxWidth
        val videoW = lerp(MiniHeight * 16f / 9f, videoWidthFull, p)
        val videoH = lerp(MiniHeight, videoWidthFull * 9f / 16f, p)
        val statusPad = with(density) { (statusTop * p).toDp() }
        val launching = launchFrom != null
        val l = launch.value

        Surface(
            color = bg,
            shape = RoundedCornerShape(corner),
            shadowElevation = lerp(6.dp, 0.dp, p),
            modifier = Modifier
                .offset { IntOffset(0, top.roundToInt()) }
                .graphicsLayer {
                    val s = 1f - 0.1f * b
                    scaleX = s
                    scaleY = s
                    transformOrigin = TransformOrigin(0.5f, 0.35f)
                    alpha = if (launching) l.coerceIn(0f, 1f) else 1f
                }
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
                    WatchDetails(
                        vm,
                        onChannel,
                        Modifier
                            .weight(1f)
                            .alpha(p)
                            .graphicsLayer {
                                translationY = if (launching) (1f - l) * 64.dp.toPx() else 0f
                            },
                    )
                }
            }
        }

        // The thumbnail flying from the feed into the player.
        val from = launchFrom
        if (from != null) {
            val toTop = statusTop
            val toH = fullW * 9f / 16f
            val x = lerpF(from.left, 0f, l)
            val y = lerpF(from.top, toTop, l)
            val w = lerpF(from.width, fullW, l)
            val h = lerpF(from.height, toH, l)
            AsyncImage(
                model = vm.current?.thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                    .size(with(density) { w.toDp() }, with(density) { h.toDp() })
                    .clip(RoundedCornerShape(lerp(12.dp, 0.dp, l.coerceIn(0f, 1f))))
                    .background(Color.Black),
            )
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
        FilledTonalIconButton(
            onClick = vm::togglePlay,
            shape = rememberMorphShape(MaterialShapes.Circle, MaterialShapes.Cookie6Sided, if (vm.isPlaying) 1f else 0f, 60f),
        ) {
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
    Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { ctx -> TextureView(ctx).also { vm.player.setVideoTextureView(it) } },
            onRelease = { vm.player.clearVideoTextureView(it) },
            modifier = Modifier.fillMaxSize().wrapContentSize().aspectRatio(vm.aspect),
        )
        // Show the thumbnail until the first video frame arrives, like the official app.
        androidx.compose.animation.AnimatedVisibility(!vm.firstFrame, enter = fadeIn(), exit = fadeOut()) {
            AsyncImage(
                model = vm.current?.thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
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
    val spin = if (buffering) rememberSpin(3_000) else 0f
    val shape = rememberMorphShape(
        from = MaterialShapes.Cookie9Sided,
        to = MaterialShapes.Square,
        target = if (playing) 1f else 0f,
        rotateBy = 90f,
    )
    val source = remember { MutableInteractionSource() }
    FilledIconButton(
        onClick = onClick,
        interactionSource = source,
        shape = shape,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        modifier = Modifier.size(76.dp).pressScale(source, 0.88f).graphicsLayer { rotationZ = spin },
    ) {
        if (buffering) {
            LoadingIndicator(Modifier.size(40.dp).graphicsLayer { rotationZ = -spin })
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

// ---------------- Watch page details (laid out like the official app) ----------------

@Composable
private fun rememberHtml(html: String?, onLink: (String) -> Unit): AnnotatedString {
    val linkColor = MaterialTheme.colorScheme.primary
    return remember(html, linkColor) {
        if (html.isNullOrBlank()) return@remember AnnotatedString("")
        val prepared = if (html.contains("<br", ignoreCase = true)) html else html.replace("\n", "<br>")
        runCatching {
            AnnotatedString.fromHtml(
                prepared,
                linkStyles = TextLinkStyles(SpanStyle(color = linkColor)),
                linkInteractionListener = { link -> (link as? LinkAnnotation.Url)?.url?.let(onLink) },
            )
        }.getOrElse { AnnotatedString(html) }
    }
}

@Composable
private fun WatchDetails(vm: PlayerViewModel, onChannel: (String) -> Unit, modifier: Modifier) {
    val item = vm.current
    val info = vm.info
    val subs by AppGraph.store.subs.collectAsState()
    val context = LocalContext.current
    var showDescription by remember { mutableStateOf(false) }
    var showComments by remember { mutableStateOf(false) }
    val onLink: (String) -> Unit = { url ->
        Links.route(
            context = context,
            url = url,
            currentVideoId = info?.id ?: item?.id,
            seekToMs = { ms ->
                vm.seekTo(ms)
                showDescription = false
                showComments = false
            },
            openVideo = { u, start ->
                showDescription = false
                showComments = false
                vm.openUrl(u, start)
            },
            openChannel = { url2 ->
                showDescription = false
                showComments = false
                onChannel(url2)
            },
        )
    }

    LazyColumn(modifier.fillMaxWidth(), contentPadding = PaddingValues(bottom = 24.dp)) {
        if (item != null) {
            item(key = "head") {
                val branding = rememberBranding(item)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { showDescription = true }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                ) {
                    Text(
                        branding?.title ?: item.title,
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp, lineHeight = 24.sp),
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val meta = listOfNotNull(
                        info?.viewCount?.takeIf { it >= 0 }?.let { "${formatCount(it)} views" } ?: item.viewsText,
                        info?.textualUploadDate ?: item.uploaded,
                    ).joinToString("  ")
                    Text(
                        "$meta  ...more".trim(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
            item(key = "channel") {
                val channelUrl = info?.uploaderUrl ?: item.channelUrl
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = channelUrl != null) { channelUrl?.let(onChannel) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(item.channelAvatar ?: info?.uploaderAvatars?.firstOrNull()?.url, 36.dp)
                    Text(
                        item.channelName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 12.dp).weight(1f, fill = false),
                    )
                    val subCount = info?.uploaderSubscriberCount ?: -1L
                    Text(
                        if (subCount >= 0) formatCount(subCount) else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp).weight(1f),
                    )
                    if (channelUrl != null) {
                        SubscribeButton(subs.any { it.url == channelUrl }) {
                            AppGraph.toggleSubscribe(Sub(channelUrl, item.channelName, item.channelAvatar))
                        }
                    }
                }
            }
            item(key = "actions") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item { LikePill(vm) }
                    item { ActionChip(Icons.Rounded.Share, "Share") { shareVideo(context, item.url) } }
                    if (AppGraph.store.signedIn) {
                        item {
                            ActionChip(Icons.AutoMirrored.Rounded.PlaylistAdd, "Save") {
                                AppGraph.scope.launch { runCatching { AppGraph.account.saveToWatchLater(item.id) } }
                                toast(context, "Saved to Watch later")
                            }
                        }
                    }
                }
            }
            item(key = "comments") {
                CommentsTeaser(vm) { showComments = true }
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
        itemsIndexed(vm.related, key = { _, v -> "r" + v.id }) { i, v ->
            VideoCard(
                v,
                onClick = { vm.open(v) },
                onChannel = onChannel,
                modifier = Modifier.animateItem().staggeredReveal(i),
            )
        }
    }

    if (showDescription && item != null) DescriptionSheet(vm, onLink) { showDescription = false }
    if (showComments) CommentsSheet(vm, onLink) { showComments = false }
}

@Composable
private fun LikePill(vm: PlayerViewModel) {
    val context = LocalContext.current
    val info = vm.info
    val likes = info?.likeCount?.takeIf { it >= 0 } ?: vm.votes?.likes
    val dislikes = vm.votes?.dislikes
    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RateButton(
                icon = if (vm.rating == Rating.LIKE) Icons.Rounded.ThumbUp else Icons.Outlined.ThumbUp,
                label = likes?.let(::formatCount).orEmpty(),
                active = vm.rating == Rating.LIKE,
            ) { if (!vm.rate(Rating.LIKE)) toast(context, "Sign in to save likes to YouTube") }
            Box(Modifier.width(1.dp).height(20.dp).background(MaterialTheme.colorScheme.outlineVariant))
            RateButton(
                icon = if (vm.rating == Rating.DISLIKE) Icons.Rounded.ThumbDown else Icons.Outlined.ThumbDown,
                label = dislikes?.let(::formatCount).orEmpty(),
                active = vm.rating == Rating.DISLIKE,
            ) { if (!vm.rate(Rating.DISLIKE)) toast(context, "Sign in to save dislikes to YouTube") }
        }
    }
}

/** Thumb icon that pops when you tap it. */
@Composable
private fun RateButton(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    val pop = remember { Animatable(1f) }
    val bloom = remember { Animatable(0f) }
    LaunchedEffect(active) {
        if (active) {
            launch { bloom.snapTo(0f); bloom.animateTo(1f, tween(520)) }
            pop.animateTo(1.4f, tween(110))
            pop.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 500f))
        }
    }
    val burstColor = MaterialTheme.colorScheme.primary
    val burstShape = remember { PolygonShape(MaterialShapes.SoftBurst) }
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (bloom.value in 0.001f..0.999f) {
                Box(
                    Modifier
                        .size(20.dp)
                        .graphicsLayer {
                            val b = bloom.value
                            scaleX = 0.6f + 1.6f * b
                            scaleY = 0.6f + 1.6f * b
                            rotationZ = 120f * b
                            alpha = (1f - b) * 0.5f
                        }
                        .clip(burstShape)
                        .background(burstColor),
                )
            }
            Icon(
                icon,
                null,
                Modifier.size(20.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value },
                tint = if (active) MaterialTheme.colorScheme.primary else LocalContentColor.current,
            )
        }
        AnimatedContent(label, label = "count") { text ->
            if (text.isNotEmpty()) {
                Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

@Composable
private fun ActionChip(icon: ImageVector, label: String, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val corner by animateDpAsState(if (pressed) 10.dp else 20.dp, MaterialTheme.motionScheme.fastSpatialSpec(), label = "chip")
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(corner),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        interactionSource = source,
        modifier = Modifier.pressScale(source, 0.94f),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(20.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 6.dp))
        }
    }
}

@Composable
private fun CommentsTeaser(vm: PlayerViewModel, onOpen: () -> Unit) {
    val first = vm.comments.firstOrNull()
    Surface(
        onClick = onOpen,
        enabled = first != null,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(12.dp).animateContentSize()) {
            Text("Comments", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            when {
                first != null -> Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(first.avatar, 24.dp)
                    val plain = remember(first.html) {
                        runCatching { AnnotatedString.fromHtml(first.html).text }.getOrDefault(first.html)
                    }
                    Text(
                        plain,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 10.dp),
                    )
                }
                vm.commentsLoading -> Text(
                    "Loading comments…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
                else -> Text(
                    "Comments are turned off or unavailable",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun DescriptionSheet(vm: PlayerViewModel, onLink: (String) -> Unit, onDismiss: () -> Unit) {
    val item = vm.current ?: return
    val info = vm.info
    val text = rememberHtml(info?.description?.content, onLink)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                Text(
                    "Description",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                Text(
                    rememberBranding(item)?.title ?: item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    Stat(info?.likeCount?.takeIf { it >= 0 }?.let(::formatCount) ?: "–", "Likes")
                    Stat(info?.viewCount?.takeIf { it >= 0 }?.let(::formatCount) ?: "–", "Views")
                    Stat(info?.textualUploadDate ?: item.uploaded ?: "–", "Uploaded")
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                ) {
                    Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(14.dp))
                }
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CommentsSheet(vm: PlayerViewModel, onLink: (String) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)) {
        Text(
            "Comments",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        HorizontalDivider(Modifier.padding(top = 12.dp))
        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
            itemsIndexed(vm.comments) { i, c ->
                CommentRow(c, onLink)
                if (i >= vm.comments.size - 4 && vm.hasMoreComments) {
                    LaunchedEffect(i) { vm.loadComments() }
                }
            }
            if (vm.commentsLoading) item { CenterLoading() }
        }
    }
}

@Composable
private fun CommentRow(c: Comment, onLink: (String) -> Unit) {
    val text = rememberHtml(c.html, onLink)
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Avatar(c.avatar, 28.dp)
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (c.pinned) Icon(Icons.Rounded.PushPin, "Pinned", Modifier.size(14.dp).padding(end = 2.dp))
                Text(
                    listOfNotNull(c.author, c.date).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp))
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.ThumbUp, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                if (c.likes > 0) {
                    Text(
                        formatCount(c.likes.toLong()),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
                if (c.hearted) {
                    Icon(Icons.Rounded.Favorite, "Hearted by creator", Modifier.padding(start = 12.dp).size(16.dp), tint = Color(0xFFE53935))
                }
                if (c.replies > 0) {
                    Text(
                        "${c.replies} ${if (c.replies == 1) "reply" else "replies"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp),
                    )
                }
            }
        }
    }
}
