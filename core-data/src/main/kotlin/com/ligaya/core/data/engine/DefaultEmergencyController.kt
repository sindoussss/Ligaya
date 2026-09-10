package com.ligaya.core.data.engine

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.ligaya.core.data.repository.EmergencyStateSnapshotRepository
import com.ligaya.core.emergencyengine.EmergencyIntentDecision
import com.ligaya.core.emergencyengine.EmergencyIntentEvaluator
import com.ligaya.core.emergencyengine.EmergencyServiceFlowState
import com.ligaya.core.emergencyengine.EmergencySnapshot
import com.ligaya.core.emergencyengine.EmergencyState
import com.ligaya.core.emergencyengine.LocationFlowState
import com.ligaya.core.emergencyengine.StructuredEmergencyIntent
import com.ligaya.core.telephony.IntentUnified911DialAction
import com.ligaya.core.telephony.Unified911FlowCoordinator
import com.ligaya.core.telephony.Unified911FlowReporter
import com.ligaya.core.uistate.EmergencyController
import com.ligaya.core.uistate.SosResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
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
 *
 * [retryCall] (Step 41's fix) is the one exception to "touches nothing from core-ai,
 * core-backend, or core-places" above — core-telephony's Unified911FlowCoordinator has no network
 * dependency of its own (ACTION_DIAL is a local intent hand-off to the system dialer, per
 * IntentUnified911DialAction's own doc comment), so wiring it here doesn't reintroduce the thing
 * that principle was actually guarding against.
 *
 * Step 48's fix: [activate] now also fires the Unified 911 dial off automatically, once, right
 * after reaching EMERGENCY_ACTIVE — section 25's "EMERGENCY_ACTIVE fans out to five independent
 * parallel flows" made real, not just reachable via a manual Retry after an already-failed call.
 * Fire-and-forget on [controllerScope], not awaited inline: activation itself must return
 * immediately (triggerSos/submitVoiceIntent's own callers, e.g. the SOS control, cannot block on
 * a real dial-intent round trip), and the flows are independent by design (section 13) — nothing
 * about 911 dialing should gate how fast activation itself is reported.
 *
 * [reportLocationFlow]/[reportEmergencyServiceFlow] are deliberately thin passthroughs to the
 * persisted machine, not full coordinator wiring: core-location and core-places both need
 * Android/Play-Services/network specifics (FusedLocationProviderClient, a real Places API key)
 * that belong at the composition root (:app's MainActivity), not here — and core-places
 * specifically is still one of the modules this class's own "touches nothing from" principle
 * names. These two methods only ever accept an already-decided state value and persist it,
 * exactly like [Unified911FlowReporter]'s own shape — they never construct or drive a
 * core-location/core-places coordinator themselves.
 */
class DefaultEmergencyController(
    private val context: Context,
    private val repository: EmergencyStateSnapshotRepository,
) : EmergencyController {

    private val mutex = Mutex()
    private var cachedMachine: PersistedEmergencyStateMachine? = null

    /** Backs [activate]'s fire-and-forget automatic 911 dial — a SupervisorJob so one failed
     *  child (an unexpected exception, not the ordinary CallFailed Result this coordinator
     *  already handles) can never cancel a sibling or this scope itself. Never cancelled by this
     *  class itself: it's meant to outlive any single [activate] call, for the app process's
     *  whole lifetime, same as [com.ligaya.core.data.engine.EmergencyForegroundService]'s own
     *  serviceScope. */
    private val controllerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

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

    /** [Unified911FlowCoordinator] built fresh per call, not cached: it's a stateless wrapper
     *  around whatever `machine()` currently resolves to (same cached instance every time, per
     *  this class's own [machine] doc), so there is nothing to gain from holding onto a specific
     *  coordinator instance across calls. */
    override suspend fun retryCall(): Result<EmergencyState> = dialUnified911()

    /** Shared by the public, user-triggered [retryCall] and [activate]'s own automatic first
     *  attempt — exactly one place constructs the coordinator, so a future change to how it's
     *  built (e.g. a different dial action) can't silently diverge between the two call sites. */
    private suspend fun dialUnified911(): Result<EmergencyState> {
        val machine = machine()
        val coordinator = Unified911FlowCoordinator(
            dialAction = IntentUnified911DialAction(context),
            reporter = Unified911FlowReporter(machine::updateUnified911Flow),
        )
        return coordinator.dial().map { it.state }
    }

    /** Step 48's fix: a thin passthrough so :app's composition root can report a real
     *  core-location result without this class depending on core-location's own Android/Play-
     *  Services specifics — see this class's own doc comment on why. Returns
     *  `Result<EmergencySnapshot>`, not `Result<EmergencyState>` (unlike [retryCall]/[markSafe]),
     *  because this is used as a direct method-reference SAM conversion for core-location's own
     *  `LocationFlowReporter` — its shape isn't this class's to choose. */
    suspend fun reportLocationFlow(state: LocationFlowState): Result<EmergencySnapshot> =
        machine().updateLocationFlow(state)

    /** Step 48's fix: same shape and same reasoning as [reportLocationFlow], for core-places'
     *  `EmergencyServiceFlowReporter`. */
    suspend fun reportEmergencyServiceFlow(state: EmergencyServiceFlowState): Result<EmergencySnapshot> =
        machine().updateEmergencyServiceFlow(state)

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

        // Step 48's fix: fire the real 911 hand-off automatically, once, right away — not just
        // reachable later via a manual Retry tap after an already-failed call. Fire-and-forget:
        // see controllerScope's own doc comment for why this must not block activation itself.
        controllerScope.launch { dialUnified911() }

        return machine.snapshot.state
    }
}
