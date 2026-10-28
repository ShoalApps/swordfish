package io.github.shoalapps.swordfish

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Live regression check for a public video known to have comments. */
@NetworkTest
@RunWith(AndroidJUnit4::class)
class CommentsExtractionTest {
    @Test fun publicVideoReturnsComments() = runBlocking {
        val service = org.schabi.newpipe.extractor.NewPipe.getService(0)
        val info = org.schabi.newpipe.extractor.comments.CommentsInfo.getInfo(service, "https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        assertTrue("No comments: disabled=${info.isCommentsDisabled}, extractor errors=${info.errors.size}", info.relatedItems.isNotEmpty())
    }
}
