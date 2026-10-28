package io.github.shoalapps.swordfish.ui

import android.content.Context
import java.net.URI
import io.github.shoalapps.swordfish.ui.components.openExternal

/** Keeps YouTube links inside Tube; everything else goes to the browser. */
object Links {
    private val videoId = Regex("""[A-Za-z0-9_-]{11}""")

    private fun youtubeUri(url: String): URI? = runCatching { URI(url) }.getOrNull()?.takeIf {
        (it.scheme.equals("https", true) || it.scheme.equals("http", true)) &&
            (it.host.equals("youtube.com", true) || it.host?.endsWith(".youtube.com", true) == true ||
                it.host.equals("youtu.be", true))
    }

    private fun videoId(url: String): String? {
        val uri = youtubeUri(url) ?: return null
        val parts = uri.path.orEmpty().trim('/').split('/')
        val id = when {
            uri.host.equals("youtu.be", true) -> parts.firstOrNull()
            parts.firstOrNull() == "watch" -> uri.rawQuery.orEmpty().split('&')
                .firstOrNull { it.startsWith("v=") }?.substringAfter('=')
            parts.firstOrNull() in listOf("shorts", "live", "embed") -> parts.getOrNull(1)
            else -> null
        }
        return id?.takeIf { videoId.matches(it) }
    }

    fun isVideo(url: String) = videoId(url) != null

    private val timeParam = Regex("""[?&#](?:t|start)=([0-9hms]+)""")

    /** Start time from t=90, t=90s, t=1h2m3s or start=90. */
    fun startSeconds(url: String): Long {
        val t = timeParam.find(url)?.groupValues?.get(1) ?: return 0
        val maxSeconds = Long.MAX_VALUE / 1000
        if (t.all(Char::isDigit)) return (t.toLongOrNull() ?: maxSeconds).coerceAtMost(maxSeconds)
        var total = 0L
        Regex("""(\d+)([hms])""").findAll(t).forEach { m ->
            val n = m.groupValues[1].toLongOrNull() ?: return maxSeconds
            val multiplier = when (m.groupValues[2]) {
                "h" -> 3600L
                "m" -> 60L
                else -> 1L
            }
            if (n > (maxSeconds - total) / multiplier) return maxSeconds
            total += n * multiplier
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
        val id = videoId(full)
        if (id != null) {
            val start = startSeconds(full)
            if (id == currentVideoId) seekToMs(start * 1000) else openVideo(full, start)
            return
        }
        val path = youtubeUri(full)?.path.orEmpty()
        if (path.startsWith("/@") || listOf("/channel/", "/c/", "/user/").any(path::startsWith)) {
            openChannel(full)
            return
        }
        openExternal(context, full)
    }
}
