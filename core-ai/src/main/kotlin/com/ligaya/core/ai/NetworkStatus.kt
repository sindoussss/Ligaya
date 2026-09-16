package com.ligaya.core.ai

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * Whether this device currently has a usable internet connection.
 *
 * Exists because section 21 lists "Internet unavailable" as its own failure row, distinct from a Gemini outage:
 * without a real check the UI could only guess at why a turn failed, and sections 21/23 forbid stating a cause the
 * system hasn't established. A `fun interface` so screens and tests can be driven offline and online without a
 * device.
 */
fun interface NetworkStatus {
    /** True only when the system reports a validated connection — not merely a connected-but-captive network. */
    fun isOnline(): Boolean
}

/**
 * The real check. [NetworkCapabilities.NET_CAPABILITY_VALIDATED] rather than plain INTERNET: a hotel portal or a
 * mobile connection with no data left is "connected" but cannot reach Gemini, and reporting that as online would
 * point the user at the wrong problem.
 */
class AndroidNetworkStatus(context: Context) : NetworkStatus {
    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    override fun isOnline(): Boolean {
        val manager = connectivityManager ?: return false
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
