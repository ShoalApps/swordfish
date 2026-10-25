package io.github.swordfishapps.video

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IntentTest : TubeTest() {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun send(intent: Intent) {
        compose.activityRule.scenario.onActivity { it.startActivity(intent.setPackage(it.packageName)) }
        compose.waitForIdle()
    }

    @Test fun youtubeLinkOpensThePlayerAndItCanBeMinimisedAndClosed() {
        send(Intent(Intent.ACTION_VIEW, Uri.parse("https://youtu.be/dQw4w9WgXcQ?t=30")))
        waitForText("Share")
        waitForText("Comments")

        // Back shrinks the watch page into the mini player…
        back()
        waitForDescription("Close")
        // …and its close button removes it.
        compose.onNodeWithContentDescription("Close").performClick()
        compose.waitForIdle()
        assertTrue(gone(hasText("Share")))
        assertTrue(gone(hasContentDescription("Close")))
        // Closing stops playback outright, so autoplay has nothing to start.
        val (state, items) = compose.runOnUiThread {
            io.github.swordfishapps.video.player.PlayerHolder.player.playbackState to io.github.swordfishapps.video.player.PlayerHolder.player.mediaItemCount
        }
        assertEquals(androidx.media3.common.Player.STATE_IDLE, state)
        assertEquals(0, items)
    }

    @Test fun closingPictureInPictureStopsEverything() {
        send(Intent(Intent.ACTION_VIEW, Uri.parse("https://youtu.be/dQw4w9WgXcQ")))
        waitForText("Share")
        // The floating window's X: Android stops the app, then takes it out of PiP.
        compose.activityRule.scenario.onActivity { it.pipEnded(stillVisible = false) }
        compose.waitForIdle()
        val (state, items) = compose.runOnUiThread {
            io.github.swordfishapps.video.player.PlayerHolder.player.playbackState to io.github.swordfishapps.video.player.PlayerHolder.player.mediaItemCount
        }
        assertEquals("player should be idle", androidx.media3.common.Player.STATE_IDLE, state)
        assertEquals("nothing left to autoplay", 0, items)
        assertTrue("the player should be closed", gone(hasText("Share")))
    }

    @Test fun expandingPictureInPictureKeepsTheVideo() {
        send(Intent(Intent.ACTION_VIEW, Uri.parse("https://youtu.be/dQw4w9WgXcQ")))
        waitForText("Share")
        // Tapping the floating window to return: the app is visible again when PiP ends.
        compose.activityRule.scenario.onActivity { it.pipEnded(stillVisible = true) }
        compose.waitForIdle()
        assertTrue("the watch page should still be open", !gone(hasText("Share")))
    }

    @Test fun sharedTextWithALinkOpensThePlayer() {
        send(
            Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, "Watch this! https://www.youtube.com/watch?v=dQw4w9WgXcQ&feature=share"),
        )
        waitForText("Share")
    }

    @Test fun nonVideoLinksAreIgnored() {
        send(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/@MrBeast")))
        assertTrue(gone(hasText("Share")))
    }

    @Test fun searchShortcutOpensSearch() {
        send(Intent(Intent.ACTION_VIEW, Uri.parse("materialtube://search")))
        waitForText("Search YouTube")
    }

    @Test fun subscriptionsShortcutOpensTheTab() {
        send(Intent(Intent.ACTION_VIEW, Uri.parse("materialtube://subs")))
        waitFor(hasText("Subscriptions") and androidx.compose.ui.test.isSelectable() and androidx.compose.ui.test.isSelected(), 5_000)
    }
}
