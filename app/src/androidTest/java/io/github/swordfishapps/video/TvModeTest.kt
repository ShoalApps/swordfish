package io.github.swordfishapps.video

import android.view.InputDevice
import android.view.KeyEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.swordfishapps.video.tv.TvActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** TV mode against the real youtube.com/tv. */
@NetworkTest
@RunWith(AndroidJUnit4::class)
class TvModeTest {
    private fun ActivityScenario<TvActivity>.js(expr: String): String {
        var out = ""
        val done = CountDownLatch(1)
        onActivity { it.web.evaluateJavascript(expr) { r -> out = r; done.countDown() } }
        done.await(10, TimeUnit.SECONDS)
        return out.trim('"')
    }

    private fun ActivityScenario<TvActivity>.waitFor(timeoutMs: Long = 30_000, what: String, check: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (check()) return
            Thread.sleep(500)
        }
        throw AssertionError("timed out waiting for $what")
    }

    private fun ActivityScenario<TvActivity>.press(code: Int) = onActivity {
        for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
            val e = KeyEvent(0, 0, action, code, 0, 0, 0, 0, 0, InputDevice.SOURCE_GAMEPAD)
            it.dispatchKeyEvent(e)
        }
    }

    /** YouTube's TV app remembers where it was; start from its welcome screen every time. */
    private fun freshStart() {
        val done = CountDownLatch(1)
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync {
            android.webkit.WebStorage.getInstance().deleteAllData()
            android.webkit.CookieManager.getInstance().removeAllCookies { done.countDown() }
        }
        done.await(10, TimeUnit.SECONDS)
    }

    @Test fun youtubesTvAppLoadsWithVacuumTubeAndAController() {
        freshStart()
        ActivityScenario.launch(TvActivity::class.java).use { tv ->
            // YouTube accepted the TV identity and served its TV app, with the modules injected.
            tv.waitFor(what = "the TV app") { tv.js("location.pathname") == "/tv" && tv.js("typeof window.__swordfishPad") == "function" }
            assertEquals("true", tv.js("!!(window.trustedTypes && trustedTypes.defaultPolicy)"))
            assertEquals("object", tv.js("typeof SwordfishTV"))
            // A controller press reaches the page as a standard gamepad button.
            // On the welcome screen, A presses "Get started", which opens the sign-in choices.
            tv.waitFor(what = "the welcome screen") { tv.js("document.activeElement && document.activeElement.textContent.trim()") == "Get started" }
            tv.press(KeyEvent.KEYCODE_BUTTON_A)
            tv.waitFor(10_000, "A to open the sign-in choices") { tv.js("document.body.innerText.includes('Watch as guest')") == "true" }
        }
    }

    @Test fun aVideoOpensStraightInTvMode() {
        val intent = android.content.Intent(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext, TvActivity::class.java)
            .putExtra(TvActivity.EXTRA_VIDEO_ID, "dQw4w9WgXcQ")
        ActivityScenario.launch<TvActivity>(intent).use { tv ->
            tv.waitFor(45_000, "the video to play") {
                tv.js("(()=>{const v=document.querySelector('video');return !!v && v.currentTime > 1 && !v.paused})()") == "true"
            }
            assertTrue(tv.js("location.hash").contains("dQw4w9WgXcQ"))
        }
    }
}
