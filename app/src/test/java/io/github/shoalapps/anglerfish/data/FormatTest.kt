package io.github.shoalapps.anglerfish.data

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {
    @Test fun countsUseYouTubeStyleSuffixes() {
        assertEquals("", formatCount(-1))
        assertEquals("0", formatCount(0))
        assertEquals("999", formatCount(999))
        assertEquals("1K", formatCount(1_000))
        assertEquals("1.5K", formatCount(1_500))
        assertEquals("15K", formatCount(15_400))
        assertEquals("999K", formatCount(999_999))
        assertEquals("1M", formatCount(1_000_000))
        assertEquals("2.3M", formatCount(2_345_678))
        assertEquals("2.3B", formatCount(2_345_678_901))
    }

    @Test fun durationsPadMinutesAndSeconds() {
        assertEquals("", formatDuration(-1))
        assertEquals("0:00", formatDuration(0))
        assertEquals("0:09", formatDuration(9))
        assertEquals("1:05", formatDuration(65))
        assertEquals("59:59", formatDuration(3599))
        assertEquals("1:00:00", formatDuration(3600))
        assertEquals("1:02:05", formatDuration(3725))
    }

    @Test fun durationsParseBackToSeconds() {
        assertEquals(-1, parseDuration(null))
        assertEquals(-1, parseDuration(""))
        assertEquals(-1, parseDuration("LIVE"))
        assertEquals(42, parseDuration("0:42"))
        assertEquals(754, parseDuration("12:34"))
        assertEquals(3725, parseDuration(" 1:02:05 "))
    }

    @Test fun durationRoundTrips() {
        for (s in listOf(0L, 1, 59, 60, 61, 599, 3599, 3600, 86_399)) {
            assertEquals(s, parseDuration(formatDuration(s)))
        }
    }
}
