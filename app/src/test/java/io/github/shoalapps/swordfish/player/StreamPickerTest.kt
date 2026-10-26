package io.github.shoalapps.swordfish.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.services.youtube.ItagItem
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream

class StreamPickerTest {
    private fun video(itag: Int, videoOnly: Boolean = true): VideoStream {
        val item = ItagItem.getItag(itag)
        return VideoStream.Builder()
            .setId("$itag")
            .setContent("https://rr1.googlevideo.com/videoplayback?itag=$itag", true)
            .setMediaFormat(item.mediaFormat)
            .setDeliveryMethod(DeliveryMethod.PROGRESSIVE_HTTP)
            .setIsVideoOnly(videoOnly)
            .setResolution(item.resolutionString)
            .setItagItem(item)
            .build()
    }

    private fun audio(itag: Int, type: AudioTrackType? = null): AudioStream {
        val item = ItagItem.getItag(itag)
        return AudioStream.Builder()
            .setId("$itag-$type")
            .setContent("https://rr1.googlevideo.com/videoplayback?itag=$itag&t=$type", true)
            .setMediaFormat(item.mediaFormat)
            .setDeliveryMethod(DeliveryMethod.PROGRESSIVE_HTTP)
            .setAverageBitrate(item.averageBitrate)
            .setAudioTrackType(type)
            .setItagItem(item)
            .build()
    }

    private fun info(type: StreamType = StreamType.VIDEO_STREAM) =
        StreamInfo(0, "https://www.youtube.com/watch?v=dQw4w9WgXcQ", "https://www.youtube.com/watch?v=dQw4w9WgXcQ", type, "dQw4w9WgXcQ", "Title", 0)

    // itags: 137 = 1080p mp4, 248 = 1080p webm, 136 = 720p mp4, 135 = 480p mp4, 313 = 2160p webm,
    // 140 = m4a 128k, 251 = opus 160k, 18 = 360p muxed mp4
    private val full = info().apply {
        videoOnlyStreams = listOf(video(313), video(248), video(137), video(136), video(135))
        audioStreams = listOf(audio(251), audio(140))
    }

    @Test fun picksTheBestQualityAtOrBelowTheLimit() {
        assertEquals(1080, StreamPicker.heightOf(StreamPicker.pick(full, 1080).video!!))
        assertEquals(720, StreamPicker.heightOf(StreamPicker.pick(full, 720).video!!))
        assertEquals(480, StreamPicker.heightOf(StreamPicker.pick(full, 600).video!!))
    }

    @Test fun prefersMp4WhenTwoStreamsMatch() {
        assertEquals(MediaFormat.MPEG_4, StreamPicker.pick(full, 1080).video?.format)
    }

    @Test fun fallsBackToTheSmallestAboveTheLimitWhenNothingFits() {
        assertEquals(480, StreamPicker.heightOf(StreamPicker.pick(full, 144).video!!))
    }

    @Test fun listsQualitiesHighestFirst() {
        assertEquals(listOf(2160, 1080, 720, 480), StreamPicker.pick(full, 1080).qualities)
    }

    @Test fun prefersOriginalM4aAudio() {
        val withDub = info().apply {
            videoOnlyStreams = listOf(video(137))
            audioStreams = listOf(audio(140, AudioTrackType.DUBBED), audio(251, AudioTrackType.ORIGINAL), audio(140, AudioTrackType.ORIGINAL))
        }
        val a = StreamPicker.pick(withDub, 1080).audio!!
        assertEquals(AudioTrackType.ORIGINAL, a.audioTrackType)
        assertEquals(MediaFormat.M4A, a.format)
    }

    @Test fun usesMuxedStreamsWithoutSeparateAudioWhenThatsAllThereIs() {
        val old = info().apply { videoStreams = listOf(video(18, videoOnly = false)) }
        val p = StreamPicker.pick(old, 1080)
        assertEquals(360, StreamPicker.heightOf(p.video!!))
        assertNull(p.audio)
    }

    @Test fun liveStreamsUseHls() {
        val live = info(StreamType.LIVE_STREAM).apply { hlsUrl = "https://manifest.googlevideo.com/live.m3u8" }
        val p = StreamPicker.pick(live, 1080)
        assertEquals("https://manifest.googlevideo.com/live.m3u8", p.hls)
        assertNull(p.video)
    }

    @Test fun nothingPlayableGivesNoVideo() {
        assertNull(StreamPicker.pick(info(), 1080).video)
    }
}
