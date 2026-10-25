package io.github.swordfishapps.video.player

import androidx.compose.runtime.mutableStateMapOf
import io.github.swordfishapps.video.AppGraph
import io.github.swordfishapps.video.data.Comment

/** Your likes and dislikes on comments this session, shared by the watch page and Shorts. */
object CommentRatings {
    private val ratings = mutableStateMapOf<String, Rating>()

    operator fun get(commentId: String): Rating = ratings[commentId] ?: Rating.NONE

    /**
     * Tapping the active thumb again clears it. The change shows straight away; signed in, it is
     * sent to YouTube and undone if that fails. Returns a message to show, or null.
     */
    suspend fun rate(videoId: String, c: Comment, target: Rating): String? {
        val from = get(c.id)
        val to = if (from == target) Rating.NONE else target
        ratings[c.id] = to
        if (!AppGraph.store.signedIn) return "Sign in to save comment likes to YouTube"
        return runCatching { AppGraph.account.rateComment(videoId, c.id, from, to) }.exceptionOrNull()?.let { e ->
            ratings[c.id] = from
            e.message ?: "Couldn't rate that comment"
        }
    }
}
