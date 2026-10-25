package com.aaronateataco.materialtube

import android.Manifest
import android.app.PictureInPictureParams
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.aaronateataco.materialtube.player.PlaybackService
import com.aaronateataco.materialtube.player.PlayerViewModel
import com.aaronateataco.materialtube.ui.AppRoot
import com.aaronateataco.materialtube.ui.theme.TubeTheme
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val playerVm: PlayerViewModel by viewModels()
    private val inPip = mutableStateOf(false)
    /** Screen to open from a launcher shortcut (search, subs, shorts). */
    private val pendingRoute = mutableStateOf<String?>(null)
    private val crashReport = mutableStateOf<String?>(null)
    private var controller: ListenableFuture<MediaController>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        crashReport.value = CrashReporter.take(this)
        AppGraph.checkForUpdate()
        handleIntent(intent)

        // Binds the background service so playback survives leaving the app.
        controller = MediaController.Builder(
            this,
            SessionToken(this, ComponentName(this, PlaybackService::class.java)),
        ).buildAsync()

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        addOnPictureInPictureModeChangedListener { info ->
            inPip.value = info.isInPictureInPictureMode
            if (!info.isInPictureInPictureMode) pipEnded(lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED))
        }

        // Android 12+: slide into picture-in-picture automatically when leaving mid-video.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            lifecycleScope.launch {
                snapshotFlow { playerVm.isPlaying && playerVm.sheetVisible }.collect { playing ->
                    setPictureInPictureParams(
                        PictureInPictureParams.Builder()
                            .setAspectRatio(Rational(16, 9))
                            .setAutoEnterEnabled(playing && AppGraph.store.settings.value.autoPip)
                            .setSeamlessResizeEnabled(true)
                            .build(),
                    )
                }
            }
        }

        setContent {
            val settings by AppGraph.store.settings.collectAsStateWithLifecycle()
            TubeTheme(settings) {
                AppRoot(
                    vm = playerVm,
                    inPip = inPip.value,
                    pendingRoute = pendingRoute.value,
                    onRouteHandled = { pendingRoute.value = null },
                )
                crashReport.value?.let { report ->
                    com.aaronateataco.materialtube.ui.CrashDialog(report) { crashReport.value = null }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /**
     * Leaving picture-in-picture while the app isn't on screen means the floating window was closed
     * with its X (or swiped away): stop the video and autoplay, like the official app. Expanding the
     * window back into the app keeps playing.
     */
    internal fun pipEnded(stillVisible: Boolean) {
        if (!stillVisible) playerVm.close()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S &&
            playerVm.isPlaying && playerVm.sheetVisible && AppGraph.store.settings.value.autoPip
        ) {
            enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build())
        }
    }

    override fun onStop() {
        super.onStop()
        if (!AppGraph.store.settings.value.backgroundPlay && !isInPictureInPictureMode) {
            playerVm.player.pause()
        }
    }

    override fun onDestroy() {
        controller?.let { MediaController.releaseFuture(it) }
        super.onDestroy()
    }

    private fun handleIntent(intent: Intent?) {
        intent?.data?.takeIf { it.scheme == "materialtube" }?.let { uri ->
            pendingRoute.value = uri.host
            return
        }
        val text = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.dataString
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            else -> null
        } ?: return
        val url = Regex("""https?://\S+""").find(text)?.value ?: return
        if (Regex("""(youtube\.com/(watch|shorts|live)|youtu\.be/)""").containsMatchIn(url)) {
            playerVm.openUrl(url, com.aaronateataco.materialtube.ui.Links.startSeconds(url))
        }
    }
}
