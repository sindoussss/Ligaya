package com.ligaya.core.location

/**
 * A resolved device location — either a current GPS fix or the last-known fallback (section 14).
 * Deliberately just the two coordinates: no consumer of this step needs accuracy or a timestamp
 * yet, and core-emergency-engine's own LocationFlowState (Step 9) carries no payload at all — its
 * job is only to say whether a fix was obtained, not what it was. This type exists purely so
 * LocationFlowCoordinator's own branching logic (current, else last-known, else unavailable) has
 * something concrete to test against without touching android.location.Location, whose stub
 * accessors throw when exercised in a plain JVM unit test.
 */
data class GeoCoordinates(val latitude: Double, val longitude: Double)
