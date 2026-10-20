package com.aaronparry.tube

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.crossfade
import com.aaronparry.tube.data.DeArrow
import com.aaronparry.tube.data.NewPipeDownloader
import com.aaronparry.tube.data.Ryd
import com.aaronparry.tube.data.SponsorBlock
import com.aaronparry.tube.data.Store
import com.aaronparry.tube.data.YouTubeRepo
import com.aaronparry.tube.data.YouTubeAccount
import com.aaronparry.tube.data.Sub
import com.aaronparry.tube.data.channelIdOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import java.util.Locale
import java.util.concurrent.TimeUnit

class TubeApp : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context).crossfade(true).build()
}

/** Tiny service locator: one instance of each piece for the whole app. */
object AppGraph {
    lateinit var app: Application
        private set

    val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
    val store by lazy { Store(app) }
    val repo by lazy { YouTubeRepo() }
    val sponsorBlock by lazy { SponsorBlock(http) }
    val deArrow by lazy { DeArrow(http) }
    val ryd by lazy { Ryd(http) }
    val account by lazy { YouTubeAccount(http, store) }

    /** For fire-and-forget work that should outlive a screen (e.g. syncing a subscribe). */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Subscribe/unsubscribe locally, and on YouTube too when signed in. */
    fun toggleSubscribe(sub: Sub) {
        val now = store.toggleSub(sub)
        val id = channelIdOf(sub.url) ?: return
        if (store.signedIn) scope.launch { runCatching { account.setSubscribed(id, now) } }
    }

    /** After signing in: load the profile and pull in YouTube subscriptions. */
    fun syncAccount() {
        if (!store.signedIn) return
        scope.launch {
            runCatching { account.refreshAccount() }
            runCatching { store.mergeSubs(account.subscriptions()) }
        }
    }

    fun signOut() {
        store.setCookie(null)
        store.setAccount(null)
        runCatching { android.webkit.CookieManager.getInstance().removeAllCookies(null) }
    }

    fun init(application: Application) {
        app = application
        val locale = Locale.getDefault()
        NewPipe.init(
            NewPipeDownloader(http),
            Localization.fromLocale(locale),
            ContentCountry(locale.country.ifBlank { "GB" }),
        )
        if (store.signedIn && store.account.value == null) syncAccount()
    }
}
