package com.aaronparry.tube.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

data class Paged<T>(val items: List<T>, val next: Page?)

data class ChannelDetails(
    val url: String,
    val name: String,
    val avatar: String?,
    val banner: String?,
    val subscribers: Long,
    val description: String?,
    val videosTab: ListLinkHandler?,
)

class YouTubeRepo {
    private val yt = ServiceList.YouTube

    private fun Page?.orNull(): Page? = if (Page.isValid(this)) this else null

    suspend fun trending(): List<VideoItem> = withContext(Dispatchers.IO) {
        val extractor = yt.kioskList.defaultKioskExtractor
        extractor.fetchPage()
        extractor.initialPage.items.filterIsInstance<StreamInfoItem>().map { it.toVideo() }
    }

    suspend fun search(query: String, page: Page? = null): Paged<Entry> = withContext(Dispatchers.IO) {
        val handler = yt.searchQHFactory.fromQuery(query, emptyList(), "")
        if (page == null) {
            val info = SearchInfo.getInfo(yt, handler)
            Paged(info.relatedItems.mapNotNull { it.toEntry() }, info.nextPage.orNull())
        } else {
            val more = SearchInfo.getMoreItems(yt, handler, page)
            Paged(more.items.mapNotNull { it.toEntry() }, more.nextPage.orNull())
        }
    }

    suspend fun suggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        runCatching { yt.suggestionExtractor.suggestionList(query) }.getOrDefault(emptyList())
    }

    suspend fun stream(url: String): StreamInfo = withContext(Dispatchers.IO) {
        StreamInfo.getInfo(yt, url)
    }

    suspend fun channel(url: String): ChannelDetails = withContext(Dispatchers.IO) {
        val info = ChannelInfo.getInfo(yt, url)
        ChannelDetails(
            url = info.url,
            name = info.name ?: "",
            avatar = info.avatars.bestUrl(),
            banner = info.banners.bestUrl(),
            subscribers = info.subscriberCount,
            description = info.description,
            videosTab = info.tabs.firstOrNull { ChannelTabs.VIDEOS in it.contentFilters },
        )
    }

    suspend fun channelVideos(tab: ListLinkHandler, page: Page? = null): Paged<VideoItem> =
        withContext(Dispatchers.IO) {
            if (page == null) {
                val info = ChannelTabInfo.getInfo(yt, tab)
                Paged(
                    info.relatedItems.filterIsInstance<StreamInfoItem>().map { it.toVideo() },
                    info.nextPage.orNull(),
                )
            } else {
                val more = ChannelTabInfo.getMoreItems(yt, tab, page)
                Paged(
                    more.items.filterIsInstance<StreamInfoItem>().map { it.toVideo() },
                    more.nextPage.orNull(),
                )
            }
        }

    /** Latest uploads from every subscription, using YouTube's lightweight RSS feeds. */
    suspend fun feed(subs: List<Sub>): List<VideoItem> = coroutineScope {
        val gate = Semaphore(6)
        subs.map { sub ->
            async(Dispatchers.IO) {
                gate.withPermit {
                    runCatching {
                        val extractor = yt.getFeedExtractor(sub.url) ?: return@runCatching emptyList<VideoItem>()
                        extractor.fetchPage()
                        extractor.initialPage.items.filterIsInstance<StreamInfoItem>().map {
                            it.toVideo().copy(channelAvatar = sub.avatar)
                        }
                    }.getOrDefault(emptyList())
                }
            }
        }.awaitAll()
            .flatten()
            .sortedByDescending { it.uploadedEpoch ?: 0L }
    }
}
