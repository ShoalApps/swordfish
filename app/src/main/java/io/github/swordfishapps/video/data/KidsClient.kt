package io.github.swordfishapps.video.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.Description
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream

/**
 * A second way to play videos YouTube marks "made for kids" (Junior Eurovision, cartoons, nursery
 * songs). YouTube answers those with "This video is not available" to the clients NewPipeExtractor
 * uses, but still serves them to its YouTube Kids app, so this asks as that app.
 */
object KidsClient {
    /** A YouTube app to ask as. If YouTube retires a version, bump it. */
    private data class Client(val name: String, val id: String, val version: String, val sdk: Int, val os: String, val userAgent: String)

    private val clients = listOf(
        // The YouTube Kids app: full quality, but some kids videos are refused to it too.
        Client("ANDROID_KIDS", "18", "10.40.3", 34, "14", "com.google.android.apps.youtube.kids/10.40.3 (Linux; U; Android 14) gzip"),
        // The YouTube app as yt-dlp presents it (github.com/yt-dlp/yt-dlp, public domain): plays
        // nearly everything, but without a proof-of-origin token only the 360p stream with sound.
        Client("ANDROID", "3", "21.26.364", 30, "11", "com.google.android.youtube/21.26.364 (Linux; U; Android 11) gzip"),
    )
    private val json = Json { ignoreUnknownKeys = true }

    /** True for the error YouTube gives made-for-kids videos. */
    fun shouldTry(e: Throwable): Boolean = generateSequence(e) { it.cause }.any {
        it is org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException && it.message.orEmpty().contains("UNPLAYABLE")
    }

    /** Tries each client in turn; the last one's error if none can play it. */
    fun stream(http: OkHttpClient, id: String, country: String = "GB", language: String = "en"): StreamInfo {
        var last: Throwable? = null
        for (c in clients) {
            runCatching { return stream(http, c, id, country, language) }.onFailure { last = it }
        }
        throw last ?: IllegalStateException("This video is not available")
    }

    private fun stream(http: OkHttpClient, c: Client, id: String, country: String, language: String): StreamInfo {
        val body = buildJsonObject {
            putJsonObject("context") {
                putJsonObject("client") {
                    put("clientName", c.name)
                    put("clientVersion", c.version)
                    put("androidSdkVersion", c.sdk)
                    put("osName", "Android")
                    put("osVersion", c.os)
                    put("userAgent", c.userAgent)
                    put("hl", language)
                    put("gl", country)
                }
            }
            put("videoId", id)
            put("contentCheckOk", true)
            put("racyCheckOk", true)
        }
        val req = Request.Builder()
            .url("https://www.youtube.com/youtubei/v1/player?prettyPrint=false")
            .header("X-YouTube-Client-Name", c.id)
            .header("X-YouTube-Client-Version", c.version)
            .header("User-Agent", c.userAgent)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        val response = http.newCall(req).execute().use { r ->
            check(r.isSuccessful) { "YouTube said ${r.code}" }
            json.parseToJsonElement(r.body!!.string()) as JsonObject
        }
        return parse(id, response).also { addDetails(http, it) }
    }

    /** Related videos for the watch page, which the Kids answer leaves out, from YouTube's web "next" endpoint. */
    val related = java.util.concurrent.ConcurrentHashMap<String, List<VideoItem>>()

    /** Fills in related videos and the upload date from YouTube's web "next" endpoint. */
    fun addDetails(http: OkHttpClient, info: StreamInfo) {
        runCatching {
            val body = buildJsonObject {
                putJsonObject("context") { putJsonObject("client") { put("clientName", "WEB"); put("clientVersion", "2.20261020.00.00"); put("hl", "en"); put("gl", "GB") } }
                put("videoId", info.id)
            }
            val req = Request.Builder().url("https://www.youtube.com/youtubei/v1/next?prettyPrint=false")
                .post(body.toString().toRequestBody("application/json".toMediaType())).build()
            val root = http.newCall(req).execute().use { json.parseToJsonElement(it.body!!.string()) }
            related[info.id] = Yti.videos(root).filter { it.id != info.id }
            Yti.findFirst(root, "dateText")?.let { Yti.text(it) }?.let { info.textualUploadDate = it }
        }
    }

    /** Turns a player response into the StreamInfo the rest of the app uses. */
    fun parse(id: String, response: JsonObject): StreamInfo {
        val status = response.obj("playabilityStatus")
        if (status.str("status") != "OK") error(status.str("reason") ?: "This video is not available")
        val details = response.obj("videoDetails")
        val streaming = response.obj("streamingData")
        val info = StreamInfo(
            ServiceList.YouTube.serviceId,
            "https://www.youtube.com/watch?v=$id",
            "https://www.youtube.com/watch?v=$id",
            if (details.bool("isLive")) StreamType.LIVE_STREAM else StreamType.VIDEO_STREAM,
            id,
            details.str("title") ?: "",
            0,
        )
        info.uploaderName = details.str("author") ?: ""
        details.str("channelId")?.let { info.uploaderUrl = "https://www.youtube.com/channel/$it" }
        info.duration = details.str("lengthSeconds")?.toLongOrNull() ?: 0
        info.viewCount = details.str("viewCount")?.toLongOrNull() ?: -1
        info.description = Description(details.str("shortDescription") ?: "", Description.Type.PLAIN_TEXT)
        info.thumbnails = (details.obj("thumbnail")["thumbnails"] as? JsonArray).orEmpty().mapNotNull { t ->
            val o = t as? JsonObject ?: return@mapNotNull null
            Image(o.str("url") ?: return@mapNotNull null, o.int("height"), o.int("width"), Image.ResolutionLevel.fromHeight(o.int("height")))
        }

        val video = mutableListOf<VideoStream>()
        val audio = mutableListOf<AudioStream>()
        for (f in (streaming["adaptiveFormats"] as? JsonArray).orEmpty()) {
            val o = f as? JsonObject ?: continue
            val url = o.str("url") ?: continue
            val mime = o.str("mimeType") ?: continue
            val itag = o.int("itag").toString()
            if (mime.startsWith("video/")) {
                val format = when { mime.contains("webm") -> MediaFormat.WEBM; else -> MediaFormat.MPEG_4 }
                // Only codecs ExoPlayer plays everywhere; AV1 needs hardware many phones lack.
                if (mime.contains("av01")) continue
                val label = o.str("qualityLabel") ?: "${minOf(o.int("width"), o.int("height"))}p"
                video += VideoStream.Builder().setId(itag).setContent(url, true).setMediaFormat(format).setIsVideoOnly(true).setResolution(label).build()
            } else if (mime.startsWith("audio/")) {
                val format = if (mime.contains("webm")) MediaFormat.WEBMA else MediaFormat.M4A
                audio += AudioStream.Builder().setId(itag).setContent(url, true).setMediaFormat(format).setAverageBitrate(o.int("averageBitrate") / 1000).build()
            }
        }
        // Streams with sound built in (the "formats" list); the only kind some clients get.
        val muxed = (streaming["formats"] as? JsonArray).orEmpty().mapNotNull { f ->
            val o = f as? JsonObject ?: return@mapNotNull null
            val url = o.str("url") ?: return@mapNotNull null
            val label = o.str("qualityLabel") ?: "${minOf(o.int("width"), o.int("height"))}p"
            VideoStream.Builder().setId(o.int("itag").toString()).setContent(url, true).setMediaFormat(MediaFormat.MPEG_4).setIsVideoOnly(false).setResolution(label).build()
        }
        // Adaptive streams need both halves; without audio, fall back to the muxed ones.
        val usable = video.isNotEmpty() && audio.isNotEmpty()
        if (!usable && muxed.isEmpty()) error("This video is not available")
        info.videoOnlyStreams = if (usable) video else emptyList()
        info.audioStreams = if (usable) audio else emptyList()
        info.videoStreams = muxed
        return info
    }

    private fun JsonObject.obj(key: String) = this[key] as? JsonObject ?: JsonObject(emptyMap())
    private fun JsonObject.str(key: String) = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
    private fun JsonObject.int(key: String) = (this[key] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0
    private fun JsonObject.bool(key: String) = (this[key] as? JsonPrimitive)?.content == "true"
}
