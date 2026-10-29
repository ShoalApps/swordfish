package io.github.shoalapps.swordfish.data

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
import org.schabi.newpipe.extractor.comments.CommentsInfo
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

data class Paged<T>(val items: List<T>, val next: Page?)

/** The extractor cannot distinguish disabled comments from Restricted Mode. */
class CommentsUnavailableException : Exception("Comments may be turned off or hidden by Restricted Mode.")

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

    /** [filter] is "", "videos" or "channels". */
    suspend fun search(query: String, page: Page? = null, filter: String = ""): Paged<Entry> = withContext(Dispatchers.IO) {
        val handler = yt.searchQHFactory.fromQuery(query, if (filter.isBlank()) emptyList() else listOf(filter), "")
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

    /** Recently fetched video details, so opening a preloaded video is instant. Stream links last a few hours. */
    private val streamCache = object : LinkedHashMap<String, Pair<Long, StreamInfo>>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, StreamInfo>>?) = size > 60
    }

    suspend fun stream(url: String): StreamInfo = withContext(Dispatchers.IO) {
        val id = videoIdOf(url)
        synchronized(streamCache) {
            streamCache[id]?.let { (at, info) -> if (System.currentTimeMillis() - at < 2 * 3600_000L) return@withContext info }
        }
        val info = try {
            StreamInfo.getInfo(yt, "https://www.youtube.com/watch?v=$id")
        } catch (e: Exception) {
            // Made-for-kids videos: YouTube still serves them to its Kids app.
            if (!KidsClient.shouldTry(e)) throw e
            runCatching { KidsClient.stream(io.github.shoalapps.swordfish.AppGraph.http, id) }.getOrElse { throw e }
        }
        synchronized(streamCache) { streamCache[id] = System.currentTimeMillis() to info }
        info
    }

    /** Drops cached details, e.g. after YouTube rejected one of its stream links. */
    fun forget(url: String) {
        synchronized(streamCache) { streamCache.remove(videoIdOf(url)) }
    }

    /** Fetch details in the background; errors are ignored. */
    suspend fun prefetch(ids: List<String>) = coroutineScope {
        if (!NetworkPolicy.backgroundAllowed()) return@coroutineScope
        ids.map { id -> async { runCatching { stream("https://www.youtube.com/watch?v=$id") } } }.awaitAll()
    }

    /** Channel pictures looked up for videos that arrived without one (chart and search results). */
    private val avatarCache = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val avatarGate = Semaphore(3)

    /** A channel's picture, fetched once per session; null if it can't be found. */
    suspend fun channelAvatar(channelUrl: String): String? {
        avatarCache[channelUrl]?.let { return it.ifEmpty { null } }
        if (NetworkPolicy.savingData()) return null
        return avatarGate.withPermit {
            avatarCache[channelUrl]?.let { return@withPermit it.ifEmpty { null } }
            val url = runCatching {
                withContext(Dispatchers.IO) { ChannelInfo.getInfo(yt, channelUrl).avatars.smallUrl() }
            }.getOrNull()
            avatarCache[channelUrl] = url.orEmpty()
            url
        }
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
                            // Channel RSS feeds link Shorts as /shorts/ID; nothing else marks them.
                            it.toVideo().let { v -> v.copy(channelUrl = sub.url, channelAvatar = sub.avatar, isShort = v.isShort || v.url.contains("/shorts/")) }
                        }
                    }.getOrDefault(emptyList())
                }
            }
        }.awaitAll()
            .flatten()
            .sortedByDescending { it.uploadedEpoch ?: 0L }
    }

    suspend fun comments(url: String, page: Page? = null): Paged<Comment> = withContext(Dispatchers.IO) {
        if (page == null) {
            val info = CommentsInfo.getInfo(yt, url)
            if (info.isCommentsDisabled) throw CommentsUnavailableException()
            Paged(info.relatedItems.filterIsInstance<CommentsInfoItem>().map { it.toComment() }, info.nextPage.orNull())
        } else {
            val more = CommentsInfo.getMoreItems(yt, url, page)
            Paged(more.items.filterIsInstance<CommentsInfoItem>().map { it.toComment() }, more.nextPage.orNull())
        }
    }

    suspend fun replies(url: String, page: Page): Paged<Comment> = withContext(Dispatchers.IO) {
        val more = CommentsInfo.getMoreItems(yt, url, page)
        Paged(more.items.filterIsInstance<CommentsInfoItem>().map { it.toComment() }, more.nextPage.orNull())
    }

    private fun CommentsInfoItem.toComment() = Comment(
        id = commentId ?: "",
        repliesPage = replies?.takeIf { Page.isValid(it) },
        author = uploaderName ?: "",
        avatar = uploaderAvatars.smallUrl(),
        html = commentText?.content ?: "",
        likes = likeCount,
        date = textualUploadDate,
        replies = replyCount,
        pinned = isPinned,
        hearted = isHeartedByUploader,
    )

    /** One of YouTube's remaining charts: trending_music, trending_gaming, trending_podcasts_episodes, trending_movies_and_shows, live. */
    suspend fun kiosk(id: String): List<VideoItem> = withContext(Dispatchers.IO) {
        val extractor = yt.kioskList.getExtractorById(id, null)
        extractor.fetchPage()
        extractor.initialPage.items.filterIsInstance<StreamInfoItem>().map { it.toVideo() }
    }

    /**
     * Signed-out Home. YouTube removed its Trending page, so this mixes the charts that
     * are left with your subscriptions, a few live streams at most.
     */
    suspend fun discoverFeed(subs: List<Sub>): List<VideoItem> = coroutineScope {
        val charts = listOf("trending_music", "trending_gaming", "trending_podcasts_episodes", "trending_movies_and_shows")
            .map { id -> async { runCatching { kiosk(id) }.getOrDefault(emptyList()) } }
        val live = async { runCatching { kiosk("live") }.getOrDefault(emptyList()).take(4) }
        val subFeed = async { if (subs.isEmpty()) emptyList() else runCatching { feed(subs).take(60) }.getOrDefault(emptyList()) }
        val lists = listOf(subFeed.await()) + charts.awaitAll().map { it.shuffled() } + listOf(live.await())
        val out = mutableListOf<VideoItem>()
        val max = lists.maxOfOrNull { it.size } ?: 0
        for (i in 0 until max) lists.forEach { l -> l.getOrNull(i)?.let(out::add) }
        out.distinctBy { it.id }
    }

    /** Shorts from a channel's Shorts tab. */
    suspend fun channelShorts(url: String): List<VideoItem> = withContext(Dispatchers.IO) {
        val info = ChannelInfo.getInfo(yt, url)
        val tab = info.tabs.firstOrNull { ChannelTabs.SHORTS in it.contentFilters } ?: return@withContext emptyList()
        ChannelTabInfo.getInfo(yt, tab).relatedItems.filterIsInstance<StreamInfoItem>().map {
            it.toVideo().copy(
                isShort = true,
                url = "https://www.youtube.com/shorts/${videoIdOf(it.url)}",
                channelName = it.uploaderName?.takeIf { n -> n.isNotBlank() } ?: info.name.orEmpty(),
                channelUrl = info.url,
                channelAvatar = info.avatars.smallUrl(),
            )
        }
    }

    /**
     * Shorts for people who aren't signed in: the Shorts tabs of channels you follow,
     * topped up with some popular channels.
     */
    suspend fun shorts(subs: List<Sub> = emptyList()): List<VideoItem> = coroutineScope {
        val popular = listOf(
            "UCX6OQ3DkcsbYNE6H8uQQuVA", "UCY1kMZp36IQSyNx_9h4mpCg", "UCHnyfMqiRRG1u-2MsSQLbXA",
            "UCsXVk37bltHxD1rDPwtNM8Q", "UCRijo3ddMTht_IHyNSNXpNQ", "UCBJycsmduvYEL83R_U4JriQ",
            "UCXuqSBlHAE6Xw-yeJA0Tunw", "UC6107grRI4m0o2-emgoDnAA", "UCwmZiChSryoWQCZMIQezgTg",
            "UCLA_DiR1FfKNvjuUpBHmylQ",
        ).map { channelUrlOf(it) }
        val urls = (subs.map { it.url }.shuffled().take(8) + popular.shuffled()).distinct().take(10)
        val gate = Semaphore(5)
        urls.map { u -> async { gate.withPermit { runCatching { channelShorts(u).take(8) }.getOrDefault(emptyList()) } } }
            .awaitAll()
            .let { lists ->
                val out = mutableListOf<VideoItem>()
                val max = lists.maxOfOrNull { it.size } ?: 0
                for (i in 0 until max) lists.shuffled().forEach { l -> l.getOrNull(i)?.let(out::add) }
                out.distinctBy { it.id }
            }
    }

    /** Educational feed: searches each chosen topic and interleaves the results. */
    suspend fun learningFeed(topicKeys: Set<String>, shuffleSeed: Int = 0): List<VideoItem> = coroutineScope {
        val topics = topicKeys.mapNotNull { LearningTopics.of(it) }.ifEmpty { LearningTopics.all.take(4) }
        val lists = topics.map { t ->
            async {
                val q = t.queries[(shuffleSeed + t.key.length) % t.queries.size]
                runCatching { search(q).items }.getOrDefault(emptyList())
                    .filterIsInstance<Entry.Video>()
                    .map { it.item }
                    .filter { !it.isShort && (it.durationSec < 0 || it.durationSec >= 180) }
            }
        }.awaitAll()
        val out = mutableListOf<VideoItem>()
        val max = lists.maxOfOrNull { it.size } ?: 0
        for (i in 0 until max) lists.forEach { l -> l.getOrNull(i)?.let(out::add) }
        out.distinctBy { it.id }
    }

    suspend fun topicFeed(key: String): List<VideoItem> {
        val t = LearningTopics.of(key) ?: return emptyList()
        return search(t.queries.first()).items.filterIsInstance<Entry.Video>().map { it.item }.filter { !it.isShort }
    }
}
