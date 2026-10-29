package io.github.shoalapps.swordfish.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import io.github.shoalapps.swordfish.LocalisedText as Text
import io.github.shoalapps.swordfish.LocalisedIcon as Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.shoalapps.swordfish.AppGraph
import io.github.shoalapps.swordfish.data.VideoItem

@Composable
fun DownloadButton(video: VideoItem) {
    val downloads by AppGraph.offline.videos.collectAsState()
    val progress by AppGraph.offline.progress.collectAsState()
    val entry = downloads.firstOrNull { it.video.id == video.id }
    if (!video.isLive) FilledTonalButton(onClick = { AppGraph.offline.keep(video) }) {
        Text(when {
            entry?.kept == true && entry.complete -> "Downloaded"
            entry?.error != null -> "Retry download"
            entry?.kept == true -> progress[video.id]?.let { "Downloading $it%" } ?: "Download queued"
            else -> "Download"
        })
    }
}

@Composable
fun Downloads(onVideo: (VideoItem) -> Unit) {
    val downloads by AppGraph.offline.videos.collectAsState()
    val progress by AppGraph.offline.progress.collectAsState()
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text("Downloads", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 12.dp))
        Text("Saved videos stay until you remove them. Automatic copies change with your video and recommendations.", style = MaterialTheme.typography.bodySmall)
        if (downloads.isEmpty()) Text("Tap Download on a video to keep it offline.", Modifier.padding(vertical = 12.dp))
        downloads.forEach { entry ->
            Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(entry.video.title.ifBlank { entry.video.id }, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                Text(when {
                    entry.error != null -> entry.error
                    entry.complete -> if (entry.kept) "Saved - ready offline" else "Automatic - ready offline"
                    progress.containsKey(entry.video.id) -> "Downloading ${progress[entry.video.id]}%"
                    else -> "Queued - waiting for an allowed connection"
                }, style = MaterialTheme.typography.bodySmall)
                progress[entry.video.id]?.let { percent -> LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth()) }
                Row {
                    if (entry.complete) TextButton(onClick = { onVideo(entry.video) }) { Text("Play") }
                    if (!entry.kept || entry.error != null) TextButton(onClick = { AppGraph.offline.keep(entry.video) }) { Text(if (entry.error != null) "Retry" else "Keep") }
                    TextButton(onClick = { AppGraph.offline.remove(entry.video.id) }) { Text(if (entry.complete) "Remove" else "Cancel") }
                }
            }
        }
    }
}
