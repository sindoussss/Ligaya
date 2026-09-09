package com.ligaya.core.data.engine

import com.ligaya.core.data.repository.EmergencyStateSnapshotRepository
import com.ligaya.core.emergencyengine.EmergencyCompanionState
import com.ligaya.core.emergencyengine.EmergencyServiceFlowState
import com.ligaya.core.emergencyengine.EmergencySnapshot
import com.ligaya.core.emergencyengine.EmergencyStateMachine
import com.ligaya.core.emergencyengine.FamilyAlertFlowState
import com.ligaya.core.emergencyengine.LocationFlowState
import com.ligaya.core.emergencyengine.Unified911FlowState

/**
 * Wraps core-emergency-engine's EmergencyStateMachine (Step 9) and persists its snapshot to Room
 * after every successful transition or subsystem update — LIGAYA_ARCHITECTURE_FINAL_VOICE.md
 * section 25's "never held only in memory" requirement.
 *
 * Deliberately a wrapper, not a change to EmergencyStateMachine itself: the engine stays a pure,
 * Android-independent, synchronously-testable class (Step 9's 18 unit tests are untouched by
 * this step), and persistence is a separate, composable concern layered on top of it — the same
 * separation already used throughout this codebase (e.g. RoomUserRepository wrapping UserDao).
 *
 * Only the two entry points below construct one: `start` for a brand-new episode, `restore` for
 * crash recovery. There is no public constructor, so a PersistedEmergencyStateMachine can never
 * exist without either a repository-backed initial write or a successfully recovered snapshot.
 */
class PersistedEmergencyStateMachine private constructor(
    private val engine: EmergencyStateMachine,
    private val repository: EmergencyStateSnapshotRepository,
    private val emergencyEventId: String?,
    private val nowEpochMillis: () -> Long,
) {
    val snapshot: EmergencySnapshot get() = engine.snapshot

    suspend fun detectEmergency(): Result<EmergencySnapshot> = persistIfSuccess(engine.detectEmergency())
    suspend fun confirmEmergency(): Result<EmergencySnapshot> = persistIfSuccess(engine.confirmEmergency())
    suspend fun activateEmergency(): Result<EmergencySnapshot> = persistIfSuccess(engine.activateEmergency())
    suspend fun markUserSafe(): Result<EmergencySnapshot> = persistIfSuccess(engine.markUserSafe())
    suspend fun resolveEmergency(): Result<EmergencySnapshot> = persistIfSuccess(engine.resolveEmergency())
    suspend fun closeEmergency(): Result<EmergencySnapshot> = persistIfSuccess(engine.closeEmergency())

    suspend fun updateLocationFlow(newState: LocationFlowState): Result<EmergencySnapshot> =
        persistIfSuccess(engine.updateLocationFlow(newState))

    suspend fun updateUnified911Flow(newState: Unified911FlowState): Result<EmergencySnapshot> =
        persistIfSuccess(engine.updateUnified911Flow(newState))

    suspend fun updateEmergencyServiceFlow(newState: EmergencyServiceFlowState): Result<EmergencySnapshot> =
        persistIfSuccess(engine.updateEmergencyServiceFlow(newState))

    suspend fun updateFamilyAlertFlow(newState: FamilyAlertFlowState): Result<EmergencySnapshot> =
        persistIfSuccess(engine.updateFamilyAlertFlow(newState))

    suspend fun updateEmergencyCompanion(newState: EmergencyCompanionState): Result<EmergencySnapshot> =
        persistIfSuccess(engine.updateEmergencyCompanion(newState))

    /** A rejected transition/update is never persisted — the on-disk snapshot only ever
     *  reflects states the engine actually accepted. */
    private suspend fun persistIfSuccess(result: Result<EmergencySnapshot>): Result<EmergencySnapshot> {
        result.getOrNull()?.let { snapshot ->
            repository.save(snapshot.toEntity(emergencyEventId, nowEpochMillis()))
        }
        return result
    }

    companion object {
        /** Starts a brand-new emergency episode (fresh IDLE engine) and persists that starting
         *  point immediately, so a crash even before the first real transition still recovers. */
        suspend fun start(
            repository: EmergencyStateSnapshotRepository,
            emergencyEventId: String? = null,
            nowEpochMillis: () -> Long = System::currentTimeMillis,
        ): PersistedEmergencyStateMachine {
            val machine = PersistedEmergencyStateMachine(EmergencyStateMachine(), repository, emergencyEventId, nowEpochMillis)
            repository.save(machine.snapshot.toEntity(emergencyEventId, nowEpochMillis()))
            return machine
        }

        /** Crash recovery: reconstructs the in-memory engine from the last persisted snapshot.
         *  Returns null if nothing has ever been persisted — there is no episode to recover. */
        suspend fun restore(
            repository: EmergencyStateSnapshotRepository,
            nowEpochMillis: () -> Long = System::currentTimeMillis,
        ): PersistedEmergencyStateMachine? {
            val entity = repository.getCurrent() ?: return null
            val engine = EmergencyStateMachine(initial = entity.toSnapshot())
            return PersistedEmergencyStateMachine(engine, repository, entity.emergencyEventId, nowEpochMillis)
        }
    }
}
