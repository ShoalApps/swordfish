package com.aaronateataco.materialtube.ui

import android.content.Context
import android.net.Uri
import com.aaronateataco.materialtube.ui.components.openExternal

/** Keeps YouTube links inside Tube; everything else goes to the browser. */
object Links {
    private val videoRegex = Regex("""(?:youtube\.com/(?:watch\?(?:.*&)?v=|shorts/|live/|embed/)|youtu\.be/)([\w-]{11})""")
    private val channelRegex = Regex("""youtube\.com/(?:@|channel/|c/|user/)""")

    fun isVideo(url: String) = videoRegex.containsMatchIn(url)

    /** Start time from t=90, t=90s or t=1h2m3s. */
    fun startSeconds(url: String): Long {
        val t = runCatching { Uri.parse(url).getQueryParameter("t") }.getOrNull() ?: return 0
        t.toLongOrNull()?.let { return it }
        var total = 0L
        Regex("""(\d+)([hms])""").findAll(t).forEach { m ->
            val n = m.groupValues[1].toLong()
            total += when (m.groupValues[2]) {
                "h" -> n * 3600
                "m" -> n * 60
                else -> n
            }
        }
        return total
    }

    fun route(
        context: Context,
        url: String,
        currentVideoId: String?,
        seekToMs: (Long) -> Unit,
        openVideo: (url: String, startSec: Long) -> Unit,
        openChannel: (String) -> Unit,
    ) {
        val full = if (url.startsWith("/")) "https://www.youtube.com$url" else url
        val match = videoRegex.find(full)
        if (match != null) {
            val id = match.groupValues[1]
            val start = startSeconds(full)
            if (id == currentVideoId) seekToMs(start * 1000) else openVideo(full, start)
            return
        }
        if (channelRegex.containsMatchIn(full)) {
            openChannel(full)
            return
        }
        openExternal(context, full)
    }
}
