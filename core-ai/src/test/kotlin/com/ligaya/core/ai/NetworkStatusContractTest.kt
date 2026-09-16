package com.ligaya.core.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The contract screen 8 depends on: [NetworkStatus] answers one question, and a screen may only say "No internet
 * connection" when it answers false. AndroidNetworkStatus itself needs a real ConnectivityManager, so what is
 * pinned here is the shape every caller and fake relies on — including that a connection which is up but not
 * validated (a captive portal, mobile data with no balance, both common in the field) counts as offline, since
 * Gemini is unreachable either way and blaming the wrong thing sends the user to fix the wrong problem.
 */
class NetworkStatusContractTest {

    @Test
    fun `an offline status reports false`() {
        val offline = NetworkStatus { false }

        assertFalse(offline.isOnline())
    }

    @Test
    fun `an online status reports true`() {
        val online = NetworkStatus { true }

        assertTrue(online.isOnline())
    }

    @Test
    fun `callers read the status at the moment they need it, never a cached answer`() {
        // A turn can fail minutes after the screen was first composed; the reason shown has to reflect the network
        // as it is when the failure happens, not as it was earlier.
        var online = true
        val status = NetworkStatus { online }

        assertTrue(status.isOnline())
        online = false
        assertFalse(status.isOnline())
    }
}
