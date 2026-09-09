package com.ligaya.core.emergencyengine

/**
 * One entry in the engine's own append-only history of every change it has accepted — section
 * 27's audit trail requirement ("every state transition and failure event"). Only a successful
 * [EmergencyStateMachine] transition or subsystem update ever produces an entry: a rejected call
 * already returns `Result.failure` and changes nothing (see `transitionMain`/`updateSubsystem`),
 * so there is nothing to audit for it. A subsystem moving to a Failed-style state is not a
 * separate audit event type of its own — it is simply one more [SubsystemStateChanged] whose
 * `to` happens to be a failure value, exactly like any other subsystem state.
 */
sealed interface AuditEvent {
    data class MainStateTransition(
        val from: EmergencyState,
        val to: EmergencyState,
    ) : AuditEvent

    data class SubsystemStateChanged(
        val subsystem: AuditedSubsystem,
        val from: String,
        val to: String,
    ) : AuditEvent
}

/** The five subsystems section 13 fans out into — see ConcurrentSubsystemStates — named here
 *  independently of their own state-enum types so a single audit entry shape can cover all five. */
enum class AuditedSubsystem {
    LOCATION,
    UNIFIED_911,
    EMERGENCY_SERVICE,
    FAMILY_ALERT,
    COMPANION,
}
