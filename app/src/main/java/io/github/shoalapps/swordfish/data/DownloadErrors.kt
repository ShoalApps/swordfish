package io.github.shoalapps.swordfish.data

/** Downloader diagnostics are for logs, never multi-page text in a download row. */
object DownloadErrors {
    fun rejectedByYouTube(message: String) = listOf("403", "Forbidden", "Requested format is not available").any { message.contains(it, true) }
    fun message(raw: String): String = when {
        raw.contains("DOWNLOADER_UPDATE_FAILED") -> "Couldn't update the downloader. Check your connection and try again."
        raw.contains("Couldn't update the downloader") -> raw
        rejectedByYouTube(raw) || raw.contains("YouTube refused") -> "YouTube refused this download. Try again later; the video can still be available to stream."
        raw.contains("free space", true) || raw.contains("ENOSPC", true) -> "Not enough free space to download this video."
        raw.contains("network settings", true) || raw.contains("paused by", true) -> "Downloads are paused by your network settings."
        raw.contains("unavailable", true) || raw.contains("private video", true) || raw.contains("sign in", true) -> "This video isn't available to download."
        else -> "Couldn't download this video. Try again."
    }
}
