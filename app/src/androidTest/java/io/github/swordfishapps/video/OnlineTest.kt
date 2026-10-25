package io.github.swordfishapps.video

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.swordfishapps.video.player.PlayerHolder
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** End-to-end checks against the real YouTube. */
@NetworkTest
@RunWith(AndroidJUnit4::class)
class OnlineTest : TubeTest() {
    private fun isPlaying() = compose.runOnUiThread { PlayerHolder.player.isPlaying }
    private fun position() = compose.runOnUiThread { PlayerHolder.player.currentPosition }

    @Test fun homeFeedLoadsAndAVideoPlays() {
        waitForDescription("More", 30_000)
        val cards = androidx.compose.ui.test.hasClickAction() and androidx.compose.ui.test.hasAnyDescendant(hasContentDescription("More"))
        // Home is whatever YouTube is showing today; a video can be age-gated or blocked in this region.
        // If the first one can't play, it must say why in words, and the next one must play.
        for (attempt in 0..1) {
            compose.onAllNodes(cards)[attempt].performClick()
            waitForText("Comments", 20_000)
            compose.waitUntil(45_000) { isPlaying() || !gone(hasText("Try again")) }
            if (isPlaying()) break
            assertTrue("no explanation shown", !gone(hasText("Try again")))
            assertTrue("gave up after two videos", attempt == 0)
            back()
            compose.onNodeWithContentDescription("Close").performClick()
            compose.waitForIdle()
        }
        val start = position()
        compose.waitUntil(20_000) { position() > start + 1_500 }
    }

    @Test fun linkWithTimestampStartsThere() {
        compose.activityRule.scenario.onActivity {
            it.startActivity(
                android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://youtu.be/dQw4w9WgXcQ?t=60"))
                    .setPackage(it.packageName),
            )
        }
        compose.waitUntil(45_000) { isPlaying() }
        assertTrue("started at ${position()}ms", position() >= 59_000)
    }

    @Test fun searchFindsVideosAndChannels() {
        compose.onNodeWithContentDescription("Search").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("kurzgesagt")
        compose.onNode(hasSetTextAction()).performImeAction()
        waitForDescription("More", 30_000)
        assertTrue(AppGraph.store.searches.value.contains("kurzgesagt"))

        compose.onNodeWithText("Channels").performClick()
        waitForText("subscribers", 30_000, substring = true)
        compose.onAllNodes(hasText("subscribers", substring = true)).onFirst().performClick()
        waitFor(hasText("Subscribe") or hasText("Subscribed"), 30_000)
    }

    @Test fun commentsOpenUnderTheVideoAndCanBeLiked() {
        compose.activityRule.scenario.onActivity {
            it.startActivity(
                android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
                    .setPackage(it.packageName),
            )
        }
        waitForText("Comments")
        compose.waitUntil(30_000) { gone(hasText("Loading comments…")) }
        compose.onAllNodes(hasText("Comments")).onFirst().performClick()
        // The panel opens under the video: the player's controls are still on screen.
        waitForDescription("Close comments", 10_000)
        val video = compose.onNode(androidx.compose.ui.test.hasTestTag("video-surface"), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val panel = compose.onNode(hasContentDescription("Close comments")).fetchSemanticsNode().boundsInRoot
        assertTrue("the video should be on screen (height ${video.height})", video.height > 100f)
        assertTrue("the video should sit above the comments, not under them", video.bottom <= panel.top)
        waitForText("Sign in to comment and reply", 10_000)
        // Liking a comment fills its thumb in.
        compose.onAllNodes(hasContentDescription("Like comment")).onFirst().performClick()
        waitForDescription("Remove like", 5_000)
        // Back closes the panel and returns to the details, still on the same video.
        back()
        compose.waitUntil(5_000) { gone(hasContentDescription("Close comments")) }
        waitForText("Share")
    }

    @Test fun chartVideosGetChannelPictures() {
        // Charts arrive without channel pictures; the app looks them up.
        val repo = AppGraph.repo
        val url = kotlinx.coroutines.runBlocking { repo.channelAvatar("https://www.youtube.com/channel/UCuAXFkgsw1L7xaCfnd5JJOw") }
        assertTrue("expected a picture URL, got $url", url != null && url.startsWith("http"))
    }

    @Test fun subscribingAddsTheChannel() {
        compose.activityRule.scenario.onActivity {
            it.startActivity(
                android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
                    .setPackage(it.packageName),
            )
        }
        waitForText("Subscribe", 30_000)
        val before = AppGraph.store.subs.value.size
        compose.onNodeWithText("Subscribe").performClick()
        compose.waitUntil(5_000) { AppGraph.store.subs.value.size == before + 1 }
        compose.onNodeWithText("Subscribed").performClick()
        compose.waitUntil(5_000) { AppGraph.store.subs.value.size == before }
    }

    @Test fun shortsPlay() {
        tab("Shorts").performClick()
        compose.waitUntil(60_000) { !gone(hasText("Comments")) || !gone(hasText("Try again")) }
        assertTrue("Shorts showed an error", gone(hasText("Try again")))
    }

    @Test fun shortsCommentsCanBeLikedAndDisliked() {
        tab("Shorts").performClick()
        // Some Shorts have comments turned off or slow to load; move on to the next one if so.
        val found = (1..3).any {
            // If Back left the Shorts tab rather than just the sheet, go back to it.
            if (runCatching { compose.waitUntil(5_000) { !gone(hasContentDescription("Comments")) } }.isFailure) tab("Shorts").performClick()
            compose.waitUntil(60_000) { !gone(hasContentDescription("Comments")) }
            compose.onAllNodes(hasContentDescription("Comments")).onFirst().performClick()
            val ok = runCatching { compose.waitUntil(30_000) { !gone(hasContentDescription("Like comment")) } }.isSuccess
            if (!ok) {
                // The comments sheet is its own window, so this needs a real key press.
                androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
                compose.onRoot().performTouchInput { swipeUp() }
            }
            ok
        }
        assertTrue("no Short with comments in three tries", found)
        compose.onAllNodes(hasContentDescription("Like comment")).onFirst().performClick()
        waitForDescription("Remove like", 5_000)
        // Disliking swaps the like for a dislike.
        compose.onAllNodes(hasContentDescription("Dislike comment")).onFirst().performClick()
        waitForDescription("Remove dislike", 5_000)
        assertTrue("like should be cleared", gone(hasContentDescription("Remove like")))
        compose.onAllNodes(hasContentDescription("Remove dislike")).onFirst().performClick()
        compose.waitUntil(5_000) { gone(hasContentDescription("Remove dislike")) }
    }
}
