package com.aaronateataco.materialtube.player

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import com.aaronateataco.materialtube.AppGraph
import com.aaronateataco.materialtube.data.Comment
import com.aaronateataco.materialtube.data.RydVotes
import com.aaronateataco.materialtube.data.SbSegment
import com.aaronateataco.materialtube.data.VideoItem
import com.aaronateataco.materialtube.data.toVideo
import com.aaronateataco.materialtube.data.videoIdOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/** Your rating of the current video. */
enum class Rating { NONE, LIKE, DISLIKE }

class PlayerViewModel : ViewModel() {
    val player = PlayerHolder.player
    private val store = AppGraph.store

    // What's loaded
    var current by mutableStateOf<VideoItem?>(null)
        private set
    var info by mutableStateOf<StreamInfo?>(null)
        private set
    var related by mutableStateOf<List<VideoItem>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var segments by mutableStateOf<List<SbSegment>>(emptyList())
        private set
    var votes by mutableStateOf<RydVotes?>(null)
        private set
    var rating by mutableStateOf(Rating.NONE)
        private set
    var qualities by mutableStateOf<List<Int>>(emptyList())
        private set
    var quality by mutableIntStateOf(1080)
        private set
    var speed by mutableFloatStateOf(AppGraph.store.settings.value.defaultSpeed)
        private set

    // Captions, chapters, sleep timer
    var captionTracks by mutableStateOf<List<CaptionTrack>>(emptyList())
        private set
    var captionTrack by mutableStateOf<CaptionTrack?>(null)
        private set
    private var cues: List<Cue> = emptyList()
    var captionText by mutableStateOf<String?>(null)
        private set
    var chapters by mutableStateOf<List<Chapter>>(emptyList())
        private set
    var chapterIndex by mutableIntStateOf(-1)
        private set
    /** Minutes chosen for the sleep timer: 0 off, -1 end of video. */
    var sleepMinutes by mutableIntStateOf(0)
        private set
    private var sleepAtMs = 0L

    // Comments
    var comments by mutableStateOf<List<Comment>>(emptyList())
        private set
    var commentsLoading by mutableStateOf(false)
        private set
    private var commentsNext: Page? = null

    // Live player state
    var isPlaying by mutableStateOf(false)
        private set
    var buffering by mutableStateOf(false)
        private set
    var firstFrame by mutableStateOf(false)
        private set
    var positionMs by mutableLongStateOf(0L)
        private set
    var durationMs by mutableLongStateOf(0L)
        private set
    var bufferedMs by mutableLongStateOf(0L)
        private set
    var aspect by mutableFloatStateOf(16f / 9f)
        private set

    // SponsorBlock "Skipped" pill
    var skipped by mutableStateOf<SbSegment?>(null)
        private set
    private val skippedIds = mutableSetOf<String>()

    // Sheet: 0 = mini player, 1 = full screen watch page
    val expand = Animatable(0f)
    var sheetVisible by mutableStateOf(false)
        private set
    var sheetTarget by mutableFloatStateOf(1f)
        private set
    var sheetNonce by mutableIntStateOf(0)
        private set
    var fullscreen by mutableStateOf(false)

    private var loadJob: Job? = null

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
        override fun onPlaybackStateChanged(state: Int) {
            buffering = state == Player.STATE_BUFFERING
            if (state == Player.STATE_READY) durationMs = player.duration.coerceAtLeast(0)
            if (state == Player.STATE_ENDED) onEnded()
        }
        override fun onVideoSizeChanged(size: VideoSize) {
            if (size.width > 0 && size.height > 0) {
                aspect = size.width * size.pixelWidthHeightRatio / size.height
            }
        }
        override fun onRenderedFirstFrame() { firstFrame = true }
        override fun onPlayerError(e: PlaybackException) = onPlaybackFailed(e)
    }

    init {
        player.addListener(listener)
        viewModelScope.launch {
            while (true) {
                // Nothing to track while no video is open.
                if (sheetVisible) tick()
                delay(200)
            }
        }
    }

    private fun tick() {
        positionMs = player.currentPosition
        bufferedMs = player.bufferedPosition
        if (player.duration > 0) durationMs = player.duration
        if (cues.isNotEmpty()) {
            val text = cues.lastOrNull { positionMs in it.startMs until it.endMs }?.text
            if (text != captionText) captionText = text
        }
        if (chapters.isNotEmpty()) {
            val idx = chapters.indexOfLast { it.startMs <= positionMs }
            if (idx != chapterIndex) chapterIndex = idx
        }
        if (sleepMinutes > 0 && System.currentTimeMillis() >= sleepAtMs) {
            player.pause()
            sleepMinutes = 0
        }
        if (!player.isPlaying) return
        val pos = positionMs / 1000.0
        val seg = segments.firstOrNull {
            it.uuid !in skippedIds && pos >= it.startSec && pos < it.endSec - 0.4
        } ?: return
        skippedIds += seg.uuid
        player.seekTo((seg.endSec * 1000).toLong())
        skipped = seg
        viewModelScope.launch {
            delay(4000)
            if (skipped == seg) skipped = null
        }
    }

    private fun onEnded() {
        val s = store.settings.value
        if (sleepMinutes == -1) {
            sleepMinutes = 0
            return
        }
        when {
            s.loopVideo -> { player.seekTo(0); player.play() }
            s.autoplayNext && !fullscreen && sheetVisible && current != null -> related.firstOrNull()?.let(::open)
        }
    }

    fun undoSkip() {
        val seg = skipped ?: return
        player.seekTo((seg.startSec * 1000).toLong())
        skipped = null
    }

    fun open(item: VideoItem) {
        current = item
        showSheet(1f)
        if (info?.url == item.url && error == null) return
        load(item.url)
    }

    fun openUrl(url: String, startSec: Long = 0) {
        val id = videoIdOf(url)
        if (id == info?.id) {
            showSheet(1f)
            if (startSec > 0) player.seekTo(startSec * 1000)
            return
        }
        // Set before loading starts, so a cached video can't begin playing from 0.
        pendingStartMs = startSec * 1000
        open(
            VideoItem(
                url = "https://www.youtube.com/watch?v=$id",
                id = id,
                title = "",
                channelName = "",
                thumbnail = "https://i.ytimg.com/vi/$id/hqdefault.jpg",
            ),
        )
    }

    private var pendingStartMs = 0L

    fun retry() { current?.let { load(it.url) } }

    /** Whether the current video has already had its one silent reload after a playback error. */
    private var recovered = false

    /**
     * Stream links expire after a few hours and YouTube sometimes rejects one mid-video.
     * The first time that happens, fetch fresh links and carry on from the same spot.
     */
    private fun onPlaybackFailed(e: PlaybackException) {
        val url = current?.url
        val http = e.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ||
            e.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
            e.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ||
            e.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW
        if (!recovered && http && url != null) {
            recovered = true
            pendingStartMs = player.currentPosition
            AppGraph.repo.forget(url)
            load(url, keepRecovered = true)
            return
        }
        error = when (e.errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                if (NetworkInfo.offline()) "You're offline. Check your connection and try again."
                else "The connection dropped while playing. Try again."
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "YouTube stopped sending this video. Try again."
            PlaybackException.ERROR_CODE_DECODING_FAILED,
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED ->
                "This phone can't play this video's format. Try a lower quality."
            else -> "Couldn't play this video (${e.errorCodeName}). Try again."
        }
    }

    private fun preferredQuality(): Int {
        val s = store.settings.value
        val cm = AppGraph.app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        val unmetered = caps != null && (
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            )
        return if (unmetered) s.qualityWifi else s.qualityMobile
    }

    private fun load(url: String, keepRecovered: Boolean = false) {
        loadJob?.cancel()
        if (!keepRecovered) recovered = false
        info = null; error = null; segments = emptyList(); votes = null; rating = Rating.NONE
        related = emptyList(); skipped = null; skippedIds.clear(); firstFrame = false
        comments = emptyList(); commentsNext = null; commentsError = null; replies.clear(); commentRatings.clear()
        captionTracks = emptyList(); captionTrack = null; cues = emptyList(); captionText = null
        chapters = emptyList(); chapterIndex = -1
        quality = preferredQuality()
        speed = store.settings.value.defaultSpeed
        loading = true
        player.stop()
        player.clearMediaItems()
        val videoId = videoIdOf(url)
        loadJob = viewModelScope.launch {
            // These don't depend on the stream, so start them straight away.
            val s = store.settings.value
            if (s.sponsorBlock) launch { segments = AppGraph.sponsorBlock.segments(videoId, s.sbSkip) }
            if (s.ryd) launch { votes = AppGraph.ryd.votes(videoId) }
            launch { loadComments(url) }
            try {
                val i = streamWithRetry(url)
                info = i
                val video = i.toVideo().let { v ->
                    // keep the avatar/thumbnail we already had if the page lacks them
                    val prev = current
                    v.copy(channelAvatar = v.channelAvatar ?: prev?.channelAvatar)
                }
                current = video
                related = com.aaronateataco.materialtube.data.FeedFilter.apply(
                    i.relatedItems.filterIsInstance<StreamInfoItem>().map { it.toVideo() },
                )
                store.addHistory(video)
                play(i, pendingStartMs)
                pendingStartMs = 0
                captionTracks = Captions.tracks(i)
                chapters = Chapters.of(i)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                pendingStartMs = 0
                error = NetworkInfo.friendlyError(e)
            } finally {
                loading = false
            }
        }
    }

    private fun isBotCheck(msg: String) = NetworkInfo.isBotCheck(msg)

    /** The bot check is sometimes a one-off, so give it one quiet second try. */
    private suspend fun streamWithRetry(url: String): StreamInfo =
        try {
            AppGraph.repo.stream(url)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!isBotCheck(e.message.orEmpty())) throw e
            delay(1500)
            AppGraph.repo.stream(url)
        }

    private fun play(i: StreamInfo, startMs: Long) {
        val choice = StreamPicker.pick(i, quality)
        qualities = choice.qualities
        val source = StreamPicker.buildSource(choice, i)
        if (source == null) {
            error = "No playable streams found for this video"
            return
        }
        player.setMediaSource(source, startMs)
        player.setPlaybackSpeed(speed)
        player.prepare()
        player.play()
    }

    var commentsError by mutableStateOf<String?>(null)
        private set
    /** Loaded replies per comment id. */
    val replies = androidx.compose.runtime.mutableStateMapOf<String, List<Comment>>()
    val repliesLoading = androidx.compose.runtime.mutableStateMapOf<String, Boolean>()

    fun loadComments(forUrl: String? = null) {
        val url = forUrl ?: info?.url ?: current?.url ?: return
        if (commentsLoading) return
        if (comments.isNotEmpty() && commentsNext == null) return
        commentsLoading = true
        commentsError = null
        viewModelScope.launch {
            runCatching { AppGraph.repo.comments(url, commentsNext) }
                .onSuccess {
                    comments = (comments + it.items).distinctBy { c -> c.id.ifEmpty { c.author + c.html } }
                    commentsNext = it.next
                }
                .onFailure { commentsError = NetworkInfo.friendlyError(it) }
            commentsLoading = false
        }
    }

    fun toggleReplies(c: Comment) {
        if (replies.containsKey(c.id)) { replies.remove(c.id); return }
        val page = c.repliesPage ?: return
        val url = info?.url ?: current?.url ?: return
        repliesLoading[c.id] = true
        viewModelScope.launch {
            runCatching { AppGraph.repo.replies(url, page) }.onSuccess { replies[c.id] = it.items }
            repliesLoading.remove(c.id)
        }
    }

    /** Posts a comment (or a reply when [to] is set). Returns an error message or null. */
    suspend fun post(text: String, to: Comment?): String? {
        val id = info?.id ?: current?.id ?: return "No video"
        if (!store.signedIn) return "Sign in to comment"
        return runCatching {
            if (to == null) AppGraph.account.postComment(id, text) else AppGraph.account.replyToComment(id, to.id, text)
        }.fold(
            onSuccess = {
                val mine = Comment(
                    id = "local-${System.currentTimeMillis()}",
                    author = store.account.value?.name ?: "You",
                    avatar = store.account.value?.avatar,
                    html = text.replace("<", "&lt;"),
                    likes = 0, date = "just now", replies = 0, pinned = false, hearted = false,
                )
                if (to == null) comments = listOf(mine) + comments
                else replies[to.id] = (replies[to.id] ?: emptyList()) + mine
                null
            },
            onFailure = { it.message ?: "Couldn't post" },
        )
    }

    val hasMoreComments get() = commentsNext != null

    /** Your like/dislike on each comment, shown straight away. */
    val commentRatings = androidx.compose.runtime.mutableStateMapOf<String, Rating>()

    /** Likes or dislikes a comment (tapping the same again clears it). Returns a message to show, or null. */
    suspend fun rateComment(c: Comment, target: Rating): String? {
        val from = commentRatings[c.id] ?: Rating.NONE
        val to = if (from == target) Rating.NONE else target
        commentRatings[c.id] = to
        if (!store.signedIn) return "Sign in to save comment likes to YouTube"
        val videoId = info?.id ?: current?.id ?: return null
        return runCatching { AppGraph.account.rateComment(videoId, c.id, from, to) }.exceptionOrNull()?.let { e ->
            commentRatings[c.id] = from
            e.message ?: "Couldn't rate that comment"
        }
    }

    /** Changes quality for this video only; the defaults live in Settings. */
    fun changeQuality(q: Int) {
        quality = q
        info?.let { play(it, player.currentPosition) }
    }

    fun selectCaptions(track: CaptionTrack?) {
        captionTrack = track
        cues = emptyList()
        captionText = null
        if (track == null) return
        viewModelScope.launch { cues = runCatching { Captions.load(track) }.getOrDefault(emptyList()) }
    }

    fun setSleepTimer(minutes: Int) {
        sleepMinutes = minutes
        if (minutes > 0) sleepAtMs = System.currentTimeMillis() + minutes * 60_000L
    }

    fun toggleLoop() = store.updateSettings { it.copy(loopVideo = !it.loopVideo) }
    fun toggleAutoplay() = store.updateSettings { it.copy(autoplayNext = !it.autoplayNext) }

    fun changeSpeed(s: Float) {
        speed = s
        player.setPlaybackSpeed(s)
        if (store.settings.value.rememberSpeed) store.updateSettings { it.copy(defaultSpeed = s) }
    }

    /** Returns false if the user needs to sign in first. */
    fun rate(target: Rating): Boolean {
        val id = info?.id ?: current?.id ?: return true
        val next = if (rating == target) Rating.NONE else target
        rating = next
        if (!store.signedIn) return false
        val action = when (next) {
            Rating.LIKE -> "like"
            Rating.DISLIKE -> "dislike"
            Rating.NONE -> "removelike"
        }
        AppGraph.scope.launch { runCatching { AppGraph.account.rate(id, action) } }
        return true
    }

    fun togglePlay() {
        if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
        if (player.isPlaying) player.pause() else player.play()
    }

    fun seekBy(ms: Long) = player.seekTo((player.currentPosition + ms).coerceAtLeast(0))
    fun seekTo(ms: Long) = player.seekTo(ms)

    fun showSheet(target: Float) {
        sheetVisible = true
        sheetTarget = target
        sheetNonce++
    }

    fun collapse() = showSheet(0f)

    fun close() {
        loadJob?.cancel()
        player.stop()
        player.clearMediaItems()
        sheetVisible = false
        fullscreen = false
        current = null
        info = null
        error = null
        viewModelScope.launch { expand.snapTo(0f) }
    }

    override fun onCleared() {
        player.removeListener(listener)
    }
}
