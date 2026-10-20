package com.aaronateataco.materialtube.data

import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap

internal val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; isLenient = true }

private fun OkHttpClient.getText(url: String): String? {
    val req = Request.Builder().url(url).header("User-Agent", "Tube/0.1 (Android)").build()
    newCall(req).execute().use { resp ->
        if (!resp.isSuccessful) return null
        return resp.body?.string()
    }
}

// ---------------- SponsorBlock ----------------

@Serializable
data class SbSegment(
    val category: String,
    val actionType: String = "skip",
    val segment: List<Double>,
    @SerialName("UUID") val uuid: String = "",
) {
    val startSec get() = segment.getOrElse(0) { 0.0 }
    val endSec get() = segment.getOrElse(1) { 0.0 }
}

data class SbCategory(val key: String, val label: String, val color: Color)

object SbCategories {
    val all = listOf(
        SbCategory("sponsor", "Sponsor", Color(0xFF00D400)),
        SbCategory("selfpromo", "Self-promotion", Color(0xFFFFFF00)),
        SbCategory("interaction", "Interaction reminder", Color(0xFFCC00FF)),
        SbCategory("intro", "Intro", Color(0xFF00FFFF)),
        SbCategory("outro", "Endcards / credits", Color(0xFF0202ED)),
        SbCategory("preview", "Preview / recap", Color(0xFF008FD6)),
        SbCategory("music_offtopic", "Non-music section", Color(0xFFFF9900)),
        SbCategory("filler", "Filler tangent", Color(0xFF7300FF)),
    )

    fun of(key: String) = all.firstOrNull { it.key == key }
}

class SponsorBlock(private val http: OkHttpClient) {
    suspend fun segments(videoId: String, categories: Set<String>): List<SbSegment> =
        withContext(Dispatchers.IO) {
            if (categories.isEmpty()) return@withContext emptyList()
            runCatching {
                val url = "https://sponsor.ajay.app/api/skipSegments".toHttpUrl().newBuilder()
                    .addQueryParameter("videoID", videoId)
                    .addQueryParameter("categories", categories.joinToString(",", "[", "]") { "\"$it\"" })
                    .build().toString()
                val body = http.getText(url) ?: return@runCatching emptyList<SbSegment>()
                json.decodeFromString<List<SbSegment>>(body).filter { it.actionType == "skip" }
            }.getOrDefault(emptyList())
        }
}

// ---------------- DeArrow ----------------

@Serializable
private data class DaTitle(
    val title: String = "",
    val original: Boolean = false,
    val votes: Int = 0,
    val locked: Boolean = false,
)

@Serializable
private data class DaThumb(
    val timestamp: Double? = null,
    val original: Boolean = false,
    val votes: Int = 0,
    val locked: Boolean = false,
)

@Serializable
private data class DaBranding(
    val titles: List<DaTitle> = emptyList(),
    val thumbnails: List<DaThumb> = emptyList(),
)

data class Branding(val title: String?, val thumbnail: String?)

class DeArrow(private val http: OkHttpClient) {
    private val cache = ConcurrentHashMap<String, Branding>()
    private val gate = Semaphore(6)

    fun cached(videoId: String): Branding? = cache[videoId]

    suspend fun branding(videoId: String): Branding {
        cache[videoId]?.let { return it }
        val result = gate.withPermit {
            withContext(Dispatchers.IO) {
                runCatching {
                    val url = "https://sponsor.ajay.app/api/branding?videoID=$videoId"
                    val body = http.getText(url) ?: return@runCatching Branding(null, null)
                    val b = json.decodeFromString<DaBranding>(body)
                    val title = b.titles
                        .firstOrNull { !it.original && (it.locked || it.votes >= 0) && it.title.isNotBlank() }
                        ?.title?.let(::cleanTitle)
                    val thumb = b.thumbnails
                        .firstOrNull { !it.original && (it.locked || it.votes >= 0) && it.timestamp != null }
                        ?.let { "https://dearrow-thumb.ajay.app/api/v1/getThumbnail?videoID=$videoId&time=${it.timestamp}" }
                    Branding(title, thumb)
                }.getOrDefault(Branding(null, null))
            }
        }
        cache[videoId] = result
        return result
    }

    /** DeArrow marks words that shouldn't be auto-formatted with ">". Strip the markers. */
    private fun cleanTitle(t: String) = t.replace(Regex("""(^|\s)>(\S)"""), "$1$2").trim()
}

// ---------------- Return YouTube Dislike ----------------

@Serializable
data class RydVotes(val likes: Long = 0, val dislikes: Long = 0)

class Ryd(private val http: OkHttpClient) {
    suspend fun votes(videoId: String): RydVotes? = withContext(Dispatchers.IO) {
        runCatching {
            http.getText("https://returnyoutubedislikeapi.com/votes?videoId=$videoId")
                ?.let { json.decodeFromString<RydVotes>(it) }
        }.getOrNull()
    }
}
