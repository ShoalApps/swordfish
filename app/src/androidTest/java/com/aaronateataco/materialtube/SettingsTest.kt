package com.aaronateataco.materialtube

import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsTest : TubeTest() {
    private fun openSettings() {
        tab("You").performClick()
        compose.onNodeWithContentDescription("Settings").performClick()
        waitForText("Appearance")
    }

    private fun scrollTo(text: String) {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(text))
    }

    @Test fun themeSwitchesToDarkAndBack() {
        openSettings()
        compose.onNodeWithText("Dark").performClick()
        compose.waitForIdle()
        assertEquals(2, AppGraph.store.settings.value.theme)
        compose.onNodeWithText("Light").performClick()
        compose.waitForIdle()
        assertEquals(1, AppGraph.store.settings.value.theme)
    }

    @Test fun turningOffShortsHidesTheTab() {
        openSettings()
        scrollTo("Turn off Shorts")
        compose.onNodeWithText("Turn off Shorts").performClick()
        compose.waitForIdle()
        assertTrue(AppGraph.store.settings.value.shortsDisabled)

        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitForIdle()
        assertTrue("Shorts tab still showing", gone(hasText("Shorts") and isSelectable()))

        AppGraph.store.updateSettings { it.copy(shortsDisabled = false) }
        compose.waitForIdle()
        tab("Shorts").assertExists()
    }

    @Test fun playbackToggles() {
        openSettings()
        scrollTo("Autoplay")
        compose.onNodeWithText("Autoplay").performClick()
        compose.waitForIdle()
        assertFalse(AppGraph.store.settings.value.autoplayNext)
    }

    @Test fun developerSettingsOpen() {
        // Normally unlocked by tapping the version number seven times on the About page.
        AppGraph.store.updateSettings { it.copy(developerMode = true) }
        openSettings()
        scrollTo("Developer settings")
        compose.onNodeWithText("Developer settings").performClick()
        waitForText("Feed filters")
        compose.onNodeWithText("Hide live streams").performClick()
        compose.waitForIdle()
        assertTrue(AppGraph.store.settings.value.hideLive)
    }

    @Test fun learningFeedCanBeChosenFromHome() {
        compose.onNodeWithContentDescription("Choose feed").performClick()
        waitForText("Your feed")
        compose.onNodeWithText("Learning").performClick()
        compose.waitForIdle()
        assertEquals("learning", AppGraph.store.settings.value.feedMode)
        waitForText("Topics")
    }
}
