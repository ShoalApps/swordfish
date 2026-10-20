package com.aaronparry.tube.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale

/** A page of signed-in results: regular videos and Shorts. */
data class AccountFeed(val videos: List<VideoItem>, val shorts: List<VideoItem>)

/**
 * Talks to YouTube's own internal API ("InnerTube") as the signed-in user,
 * using the cookies captured by the sign-in page.
 */
class YouTubeAccount(private val http: OkHttpClient, private val store: Store) {
    private val origin = "https://www.youtube.com"

    private fun version(): String = "2.20260930.01.00"

    private fun authHeader(cookie: String): String? {
        val parts = cookie.split(";").map { it.trim() }
        val sapisid = parts.firstOrNull { it.startsWith("SAPISID=") }?.substringAfter("=")
            ?: parts.firstOrNull { it.startsWith("__Secure-3PAPISID=") }?.substringAfter("=")
            ?: return null
        val ts = System.currentTimeMillis() / 1000
        val hash = MessageDigest.getInstance("SHA-1")
            .digest("$ts $sapisid $origin".toByteArray())
            .joinToString("") { "%02x".format(it) }
        return "SAPISIDHASH ${ts}_$hash"
    }

    private suspend fun post(endpoint: String, extra: JsonObjectBuilder.() -> Unit = {}): JsonObject =
        withContext(Dispatchers.IO) {
            val cookie = store.cookie.value ?: throw IOException("Not signed in")
            val v = version()
            val locale = Locale.getDefault()
            val body = buildJsonObject {
                putJsonObject("context") {
                    putJsonObject("client") {
                        put("clientName", "WEB")
                        put("clientVersion", v)
                        put("hl", locale.language.ifBlank { "en" })
                        put("gl", locale.country.ifBlank { "GB" })
                    }
                }
                extra()
            }
            val request = Request.Builder()
                .url("$origin/youtubei/v1/$endpoint?prettyPrint=false")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .header("Cookie", cookie)
                .header("Authorization", authHeader(cookie) ?: "")
                .header("Origin", origin)
                .header("X-Origin", origin)
                .header("X-Goog-AuthUser", "0")
                .header("X-Youtube-Client-Name", "1")
                .header("X-Youtube-Client-Version", v)
                .header("User-Agent", NewPipeDownloader.USER_AGENT)
                .build()
            http.newCall(request).execute().use { resp ->
                if (resp.code == 401 || resp.code == 403) throw IOException("YouTube rejected the sign-in (${resp.code}). Try signing in again.")
                if (!resp.isSuccessful) throw IOException("YouTube returned ${resp.code}")
                json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
            }
        }

    private suspend fun browse(browseId: String, pages: Int = 1): List<JsonObject> {
        val out = mutableListOf(post("browse") { put("browseId", browseId) })
        repeat(pages - 1) {
            val token = Yti.continuation(out.last()) ?: return out
            out += post("browse") { put("continuation", token) }
        }
        return out
    }

    // -------- Account --------

    suspend fun refreshAccount(): Account? {
        val root = post("account/account_menu")
        val header = Yti.findFirst(root, "activeAccountHeaderRenderer") ?: return null
        val account = Account(
            name = Yti.text(header["accountName"]) ?: "YouTube account",
            handle = Yti.text(header["channelHandle"]),
            avatar = Yti.thumb(header["accountPhoto"]),
        )
        store.setAccount(account)
        return account
    }

    // -------- Feeds --------

    suspend fun home(): AccountFeed {
        val pages = browse("FEwhat_to_watch", pages = 2)
        return AccountFeed(
            videos = pages.flatMap { Yti.videos(it) }.distinctBy { it.id },
            shorts = pages.flatMap { Yti.shorts(it) }.distinctBy { it.id },
        )
    }

    suspend fun subscriptionFeed(): List<VideoItem> =
        browse("FEsubscriptions", pages = 2).flatMap { Yti.videos(it) }.distinctBy { it.id }

    suspend fun playlist(browseId: String): List<VideoItem> =
        browse(browseId, pages = 2).flatMap { Yti.videos(it) }.distinctBy { it.id }

    suspend fun likedVideos() = playlist("VLLL")
    suspend fun watchLater() = playlist("VLWL")
    suspend fun history() = playlist("FEhistory")

    suspend fun subscriptions(): List<Sub> =
        browse("FEchannels", pages = 10).flatMap { Yti.channels(it) }.distinctBy { it.url }

    // -------- Actions --------

    suspend fun setSubscribed(channelId: String, subscribed: Boolean) {
        post(if (subscribed) "subscription/subscribe" else "subscription/unsubscribe") {
            putJsonArray("channelIds") { add(channelId) }
        }
    }

    /** action = "like", "dislike" or "removelike" */
    suspend fun rate(videoId: String, action: String) {
        post("like/$action") { putJsonObject("target") { put("videoId", videoId) } }
    }

    suspend fun saveToWatchLater(videoId: String) {
        post("browse/edit_playlist") {
            put("playlistId", "WL")
            putJsonArray("actions") {
                addJsonObject {
                    put("addedVideoId", videoId)
                    put("action", "ACTION_ADD_VIDEO")
                }
            }
        }
    }
}

/** Forgiving reader for InnerTube's deeply nested JSON. */
object Yti {
    private val JsonElement?.obj get() = this as? JsonObject
    private val JsonElement?.arr get() = this as? JsonArray
    private val JsonElement?.str get() = (this as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonElement?.path(vararg keys: String): JsonElement? =
        keys.fold(this) { cur, k -> cur.obj?.get(k) }

    private fun fixUrl(u: String) = if (u.startsWith("//")) "https:$u" else u

    /** Calls [visit] on every object stored under a key; return true to stop going deeper there. */
    fun walk(el: JsonElement?, visit: (String, JsonObject) -> Boolean) {
        when (el) {
            is JsonObject -> for ((k, v) in el) {
                if (v is JsonObject && visit(k, v)) continue
                walk(v, visit)
            }
            is JsonArray -> el.forEach { walk(it, visit) }
            else -> Unit
        }
    }

    fun findFirst(el: JsonElement?, key: String): JsonObject? {
        var found: JsonObject? = null
        walk(el) { k, v ->
            if (found == null && k == key) found = v
            found != null
        }
        return found
    }

    private fun findString(el: JsonElement?, key: String): String? {
        var found: String? = null
        fun go(e: JsonElement?) {
            if (found != null) return
            when (e) {
                is JsonObject -> for ((k, v) in e) {
                    if (k == key && v.str != null) { found = v.str; return }
                    go(v)
                }
                is JsonArray -> e.forEach { go(it) }
                else -> Unit
            }
        }
        go(el)
        return found
    }

    fun text(el: JsonElement?): String? {
        val o = el.obj ?: return el.str
        o["simpleText"].str?.let { return it }
        o["content"].str?.let { return it }
        o["runs"].arr?.let { runs -> return runs.joinToString("") { it.obj?.get("text").str.orEmpty() } }
        return null
    }

    fun thumb(el: JsonElement?): String? =
        (el.obj?.get("thumbnails").arr ?: el.obj?.get("sources").arr)
            ?.lastOrNull()?.obj?.get("url")?.str?.let(::fixUrl)

    fun continuation(root: JsonElement): String? =
        findFirst(root, "continuationCommand")?.get("token").str
            ?: findFirst(root, "nextContinuationData")?.get("continuation").str

    fun videos(root: JsonElement): List<VideoItem> {
        val out = mutableListOf<VideoItem>()
        walk(root) { k, v ->
            val item = when (k) {
                "videoRenderer", "gridVideoRenderer", "compactVideoRenderer", "playlistVideoRenderer" -> fromVideoRenderer(v)
                "lockupViewModel" -> fromLockup(v)
                else -> null
            }
            if (item != null) out += item
            item != null || k == "shortsLockupViewModel" || k == "reelItemRenderer"
        }
        return out
    }

    fun shorts(root: JsonElement): List<VideoItem> {
        val out = mutableListOf<VideoItem>()
        walk(root) { k, v ->
            val item = when (k) {
                "shortsLockupViewModel" -> fromShortsLockup(v)
                "reelItemRenderer" -> fromReel(v)
                else -> null
            }
            if (item != null) out += item
            item != null
        }
        return out
    }

    fun channels(root: JsonElement): List<Sub> {
        val out = mutableListOf<Sub>()
        walk(root) { k, v ->
            if (k == "channelRenderer" || k == "gridChannelRenderer") {
                val id = v["channelId"].str
                if (id != null) out += Sub(channelUrlOf(id), text(v["title"]).orEmpty(), thumb(v["thumbnail"]))
                true
            } else false
        }
        return out
    }

    private fun video(
        id: String,
        title: String,
        channel: String,
        channelId: String?,
        avatar: String?,
        thumbnail: String?,
        duration: String?,
        views: String?,
        published: String?,
        short: Boolean = false,
    ) = VideoItem(
        url = if (short) "https://www.youtube.com/shorts/$id" else "https://www.youtube.com/watch?v=$id",
        id = id,
        title = title,
        channelName = channel,
        channelUrl = channelId?.takeIf { it.startsWith("UC") }?.let(::channelUrlOf),
        channelAvatar = avatar,
        thumbnail = thumbnail ?: "https://i.ytimg.com/vi/$id/hqdefault.jpg",
        durationSec = parseDuration(duration),
        viewsText = views,
        uploaded = published,
        isLive = duration == null && (views?.contains("watching", true) == true),
        isShort = short,
    )

    private fun fromVideoRenderer(o: JsonObject): VideoItem? {
        val id = o["videoId"].str ?: return null
        val owner = o["ownerText"] ?: o["shortBylineText"] ?: o["longBylineText"]
        val channelId = owner.path("runs").arr?.firstOrNull().path("navigationEndpoint", "browseEndpoint", "browseId").str
        val avatar = thumb(o.path("channelThumbnailSupportedRenderers", "channelThumbnailWithLinkRenderer", "thumbnail"))
            ?: thumb(o["channelThumbnail"])
        val duration = text(o["lengthText"])
            ?: findFirst(o, "thumbnailOverlayTimeStatusRenderer")?.let { text(it["text"]) }
        val views = text(o["shortViewCountText"]) ?: text(o["viewCountText"])
        return video(
            id = id,
            title = text(o["title"]).orEmpty(),
            channel = text(owner).orEmpty(),
            channelId = channelId,
            avatar = avatar,
            thumbnail = thumb(o["thumbnail"]),
            duration = duration?.takeIf { it.contains(":") },
            views = views,
            published = text(o["publishedTimeText"]),
        )
    }

    private fun fromLockup(o: JsonObject): VideoItem? {
        if (o["contentType"].str?.contains("VIDEO") != true) return null
        val id = o["contentId"].str ?: return null
        val meta = o.path("metadata", "lockupMetadataViewModel")
        val rows = meta.path("metadata", "contentMetadataViewModel", "metadataRows").arr
        val parts = rows?.map { row ->
            row.obj?.get("metadataParts").arr?.mapNotNull { text(it.obj?.get("text")) } ?: emptyList()
        } ?: emptyList()
        var duration: String? = null
        walk(o.path("contentImage")) { k, v ->
            if (k == "thumbnailBadgeViewModel" && duration == null) {
                duration = v["text"].str?.takeIf { it.contains(":") }
            }
            false
        }
        val avatar = findFirst(meta.path("image"), "image")?.let { thumb(it) }
            ?: findFirst(meta.path("image"), "avatarViewModel")?.let { thumb(it["image"]) }
        val rest = parts.drop(1).flatten()
        return video(
            id = id,
            title = text(meta.path("title")).orEmpty(),
            channel = parts.firstOrNull()?.firstOrNull().orEmpty(),
            channelId = findString(meta, "browseId"),
            avatar = avatar,
            thumbnail = thumb(o.path("contentImage", "thumbnailViewModel", "image")),
            duration = duration,
            views = rest.firstOrNull { it.contains("view", true) || it.contains("watching", true) },
            published = rest.firstOrNull { it.contains("ago", true) || it.contains("Streamed", true) },
        )
    }

    private fun fromShortsLockup(o: JsonObject): VideoItem? {
        val id = findString(o, "videoId")
            ?: o["entityId"].str?.substringAfterLast("-")?.takeIf { it.length == 11 }
            ?: return null
        return video(
            id = id,
            title = text(o.path("overlayMetadata", "primaryText")).orEmpty(),
            channel = "",
            channelId = null,
            avatar = null,
            thumbnail = thumb(o.path("thumbnail")) ?: thumb(o.path("thumbnailViewModel", "image")),
            duration = null,
            views = text(o.path("overlayMetadata", "secondaryText")),
            published = null,
            short = true,
        )
    }

    private fun fromReel(o: JsonObject): VideoItem? {
        val id = o["videoId"].str ?: return null
        return video(
            id = id,
            title = text(o["headline"]).orEmpty(),
            channel = "",
            channelId = null,
            avatar = null,
            thumbnail = thumb(o["thumbnail"]),
            duration = null,
            views = text(o["viewCountText"]),
            published = null,
            short = true,
        )
    }
}
