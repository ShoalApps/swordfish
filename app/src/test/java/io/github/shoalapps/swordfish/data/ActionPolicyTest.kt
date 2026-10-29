package io.github.shoalapps.swordfish.data

import org.junit.Assert.*
import org.junit.Test

class ActionPolicyTest {
    private fun action(kind: String = "rating", target: String = "video", value: String = "like", account: String = "a", state: String = "pending") =
        OfflineAction(account = account, kind = kind, target = target, value = value, state = state)

    @Test fun latestDesiredStateWinsWithoutChangingOtherAccounts() {
        val first = action()
        val other = action(account = "b")
        val last = action(value = "removelike")
        assertEquals(listOf(other, last), ActionPolicy.add(listOf(first, other), last))
    }

    @Test fun anInFlightActionIsNeverLostWhenTheUserChangesTheirMind() {
        val sending = action(state = "sending")
        val last = action(value = "dislike")
        assertEquals(listOf(sending, last), ActionPolicy.add(listOf(sending), last))
    }

    @Test fun newDesiredStateSupersedesAFailedOne() {
        val last = action(value = "removelike")
        assertEquals(listOf(last), ActionPolicy.add(listOf(action(state = "failed")), last))
    }

    @Test fun commentsAreDistinctEvenWhenTheirTextMatches() {
        val first = action(kind = "comment")
        val second = action(kind = "comment")
        assertEquals(listOf(first, second), ActionPolicy.add(listOf(first), second))
    }

    @Test fun interruptedCommentsNeedReviewWhileRatingsCanBeRetried() {
        val comment = action(kind = "comment", state = "sending")
        val rating = action(state = "sending")
        val recovered = ActionPolicy.recover(listOf(comment, rating))
        assertEquals("review", recovered[0].state)
        assertEquals("pending", recovered[1].state)
        assertEquals(comment.id, recovered[0].id)
    }

    @Test fun bellAndSubscriptionRemainSeparateAndOrdered() {
        val subscribe = action(kind = "subscription")
        val bell = action(kind = "bell")
        assertEquals(listOf(subscribe, bell), ActionPolicy.add(listOf(subscribe), bell))
    }
}
