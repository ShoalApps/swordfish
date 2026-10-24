package com.aaronateataco.materialtube.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeTest {
    private val now get() = System.currentTimeMillis() / 1000

    @Test fun relativeTimes() {
        assertEquals("just now", timeAgo(now - 5))
        assertEquals("just now", timeAgo(now + 500)) // clock skew never goes negative
        assertEquals("1 minute ago", timeAgo(now - 60))
        assertEquals("2 minutes ago", timeAgo(now - 150))
        assertEquals("1 hour ago", timeAgo(now - 3600))
        assertEquals("5 hours ago", timeAgo(now - 5 * 3600))
        assertEquals("1 day ago", timeAgo(now - 86_400))
        assertEquals("2 weeks ago", timeAgo(now - 15 * 86_400))
        assertEquals("3 months ago", timeAgo(now - 95 * 86_400))
        assertEquals("2 years ago", timeAgo(now - 800 * 86_400))
    }

    @Test fun isoDatesParse() {
        assertTrue(looksLikeIsoDate("2026-10-01T12:00:00Z"))
        assertFalse(looksLikeIsoDate("3 days ago"))
        assertEquals(1790812800L, parseIsoEpoch("2026-10-01T00:00:00Z"))
        assertEquals(1790812800L, parseIsoEpoch("2026-10-01T01:00:00+01:00"))
        assertNotNull(parseIsoEpoch("2026-10-01"))
        assertNull(parseIsoEpoch("yesterday"))
        assertNull(parseIsoEpoch(null))
    }

    @Test fun uploadedTextKeepsYouTubesOwnWording() {
        val v = VideoItem(url = "u", id = "i", title = "t", channelName = "c", uploaded = "Streamed 3 hours ago")
        assertEquals("Streamed 3 hours ago", v.uploadedText())
    }

    @Test fun uploadedTextTurnsIsoDatesIntoRelativeTime() {
        val iso = java.time.Instant.ofEpochSecond(now - 2 * 86_400).toString()
        val v = VideoItem(url = "u", id = "i", title = "t", channelName = "c", uploaded = iso)
        assertEquals("2 days ago", v.uploadedText())
    }

    @Test fun subtitleJoinsChannelViewsAndDate() {
        val v = VideoItem(url = "u", id = "i", title = "t", channelName = "Chan", views = 1_200_000, uploaded = "1 day ago")
        assertEquals("Chan · 1.2M views · 1 day ago", v.subtitle())
        assertEquals("Chan · Live", v.copy(views = -1, isLive = true, uploaded = null).subtitle())
        assertEquals("Chan · 3K watching", v.copy(viewsText = "3K watching", uploaded = null).subtitle())
    }
}
