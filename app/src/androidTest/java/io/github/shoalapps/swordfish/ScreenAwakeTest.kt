package io.github.shoalapps.swordfish

import android.view.View
import android.view.ViewGroup
import androidx.media3.common.Player
import androidx.media3.exoplayer.source.SilenceMediaSource
import io.github.shoalapps.swordfish.player.PlayerHolder
import org.junit.Test

class ScreenAwakeTest : TubeTest() {
    private fun keepsScreenOn(): Boolean {
        fun hasFlag(view: View): Boolean = view.keepScreenOn ||
            (view is ViewGroup && (0 until view.childCount).any { hasFlag(view.getChildAt(it)) })
        return compose.activity.window.decorView.let(::hasFlag)
    }

    @Test fun playingKeepsScreenAwakeAndPausingReleasesIt() {
        compose.runOnUiThread {
            PlayerHolder.player.apply {
                setMediaSource(SilenceMediaSource.Factory().setDurationUs(30_000_000).createMediaSource())
                repeatMode = Player.REPEAT_MODE_ONE
                prepare()
                play()
            }
        }
        compose.waitUntil(10_000) { compose.runOnUiThread { PlayerHolder.player.isPlaying && keepsScreenOn() } }
        compose.runOnUiThread { PlayerHolder.player.pause() }
        compose.waitUntil(5_000) { compose.runOnUiThread { !keepsScreenOn() } }
        compose.runOnUiThread { PlayerHolder.player.repeatMode = Player.REPEAT_MODE_OFF }
    }
}
