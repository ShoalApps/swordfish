package io.github.shoalapps.swordfish.data

import io.github.shoalapps.swordfish.AppGraph
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
    internal suspend fun perform(action: OfflineAction) {
        when (action.kind) {
            "subscription" -> sendSetSubscribed(action.target, action.value.toBooleanStrict())
            "bell" -> sendSetNotifications(action.target, NotificationMode.valueOf(action.value))
            "rating" -> sendRate(action.target, action.value)
            "comment" -> sendPostComment(action.target, action.value)
            "reply" -> sendReplyToComment(action.target, action.extra, action.value)
            "watchLater" -> sendSaveToWatchLater(action.target)
            "commentRating" -> sendRateComment(action.target.substringBefore('|'), action.target.substringAfter('|'),
                io.github.shoalapps.swordfish.player.Rating.valueOf(action.extra), io.github.shoalapps.swordfish.player.Rating.valueOf(action.value))
            else -> throw IOException("Unsupported saved action")
        }
    }

    private val origin = "https://www.youtube.com"

    private fun version(): String = "2.20260930.01.00"

    /** SAPISIDHASH auth, built the same way yt-dlp and YouTube's own web app do it. */
    private fun authHeader(cookie: String): String? {
        val jar = cookie.split(";").map { it.trim() }.associate { it.substringBefore("=") to it.substringAfter("=", "") }
        val sapisid = jar["SAPISID"]?.takeIf { it.isNotEmpty() } ?: jar["__Secure-3PAPISID"]
        val schemes = listOf(
            "SAPISIDHASH" to sapisid,
            "SAPISID1PHASH" to jar["__Secure-1PAPISID"],
            "SAPISID3PHASH" to jar["__Secure-3PAPISID"],
        )
        val ts = (System.currentTimeMillis() / 1000).toString()
        val parts = schemes.mapNotNull { (scheme, sid) ->
            if (sid.isNullOrEmpty()) return@mapNotNull null
            val hash = MessageDigest.getInstance("SHA-1")
                .digest("$ts $sid $origin".toByteArray())
                .joinToString("") { "%02x".format(it) }
            "$scheme ${ts}_$hash"
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" ")
    }

    private suspend fun post(
        endpoint: String,
        useChannel: Boolean = true,
        extra: JsonObjectBuilder.() -> Unit = {},
    ): JsonObject =
        withContext(Dispatchers.IO) {
            val cookie = store.cookie.value ?: throw IOException("Not signed in")
            val v = version()
            val locale = Locale.getDefault()
            val pageId = if (useChannel) store.pageId.value else null
            val body = buildJsonObject {
                putJsonObject("context") {
                    putJsonObject("client") {
                        put("clientName", "WEB")
                        put("clientVersion", v)
                        put("hl", locale.language.ifBlank { "en" })
                        put("gl", locale.country.ifBlank { "GB" })
                    }
                    if (pageId != null) putJsonObject("user") { put("onBehalfOfUser", pageId) }
                }
                extra()
            }
            val request = Request.Builder()
                .url("$origin/youtubei/v1/$endpoint?prettyPrint=false")
                .apply { if (pageId != null) header("X-Goog-PageId", pageId) }
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

    /** Raw responses from the last channel lookup, for Developer settings > Debug. */
    @Volatile var lastChannelDebug: String = ""
        private set

    /** Every channel on this Google account (main channel and Brand Account channels). */
    suspend fun channels(): List<ChannelChoice> {
        val debug = StringBuilder()
        val found = mutableListOf<ChannelChoice>()

        // 1. InnerTube channel switcher (what the website's "Switch account" uses)
        runCatching {
            val root = post("account/accounts_list", useChannel = false) {
                put("requestType", "ACCOUNTS_LIST_REQUEST_TYPE_CHANNEL_SWITCHER")
                put("callCircumstance", "SWITCHING_USERS_FULL")
            }
            debug.append("accounts_list:\n").append(root.toString().take(6000)).append("\n\n")
            found += Yti.accountItems(root)
        }.onFailure { debug.append("accounts_list failed: ${it.message}\n\n") }

        // 2. Older web endpoint, still used by the desktop site
        if (found.isEmpty()) runCatching {
            val text = getText("$origin/getAccountSwitcherEndpoint")
            debug.append("getAccountSwitcherEndpoint:\n").append(text.take(6000)).append("\n\n")
            val root = json.parseToJsonElement(text.removePrefix(")]}'").trim())
            found += Yti.accountItems(root)
        }.onFailure { debug.append("getAccountSwitcherEndpoint failed: ${it.message}\n\n") }

        // 3. At minimum, offer the signed-in channel itself
        if (found.isEmpty()) runCatching {
            val root = post("account/account_menu", useChannel = false)
            val header = Yti.findFirst(root, "activeAccountHeaderRenderer")
            if (header != null) {
                found += ChannelChoice(
                    name = Yti.text(header["accountName"]) ?: "Main channel",
                    handle = Yti.text(header["channelHandle"]),
                    avatar = Yti.thumb(header["accountPhoto"]),
                    pageId = null,
                    selected = true,
                )
            }
        }

        lastChannelDebug = debug.toString()
        return found
    }

    private suspend fun getText(url: String): String = withContext(Dispatchers.IO) {
        val cookie = store.cookie.value ?: throw IOException("Not signed in")
        val request = Request.Builder()
            .url(url)
            .header("Cookie", cookie)
            .header("User-Agent", NewPipeDownloader.USER_AGENT)
            .header("Referer", "$origin/")
            .build()
        http.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            resp.body?.string().orEmpty()
        }
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

    suspend fun playlist(browseId: String): List<VideoItem> {
        if (io.github.shoalapps.swordfish.player.NetworkInfo.offline()) return AppGraph.actions.playlist(browseId)
        val list = browse(browseId, pages = 2).flatMap { Yti.videos(it) }.distinctBy { it.id }
        AppGraph.actions.rememberPlaylist(browseId, list)
        return list
    }

    suspend fun likedVideos() = playlist("VLLL")
    suspend fun watchLater() = playlist("VLWL")
    suspend fun history() = playlist("FEhistory")

    suspend fun subscriptions(): List<Sub> =
        browse("FEchannels", pages = 10).flatMap { Yti.channels(it) }.distinctBy { it.url }

    // -------- Actions --------

    suspend fun setSubscribed(channelId: String, subscribed: Boolean) { AppGraph.actions.submit("subscription", channelId, subscribed.toString()) }

    private suspend fun sendSetSubscribed(channelId: String, subscribed: Boolean) {
        post(if (subscribed) "subscription/subscribe" else "subscription/unsubscribe") {
            putJsonArray("channelIds") { add(channelId) }
        }
    }

    suspend fun setNotifications(channelId: String, mode: NotificationMode) { AppGraph.actions.submit("bell", channelId, mode.name) }

    private suspend fun sendSetNotifications(channelId: String, mode: NotificationMode) {
        post("notification/modify_channel_preference") {
            put("params", SubscriptionNotifications.preferenceParams(channelId, mode))
        }
    }

    /** action = "like", "dislike" or "removelike" */
    suspend fun rate(videoId: String, action: String) { AppGraph.actions.submit("rating", videoId, action) }

    private suspend fun sendRate(videoId: String, action: String) {
        post("like/$action") { putJsonObject("target") { put("videoId", videoId) } }
    }

    // -------- Comments --------

    /** Opens the signed-in comments section for a video and returns its first page. */
    private suspend fun commentsPage(videoId: String): JsonObject {
        val watch = post("next") { put("videoId", videoId) }
        return withContext(Dispatchers.Default) { findCommentsToken(watch) }
            ?.let { t -> post("next") { put("continuation", t) } }
            ?: throw IOException("Comments are turned off for this video")
    }

    private fun findCommentsToken(watch: JsonObject): String? {
        var token: String? = null
        Yti.walk(watch) { k, v ->
            if (token == null && k == "itemSectionRenderer" &&
                (v["sectionIdentifier"] as? JsonPrimitive)?.content == "comment-item-section"
            ) {
                token = Yti.continuation(v)
            }
            token != null
        }
        if (token == null) {
            // Newer layout: the comments panel continuation lives in an engagement panel.
            Yti.walk(watch) { k, v ->
                if (token == null && k == "engagementPanelSectionListRenderer" &&
                    v.toString().contains("comment", true)
                ) token = Yti.continuation(v)
                token != null
            }
        }
        return token
    }

    suspend fun postComment(videoId: String, text: String) { AppGraph.actions.submit("comment", videoId, text) }

    private suspend fun sendPostComment(videoId: String, text: String) {
        val page = commentsPage(videoId)
        val params = Yti.findStringPublic(page, "createCommentParams")
            ?: throw IOException("YouTube didn't allow commenting here")
        post("comment/create_comment") {
            put("commentText", text)
            put("createCommentParams", params)
        }
    }

    suspend fun replyToComment(videoId: String, commentId: String, text: String) { AppGraph.actions.submit("reply", videoId, text, commentId) }

    private suspend fun sendReplyToComment(videoId: String, commentId: String, text: String) {
        val page = commentsPage(videoId)
        // Find the smallest object mentioning this comment that carries reply parameters.
        val params = withContext(Dispatchers.Default) {
            var best: JsonObject? = null
            var bestSize = Int.MAX_VALUE
            Yti.walk(page) { _, v ->
                val str = v.toString()
                if (str.length < bestSize && str.contains(commentId) && str.contains("createReplyParams")) {
                    best = v; bestSize = str.length
                }
                false
            }
            best?.let { Yti.findStringPublic(it, "createReplyParams") }
        }
            ?: throw IOException("Replying is only available on the first page of comments")
        post("comment/create_comment_reply") {
            put("replyText", text)
            put("createReplyParams", params)
        }
    }

    /**
     * Likes, dislikes or clears your rating on a comment. YouTube only hands out the action tokens
     * with the signed-in comments page, so this works for comments on its first page.
     */
    suspend fun rateComment(videoId: String, commentId: String, from: io.github.shoalapps.swordfish.player.Rating, to: io.github.shoalapps.swordfish.player.Rating) { AppGraph.actions.submit("commentRating", videoId + "|" + commentId, to.name, from.name) }

    private suspend fun sendRateComment(videoId: String, commentId: String, from: io.github.shoalapps.swordfish.player.Rating, to: io.github.shoalapps.swordfish.player.Rating) {
        val page = commentsPage(videoId)
        val tokens = withContext(Dispatchers.Default) { Yti.commentActions(page, commentId) }
        val kind = when (to) {
            io.github.shoalapps.swordfish.player.Rating.LIKE -> "like"
            io.github.shoalapps.swordfish.player.Rating.DISLIKE -> "dislike"
            io.github.shoalapps.swordfish.player.Rating.NONE -> if (from == io.github.shoalapps.swordfish.player.Rating.DISLIKE) "undislike" else "unlike"
        }
        val token = tokens[kind] ?: throw IOException("YouTube only allows rating comments near the top of the list")
        post("comment/perform_comment_action") { putJsonArray("actions") { add(JsonPrimitive(token)) } }
    }

    suspend fun saveToWatchLater(videoId: String) { AppGraph.actions.submit("watchLater", videoId) }

    private suspend fun sendSaveToWatchLater(videoId: String) {
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

/** One channel you can use YouTube as. [pageId] is null for the account's main channel. */
data class ChannelChoice(
    val name: String,
    val handle: String?,
    val avatar: String?,
    val pageId: String?,
    val selected: Boolean,
)

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

    /** Calls [visit] on every object anywhere in [el], including objects directly inside arrays. */
    fun everyObject(el: JsonElement?, visit: (JsonObject) -> Unit) {
        when (el) {
            is JsonObject -> { visit(el); el.values.forEach { everyObject(it, visit) } }
            is JsonArray -> el.forEach { everyObject(it, visit) }
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

    fun findStringPublic(el: JsonElement?, key: String) = findString(el, key)

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

    /**
     * Finds channel entries in the switcher response. YouTube has shipped several shapes
     * (accountItem, accountItemRenderer, view models), so anything with an account name counts.
     */
    fun accountItems(root: JsonElement): List<ChannelChoice> {
        val out = mutableListOf<ChannelChoice>()
        walk(root) { k, v ->
            val name = text(v["accountName"]) ?: text(v["title"]).takeIf { k.contains("account", true) }
            val looksLikeAccount = name != null && (
                k.contains("accountItem", true) || v.containsKey("accountPhoto") ||
                    v.containsKey("serviceEndpoint") && v.containsKey("accountName")
                )
            if (looksLikeAccount) {
                val disabled = (v["isDisabled"] as? JsonPrimitive)?.content == "true"
                val hasChannel = (v["hasChannel"] as? JsonPrimitive)?.content != "false"
                if (!disabled && hasChannel) {
                    out += ChannelChoice(
                        name = name!!,
                        handle = text(v["channelHandle"]) ?: text(v["accountByline"]),
                        avatar = thumb(v["accountPhoto"]) ?: thumb(v["thumbnail"]),
                        pageId = findString(v, "pageId"),
                        selected = (v["isSelected"] as? JsonPrimitive)?.content == "true",
                    )
                }
                true
            } else false
        }
        return out.distinctBy { it.pageId ?: "main:${it.name}" }
    }

    /**
     * The like/unlike/dislike/undislike action tokens for one comment, from either of YouTube's
     * comment layouts: the newer one (a commentViewModel pointing at a toolbar entity) or the older
     * commentRenderer with toggle buttons.
     */
    fun commentActions(root: JsonElement, commentId: String): Map<String, String> {
        val out = mutableMapOf<String, String>()
        fun actionIn(el: JsonElement?): String? =
            findFirst(el, "performCommentActionEndpoint")?.get("action").str
        // Newer layout.
        var surfaceKey: String? = null
        everyObject(root) { v -> if (surfaceKey == null && v["commentId"].str == commentId) surfaceKey = v["toolbarSurfaceKey"].str }
        surfaceKey?.let { key ->
            var surface: JsonObject? = null
            everyObject(root) { v -> if (surface == null && v["entityKey"].str == key) surface = v }
            surface?.let { s ->
                for ((name, kind) in listOf("likeCommand" to "like", "unlikeCommand" to "unlike", "dislikeCommand" to "dislike", "undislikeCommand" to "undislike")) {
                    findFirst(s, name)?.let(::actionIn)?.let { out[kind] = it }
                }
            }
        }
        if (out.isNotEmpty()) return out
        // Older layout.
        var renderer: JsonObject? = null
        walk(root) { k, v ->
            if (renderer == null && k == "commentRenderer" && v["commentId"].str == commentId) renderer = v
            renderer != null
        }
        renderer?.let { r ->
            findFirst(r, "likeButton")?.let { b ->
                actionIn(b["toggleButtonRenderer"].obj?.get("defaultServiceEndpoint"))?.let { out["like"] = it }
                actionIn(b["toggleButtonRenderer"].obj?.get("toggledServiceEndpoint"))?.let { out["unlike"] = it }
            }
            findFirst(r, "dislikeButton")?.let { b ->
                actionIn(b["toggleButtonRenderer"].obj?.get("defaultServiceEndpoint"))?.let { out["dislike"] = it }
                actionIn(b["toggleButtonRenderer"].obj?.get("toggledServiceEndpoint"))?.let { out["undislike"] = it }
            }
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
