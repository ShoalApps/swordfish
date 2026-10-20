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

    fun init(application: Application) {
        app = application
        val locale = Locale.getDefault()
        NewPipe.init(
            NewPipeDownloader(http),
            Localization.fromLocale(locale),
            ContentCountry(locale.country.ifBlank { "GB" }),
        )
    }
}
