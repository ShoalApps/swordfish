package com.aaronparry.tube.player

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
import com.aaronparry.tube.AppGraph
import com.aaronparry.tube.data.Comment
import com.aaronparry.tube.data.RydVotes
import com.aaronparry.tube.data.SbSegment
import com.aaronparry.tube.data.VideoItem
import com.aaronparry.tube.data.toVideo
import com.aaronparry.tube.data.videoIdOf
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
    var speed by mutableFloatStateOf(1f)
        private set

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
        }
        override fun onVideoSizeChanged(size: VideoSize) {
            if (size.width > 0 && size.height > 0) {
                aspect = size.width * size.pixelWidthHeightRatio / size.height
            }
        }
        override fun onRenderedFirstFrame() { firstFrame = true }
        override fun onPlayerError(e: PlaybackException) {
            error = "Playback error: ${e.errorCodeName}"
        }
    }

    init {
        player.addListener(listener)
        viewModelScope.launch {
            while (true) {
                tick()
                delay(200)
            }
        }
    }

    private fun tick() {
        positionMs = player.currentPosition
        bufferedMs = player.bufferedPosition
        if (player.duration > 0) durationMs = player.duration
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
        open(
            VideoItem(
                url = "https://www.youtube.com/watch?v=$id",
                id = id,
                title = "",
                channelName = "",
                thumbnail = "https://i.ytimg.com/vi/$id/hqdefault.jpg",
            ),
        )
        pendingStartMs = startSec * 1000
    }

    private var pendingStartMs = 0L

    fun retry() { current?.let { load(it.url) } }

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

    private fun load(url: String) {
        loadJob?.cancel()
        info = null; error = null; segments = emptyList(); votes = null; rating = Rating.NONE
        related = emptyList(); skipped = null; skippedIds.clear(); firstFrame = false
        comments = emptyList(); commentsNext = null
        quality = preferredQuality()
        loading = true
        player.stop()
        player.clearMediaItems()
        loadJob = viewModelScope.launch {
            try {
                val i = AppGraph.repo.stream(url)
                info = i
                val video = i.toVideo().let { v ->
                    // keep the avatar/thumbnail we already had if the page lacks them
                    val prev = current
                    v.copy(channelAvatar = v.channelAvatar ?: prev?.channelAvatar)
                }
                current = video
                related = i.relatedItems.filterIsInstance<StreamInfoItem>().map { it.toVideo() }
                store.addHistory(video)
                play(i, pendingStartMs)
                pendingStartMs = 0

                val s = store.settings.value
                if (s.sponsorBlock) launch { segments = AppGraph.sponsorBlock.segments(i.id, s.sbSkip) }
                if (s.ryd) launch { votes = AppGraph.ryd.votes(i.id) }
                launch { loadComments() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
            } finally {
                loading = false
            }
        }
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

    fun loadComments() {
        val url = info?.url ?: return
        if (commentsLoading) return
        if (comments.isNotEmpty() && commentsNext == null) return
        commentsLoading = true
        viewModelScope.launch {
            runCatching { AppGraph.repo.comments(url, commentsNext) }.onSuccess {
                comments = comments + it.items
                commentsNext = it.next
            }
            commentsLoading = false
        }
    }

    val hasMoreComments get() = commentsNext != null

    /** Changes quality for this video only; the defaults live in Settings. */
    fun changeQuality(q: Int) {
        quality = q
        info?.let { play(it, player.currentPosition) }
    }

    fun changeSpeed(s: Float) {
        speed = s
        player.setPlaybackSpeed(s)
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
