package com.aaronateataco.materialtube

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.crossfade
import coil3.memory.MemoryCache
import coil3.disk.DiskCache
import okio.Path.Companion.toOkioPath
import com.aaronateataco.materialtube.data.DeArrow
import com.aaronateataco.materialtube.data.NewPipeDownloader
import com.aaronateataco.materialtube.data.Ryd
import com.aaronateataco.materialtube.data.SponsorBlock
import com.aaronateataco.materialtube.data.Store
import com.aaronateataco.materialtube.data.YouTubeRepo
import com.aaronateataco.materialtube.data.YouTubeAccount
import com.aaronateataco.materialtube.data.Sub
import com.aaronateataco.materialtube.data.channelIdOf
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
        CrashReporter.install(this)
        AppGraph.init(this)
    }

    /** Big memory + disk caches so thumbnails never reload when you scroll back up. */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .crossfade(200)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.30)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("thumbnails").toOkioPath())
                    .maxSizeBytes(512L * 1024 * 1024)
                    .build()
            }
            .build()
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
    fun syncAccount(replaceSubs: Boolean = false) {
        if (!store.signedIn) return
        scope.launch {
            runCatching { account.refreshAccount() }
            runCatching {
                val subs = account.subscriptions()
                if (replaceSubs) store.replaceSubs(subs) else store.mergeSubs(subs)
            }
        }
    }

    /** Switch which channel on the Google account Tube acts as. */
    fun selectChannel(choice: com.aaronateataco.materialtube.data.ChannelChoice) {
        store.setPageId(choice.pageId)
        store.setAccount(com.aaronateataco.materialtube.data.Account(choice.name, choice.handle, choice.avatar))
        syncAccount(replaceSubs = true)
    }

    /** True while the phone has a working internet connection; screens use it to recover by themselves. */
    val online = kotlinx.coroutines.flow.MutableStateFlow(true)

    private fun watchNetwork() {
        val cm = app.getSystemService(android.net.ConnectivityManager::class.java) ?: return
        online.value = !com.aaronateataco.materialtube.player.NetworkInfo.offline()
        runCatching {
            cm.registerDefaultNetworkCallback(object : android.net.ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: android.net.Network, caps: android.net.NetworkCapabilities) {
                    online.value = caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                }
                override fun onLost(network: android.net.Network) { online.value = false }
            })
        }
    }

    /** Shorts fetched at startup so the Shorts tab opens instantly. */
    @Volatile var shortsCache: List<com.aaronateataco.materialtube.data.VideoItem>? = null

    /** Warm things up in the background after launch. */
    private fun warmUp() {
        scope.launch {
            kotlinx.coroutines.delay(2500) // let Home load first
            if (store.settings.value.shortsDisabled) return@launch
            val fromAccount = if (store.signedIn) runCatching { account.home().shorts }.getOrDefault(emptyList()) else emptyList()
            val more = if (fromAccount.size >= 20) emptyList() else runCatching { repo.shorts(store.subs.value) }.getOrDefault(emptyList())
            val list = (fromAccount + more).distinctBy { it.id }
            shortsCache = list
            repo.prefetch(list.take(3).map { it.id })
        }
    }

    /** Adds a video to Watch later and says whether it worked. */
    fun saveToWatchLater(context: android.content.Context, videoId: String) {
        val app = context.applicationContext
        scope.launch {
            val ok = runCatching { account.saveToWatchLater(videoId) }.isSuccess
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                android.widget.Toast.makeText(app, if (ok) "Saved to Watch later" else "Couldn't save to Watch later", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun signOut() {
        store.setCookie(null)
        store.setAccount(null)
        store.setPageId(null)
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
        watchNetwork()
        warmUp()
    }
}
