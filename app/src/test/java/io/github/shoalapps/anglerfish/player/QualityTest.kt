package io.github.shoalapps.anglerfish.player

import org.junit.Assert.assertEquals
import org.junit.Test

class QualityTest {
    @Test fun verticalVideosAreNamedByTheirShortSide() {
        // YouTube's own label wins, frame rate or not.
        assertEquals(1080, StreamPicker.qualityOf("1080p60", 1080, 1920))
        assertEquals(720, StreamPicker.qualityOf("720p", 720, 1280))
        assertEquals(144, StreamPicker.qualityOf("144p", 144, 256))
        // Without a label, the short side, whichever way round the frame is.
        assertEquals(1080, StreamPicker.qualityOf(null, 1080, 1920))
        assertEquals(1080, StreamPicker.qualityOf("", 1920, 1080))
        // Only a height known.
        assertEquals(480, StreamPicker.qualityOf(null, -1, 480))
        assertEquals(0, StreamPicker.qualityOf(null, -1, -1))
    }
}
