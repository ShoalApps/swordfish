package io.github.shoalapps.swordfish.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IdsTest {
    @Test fun videoIdFromEveryLinkShape() {
        val id = "dQw4w9WgXcQ"
        listOf(
            "https://www.youtube.com/watch?v=$id",
            "https://www.youtube.com/watch?feature=share&v=$id&t=10",
            "https://youtu.be/$id",
            "https://youtu.be/$id?si=abc",
            "https://www.youtube.com/shorts/$id",
            "https://www.youtube.com/live/$id",
            "https://www.youtube.com/embed/$id",
            "https://m.youtube.com/watch?v=$id",
        ).forEach { assertEquals(it, id, videoIdOf(it)) }
    }

    @Test fun unknownLinksAreReturnedUnchanged() {
        assertEquals("not a link", videoIdOf("not a link"))
    }

    @Test fun channelIds() {
        val id = "UCX6OQ3DkcsbYNE6H8uQQuVA"
        assertEquals(id, channelIdOf("https://www.youtube.com/channel/$id"))
        assertEquals(id, channelIdOf(channelUrlOf(id)))
        assertNull(channelIdOf("https://www.youtube.com/@MrBeast"))
        assertNull(channelIdOf(null))
    }
}
