package com.ligaya.core.location

/**
 * Abstracts device location acquisition so LocationFlowCoordinator's branching logic (section
 * 14's diagram: current fix, else last-known, else unavailable) is unit-testable without any
 * Android runtime or emulator. FusedLocationSource is the real Play-Services-backed
 * implementation; tests use a fake.
 *
 * Both functions return null rather than throwing on "no fix available" — that is an expected,
 * named outcome in section 14's flow, not an error.
 */
interface LocationSource {
    suspend fun getCurrentLocation(): GeoCoordinates?
    suspend fun getLastKnownLocation(): GeoCoordinates?
}
