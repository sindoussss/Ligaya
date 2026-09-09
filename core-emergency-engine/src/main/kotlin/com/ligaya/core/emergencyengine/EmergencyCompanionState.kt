package com.ligaya.core.emergencyengine

/**
 * One of section 25's five concurrent subsystems (section 19). No named failure sub-state in
 * the architecture diagram — the companion "runs independently" and a Gemini/AI failure is
 * handled as a degraded-capability condition (section 21), not a state-machine failure state.
 */
sealed interface EmergencyCompanionState {
    data object Pending : EmergencyCompanionState
    data object Active : EmergencyCompanionState
}
