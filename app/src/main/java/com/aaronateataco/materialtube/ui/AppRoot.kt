package com.aaronateataco.materialtube.ui

import android.net.Uri
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.aaronateataco.materialtube.AppGraph
import com.aaronateataco.materialtube.data.VideoItem
import com.aaronateataco.materialtube.player.PlayerViewModel
import com.aaronateataco.materialtube.ui.components.Avatar
import com.aaronateataco.materialtube.ui.components.LocalBottomPadding
import com.aaronateataco.materialtube.ui.player.PlayerSheet
import com.aaronateataco.materialtube.ui.player.VideoSurface
import com.aaronateataco.materialtube.ui.screens.ChannelScreen
import com.aaronateataco.materialtube.ui.screens.AboutScreen
import com.aaronateataco.materialtube.ui.screens.DeveloperSettingsScreen
import com.aaronateataco.materialtube.ui.screens.ChannelPickerScreen
import com.aaronateataco.materialtube.ui.screens.HomeScreen
import com.aaronateataco.materialtube.ui.screens.LoginScreen
import com.aaronateataco.materialtube.ui.screens.PlaylistScreen
import com.aaronateataco.materialtube.ui.screens.SearchScreen
import com.aaronateataco.materialtube.ui.screens.SettingsScreen
import com.aaronateataco.materialtube.ui.screens.ShortsScreen
import com.aaronateataco.materialtube.ui.screens.SubscriptionsScreen
import com.aaronateataco.materialtube.ui.screens.YouScreen

private data class Tab(val route: String, val label: String, val selected: ImageVector, val unselected: ImageVector)

private val tabs = listOf(
    Tab("home", "Home", Icons.Rounded.Home, Icons.Outlined.Home),
    Tab("shorts", "Shorts", Icons.Rounded.VideoLibrary, Icons.Outlined.VideoLibrary),
    Tab("subs", "Subscriptions", Icons.Rounded.Subscriptions, Icons.Outlined.Subscriptions),
    Tab("you", "You", Icons.Rounded.AccountCircle, Icons.Outlined.AccountCircle),
)

private fun isTab(route: String?) = tabs.any { it.route == route }

/** Set once the first Home feed has loaded, so the intro can step aside. */
object StartupState {
    var homeReady by androidx.compose.runtime.mutableStateOf(false)
}

/** Lets the Shorts screen ask for the screen to stay on. */
object ScreenAwake {
    var shorts by androidx.compose.runtime.mutableStateOf(false)
}

// Material motion: fade-through between tabs, shared X-axis for going deeper.
private fun AnimatedContentTransitionScope<NavBackStackEntry>.betweenTabs() =
    isTab(initialState.destination.route) && isTab(targetState.destination.route)

private fun AnimatedContentTransitionScope<NavBackStackEntry>.enter(): EnterTransition =
    if (betweenTabs()) fadeIn(tween(210, delayMillis = 90)) + scaleIn(tween(210, delayMillis = 90), initialScale = 0.92f)
    else slideInHorizontally(tween(300)) { it / 6 } + fadeIn(tween(300))

private fun AnimatedContentTransitionScope<NavBackStackEntry>.exit(): ExitTransition =
    if (betweenTabs()) fadeOut(tween(90))
    else slideOutHorizontally(tween(300)) { -it / 6 } + fadeOut(tween(200))

private fun AnimatedContentTransitionScope<NavBackStackEntry>.popEnter(): EnterTransition =
    if (betweenTabs()) fadeIn(tween(210, delayMillis = 90)) + scaleIn(tween(210, delayMillis = 90), initialScale = 0.92f)
    else slideInHorizontally(tween(300)) { -it / 6 } + fadeIn(tween(300))

private fun AnimatedContentTransitionScope<NavBackStackEntry>.popExit(): ExitTransition =
    if (betweenTabs()) fadeOut(tween(90))
    else slideOutHorizontally(tween(300)) { it / 6 } + fadeOut(tween(200))

@Composable
fun AppRoot(vm: PlayerViewModel, inPip: Boolean, pendingRoute: String? = null, onRouteHandled: () -> Unit = {}) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showBar = route == null || isTab(route)
    val account by AppGraph.store.account.collectAsState()
    val settings by AppGraph.store.settings.collectAsState()
    val visibleTabs = if (settings.shortsDisabled) tabs.filter { it.route != "shorts" } else tabs
    val goTab: (String) -> Unit = { r ->
        nav.navigate(r) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // Launcher shortcuts (long-press the app icon).
    LaunchedEffect(pendingRoute) {
        when (pendingRoute) {
            "search" -> nav.navigate("search")
            "subs", "shorts", "you", "home" -> goTab(pendingRoute)
        }
        if (pendingRoute != null) onRouteHandled()
    }

    // Keep the screen on while a video or Short is playing, like the official app.
    val view = LocalView.current
    val awake = (vm.isPlaying && (vm.sheetVisible || inPip)) || ScreenAwake.shorts
    DisposableEffect(awake) {
        view.keepScreenOn = awake
        onDispose { view.keepScreenOn = false }
    }

    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // Like the official app, the mini player steps aside while you're in Shorts.
    val miniHidden = route == "shorts" || route == "login"
    val bottomExtra = (if (showBar) 0.dp else navInset) + (if (vm.sheetVisible && !miniHidden) 76.dp else 0.dp)

    val openVideo: (VideoItem) -> Unit = { vm.open(it) }
    val openChannel: (String) -> Unit = { url ->
        if (vm.sheetVisible) vm.collapse()
        nav.navigate("channel/${Uri.encode(url)}")
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Scaffold(
            contentWindowInsets = WindowInsets(0),
            bottomBar = {
                AnimatedVisibility(
                    visible = showBar,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut(),
                ) {
                    // On wide screens the tabs gather in the middle instead of spreading edge to edge.
                    Box(
                        Modifier
                            .graphicsLayer {
                                translationY = if (vm.sheetVisible && !miniHidden) size.height * vm.expand.value else 0f
                            }
                            .fillMaxWidth()
                            .background(androidx.compose.material3.NavigationBarDefaults.containerColor),
                        contentAlignment = androidx.compose.ui.Alignment.Center,
                    ) {
                    NavigationBar(
                        modifier = Modifier.widthIn(max = 640.dp),
                    ) {
                        visibleTabs.forEach { tab ->
                            val selected = route == tab.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { goTab(tab.route) },
                                alwaysShowLabel = settings.navLabels,
                                icon = {
                                    val avatar = account?.avatar
                                    if (tab.route == "you" && avatar != null) {
                                        Avatar(
                                            avatar,
                                            26.dp,
                                            if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier,
                                        )
                                    } else {
                                        Icon(if (selected) tab.selected else tab.unselected, tab.label)
                                    }
                                },
                                label = if (settings.navLabels) ({ Text(tab.label) }) else null,
                            )
                        }
                    }
                    }
                }
            },
        ) { pad ->
            CompositionLocalProvider(LocalBottomPadding provides bottomExtra) {
                NavHost(
                    navController = nav,
                    startDestination = "home",
                    modifier = Modifier.padding(bottom = pad.calculateBottomPadding()),
                    enterTransition = { enter() },
                    exitTransition = { exit() },
                    popEnterTransition = { popEnter() },
                    popExitTransition = { popExit() },
                ) {
                    composable("home") {
                        HomeScreen(
                            onVideo = openVideo,
                            onChannel = openChannel,
                            onSearch = { nav.navigate("search") },
                            onNotifications = { goTab("subs") },
                            onShorts = { short ->
                                com.aaronateataco.materialtube.ui.screens.ShortsLaunch.start = short
                                vm.collapse()
                                goTab("shorts")
                            },
                        )
                    }
                    composable("shorts") {
                        ShortsScreen(onChannel = openChannel, onExit = { goTab("home") })
                    }
                    composable("subs") {
                        SubscriptionsScreen(onVideo = openVideo, onChannel = openChannel)
                    }
                    composable("you") {
                        YouScreen(
                            onVideo = openVideo,
                            onSettings = { nav.navigate("settings") },
                            onSignIn = { nav.navigate("login") },
                            onPlaylist = { id -> nav.navigate("playlist/$id") },
                            onSwitchChannel = { nav.navigate("channels") },
                            onAbout = { nav.navigate("about") },
                        )
                    }
                    composable("search") {
                        SearchScreen(onBack = { nav.popBackStack() }, onVideo = openVideo, onChannel = openChannel)
                    }
                    composable("channel/{url}") { e ->
                        val url = Uri.decode(e.arguments?.getString("url").orEmpty())
                        ChannelScreen(url = url, onBack = { nav.popBackStack() }, onVideo = openVideo)
                    }
                    composable("playlist/{id}") { e ->
                        PlaylistScreen(
                            browseId = e.arguments?.getString("id").orEmpty(),
                            onBack = { nav.popBackStack() },
                            onVideo = openVideo,
                        )
                    }
                    composable("settings") {
                        SettingsScreen(
                            onBack = { nav.popBackStack() },
                            onSignIn = { nav.navigate("login") },
                            onSwitchChannel = { nav.navigate("channels") },
                            onDeveloper = { nav.navigate("developer") },
                            onAbout = { nav.navigate("about") },
                        )
                    }
                    composable("developer") { DeveloperSettingsScreen(onBack = { nav.popBackStack() }) }
                    composable("about") { AboutScreen(onBack = { nav.popBackStack() }) }
                    composable("login") {
                        LoginScreen(
                            onDone = { nav.navigate("channels") { popUpTo("login") { inclusive = true } } },
                            onBack = { nav.popBackStack() },
                        )
                    }
                    composable("channels") {
                        ChannelPickerScreen(onDone = { nav.popBackStack() }, onBack = { nav.popBackStack() })
                    }
                }
            }
        }

        if (vm.sheetVisible && !inPip && !miniHidden) {
            PlayerSheet(vm, bottomBarVisible = showBar, onChannel = openChannel)
        }

        IntroOverlay()

        // Picture-in-picture: just the video, on black.
        if (inPip) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                VideoSurface(vm, Modifier.fillMaxSize())
            }
        }
    }
}
