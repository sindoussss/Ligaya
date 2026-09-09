package com.ligaya.core.emergencyengine

/**
 * The deterministic safety engine's core state machine (LIGAYA_ARCHITECTURE_FINAL_VOICE.md
 * section 25 / section 12: "the deterministic engine owns the emergency state machine").
 *
 * This class only tracks and validates state — it never performs an emergency action itself
 * (dialing, sending an alert, etc.). Per section 11 ("AI understands. Code decides and
 * executes."), and per this step's own scope, that execution logic belongs to each subsystem's
 * own later implementation step; this engine is the single authority those steps report into.
 *
 * Resolution rule (section 20/25, stated explicitly there): USER_MARKED_SAFE is always reachable
 * from EMERGENCY_ACTIVE regardless of whether any subsystem has completed — markUserSafe below
 * never inspects subsystem state, only the main state, by design. The same is true one step
 * further down the chain: resolveEmergency (USER_MARKED_SAFE -> EMERGENCY_RESOLVED) and
 * closeEmergency (EMERGENCY_RESOLVED -> CLOSED) are both plain transitionMain calls too, so a
 * subsystem stuck FAILED or still PENDING can never block the user from reaching a fully closed
 * episode — section 25's diagram draws exactly one resolution path, not one gated on subsystem
 * success.
 *
 * Terminology (section 20, stated explicitly there): EMERGENCY_RESOLVED means "the user marked
 * themselves safe," full stop — it must never be surfaced (here or in any later UI/copy layer)
 * as "help arrived" or similar, since this engine has no way to know that and never claims it.
 *
 * Section 21's Gemini-failure table (Step 27): "Deterministic emergency engine — still works" is
 * not a runtime check this class performs, it is a build-time guarantee — this whole module has
 * zero dependency on core-ai (see this module's own build.gradle.kts), so there is no code path
 * by which a Gemini/AI outage could reach anything in this file at all.
 *

 * Subsystem-update window: a subsystem can only be in a non-Pending state while an emergency
 * exists, and section 25 shows subsystems continuing to report (e.g. a family alert still in
 * flight) even after the user has marked themselves safe. So subsystem updates are allowed for
 * EMERGENCY_ACTIVE, USER_MARKED_SAFE, and EMERGENCY_RESOLVED — not before (nothing has started
 * yet) and not once CLOSED (the session is over). This window is this class's own considered
 * interpretation of the diagram, not something the diagram states in as many words — flagged
 * here rather than assumed silently.
 */
class EmergencyStateMachine(initial: EmergencySnapshot = EmergencySnapshot()) {

    var snapshot: EmergencySnapshot = initial
        private set

    private val mutableAuditTrail = mutableListOf<AuditEvent>()

    /** Section 27's audit trail: every entry this instance has itself accepted, in order, since
     *  construction — see [AuditEvent]'s own doc for why only successful calls appear here. Does
     *  NOT include whatever produced [initial] (e.g. a restored snapshot's own prior history) —
     *  this engine only tracks what happens from this instance's construction forward. */
    val auditTrail: List<AuditEvent> get() = mutableAuditTrail

    fun detectEmergency(): Result<EmergencySnapshot> = transitionMain(EmergencyState.EMERGENCY_DETECTED)

    fun confirmEmergency(): Result<EmergencySnapshot> = transitionMain(EmergencyState.EMERGENCY_CONFIRMED)

    fun activateEmergency(): Result<EmergencySnapshot> = transitionMain(EmergencyState.EMERGENCY_ACTIVE)

    /** Always available from EMERGENCY_ACTIVE — see class doc's resolution rule. */
    fun markUserSafe(): Result<EmergencySnapshot> = transitionMain(EmergencyState.USER_MARKED_SAFE)

    /** Always available from USER_MARKED_SAFE regardless of subsystem outcome — see class doc's
     *  resolution rule. Represents only "the user marked themselves safe," never "help arrived." */
    fun resolveEmergency(): Result<EmergencySnapshot> = transitionMain(EmergencyState.EMERGENCY_RESOLVED)

    fun closeEmergency(): Result<EmergencySnapshot> = transitionMain(EmergencyState.CLOSED)

    fun updateLocationFlow(newState: LocationFlowState): Result<EmergencySnapshot> =
        updateSubsystem(AuditedSubsystem.LOCATION, snapshot.subsystems.location.toString(), newState.toString()) {
            it.copy(location = newState)
        }

    fun updateUnified911Flow(newState: Unified911FlowState): Result<EmergencySnapshot> =
        updateSubsystem(AuditedSubsystem.UNIFIED_911, snapshot.subsystems.unified911.toString(), newState.toString()) {
            it.copy(unified911 = newState)
        }

    fun updateEmergencyServiceFlow(newState: EmergencyServiceFlowState): Result<EmergencySnapshot> =
        updateSubsystem(AuditedSubsystem.EMERGENCY_SERVICE, snapshot.subsystems.emergencyService.toString(), newState.toString()) {
            it.copy(emergencyService = newState)
        }

    fun updateFamilyAlertFlow(newState: FamilyAlertFlowState): Result<EmergencySnapshot> =
        updateSubsystem(AuditedSubsystem.FAMILY_ALERT, snapshot.subsystems.familyAlert.toString(), newState.toString()) {
            it.copy(familyAlert = newState)
        }

    fun updateEmergencyCompanion(newState: EmergencyCompanionState): Result<EmergencySnapshot> =
        updateSubsystem(AuditedSubsystem.COMPANION, snapshot.subsystems.companion.toString(), newState.toString()) {
            it.copy(companion = newState)
        }

    private fun transitionMain(target: EmergencyState): Result<EmergencySnapshot> {
        val current = snapshot.state
        if (!isLegalMainTransition(current, target)) {
            return Result.failure(IllegalEmergencyTransitionException(current, target))
        }
        snapshot = snapshot.copy(state = target)
        mutableAuditTrail += AuditEvent.MainStateTransition(current, target)
        return Result.success(snapshot)
    }

    private fun updateSubsystem(
        subsystem: AuditedSubsystem,
        from: String,
        to: String,
        update: (ConcurrentSubsystemStates) -> ConcurrentSubsystemStates,
    ): Result<EmergencySnapshot> {
        if (snapshot.state !in SUBSYSTEM_UPDATE_ALLOWED_STATES) {
            return Result.failure(SubsystemUpdateNotAllowedException(snapshot.state))
        }
        snapshot = snapshot.copy(subsystems = update(snapshot.subsystems))
        mutableAuditTrail += AuditEvent.SubsystemStateChanged(subsystem, from, to)
        return Result.success(snapshot)
    }

    private fun isLegalMainTransition(from: EmergencyState, to: EmergencyState): Boolean =
        LEGAL_MAIN_TRANSITIONS[from] == to

    companion object {
        private val LEGAL_MAIN_TRANSITIONS: Map<EmergencyState, EmergencyState> = mapOf(
            EmergencyState.IDLE to EmergencyState.EMERGENCY_DETECTED,
            EmergencyState.EMERGENCY_DETECTED to EmergencyState.EMERGENCY_CONFIRMED,
            EmergencyState.EMERGENCY_CONFIRMED to EmergencyState.EMERGENCY_ACTIVE,
            EmergencyState.EMERGENCY_ACTIVE to EmergencyState.USER_MARKED_SAFE,
            EmergencyState.USER_MARKED_SAFE to EmergencyState.EMERGENCY_RESOLVED,
            EmergencyState.EMERGENCY_RESOLVED to EmergencyState.CLOSED,
            // EmergencyState.CLOSED intentionally has no entry: terminal.
        )

        private val SUBSYSTEM_UPDATE_ALLOWED_STATES: Set<EmergencyState> = setOf(
            EmergencyState.EMERGENCY_ACTIVE,
            EmergencyState.USER_MARKED_SAFE,
            EmergencyState.EMERGENCY_RESOLVED,
        )
    }
}
