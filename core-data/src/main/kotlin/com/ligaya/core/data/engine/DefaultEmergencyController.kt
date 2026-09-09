package com.ligaya.core.data.engine

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.ligaya.core.data.repository.EmergencyStateSnapshotRepository
import com.ligaya.core.emergencyengine.EmergencyIntentDecision
import com.ligaya.core.emergencyengine.EmergencyIntentEvaluator
import com.ligaya.core.emergencyengine.EmergencySnapshot
import com.ligaya.core.emergencyengine.EmergencyState
import com.ligaya.core.emergencyengine.StructuredEmergencyIntent
import com.ligaya.core.uistate.EmergencyController
import com.ligaya.core.uistate.SosResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The concrete implementation of core-ui-state's EmergencyController — section 9 Path B, the
 * AI-independent floor. Deliberately touches nothing from core-ai, core-backend, or
 * core-places itself: [submitVoiceIntent] (Step 23) takes an already-computed
 * StructuredEmergencyIntent — a plain data value — as input, so this class still never performs
 * or requires a network call itself; whatever produced that intent (core-voice + core-ai) is the
 * only place any network dependency lives. This class's own dependency graph remains
 * core-emergency-engine (pure Kotlin) plus local Room persistence and an Android Service start.
 *
 * Restores a persisted episode if one exists (so a process restart mid-emergency still lets SOS
 * report AlreadyInProgress rather than stomping on it with a second fresh episode); otherwise
 * starts a fresh one on first use. Cached for this instance's lifetime behind a Mutex, not
 * reconstructed per call.
 */
class DefaultEmergencyController(
    private val context: Context,
    private val repository: EmergencyStateSnapshotRepository,
) : EmergencyController {

    private val mutex = Mutex()
    private var cachedMachine: PersistedEmergencyStateMachine? = null

    private suspend fun machine(): PersistedEmergencyStateMachine = mutex.withLock {
        cachedMachine ?: (PersistedEmergencyStateMachine.restore(repository)
            ?: PersistedEmergencyStateMachine.start(repository)).also { cachedMachine = it }
    }

    override suspend fun triggerSos(): SosResult {
        val machine = machine()

        if (machine.snapshot.state != EmergencyState.IDLE) {
            return SosResult.AlreadyInProgress(machine.snapshot.state)
        }

        return SosResult.Activated(activate(machine))
    }

    /** Step 37's "I'm safe" control. A freshly-started (never-activated) machine legitimately
     *  rejects this — EmergencyStateMachine.markUserSafe() only accepts EMERGENCY_ACTIVE — and
     *  that Result.failure is exactly this method's own documented "nothing to mark safe from"
     *  case, not a bug to special-case around here. */
    override suspend fun markSafe(): Result<EmergencyState> =
        machine().markUserSafe().map { it.state }

    /** Maps core-data's own entity-typed live query through the same EmergencySnapshotMapper
     *  (Step 9/30) every persistence path already uses — no second, parallel entity->snapshot
     *  conversion. */
    override fun observeSnapshot(): Flow<EmergencySnapshot?> =
        repository.observeCurrent().map { it?.toSnapshot() }

    /**
     * Step 23's wake-path entry point — section 9's requirement that voice and SOS converge on
     * the same engine intake made concrete: this calls the exact same [activate] this class's
     * own [triggerSos] calls, gated only by [EmergencyIntentEvaluator] (core-emergency-engine,
     * Step 9) deciding the intent is confident enough to act on — not a second, parallel
     * implementation of what "activating" means.
     */
    suspend fun submitVoiceIntent(intent: StructuredEmergencyIntent): VoiceIntentResult {
        val machine = machine()

        if (machine.snapshot.state != EmergencyState.IDLE) {
            return VoiceIntentResult.AlreadyInProgress(machine.snapshot.state)
        }

        return when (EmergencyIntentEvaluator.evaluate(intent)) {
            is EmergencyIntentDecision.Confirmed -> VoiceIntentResult.Activated(activate(machine))
            is EmergencyIntentDecision.NeedsClarification -> VoiceIntentResult.NeedsClarification(intent)
        }
    }

    /** The one place this class actually drives the engine to EMERGENCY_ACTIVE and starts the
     *  foreground service — shared by [triggerSos] and [submitVoiceIntent] so there is exactly
     *  one activation sequence in this class, not two that happen to agree. */
    private suspend fun activate(machine: PersistedEmergencyStateMachine): EmergencyState {
        machine.detectEmergency()
        machine.confirmEmergency()
        machine.activateEmergency()

        ContextCompat.startForegroundService(context, Intent(context, EmergencyForegroundService::class.java))

        return machine.snapshot.state
    }
}
