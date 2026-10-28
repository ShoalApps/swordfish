package io.github.shoalapps.swordfish.player

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
import io.github.shoalapps.swordfish.AppGraph
import io.github.shoalapps.swordfish.data.Comment
import io.github.shoalapps.swordfish.data.RydVotes
import io.github.shoalapps.swordfish.data.SbSegment
import io.github.shoalapps.swordfish.data.VideoItem
import io.github.shoalapps.swordfish.data.toVideo
import io.github.shoalapps.swordfish.data.videoIdOf
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

    /**
     * YouTube refused the video to every client the app can use, even the Kids one (some
     * made-for-kids videos), so the error offers to open it in YouTube's own app.
     */
    var onlyYouTubeCanPlay by mutableStateOf(false)
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
        override fun onPositionDiscontinuity(old: Player.PositionInfo, new: Player.PositionInfo, reason: Int) {
            if (reason != Player.DISCONTINUITY_REASON_SEEK) return
            // Going back into a segment means you want to watch it: don't skip it, and load it.
            val pos = new.positionMs / 1000.0
            segments.firstOrNull { pos >= it.startSec && pos < it.endSec - 0.4 }?.let { skippedIds += it.uuid }
            planSkips()
        }
        override fun onPlayerError(e: PlaybackException) = onPlaybackFailed(e)
    }

    init {
        player.addListener(listener)
        viewModelScope.launch {
            while (true) {
                // Nothing to track while no video is open (but keep skipping while it plays in the background).
                if (sheetVisible || player.playWhenReady) tick()
                delay(200)
            }
        }
    }

    private fun tick() {
        positionMs = player.currentPosition
        bufferedMs = player.bufferedPosition
        if (io.github.shoalapps.swordfish.BuildConfig.DEBUG && positionMs / 1000 != lastLoggedSec) {
            lastLoggedSec = positionMs / 1000
            android.util.Log.d("SkipAware", "at ${positionMs / 1000.0}s buffered to ${bufferedMs / 1000.0}s, gate ${SkipAware.gateUs.let { if (it == Long.MAX_VALUE) "none" else (it / 1_000_000.0).toString() }}s, state ${player.playbackState}")
        }
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
        // While wanting to play, not just playing: loading stops at a segment, so the player may be
        // waiting there for the skip.
        if (!player.playWhenReady) return
        val pos = positionMs / 1000.0
        val seg = segments.firstOrNull {
            it.uuid !in skippedIds && pos >= it.startSec && pos < it.endSec - 0.4
        } ?: return
        skippedIds += seg.uuid
        val dur = player.duration
        if (dur > 0 && seg.endSec * 1000 >= dur - 1500) {
            // It runs to the end (an outro): that's the end of the video, so finish as if it ended,
            // rather than jumping past the last frame (which YouTube has nothing to send for).
            player.pause()
            endedAtOutro = true
            onEnded()
        } else player.seekTo((seg.endSec * 1000).toLong())
        planSkips()
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
            s.autoplayNext && !fullscreen && sheetVisible && current != null -> autoplayNext()
        }
    }

    /** A stream fetched while choosing what to autoplay, so opening it doesn't fetch it again. */
    private var prefetched: Pair<String, StreamInfo>? = null

    /**
     * Plays the first suggestion that will actually play. Some (often kids videos) only play in
     * YouTube's own apps; those are skipped quietly rather than ending the video on an error.
     * If none of the first few play, it stays on the finished video.
     */
    private fun autoplayNext() {
        val candidates = related.take(5)
        viewModelScope.launch {
            for (c in candidates) {
                val i = runCatching { streamWithRetry(c.url) }.getOrNull() ?: continue
                // You picked something yourself (or pressed play again) while this was being chosen.
                if (player.playbackState != Player.STATE_ENDED && !(endedAtOutro && !player.playWhenReady)) return@launch
                prefetched = c.url to i
                open(c)
                return@launch
            }
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
        io.github.shoalapps.swordfish.Telemetry.report(e)
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
        // Android tracks metering through VPNs and user-marked metered Wi-Fi.
        // When the connection is unknown, use the conservative setting.
        val unmetered = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == true
        return if (unmetered) s.qualityWifi else s.qualityMobile
    }

    private fun load(url: String, keepRecovered: Boolean = false) {
        loadJob?.cancel()
        if (!keepRecovered) recovered = false
        info = null; error = null; onlyYouTubeCanPlay = false; segments = emptyList(); votes = null; rating = Rating.NONE
        related = emptyList(); skipped = null; skippedIds.clear(); firstFrame = false
        comments = emptyList(); commentsNext = null; commentsError = null; replies.clear()
        captionTracks = emptyList(); captionTrack = null; cues = emptyList(); captionText = null
        chapters = emptyList(); chapterIndex = -1
        quality = preferredQuality()
        speed = store.settings.value.defaultSpeed
        loading = true
        player.stop()
        player.clearMediaItems()
        SkipAware.clear(); playing = null; endedAtOutro = false
        val videoId = videoIdOf(url)
        loadJob = viewModelScope.launch {
            // These don't depend on the stream, so start them straight away.
            val s = store.settings.value
            if (s.sponsorBlock) launch { segments = AppGraph.sponsorBlock.segments(videoId, s.sbSkip); planSkips() }
            if (s.ryd) launch { votes = AppGraph.ryd.votes(videoId) }
            launch { loadComments(url) }
            try {
                val i = prefetched?.takeIf { it.first == url }?.second ?: streamWithRetry(url)
                prefetched = null
                info = i
                val video = i.toVideo().let { v ->
                    // keep the avatar/thumbnail we already had if the page lacks them
                    val prev = current
                    v.copy(channelAvatar = v.channelAvatar ?: prev?.channelAvatar)
                }
                current = video
                related = io.github.shoalapps.swordfish.data.FeedFilter.apply(
                    i.relatedItems.filterIsInstance<StreamInfoItem>().map { it.toVideo() }
                        .ifEmpty { io.github.shoalapps.swordfish.data.KidsClient.related[i.id].orEmpty() },
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
                io.github.shoalapps.swordfish.Telemetry.report(e)
                error = NetworkInfo.friendlyError(e)
                onlyYouTubeCanPlay = io.github.shoalapps.swordfish.data.KidsClient.shouldTry(e)
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
        playing = choice
        player.setMediaSource(source, startMs)
        player.setPlaybackSpeed(speed)
        player.prepare()
        player.play()
        planSkips()
    }

    private var lastLoggedSec = -1L

    /** The video "ended" by skipping an outro that runs to the end (it's paused there, not ended). */
    private var endedAtOutro = false

    /** The streams playing now, for fetching ahead past segments. */
    private var playing: Playable? = null

    /**
     * Tells the loader about the next segment SponsorBlock will skip: stop at its start, fetch what
     * plays after it. Segments you've gone back into (or already skipped) are loaded normally.
     */
    private fun planSkips() {
        val choice = playing
        val pos = player.currentPosition
        val next = segments.filter { it.uuid !in skippedIds && it.endSec * 1000 > pos + 500 }.minByOrNull { it.startSec }
        if (choice == null || choice.hls != null || next == null) { SkipAware.clear(); return }
        val streams = listOfNotNull<org.schabi.newpipe.extractor.stream.Stream>(choice.video, choice.audio)
        val total = info?.duration?.times(1000) ?: durationMs
        // A segment running to the end has nothing after it to fetch; just don't load it.
        val toEnd = total > 0 && next.endSec * 1000 >= total - 1500
        SkipAware.plan((next.startSec * 1000).toLong(), if (toEnd) -1 else (next.endSec * 1000).toLong(), total, if (toEnd) emptyList() else streams, next.uuid + "@" + (choice.video?.content?.hashCode() ?: 0))
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

    /** Likes or dislikes a comment (tapping the same again clears it). Returns a message to show, or null. */

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
