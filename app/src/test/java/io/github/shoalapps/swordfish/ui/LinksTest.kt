package io.github.shoalapps.swordfish.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LinksTest {
    @Test fun startTimes() {
        assertEquals(0, Links.startSeconds("https://youtu.be/dQw4w9WgXcQ"))
        assertEquals(90, Links.startSeconds("https://youtu.be/dQw4w9WgXcQ?t=90"))
        assertEquals(45, Links.startSeconds("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=45s"))
        assertEquals(3723, Links.startSeconds("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=1h2m3s"))
        assertEquals(125, Links.startSeconds("https://www.youtube.com/watch?v=dQw4w9WgXcQ#t=2m5s"))
        assertEquals(30, Links.startSeconds("https://www.youtube.com/embed/dQw4w9WgXcQ?start=30"))
    }

    @Test fun hugeTimesDoNotOverflow() {
        assertEquals(Long.MAX_VALUE / 1000, Links.startSeconds("https://youtu.be/dQw4w9WgXcQ?t=999999999999999999999h"))
        assertEquals(Long.MAX_VALUE / 1000, Links.startSeconds("https://youtu.be/dQw4w9WgXcQ?t=9223372036854775807"))
    }

    @Test fun otherParametersAreNotMistakenForTime() {
        assertEquals(0, Links.startSeconds("https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PLt=5"))
        assertEquals(0, Links.startSeconds("https://www.youtube.com/watch?v=dQw4w9WgXcQ&feature=share"))
    }

    @Test fun recognisesVideoLinks() {
        assertTrue(Links.isVideo("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertTrue(Links.isVideo("https://www.youtube.com/watch?app=desktop&v=dQw4w9WgXcQ"))
        assertTrue(Links.isVideo("https://youtube.com/shorts/dQw4w9WgXcQ"))
        assertFalse(Links.isVideo("https://www.youtube.com/@MrBeast"))
        assertFalse(Links.isVideo("https://example.com/watch?v=dQw4w9WgXcQ"))
        assertFalse(Links.isVideo("https://evil.example/youtube.com/watch?v=dQw4w9WgXcQ"))
        assertFalse(Links.isVideo("https://youtube.com.evil.example/watch?v=dQw4w9WgXcQ"))
        assertFalse(Links.isVideo("https://youtube.com/watch?v=dQw4w9WgXcQextra"))
        assertTrue(Links.isVideo("https://youtu.be/dQw4w9WgXcQ?t=30"))
    }
}
