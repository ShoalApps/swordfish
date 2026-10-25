package io.github.swordfishapps.video.data

import io.github.swordfishapps.video.AppGraph

/** Applies the user's feed filters (Shorts off, live, watched, keywords) to any list of videos. */
object FeedFilter {
    fun apply(list: List<VideoItem>): List<VideoItem> {
        val s = AppGraph.store.settings.value
        val keywords = s.keywordFilter.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        val watched = if (s.hideWatched) AppGraph.store.history.value.map { it.id }.toSet() else emptySet()
        return list.distinctBy { it.id }.filter { v ->
            !(s.shortsDisabled && (v.isShort || v.url.contains("/shorts/"))) &&
                !(s.hideLive && v.isLive) &&
                v.id !in watched &&
                keywords.none { k -> v.title.lowercase().contains(k) || v.channelName.lowercase().contains(k) }
        }
    }

    fun entries(list: List<Entry>): List<Entry> {
        val allowed = apply(list.filterIsInstance<Entry.Video>().map { it.item }).map { it.id }.toSet()
        return list.filter { it !is Entry.Video || it.item.id in allowed }.distinctBy { it.key() }
    }

    /** Unique key for lists; duplicates would crash a LazyColumn. */
    fun Entry.key() = when (this) {
        is Entry.Video -> "v" + item.id
        is Entry.Channel -> "c" + item.url
    }

    /** Clean share link: no tracking parameters, optional timestamp. */
    fun shareUrl(id: String, url: String, atSeconds: Long = 0): String {
        if (!AppGraph.store.settings.value.sanitizeShareLinks) return url
        return if (atSeconds > 0) "https://youtu.be/$id?t=$atSeconds" else "https://youtu.be/$id"
    }
}
