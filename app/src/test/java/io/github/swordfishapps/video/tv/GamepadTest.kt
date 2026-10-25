package io.github.swordfishapps.video.tv

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GamepadTest {
    @Test fun buttonsFollowTheStandardLayoutYouTubeExpects() {
        // YouTube's TV app reads standard gamepad indices (key code 32768 + index).
        assertEquals(0, Gamepad.buttonIndex(KeyEvent.KEYCODE_BUTTON_A))
        assertEquals(1, Gamepad.buttonIndex(KeyEvent.KEYCODE_BUTTON_B))
        assertEquals(4, Gamepad.buttonIndex(KeyEvent.KEYCODE_BUTTON_L1))
        assertEquals(7, Gamepad.buttonIndex(KeyEvent.KEYCODE_BUTTON_R2))
        assertEquals(9, Gamepad.buttonIndex(KeyEvent.KEYCODE_BUTTON_START))
        assertEquals(12, Gamepad.buttonIndex(KeyEvent.KEYCODE_DPAD_UP))
        assertEquals(15, Gamepad.buttonIndex(KeyEvent.KEYCODE_DPAD_RIGHT))
        assertEquals(16, Gamepad.buttonIndex(KeyEvent.KEYCODE_BUTTON_MODE))
        // Keyboard keys aren't controller buttons.
        assertNull(Gamepad.buttonIndex(KeyEvent.KEYCODE_ENTER))
    }
}
