package io.github.shoalapps.swordfish

import io.github.shoalapps.swordfish.player.SkipAware
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer

/** Reading an MP4 fragment index (sidx), which tells the loader which bytes hold which seconds. */
class SkipAwareTest {
    private fun sidx(timescale: Int, firstOffset: Int, refs: List<Pair<Int, Int>>): ByteArray {
        val b = ByteBuffer.allocate(32 + refs.size * 12)
        b.putInt(32 + refs.size * 12); b.put("sidx".toByteArray())
        b.put(0); b.put(byteArrayOf(0, 0, 0)) // version 0, flags
        b.putInt(1); b.putInt(timescale); b.putInt(0); b.putInt(firstOffset)
        b.putShort(0); b.putShort(refs.size.toShort())
        refs.forEach { (size, dur) -> b.putInt(size); b.putInt(dur); b.putInt(0) }
        return b.array()
    }

    @Test fun mapsTimesToBytes() {
        // Three 5-second fragments of 1000, 2000 and 3000 bytes, starting right after the index.
        val refs = SkipAware.parseSidx(sidx(1000, 0, listOf(1000 to 5000, 2000 to 5000, 3000 to 5000)), firstByteAfterIndex = 500)!!
        assertEquals(listOf(0L, 5000L, 10000L), refs.map { it.startMs })
        assertEquals(listOf(500L, 1500L, 3500L), refs.map { it.offset })
        assertEquals(3000L, refs[2].size)
    }

    @Test fun notAnIndex() {
        assertEquals(null, SkipAware.parseSidx(ByteArray(40), 0))
    }
}
