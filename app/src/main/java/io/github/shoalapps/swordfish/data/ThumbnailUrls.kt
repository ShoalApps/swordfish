package io.github.shoalapps.swordfish.data

object ThumbnailUrls {
    private val smallYouTube = Regex("^https://i(?:[0-9]+)?\\.ytimg\\.com/(?:vi|vi_webp)/([A-Za-z0-9_-]{11})/(?:default|mqdefault|hqdefault|sddefault)\\.(?:jpg|webp)(?:\\?.*)?$")

    /** Ask for a 720p video thumbnail; the image loader retries the supplied URL if unavailable. */
    fun preferred(url: String): String = smallYouTube.matchEntire(url)?.groupValues?.get(1)
        ?.let { "https://i.ytimg.com/vi/$it/hq720.jpg" } ?: url
}
