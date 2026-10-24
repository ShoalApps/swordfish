package com.aaronateataco.materialtube.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class NetworkInfoTest {
    private fun d(msg: String?, e: Throwable? = null, vpn: Boolean = false, offline: Boolean = false) =
        NetworkInfo.describe(msg, e, vpn, offline)

    @Test fun botCheckMentionsTheVpnWhenOnOne() {
        val msg = "Sign in to confirm you're not a bot"
        assertTrue(d(msg).contains("prove it isn't a bot"))
        assertTrue(d(msg, vpn = true).contains("VPN"))
        assertTrue(d("LOGIN_REQUIRED").contains("bot"))
    }

    @Test fun botCheckIsFoundInTheCauseChain() {
        val e = RuntimeException("wrapper", IOException("Sign in to confirm you’re not a bot"))
        assertTrue(d(e.message, e).contains("bot"))
    }

    @Test fun offlineBeatsEverythingButTheBotCheck() {
        assertEquals("You're offline. Check your connection and try again.", d("Unable to resolve host", UnknownHostException()))
        assertEquals("You're offline. Check your connection and try again.", d("whatever", offline = true))
    }

    @Test fun timeoutsAndGenericNetworkErrors() {
        assertTrue(d("timeout", SocketTimeoutException()).contains("too long"))
        assertEquals("Couldn't reach YouTube. Try again.", d("Connection reset", IOException("Connection reset")))
    }

    @Test fun videoProblems() {
        assertEquals("This video is age-restricted.", d("This age-restricted video..."))
        assertEquals("This video is private.", d("This video is private"))
        assertEquals("This video isn't available.", d("Video unavailable"))
        assertEquals("This video is for channel members only.", d("Join this channel to get access to members-only content"))
    }

    @Test fun fallbacks() {
        assertEquals("Something went wrong. Try again.", d(null))
        assertEquals("Some new message", d("Some new message"))
    }
}
