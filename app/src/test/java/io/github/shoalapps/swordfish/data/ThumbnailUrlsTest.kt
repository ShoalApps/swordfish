package io.github.shoalapps.swordfish.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ThumbnailUrlsTest {
    @Test fun upgradesSmallYouTubeThumbnails() {
        assertEquals("https://i.ytimg.com/vi/dQw4w9WgXcQ/hq720.jpg", ThumbnailUrls.preferred("https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg"))
    }
    @Test fun leavesHighResolutionAndExternalImagesAlone() {
        val high = "https://i.ytimg.com/vi/dQw4w9WgXcQ/maxresdefault.jpg"
        assertEquals(high, ThumbnailUrls.preferred(high))
        val external = "https://example.org/vi/dQw4w9WgXcQ/hqdefault.jpg"
        assertEquals(external, ThumbnailUrls.preferred(external))
    }
}
