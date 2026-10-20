@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.aaronateataco.materialtube.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import com.aaronateataco.materialtube.AppGraph
import com.aaronateataco.materialtube.data.SbCategories
import com.aaronateataco.materialtube.data.Settings
import com.aaronateataco.materialtube.ui.components.LocalBottomPadding
import com.aaronateataco.materialtube.ui.components.MorphShape
import com.aaronateataco.materialtube.ui.components.PolygonShape
import com.aaronateataco.materialtube.ui.components.listPadding
import com.aaronateataco.materialtube.ui.components.openExternal
import com.aaronateataco.materialtube.ui.components.toast
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val qualityOptions = listOf(2160, 1440, 1080, 720, 480, 360, 240, 144)

/** A pending "pick one of these" dialog. */
private data class Choice(val title: String, val options: List<Pair<String, String>>, val selected: String, val onPick: (String) -> Unit)

// ---------------- Main settings ----------------

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    onSwitchChannel: () -> Unit,
    onDeveloper: () -> Unit,
    onAbout: () -> Unit,
) {
    val store = AppGraph.store
    val s by store.settings.collectAsState()
    val account by store.account.collectAsState()
    val cookie by store.cookie.collectAsState()
    val signedIn = cookie?.contains("SAPISID") == true
    var choice by remember { mutableStateOf<Choice?>(null) }
    val update: ((Settings) -> Settings) -> Unit = { store.updateSettings(it) }

    SettingsScaffold("Settings", onBack) {
        item { Section("Account") }
        item {
            if (signedIn) {
                Row2(account?.name ?: "Signed in", account?.handle ?: "YouTube account") {
                    TextButton(onClick = { AppGraph.signOut() }) { Text("Sign out") }
                }
            } else {
                Link("Sign in to YouTube", "Your feed, subscriptions, likes and Watch later", onSignIn)
            }
        }
        if (signedIn) {
            item { Link("Switch channel", "Use a different channel on this Google account", onSwitchChannel) }
            item { Link("Import subscriptions from YouTube", "Adds any channels you follow on YouTube") { AppGraph.syncAccount() } }
        }

        item { Section("Appearance") }
        item {
            Segmented("Theme", listOf("System", "Light", "Dark"), s.theme) { i -> update { it.copy(theme = i) } }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            item { Toggle("Dynamic colour", "Match colours to your wallpaper", s.dynamicColor) { v -> update { it.copy(dynamicColor = v) } } }
        }

        item { Section("Feed") }
        item {
            Segmented("Home feed", listOf("Recommended", "Learning"), if (s.feedMode == "learning") 1 else 0) { i ->
                update { it.copy(feedMode = if (i == 1) "learning" else "recommended") }
            }
        }
        if (s.feedMode == "learning") {
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("Learning topics", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 8.dp))
                    TopicPicker(s.learningTopics) { t -> update { it.copy(learningTopics = t) } }
                }
            }
        }

        item { Section("Shorts") }
        item {
            Toggle("Turn off Shorts", "Hides the Shorts tab, shelves and Shorts in every feed", s.shortsDisabled) { v ->
                update { it.copy(shortsDisabled = v) }
            }
        }
        if (!s.shortsDisabled) {
            item {
                val keys = listOf("youtube", "tiktok", "reels")
                Segmented("Shorts layout", listOf("YouTube", "TikTok", "Reels"), keys.indexOf(s.shortsLayout).coerceAtLeast(0)) { i ->
                    update { it.copy(shortsLayout = keys[i]) }
                }
            }
            item {
                val opts = listOf(0, 5, 10, 20, 50).map { (if (it == 0) "No limit" else "$it Shorts") to it.toString() }
                Link("Limit per visit", if (s.shortsSessionLimit == 0) "No limit" else "${s.shortsSessionLimit} Shorts, then a break screen") {
                    choice = Choice("Shorts per visit", opts, s.shortsSessionLimit.toString()) { v -> update { it.copy(shortsSessionLimit = v.toInt()) } }
                }
            }
            item {
                val today = store.shortsMsToday() / 60_000
                val opts = listOf(0, 10, 15, 30, 60, 90).map { (if (it == 0) "No limit" else "$it minutes") to it.toString() }
                Link(
                    "Daily time limit",
                    (if (s.shortsDailyMinutes == 0) "No limit" else "${s.shortsDailyMinutes} minutes a day") + " · $today min today",
                ) {
                    choice = Choice("Shorts per day", opts, s.shortsDailyMinutes.toString()) { v -> update { it.copy(shortsDailyMinutes = v.toInt()) } }
                }
            }
        }

        item { Section("Video quality preferences") }
        item {
            Link("On Wi-Fi", "${s.qualityWifi}p") {
                choice = Choice("Quality on Wi-Fi", qualityChoices(), s.qualityWifi.toString()) { v -> update { it.copy(qualityWifi = v.toInt()) } }
            }
        }
        item {
            Link("On mobile networks", "${s.qualityMobile}p") {
                choice = Choice("Quality on mobile networks", qualityChoices(), s.qualityMobile.toString()) { v -> update { it.copy(qualityMobile = v.toInt()) } }
            }
        }

        item { Section("Playback") }
        item { Toggle("Autoplay", "Play the next video when one ends", s.autoplayNext) { v -> update { it.copy(autoplayNext = v) } } }
        item { Toggle("Background play", "Keep audio playing with the screen off or app closed", s.backgroundPlay) { v -> update { it.copy(backgroundPlay = v) } } }
        item { Toggle("Picture-in-picture", "Shrink to a floating window when you leave the app", s.autoPip) { v -> update { it.copy(autoPip = v) } } }
        item { Toggle("Ambient mode", "A soft glow of the video's colours around the player", s.ambientMode) { v -> update { it.copy(ambientMode = v) } } }

        item { Section("SponsorBlock") }
        item { Toggle("Skip segments", "Crowd-sourced skipping of sponsors and other filler", s.sponsorBlock) { v -> update { it.copy(sponsorBlock = v) } } }
        if (s.sponsorBlock) {
            SbCategories.all.forEach { cat ->
                item(key = cat.key) {
                    ListItem(
                        headlineContent = { Text(cat.label) },
                        leadingContent = { Box(Modifier.size(14.dp).clip(CircleShape).background(cat.color)) },
                        trailingContent = {
                            Switch(
                                checked = cat.key in s.sbSkip,
                                onCheckedChange = { on -> update { it.copy(sbSkip = if (on) it.sbSkip + cat.key else it.sbSkip - cat.key) } },
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.padding(start = 16.dp),
                    )
                }
            }
        }

        item { Section("Community") }
        item { Toggle("DeArrow", "Replace clickbait titles and thumbnails", s.deArrow) { v -> update { it.copy(deArrow = v) } } }
        item { Toggle("Return YouTube Dislike", "Show dislike counts", s.ryd) { v -> update { it.copy(ryd = v) } } }

        item { Section("More") }
        if (s.developerMode) item { Link("Developer settings", "Advanced tweaks, filters and debugging", onDeveloper) }
        item { Link("About MaterialTube", "Version, credits and licence", onAbout) }
    }

    choice?.let { c -> ChoiceDialog(c) { choice = null } }
}

private fun qualityChoices() = qualityOptions.map { (if (it == 1080) "${it}p (default)" else "${it}p") to it.toString() }

// ---------------- Developer settings (Morphe-style) ----------------

@Composable
fun DeveloperSettingsScreen(onBack: () -> Unit) {
    val store = AppGraph.store
    val s by store.settings.collectAsState()
    val context = LocalContext.current
    var choice by remember { mutableStateOf<Choice?>(null) }
    var keywordDialog by remember { mutableStateOf(false) }
    var debugText by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val update: ((Settings) -> Settings) -> Unit = { store.updateSettings(it) }

    SettingsScaffold("Developer settings", onBack) {
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) {
                Text(
                    "Extra tweaks in the style of Morphe. Some change how the app behaves in ways that may surprise you.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }

        item { Section("Feed filters") }
        item { Toggle("Hide live streams", null, s.hideLive) { v -> update { it.copy(hideLive = v) } } }
        item { Toggle("Hide watched videos", "Anything in your history", s.hideWatched) { v -> update { it.copy(hideWatched = v) } } }
        item { Toggle("Hide Shorts shelf on Home", null, s.hideShortsShelf) { v -> update { it.copy(hideShortsShelf = v) } } }
        item {
            Link("Keyword filter", s.keywordFilter.ifBlank { "Hide videos whose title or channel contains these words" }) { keywordDialog = true }
        }

        item { Section("Player") }
        item { Toggle("Remember playback speed", "Keep the last speed you picked", s.rememberSpeed) { v -> update { it.copy(rememberSpeed = v) } } }
        item {
            val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
            Link("Default playback speed", "${s.defaultSpeed}x") {
                choice = Choice("Default speed", speeds.map { (if (it == 1f) "Normal" else "${it}x") to it.toString() }, s.defaultSpeed.toString()) { v ->
                    update { it.copy(defaultSpeed = v.toFloat()) }
                }
            }
        }
        item {
            Link("Double-tap seek", "${s.doubleTapSeconds} seconds") {
                choice = Choice("Double-tap seek", listOf(5, 10, 15, 20, 30).map { "$it seconds" to it.toString() }, s.doubleTapSeconds.toString()) { v ->
                    update { it.copy(doubleTapSeconds = v.toInt()) }
                }
            }
        }
        item { Toggle("Swipe controls", "Swipe up/down in full screen: left for brightness, right for volume", s.swipeGestures) { v -> update { it.copy(swipeGestures = v) } } }
        item { Toggle("Loop videos", "Replay instead of ending", s.loopVideo) { v -> update { it.copy(loopVideo = v) } } }

        item { Section("Layout") }
        item { Toggle("Compact feed", "Small thumbnails in lists instead of full-width cards", s.compactFeed) { v -> update { it.copy(compactFeed = v) } } }
        item { Toggle("Hide related videos", "No 'up next' list under the player", s.hideRelated) { v -> update { it.copy(hideRelated = v) } } }
        item { Toggle("Hide comments", null, s.hideComments) { v -> update { it.copy(hideComments = v) } } }
        item { Toggle("Navigation labels", "Show text under the bottom bar icons", s.navLabels) { v -> update { it.copy(navLabels = v) } } }
        item { Toggle("Hide channel row in Subscriptions", null, s.hideSubsAvatars) { v -> update { it.copy(hideSubsAvatars = v) } } }

        item { Section("Sharing") }
        item { Toggle("Clean share links", "Remove tracking (si=…) and use short youtu.be links", s.sanitizeShareLinks) { v -> update { it.copy(sanitizeShareLinks = v) } } }

        item { Section("Debug") }
        item {
            Link("Channel switcher response", "What YouTube sent when listing your channels") {
                debugText = "Loading…"
                scope.launch {
                    runCatching { AppGraph.account.channels() }
                    debugText = AppGraph.account.lastChannelDebug.ifBlank { "Nothing yet. Sign in first." }
                }
            }
        }
        item {
            Link("Clear image cache", "Thumbnails will download again") {
                val loader = SingletonImageLoader.get(context)
                loader.memoryCache?.clear()
                loader.diskCache?.clear()
                toast(context, "Image cache cleared")
            }
        }
        item {
            Link("Reset all settings", "Keeps your sign-in, subscriptions and history") {
                store.updateSettings { Settings() }
                toast(context, "Settings reset")
                onBack()
            }
        }
        item {
            Link("Turn off developer settings", null) {
                update { it.copy(developerMode = false) }
                onBack()
            }
        }
    }

    choice?.let { c -> ChoiceDialog(c) { choice = null } }

    if (keywordDialog) {
        var text by remember { mutableStateOf(s.keywordFilter) }
        AlertDialog(
            onDismissRequest = { keywordDialog = false },
            title = { Text("Keyword filter") },
            text = {
                Column {
                    Text("Separate words with commas.", style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth().padding(top = 12.dp), placeholder = { Text("e.g. reaction, prank") })
                }
            },
            confirmButton = {
                TextButton(onClick = { update { it.copy(keywordFilter = text.trim()) }; keywordDialog = false }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { keywordDialog = false }) { Text("Cancel") } },
        )
    }

    debugText?.let { text ->
        AlertDialog(
            onDismissRequest = { debugText = null },
            title = { Text("Channel switcher") },
            text = {
                Text(
                    text,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("debug", text))
                    toast(context, "Copied")
                }) { Text("Copy") }
            },
            dismissButton = { TextButton(onClick = { debugText = null }) { Text("Close") } },
        )
    }
}

// ---------------- About ----------------

private const val GITHUB_USER = "aaronateataco"

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val s by AppGraph.store.settings.collectAsState()
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
    }
    var taps by remember { mutableIntStateOf(0) }

    SettingsScaffold("About", onBack) {
        item {
            Column(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                MorphingAvatar("https://github.com/$GITHUB_USER.png?size=460", 200.dp)
                Text("MaterialTube", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 20.dp))
                Text(
                    "Made by @$GITHUB_USER",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { openExternal(context, "https://github.com/$GITHUB_USER") }
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
        }
        item {
            ListItem(
                headlineContent = { Text("Version") },
                supportingContent = { Text(version) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable {
                    if (s.developerMode) {
                        toast(context, "Developer settings are already on")
                        return@clickable
                    }
                    taps++
                    when {
                        taps >= 7 -> {
                            AppGraph.store.updateSettings { it.copy(developerMode = true) }
                            toast(context, "Developer settings unlocked. Find them in Settings.")
                        }
                        taps >= 3 -> toast(context, "${7 - taps} more taps to unlock developer settings")
                    }
                },
            )
        }
        item { Link("Source code", "github.com/$GITHUB_USER/tube") { openExternal(context, "https://github.com/$GITHUB_USER/tube") } }
        item { Section("Licence") }
        item {
            Text(
                "MaterialTube is under the Floodgate License: view-only and all rights reserved until the author opens the floodgate, after which it becomes GPL-3.0-or-later.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        item { Section("Built with") }
        listOf(
            "NewPipeExtractor" to "https://github.com/TeamNewPipe/NewPipeExtractor",
            "SponsorBlock" to "https://sponsor.ajay.app",
            "DeArrow" to "https://dearrow.ajay.app",
            "Return YouTube Dislike" to "https://returnyoutubedislike.com",
            "Jetpack Compose & Material 3" to "https://developer.android.com/compose",
            "Media3 ExoPlayer" to "https://developer.android.com/media/media3",
            "Coil" to "https://coil-kt.github.io/coil/",
        ).forEach { (name, url) ->
            item(key = name) { Link(name, null) { openExternal(context, url) } }
        }
        item {
            Text(
                "Not affiliated with YouTube or Google.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(24.dp),
            )
        }
    }
}

/** A picture that keeps morphing through Material 3 Expressive shapes while slowly turning. */
@Composable
fun MorphingAvatar(url: String, size: Dp) {
    val shapes = remember {
        listOf(
            MaterialShapes.Cookie12Sided, MaterialShapes.Clover8Leaf, MaterialShapes.Sunny, MaterialShapes.SoftBurst,
            MaterialShapes.Cookie9Sided, MaterialShapes.Clover4Leaf, MaterialShapes.Cookie6Sided, MaterialShapes.Circle,
        )
    }
    val morphs = remember { shapes.indices.map { i -> Morph(shapes[i], shapes[(i + 1) % shapes.size]) } }
    var index by remember { mutableIntStateOf(0) }
    val progress = remember { Animatable(0f) }
    val spin = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val spatial = MaterialTheme.motionScheme.slowSpatialSpec<Float>()

    LaunchedEffect(Unit) {
        while (true) {
            delay(1400)
            progress.animateTo(1f, spatial)
            index = (index + 1) % morphs.size
            progress.snapTo(0f)
        }
    }
    val t = rememberInfiniteTransition(label = "about")
    val turn by t.animateFloat(0f, 360f, infiniteRepeatable(tween(20_000, easing = LinearEasing)), label = "turn")
    val breath by t.animateFloat(0.96f, 1.04f, infiniteRepeatable(tween(2600), RepeatMode.Reverse), label = "breath")
    val haloTurn by t.animateFloat(360f, 0f, infiniteRepeatable(tween(32_000, easing = LinearEasing)), label = "halo")

    val shape = MorphShape(morphs[index], progress.value, turn + spin.value)
    val halo = remember(haloTurn) { PolygonShape(MaterialShapes.Cookie12Sided, haloTurn) }

    Box(Modifier.size(size * 1.18f), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(size * 1.16f)
                .graphicsLayer { scaleX = 2f - breath; scaleY = 2f - breath }
                .clip(halo)
                .background(MaterialTheme.colorScheme.primaryContainer),
        )
        Box(
            Modifier
                .size(size)
                .graphicsLayer { scaleX = breath; scaleY = breath }
                .clip(shape)
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .clickable { scope.launch { spin.animateTo(spin.value + 360f, spatial) } },
        ) {
            AsyncImage(model = url, contentDescription = "Profile picture", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

// ---------------- Shared pieces ----------------

@Composable
private fun SettingsScaffold(title: String, onBack: () -> Unit, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(top = pad.calculateTopPadding()),
            contentPadding = listPadding(LocalBottomPadding.current),
            content = content,
        )
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 4.dp),
    )
}

@Composable
private fun Toggle(title: String, body: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = body?.let { { Text(it) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable { onChange(!checked) },
    )
}

@Composable
private fun Link(title: String, body: String?, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = body?.let { { Text(it) } },
        trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun Row2(title: String, body: String, trailing: @Composable () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(body) },
        trailingContent = trailing,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun Segmented(title: String, labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 8.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            labels.forEachIndexed { i, label ->
                SegmentedButton(
                    selected = selected == i,
                    onClick = { onSelect(i) },
                    shape = SegmentedButtonDefaults.itemShape(i, labels.size),
                ) { Text(label) }
            }
        }
    }
}

@Composable
private fun ChoiceDialog(c: Choice, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(c.title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                c.options.forEach { (label, value) ->
                    ListItem(
                        headlineContent = { Text(label) },
                        leadingContent = { RadioButton(selected = c.selected == value, onClick = null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable {
                            c.onPick(value)
                            onDismiss()
                        },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
