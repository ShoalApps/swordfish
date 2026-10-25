package io.github.swordfishapps.video.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptionsTest {
    @Test fun readsCuesAndTimes() {
        val vtt = """
            WEBVTT
            Kind: captions

            00:00:01.000 --> 00:00:03.500
            Hello &amp; welcome

            00:01:02.250 --> 00:01:04.000 align:start position:0%
            <c>Second</c> line
            and more
        """.trimIndent()
        val cues = Captions.parseVtt(vtt)
        assertEquals(2, cues.size)
        assertEquals(Cue(1000, 3500, "Hello & welcome"), cues[0])
        assertEquals(62_250, cues[1].startMs)
        assertEquals("Second line\nand more", cues[1].text)
    }

    @Test fun shortFormTimestampsWithoutHours() {
        val cues = Captions.parseVtt("WEBVTT\n\n01:02.000 --> 01:03.000\nHi\n")
        assertEquals(listOf(Cue(62_000, 63_000, "Hi")), cues)
    }

    @Test fun rollingAutoCaptionsDropTheRepeatedLine() {
        val vtt = """
            WEBVTT

            00:00:00.000 --> 00:00:02.000
            the quick brown

            00:00:02.000 --> 00:00:04.000
            the quick brown
            fox jumps over
        """.trimIndent()
        val cues = Captions.parseVtt(vtt)
        assertEquals("fox jumps over", cues[1].text)
    }

    @Test fun tinyAndEmptyCuesAreSkipped() {
        val vtt = "WEBVTT\n\n00:00:01.000 --> 00:00:01.010\nblink\n\n00:00:02.000 --> 00:00:03.000\n\n"
        assertTrue(Captions.parseVtt(vtt).isEmpty())
    }

    @Test fun windowsLineEndings() {
        val cues = Captions.parseVtt("WEBVTT\r\n\r\n00:00:01.000 --> 00:00:02.000\r\nHi\r\n")
        assertEquals("Hi", cues.single().text)
    }
}
