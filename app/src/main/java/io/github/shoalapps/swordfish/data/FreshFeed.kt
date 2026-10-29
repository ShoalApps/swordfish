package io.github.shoalapps.swordfish.data

import kotlin.random.Random

object FreshFeed {
    /** New results lead. Repeated server results are mixed without pretending they are new videos. */
    fun order(incoming: List<VideoItem>, previous: List<VideoItem>, seed: Int): List<VideoItem> {
        val seen = previous.map { it.id }.toSet()
        val unique = incoming.distinctBy { it.id }.shuffled(Random(seed))
        val ordered = unique.filterNot { it.id in seen } + unique.filter { it.id in seen }
        return if (ordered.size > 1 && ordered.map { it.id } == previous.map { it.id })
            ordered.drop(1) + ordered.take(1) else ordered
    }
}
