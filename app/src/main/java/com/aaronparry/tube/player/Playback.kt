@file:OptIn(UnstableApi::class)

package com.aaronparry.tube.player

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.aaronparry.tube.AppGraph
import com.aaronparry.tube.MainActivity
import com.aaronparry.tube.data.NewPipeDownloader
import com.aaronparry.tube.data.bestUrl
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream

/** One ExoPlayer for the whole app, shared by the UI and the background service. */
object PlayerHolder {
    val player: ExoPlayer by lazy {
        ExoPlayer.Builder(AppGraph.app)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
    }

    val dataSourceFactory: DataSource.Factory by lazy { YtDataSource.Factory() }
}

/**
 * Wraps the normal HTTP source with the tricks YouTube's video servers expect:
 * byte ranges in the URL (avoids throttling) and the right request style per client.
 */
class YtDataSource(private val upstream: HttpDataSource) : DataSource by upstream {

    override fun open(dataSpec: DataSpec): Long {
        val uri = dataSpec.uri
        val isYtVideo = uri.host?.endsWith("googlevideo.com") == true &&
            uri.getQueryParameter("itag") != null
        if (!isYtVideo) return upstream.open(dataSpec)

        val client = uri.getQueryParameter("c")?.uppercase().orEmpty()
        val isAndroid = client.startsWith("ANDROID")
        val isIos = client.startsWith("IOS")

        val start = dataSpec.position
        val end = if (dataSpec.length != C.LENGTH_UNSET.toLong()) (start + dataSpec.length - 1).toString() else ""
        val newUri = uri.buildUpon()
            .appendQueryParameter("range", "$start-$end")
            .appendQueryParameter("rn", (requestNumber++).toString())
            .build()

        val headers = HashMap(dataSpec.httpRequestHeaders)
        when {
            isAndroid -> headers["User-Agent"] = ANDROID_UA
            isIos -> headers["User-Agent"] = IOS_UA
            else -> {
                headers["User-Agent"] = NewPipeDownloader.USER_AGENT
                headers["Origin"] = "https://www.youtube.com"
                headers["Referer"] = "https://www.youtube.com/"
            }
        }

        val builder = dataSpec.buildUpon()
            .setUri(newUri)
            .setPosition(0)
            .setLength(C.LENGTH_UNSET.toLong())
            .setHttpRequestHeaders(headers)
        if (isAndroid || isIos) {
            builder.setHttpMethod(DataSpec.HTTP_METHOD_POST).setHttpBody(byteArrayOf(0x78, 0x00))
        }
        return upstream.open(builder.build())
    }

    class Factory : DataSource.Factory {
        private val http = DefaultHttpDataSource.Factory()
            .setUserAgent(null)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)

        override fun createDataSource(): DataSource = YtDataSource(http.createDataSource())
    }

    companion object {
        private var requestNumber = 0
        private const val ANDROID_UA =
            "com.google.android.youtube/20.10.38 (Linux; U; Android 14) gzip"
        private const val IOS_UA =
            "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)"
    }
}

/** What we chose to play from a video's list of streams. */
data class Playable(
    val video: VideoStream?,
    val audio: AudioStream?,
    val hls: String?,
    val qualities: List<Int>,
)

object StreamPicker {
    fun pick(info: StreamInfo, maxHeight: Int): Playable {
        val isLive = info.streamType == StreamType.LIVE_STREAM || info.streamType == StreamType.AUDIO_LIVE_STREAM
        if (isLive && !info.hlsUrl.isNullOrBlank()) {
            return Playable(null, null, info.hlsUrl, emptyList())
        }

        val progressive = { d: DeliveryMethod -> d == DeliveryMethod.PROGRESSIVE_HTTP }

        var videos = info.videoOnlyStreams.filter { it.isUrl && progressive(it.deliveryMethod) }
        val muxedFallback = videos.isEmpty()
        if (muxedFallback) videos = info.videoStreams.filter { it.isUrl && progressive(it.deliveryMethod) }

        val qualities = videos.map { it.height }.filter { it > 0 }.distinct().sortedDescending()

        val video = videos.sortedWith(
            compareByDescending<VideoStream> { if (it.height <= maxHeight) it.height else -it.height }
                .thenByDescending { it.format == MediaFormat.MPEG_4 }
                .thenByDescending { it.fps },
        ).firstOrNull()

        val audio = if (muxedFallback) null else info.audioStreams
            .filter { it.isUrl && progressive(it.deliveryMethod) }
            .sortedWith(
                compareByDescending<AudioStream> {
                    it.audioTrackType == null || it.audioTrackType == AudioTrackType.ORIGINAL
                }
                    .thenByDescending { it.format == MediaFormat.M4A }
                    .thenByDescending { it.averageBitrate },
            ).firstOrNull()

        return Playable(video, audio, null, qualities)
    }

    fun buildSource(p: Playable, info: StreamInfo): MediaSource? {
        val factory = PlayerHolder.dataSourceFactory
        val metadata = MediaMetadata.Builder()
            .setTitle(info.name)
            .setArtist(info.uploaderName)
            .setArtworkUri(info.thumbnails.bestUrl()?.let(Uri::parse))
            .build()

        p.hls?.let { url ->
            val item = MediaItem.Builder()
                .setUri(url)
                .setMediaId(info.id)
                .setMimeType(MimeTypes.APPLICATION_M3U8)
                .setMediaMetadata(metadata)
                .build()
            return HlsMediaSource.Factory(factory).createMediaSource(item)
        }

        val video = p.video ?: return null
        val progressive = ProgressiveMediaSource.Factory(factory)
        val videoSource = progressive.createMediaSource(
            MediaItem.Builder().setUri(video.content).setMediaId(info.id).setMediaMetadata(metadata).build(),
        )
        val audio = p.audio ?: return videoSource
        val audioSource = progressive.createMediaSource(MediaItem.fromUri(audio.content))
        return MergingMediaSource(true, videoSource, audioSource)
    }
}

/** Keeps playback going with the screen off and shows the media notification. */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, PlayerHolder.player)
            .setSessionActivity(openApp)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        PlayerHolder.player.pause()
        stopSelf()
    }

    override fun onDestroy() {
        session?.release()
        session = null
        super.onDestroy()
    }
}
