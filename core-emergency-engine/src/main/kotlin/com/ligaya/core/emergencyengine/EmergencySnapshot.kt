package com.ligaya.core.emergencyengine

/** The engine's full state at a point in time — the shape a later step persists (never held
 *  only in memory, section 25) and restores from on crash recovery. */
data class EmergencySnapshot(
    val state: EmergencyState = EmergencyState.IDLE,
    val subsystems: ConcurrentSubsystemStates = ConcurrentSubsystemStates(),
)
