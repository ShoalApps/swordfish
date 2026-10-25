package io.github.shoalapps.anglerfish.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException

class KidsClientTest {
    private fun json(s: String) = Json.parseToJsonElement(s) as JsonObject

    @Test fun aPlayableAnswerBecomesStreams() {
        val info = KidsClient.parse("wKlsc3Cgoj4", json("""
            {"playabilityStatus":{"status":"OK"},
             "videoDetails":{"title":"Who I Can Be","author":"Junior Eurovision Song Contest","channelId":"UC1","lengthSeconds":"181","viewCount":"166000","shortDescription":"Cyprus","thumbnail":{"thumbnails":[{"url":"https://i.ytimg.com/a.jpg","width":480,"height":360}]}},
             "streamingData":{"adaptiveFormats":[
               {"itag":137,"url":"https://v/137","mimeType":"video/mp4; codecs=\"avc1\"","qualityLabel":"1080p","width":1920,"height":1080},
               {"itag":401,"url":"https://v/401","mimeType":"video/mp4; codecs=\"av01.0.12M.08\"","qualityLabel":"2160p"},
               {"itag":140,"url":"https://a/140","mimeType":"audio/mp4; codecs=\"mp4a.40.2\"","averageBitrate":129000}]}}
        """))
        assertEquals("Who I Can Be", info.name)
        assertEquals("Junior Eurovision Song Contest", info.uploaderName)
        assertEquals("https://www.youtube.com/channel/UC1", info.uploaderUrl)
        assertEquals(181L, info.duration)
        // AV1 is left out: many phones can't decode it.
        assertEquals(listOf("1080p"), info.videoOnlyStreams.map { it.resolution })
        assertEquals(129, info.audioStreams.single().averageBitrate)
    }

    @Test fun withOnlyAMuxedStreamItIsUsed() {
        val info = KidsClient.parse("x", json("""
            {"playabilityStatus":{"status":"OK"},"videoDetails":{"title":"Live"},
             "streamingData":{"formats":[{"itag":18,"url":"https://v/18","mimeType":"video/mp4","qualityLabel":"360p"}],
               "adaptiveFormats":[{"itag":299,"signatureCipher":"s=..","mimeType":"video/mp4"}]}}
        """))
        assertEquals(listOf("360p"), info.videoStreams.map { it.resolution })
        assertTrue(info.videoOnlyStreams.isEmpty())
    }

    @Test fun aRefusalKeepsYouTubesReason() {
        try {
            KidsClient.parse("x", json("""{"playabilityStatus":{"status":"ERROR","reason":"This video is unavailable"}}"""))
            fail()
        } catch (e: IllegalStateException) {
            assertEquals("This video is unavailable", e.message)
        }
    }

    @Test fun onlyYouTubesUnplayableAnswerTriggersIt() {
        assertTrue(KidsClient.shouldTry(ContentNotAvailableException("Got error UNPLAYABLE: \"This video is not available\"")))
        assertTrue(!KidsClient.shouldTry(ContentNotAvailableException("This video is private")))
        assertTrue(!KidsClient.shouldTry(java.io.IOException("timeout")))
    }
}
