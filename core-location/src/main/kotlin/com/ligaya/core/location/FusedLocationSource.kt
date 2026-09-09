package com.ligaya.core.location

import android.annotation.SuppressLint
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Priority
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The real device-location adapter, over Play Services' FusedLocationProviderClient.
 *
 * Callers must already have confirmed ACCESS_FINE_LOCATION is granted — that check belongs to
 * LocationFlowCoordinator, not here (this codebase consistently separates permission-checking
 * from permission-consuming code; see core-permissions' own module doc). @SuppressLint documents
 * that this class trusts its caller rather than re-checking.
 *
 * A failed or timed-out request is treated as "no fix," not an error: section 14's flow only
 * cares whether a real fix was obtained, and IllegalStateException/SecurityException from the
 * underlying client are exactly the kind of "location unavailable" outcome that flow already
 * has a well-defined state for.
 *
 * Both the request's own `durationMillis` AND an outer `withTimeoutOrNull` bound how long this
 * waits for a fix — found via a real, reproduced hang (Step 30): with no location provider
 * actually available (e.g. airplane mode, no cached fix), an unbounded
 * `getCurrentLocation(request, null)` call simply never completed at all, which section 22's own
 * "graceful degradation, never a silent hang" rule explicitly forbids. The request-level bound
 * alone wasn't trusted to be sufficient once that was observed directly — the outer timeout is
 * what actually guarantees this function returns.
 */
class FusedLocationSource(
    private val client: FusedLocationProviderClient,
    private val requestDurationMillis: Long = DEFAULT_REQUEST_DURATION_MILLIS,
) : LocationSource {

    @SuppressLint("MissingPermission")
    override suspend fun getCurrentLocation(): GeoCoordinates? {
        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setDurationMillis(requestDurationMillis)
            .build()
        // The outer bound is deliberately looser than the request's own durationMillis, not
        // equal to it: the normal path is the request completing on its own just under that
        // duration, and this only needs to catch the case where even that internal bound somehow
        // doesn't fire — which is exactly what was observed happening at all under airplane mode
        // with no provider available.
        val location = withTimeoutOrNull(requestDurationMillis + OUTER_TIMEOUT_BUFFER_MILLIS) {
            runCatching { client.getCurrentLocation(request, null).await() }.getOrNull()
        }
        return location?.let { GeoCoordinates(it.latitude, it.longitude) }
    }

    @SuppressLint("MissingPermission")
    override suspend fun getLastKnownLocation(): GeoCoordinates? {
        // Same reasoning and same bound as getCurrentLocation: `lastLocation` is normally a fast
        // cached lookup with nothing to wait for, but it has no documented guarantee of ever
        // completing, and this class doesn't get to assume "should be fast" is "is bounded."
        val location = withTimeoutOrNull(LAST_KNOWN_LOCATION_TIMEOUT_MILLIS) {
            runCatching { client.lastLocation.await() }.getOrNull()
        }
        return location?.let { GeoCoordinates(it.latitude, it.longitude) }
    }

    companion object {
        private const val DEFAULT_REQUEST_DURATION_MILLIS = 10_000L
        private const val OUTER_TIMEOUT_BUFFER_MILLIS = 5_000L
        private const val LAST_KNOWN_LOCATION_TIMEOUT_MILLIS = 10_000L
    }
}
