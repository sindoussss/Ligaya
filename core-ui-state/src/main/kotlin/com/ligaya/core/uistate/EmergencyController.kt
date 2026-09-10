package com.ligaya.core.uistate

import com.ligaya.core.emergencyengine.EmergencySnapshot
import com.ligaya.core.emergencyengine.EmergencyState
import kotlinx.coroutines.flow.Flow

/**
 * The only way :app is allowed to trigger or read emergency state (see this module's own
 * build.gradle.kts and the root project's :checkModuleBoundaries task) — :app depends on this
 * interface, never on :core-emergency-engine directly. The concrete implementation
 * (core-data's DefaultEmergencyController) needs Android/Room/Service access this pure-Kotlin
 * module deliberately doesn't have, so it's provided by whoever composes the app (the
 * composition root), not constructed here.
 *
 * Returning EmergencyState/EmergencySnapshot directly (rather than inventing a parallel wrapper
 * type) is deliberate: re-exporting a core-emergency-engine type through this module's own API is
 * exactly what this presentation-mapping layer exists to do — the boundary rule is about the
 * module dependency edge, not about type reuse through the one sanctioned path.
 *
 * [observeSnapshot] and [markSafe] (Step 37): the Emergency Active screen needs to render live,
 * changing subsystem state and offer the persistent "I'm safe" control — neither existed on this
 * interface before, since nothing before Step 37 needed to observe state continuously rather than
 * read it once per action.
 *
 * [retryCall] (Step 41's own limitation, fixed): the Emergency Active screen's CallFailedCard
 * needs a real retry action, not a no-op — core-telephony's own Unified911FlowCoordinator doc
 * comment already documented exactly this wiring (`Unified911FlowReporter(persistedMachine::
 * updateUnified911Flow)`), it was simply never connected to a live episode anywhere in the app.
 */
interface EmergencyController {
    suspend fun triggerSos(): SosResult

    /** Marks the user safe from whatever the current emergency is — a no-op Result.failure (not
     *  a thrown exception) if no emergency is active to mark safe from; see EmergencyStateMachine
     *  (Step 9) for exactly which transitions this can legally cause. */
    suspend fun markSafe(): Result<EmergencyState>

    /** Re-attempts the Unified 911 hand-off (section 15's "Allow retry" — calling dial() again,
     *  per Unified911FlowCoordinator's own doc comment, is the entire retry mechanism; there is no
     *  separate concept of a retry beyond that). A no-op Result.failure, same shape as [markSafe],
     *  if no emergency is active. */
    suspend fun retryCall(): Result<EmergencyState>

    /** The live emergency snapshot, or null when no episode has ever started (Step 30's
     *  PersistedEmergencyStateMachine.restore() returning null — never emitted again once an
     *  episode has started at least once, per that class's own "never held only in memory" — but
     *  a genuinely fresh install/process has nothing to emit until the first triggerSos()). */
    fun observeSnapshot(): Flow<EmergencySnapshot?>
}

/**
 * section 9 Path B: pressing SOS is always available and always succeeds at reaching (or
 * confirming it has already reached) EMERGENCY_ACTIVE — AlreadyInProgress is not a failure,
 * just an honest answer to "the user pressed SOS again while one was already running."
 */
sealed interface SosResult {
    data class Activated(val state: EmergencyState) : SosResult
    data class AlreadyInProgress(val state: EmergencyState) : SosResult
}
