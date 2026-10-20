package com.aaronateataco.materialtube.data

import kotlinx.serialization.Serializable
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import java.util.Locale

@Serializable
data class VideoItem(
    val url: String,
    val id: String,
    val title: String,
    val channelName: String,
    val channelUrl: String? = null,
    val channelAvatar: String? = null,
    val thumbnail: String? = null,
    val durationSec: Long = -1,
    val views: Long = -1,
    val uploaded: String? = null,
    val uploadedEpoch: Long? = null,
    val isLive: Boolean = false,
    val isShort: Boolean = false,
    /** Ready-made "1.2M views" text from YouTube's own API, when we have it. */
    val viewsText: String? = null,
)

@Serializable
data class ChannelItem(
    val url: String,
    val name: String,
    val avatar: String? = null,
    val subscribers: Long = -1,
)

sealed interface Entry {
    data class Video(val item: VideoItem) : Entry
    data class Channel(val item: ChannelItem) : Entry
}

private val idRegex = Regex("""(?:v=|youtu\.be/|shorts/|live/|embed/)([\w-]{11})""")

fun videoIdOf(url: String): String = idRegex.find(url)?.groupValues?.get(1) ?: url

fun List<Image>.bestUrl(): String? =
    maxByOrNull { if (it.height > 0) it.height else 0 }?.url ?: firstOrNull()?.url

fun List<Image>.smallUrl(): String? =
    filter { it.height in 1..240 }.maxByOrNull { it.height }?.url ?: bestUrl()

fun StreamInfoItem.toVideo() = VideoItem(
    url = url,
    id = videoIdOf(url),
    title = name ?: "",
    channelName = uploaderName ?: "",
    channelUrl = uploaderUrl,
    channelAvatar = uploaderAvatars.smallUrl(),
    thumbnail = thumbnails.bestUrl() ?: "https://i.ytimg.com/vi/${videoIdOf(url)}/hqdefault.jpg",
    durationSec = duration,
    views = viewCount,
    uploaded = textualUploadDate,
    uploadedEpoch = runCatching { uploadDate?.offsetDateTime()?.toEpochSecond() }.getOrNull()
        ?: parseIsoEpoch(textualUploadDate),
    isLive = streamType == StreamType.LIVE_STREAM,
    isShort = isShortFormContent,
)

fun ChannelInfoItem.toChannel() = ChannelItem(
    url = url,
    name = name ?: "",
    avatar = thumbnails.bestUrl(),
    subscribers = subscriberCount,
)

fun InfoItem.toEntry(): Entry? = when (this) {
    is StreamInfoItem -> Entry.Video(toVideo())
    is ChannelInfoItem -> Entry.Channel(toChannel())
    else -> null
}

fun StreamInfo.toVideo() = VideoItem(
    url = url,
    id = id,
    title = name ?: "",
    channelName = uploaderName ?: "",
    channelUrl = uploaderUrl,
    channelAvatar = uploaderAvatars.smallUrl(),
    thumbnail = thumbnails.bestUrl() ?: "https://i.ytimg.com/vi/$id/hqdefault.jpg",
    durationSec = duration,
    views = viewCount,
    uploaded = textualUploadDate,
    uploadedEpoch = runCatching { uploadDate?.offsetDateTime()?.toEpochSecond() }.getOrNull()
        ?: parseIsoEpoch(textualUploadDate),
    isLive = streamType == StreamType.LIVE_STREAM,
)

fun formatCount(n: Long): String = when {
    n < 0 -> ""
    n < 1_000 -> n.toString()
    n < 1_000_000 -> trim(n / 1_000.0) + "K"
    n < 1_000_000_000 -> trim(n / 1_000_000.0) + "M"
    else -> trim(n / 1_000_000_000.0) + "B"
}

private fun trim(v: Double): String =
    if (v >= 10) v.toLong().toString() else String.format(Locale.UK, "%.1f", v).removeSuffix(".0")

fun formatDuration(totalSec: Long): String {
    if (totalSec < 0) return ""
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) String.format(Locale.UK, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.UK, "%d:%02d", m, s)
}

fun VideoItem.subtitle(): String = listOfNotNull(
    channelName.takeIf { it.isNotBlank() },
    viewsText?.takeIf { it.isNotBlank() }
        ?: if (isLive) "Live" else views.takeIf { it >= 0 }?.let { "${formatCount(it)} views" },
    uploadedText(),
).joinToString(" · ")

/** "3 days ago", the way YouTube writes it, whatever format the source used. */
fun VideoItem.uploadedText(): String? {
    val raw = uploaded?.takeIf { it.isNotBlank() }
    // Text like "2 days ago" or "Streamed 3 hours ago" is already right.
    if (raw != null && !looksLikeIsoDate(raw)) return raw
    return uploadedEpoch?.let(::timeAgo) ?: raw?.let(::parseIsoEpoch)?.let(::timeAgo)
}

private val isoRegex = Regex("""^\d{4}-\d{2}-\d{2}""")

fun looksLikeIsoDate(text: String) = isoRegex.containsMatchIn(text.trim())

fun parseIsoEpoch(text: String?): Long? {
    if (text.isNullOrBlank() || !looksLikeIsoDate(text)) return null
    val t = text.trim()
    return runCatching { java.time.OffsetDateTime.parse(t).toEpochSecond() }.getOrNull()
        ?: runCatching { java.time.Instant.parse(t).epochSecond }.getOrNull()
        ?: runCatching { java.time.LocalDateTime.parse(t).atZone(java.time.ZoneId.systemDefault()).toEpochSecond() }.getOrNull()
        ?: runCatching { java.time.LocalDate.parse(t.take(10)).atStartOfDay(java.time.ZoneId.systemDefault()).toEpochSecond() }.getOrNull()
}

fun timeAgo(epochSec: Long): String {
    val diff = (System.currentTimeMillis() / 1000 - epochSec).coerceAtLeast(0)
    fun fmt(n: Long, unit: String) = "$n $unit${if (n == 1L) "" else "s"} ago"
    return when {
        diff < 60 -> "just now"
        diff < 3600 -> fmt(diff / 60, "minute")
        diff < 86_400 -> fmt(diff / 3600, "hour")
        diff < 7 * 86_400 -> fmt(diff / 86_400, "day")
        diff < 30 * 86_400 -> fmt(diff / (7 * 86_400), "week")
        diff < 365 * 86_400 -> fmt(diff / (30 * 86_400), "month")
        else -> fmt(diff / (365 * 86_400), "year")
    }
}

/** "19 Oct 2026" for the description panel. */
fun fullDate(epochSec: Long): String =
    java.time.Instant.ofEpochSecond(epochSec).atZone(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", Locale.UK))

private val channelIdRegex = Regex("""/channel/(UC[\w-]{22})""")

fun channelIdOf(url: String?): String? = url?.let { channelIdRegex.find(it)?.groupValues?.get(1) }

fun channelUrlOf(id: String) = "https://www.youtube.com/channel/$id"

/** "12:34" or "1:02:03" to seconds. */
fun parseDuration(text: String?): Long {
    if (text.isNullOrBlank()) return -1
    val parts = text.trim().split(":").mapNotNull { it.trim().toLongOrNull() }
    if (parts.isEmpty()) return -1
    return parts.fold(0L) { acc, n -> acc * 60 + n }
}

data class Comment(
    val id: String = "",
    val repliesPage: org.schabi.newpipe.extractor.Page? = null,
    val author: String,
    val avatar: String?,
    val html: String,
    val likes: Int,
    val date: String?,
    val replies: Int,
    val pinned: Boolean,
    val hearted: Boolean,
)
