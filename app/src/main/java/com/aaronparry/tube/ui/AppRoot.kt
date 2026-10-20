package com.aaronparry.tube.ui

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.outlined.VideoLibrary
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.aaronparry.tube.player.PlayerViewModel
import com.aaronparry.tube.ui.components.LocalBottomPadding
import com.aaronparry.tube.ui.player.PlayerSheet
import com.aaronparry.tube.ui.player.VideoSurface
import com.aaronparry.tube.ui.screens.ChannelScreen
import com.aaronparry.tube.ui.screens.HomeScreen
import com.aaronparry.tube.ui.screens.LibraryScreen
import com.aaronparry.tube.ui.screens.SearchScreen
import com.aaronparry.tube.ui.screens.SettingsScreen
import com.aaronparry.tube.ui.screens.SubscriptionsScreen

private data class Tab(val route: String, val label: String, val selected: ImageVector, val unselected: ImageVector)

private val tabs = listOf(
    Tab("home", "Home", Icons.Rounded.Home, Icons.Outlined.Home),
    Tab("subs", "Subscriptions", Icons.Rounded.Subscriptions, Icons.Outlined.Subscriptions),
    Tab("library", "You", Icons.Rounded.VideoLibrary, Icons.Outlined.VideoLibrary),
)

@Composable
fun AppRoot(vm: PlayerViewModel, inPip: Boolean) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showBar = route == null || tabs.any { it.route == route }

    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomExtra = (if (showBar) 0.dp else navInset) + (if (vm.sheetVisible) 76.dp else 0.dp)

    val openVideo: (com.aaronparry.tube.data.VideoItem) -> Unit = { vm.open(it) }
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
                    NavigationBar(
                        Modifier.graphicsLayer {
                            translationY = if (vm.sheetVisible) size.height * vm.expand.value else 0f
                        },
                    ) {
                        tabs.forEach { tab ->
                            val selected = route == tab.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    nav.navigate(tab.route) {
                                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(if (selected) tab.selected else tab.unselected, null) },
                                label = { Text(tab.label) },
                            )
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
                    enterTransition = { fadeIn(tween(250)) + scaleIn(tween(250), initialScale = 0.97f) },
                    exitTransition = { fadeOut(tween(150)) },
                    popEnterTransition = { fadeIn(tween(250)) + scaleIn(tween(250), initialScale = 1.03f) },
                    popExitTransition = { fadeOut(tween(150)) },
                ) {
                    composable("home") {
                        HomeScreen(onVideo = openVideo, onChannel = openChannel, onSearch = { nav.navigate("search") })
                    }
                    composable("subs") {
                        SubscriptionsScreen(onVideo = openVideo, onChannel = openChannel)
                    }
                    composable("library") {
                        LibraryScreen(onVideo = openVideo, onSettings = { nav.navigate("settings") })
                    }
                    composable("search") {
                        SearchScreen(onBack = { nav.popBackStack() }, onVideo = openVideo, onChannel = openChannel)
                    }
                    composable("channel/{url}") { e ->
                        val url = Uri.decode(e.arguments?.getString("url").orEmpty())
                        ChannelScreen(url = url, onBack = { nav.popBackStack() }, onVideo = openVideo)
                    }
                    composable("settings") {
                        SettingsScreen(onBack = { nav.popBackStack() })
                    }
                }
            }
        }

        if (vm.sheetVisible && !inPip) {
            PlayerSheet(vm, bottomBarVisible = showBar, onChannel = openChannel)
        }

        // Picture-in-picture: just the video, on black.
        if (inPip) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                VideoSurface(vm, Modifier.fillMaxSize())
            }
        }
    }
}
