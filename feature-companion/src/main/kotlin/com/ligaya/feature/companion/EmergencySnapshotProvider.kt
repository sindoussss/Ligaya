package com.ligaya.feature.companion

import com.ligaya.core.emergencyengine.EmergencySnapshot

/** Reads the engine's current, actual persisted state fresh on every companion turn — the
 *  coordinator never caches a snapshot across turns, since a subsystem's real state (911 call
 *  succeeded, family alert failed, ...) can change at any point during a live conversation, and
 *  ResponseValidator must always check against what is actually true right now. */
fun interface EmergencySnapshotProvider {
    suspend fun currentSnapshot(): EmergencySnapshot
}
