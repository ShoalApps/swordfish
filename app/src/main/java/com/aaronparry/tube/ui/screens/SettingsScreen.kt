@file:OptIn(ExperimentalMaterial3Api::class)

package com.aaronparry.tube.ui.screens

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.aaronparry.tube.AppGraph
import com.aaronparry.tube.data.SbCategories
import com.aaronparry.tube.ui.components.LocalBottomPadding
import com.aaronparry.tube.ui.components.listPadding

private val qualityOptions = listOf(2160, 1440, 1080, 720, 480, 360, 240, 144)

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val store = AppGraph.store
    val s by store.settings.collectAsState()
    var qualityDialog by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(top = pad.calculateTopPadding()),
            contentPadding = listPadding(LocalBottomPadding.current),
        ) {
            item { Section("Appearance") }
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("Theme", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 8.dp))
                    val labels = listOf("System", "Light", "Dark")
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        labels.forEachIndexed { i, label ->
                            SegmentedButton(
                                selected = s.theme == i,
                                onClick = { store.updateSettings { it.copy(theme = i) } },
                                shape = SegmentedButtonDefaults.itemShape(i, labels.size),
                            ) { Text(label) }
                        }
                    }
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                item {
                    Toggle("Dynamic colour", "Match colours to your wallpaper", s.dynamicColor) { v ->
                        store.updateSettings { it.copy(dynamicColor = v) }
                    }
                }
            }

            item { Section("Playback") }
            item {
                ListItem(
                    headlineContent = { Text("Preferred quality") },
                    supportingContent = { Text("${s.quality}p") },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { qualityDialog = true },
                )
            }
            item {
                Toggle("Background play", "Keep audio playing with the screen off or app closed", s.backgroundPlay) { v ->
                    store.updateSettings { it.copy(backgroundPlay = v) }
                }
            }
            item {
                Toggle("Picture-in-picture", "Shrink to a floating window when you leave the app", s.autoPip) { v ->
                    store.updateSettings { it.copy(autoPip = v) }
                }
            }

            item { Section("SponsorBlock") }
            item {
                Toggle("Skip segments", "Crowd-sourced skipping of sponsors and other filler", s.sponsorBlock) { v ->
                    store.updateSettings { it.copy(sponsorBlock = v) }
                }
            }
            if (s.sponsorBlock) {
                SbCategories.all.forEach { cat ->
                    item(key = cat.key) {
                        ListItem(
                            headlineContent = { Text(cat.label) },
                            leadingContent = {
                                Box(Modifier.size(14.dp).clip(CircleShape).background(cat.color))
                            },
                            trailingContent = {
                                Switch(
                                    checked = cat.key in s.sbSkip,
                                    onCheckedChange = { on ->
                                        store.updateSettings {
                                            it.copy(sbSkip = if (on) it.sbSkip + cat.key else it.sbSkip - cat.key)
                                        }
                                    },
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.padding(start = 16.dp),
                        )
                    }
                }
            }

            item { Section("Community") }
            item {
                Toggle("DeArrow", "Replace clickbait titles and thumbnails", s.deArrow) { v ->
                    store.updateSettings { it.copy(deArrow = v) }
                }
            }
            item {
                Toggle("Return YouTube Dislike", "Show dislike counts", s.ryd) { v ->
                    store.updateSettings { it.copy(ryd = v) }
                }
            }

            item { Section("About") }
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(
                        "Tube is built on NewPipeExtractor, SponsorBlock, DeArrow and Return YouTube Dislike. " +
                            "No Google account, no ads, no tracking.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }

    if (qualityDialog) {
        AlertDialog(
            onDismissRequest = { qualityDialog = false },
            title = { Text("Preferred quality") },
            text = {
                Column {
                    qualityOptions.forEach { q ->
                        ListItem(
                            headlineContent = { Text("${q}p") },
                            leadingContent = { RadioButton(selected = s.quality == q, onClick = null) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable {
                                store.updateSettings { it.copy(quality = q) }
                                qualityDialog = false
                            },
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { qualityDialog = false }) { Text("Close") } },
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
private fun Toggle(title: String, body: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(body) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable { onChange(!checked) },
    )
}
