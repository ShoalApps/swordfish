@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.aaronparry.tube.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import androidx.compose.runtime.collectAsState
import com.aaronparry.tube.AppGraph
import com.aaronparry.tube.data.Branding
import com.aaronparry.tube.data.ChannelItem
import com.aaronparry.tube.data.VideoItem
import com.aaronparry.tube.data.formatCount
import com.aaronparry.tube.data.formatDuration
import com.aaronparry.tube.data.subtitle

/** Extra space lists need at the bottom (nav bar / mini player). */
val LocalBottomPadding = staticCompositionLocalOf { 0.dp }

fun listPadding(bottom: Dp, top: Dp = 0.dp) = PaddingValues(top = top, bottom = bottom + 8.dp)

/** Expressive "squish" when pressed. */
@Composable
fun Modifier.pressScale(source: MutableInteractionSource, pressed: Float = 0.96f): Modifier {
    val isPressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressed else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "press",
    )
    return graphicsLayer { scaleX = scale; scaleY = scale }
}

/** DeArrow title + thumbnail for a video, if enabled. */
@Composable
fun rememberBranding(item: VideoItem): Branding? {
    val enabled = AppGraph.store.settings.collectAsState().value.deArrow
    val state by produceState(
        initialValue = if (enabled) AppGraph.deArrow.cached(item.id) else null,
        key1 = item.id,
        key2 = enabled,
    ) {
        value = if (enabled) AppGraph.deArrow.branding(item.id) else null
    }
    return state
}

@Composable
fun Thumbnail(
    item: VideoItem,
    branding: Branding?,
    modifier: Modifier = Modifier,
    corner: Dp = 16.dp,
    showBadge: Boolean = true,
) {
    var dearrowFailed by remember(item.id) { mutableStateOf(false) }
    val url = branding?.thumbnail?.takeIf { !dearrowFailed } ?: item.thumbnail
    Box(
        modifier
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(corner))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            onError = { if (url != item.thumbnail) dearrowFailed = true },
        )
        if (showBadge) {
            val label = if (item.isLive) "LIVE" else formatDuration(item.durationSec)
            if (label.isNotEmpty()) {
                Text(
                    text = label,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (item.isLive) Color(0xFFCC0000) else Color.Black.copy(alpha = 0.78f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}

@Composable
fun Avatar(url: String?, size: Dp, modifier: Modifier = Modifier) {
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    )
}

/** Full-width card, like the official app's home feed. */
@Composable
fun VideoCard(
    item: VideoItem,
    onClick: () -> Unit,
    onChannel: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val branding = rememberBranding(item)
    val source = remember { MutableInteractionSource() }
    Column(
        modifier
            .fillMaxWidth()
            .pressScale(source)
            .clip(RoundedCornerShape(20.dp))
            .clickable(interactionSource = source, indication = androidx.compose.material3.ripple(), onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Thumbnail(item, branding, Modifier.fillMaxWidth())
        Row(Modifier.padding(top = 10.dp, start = 2.dp, end = 2.dp)) {
            Avatar(
                url = item.channelAvatar,
                size = 36.dp,
                modifier = Modifier.clickable(enabled = onChannel != null && item.channelUrl != null) {
                    item.channelUrl?.let { onChannel?.invoke(it) }
                },
            )
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                AnimatedContent(
                    targetState = branding?.title ?: item.title,
                    transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
                    label = "title",
                ) { title ->
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = item.subtitle(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

/** Compact row: small thumbnail on the left (history, related on wide screens). */
@Composable
fun VideoRow(item: VideoItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val branding = rememberBranding(item)
    val source = remember { MutableInteractionSource() }
    Row(
        modifier
            .fillMaxWidth()
            .pressScale(source, 0.97f)
            .clip(RoundedCornerShape(16.dp))
            .clickable(interactionSource = source, indication = androidx.compose.material3.ripple(), onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Thumbnail(item, branding, Modifier.width(164.dp), corner = 12.dp)
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(
                text = branding?.title ?: item.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.subtitle(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
fun ChannelRow(item: ChannelItem, onClick: () -> Unit) {
    val subs by AppGraph.store.subs.collectAsState()
    val subscribed = subs.any { it.url == item.url }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(item.avatar, 64.dp)
        Column(Modifier.padding(horizontal = 16.dp).weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (item.subscribers >= 0) {
                Text(
                    "${formatCount(item.subscribers)} subscribers",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        SubscribeButton(subscribed) {
            AppGraph.store.toggleSub(com.aaronparry.tube.data.Sub(item.url, item.name, item.avatar))
        }
    }
}

/** Morphs from a pill to a softer square when subscribed. */
@Composable
fun SubscribeButton(subscribed: Boolean, onClick: () -> Unit) {
    val corner by animateDpAsState(
        if (subscribed) 14.dp else 24.dp,
        MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "corner",
    )
    val container by animateColorAsState(
        if (subscribed) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.primary,
        label = "container",
    )
    val contentColor by animateColorAsState(
        if (subscribed) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary,
        label = "content",
    )
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(corner),
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = contentColor),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    ) {
        AnimatedContent(
            targetState = subscribed,
            transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.8f)) togetherWith fadeOut() },
            label = "sub",
        ) { isSub ->
            if (isSub) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.NotificationsNone, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Subscribed")
                }
            } else {
                Text("Subscribe")
            }
        }
    }
}

@Composable
fun CenterLoading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
        LoadingIndicator(Modifier.size(56.dp))
    }
}

@Composable
fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            Icons.Rounded.ErrorOutline,
            null,
            Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.error,
        )
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
        FilledTonalButton(onClick = onRetry) { Text("Try again") }
    }
}

@Composable
fun EmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
            Icon(icon, null, Modifier.padding(20.dp).size(40.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Pulsing placeholder cards while a feed loads. */
@Composable
fun SkeletonCards(count: Int = 3) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "alpha",
    )
    Column(Modifier.alpha(alpha)) {
        repeat(count) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Box(
                    Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                )
                Row(Modifier.padding(top = 10.dp)) {
                    Box(Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest))
                    Column(Modifier.padding(start = 12.dp)) {
                        Box(
                            Modifier.fillMaxWidth(0.85f).height(14.dp).clip(RoundedCornerShape(7.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        )
                        Spacer(Modifier.height(8.dp))
                        Box(
                            Modifier.fillMaxWidth(0.5f).height(12.dp).clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        )
                    }
                }
            }
        }
    }
}
