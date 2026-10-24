package com.aaronateataco.materialtube

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aaronateataco.materialtube.player.PlayerHolder
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
        // Tap the first video's title area.
        compose.onAllNodesWithContentDescription("More").onFirst().assertExists()
        compose.onAllNodes(androidx.compose.ui.test.hasClickAction() and androidx.compose.ui.test.hasAnyDescendant(hasContentDescription("More")))
            .onFirst().performClick()
        waitForText("Comments", 20_000)
        compose.waitUntil(45_000) { isPlaying() }
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

    @Test fun commentsOpenOnTheWatchPage() {
        compose.activityRule.scenario.onActivity {
            it.startActivity(
                android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
                    .setPackage(it.packageName),
            )
        }
        waitForText("Comments")
        compose.waitUntil(30_000) { gone(hasText("Loading comments…")) }
        compose.onAllNodes(hasText("Comments")).onFirst().performClick()
        waitForText("Sign in to comment and reply", 10_000)
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
}
