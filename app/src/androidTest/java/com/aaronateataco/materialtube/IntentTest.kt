package com.aaronateataco.materialtube

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
        Espresso.pressBack()
        waitForDescription("Close")
        // …and its close button removes it.
        compose.onNodeWithContentDescription("Close").performClick()
        compose.waitForIdle()
        assertTrue(gone(hasText("Share")))
        assertTrue(gone(hasContentDescription("Close")))
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
