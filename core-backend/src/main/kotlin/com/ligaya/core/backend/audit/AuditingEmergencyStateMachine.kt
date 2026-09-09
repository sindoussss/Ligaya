package com.ligaya.core.backend.audit

import com.ligaya.core.emergencyengine.EmergencyCompanionState
import com.ligaya.core.emergencyengine.EmergencyServiceFlowState
import com.ligaya.core.emergencyengine.EmergencySnapshot
import com.ligaya.core.emergencyengine.EmergencyStateMachine
import com.ligaya.core.emergencyengine.FamilyAlertFlowState
import com.ligaya.core.emergencyengine.LocationFlowState
import com.ligaya.core.emergencyengine.Unified911FlowState

/**
 * Wraps core-emergency-engine's EmergencyStateMachine (Step 9) and, after every successful
 * transition or subsystem update, appends the newly-produced audit entries to an
 * [AuditLogRepository] — section 27's audit trail (Step 31) made concrete against a real backend.
 *
 * Deliberately a decorator around the plain EmergencyStateMachine, not around core-data's
 * PersistedEmergencyStateMachine: that class's own doc comment says it "deliberately touches
 * nothing from core-ai, core-backend, or core-places" — a boundary from an earlier step this
 * class has no reason to cross. Wrapping EmergencyStateMachine directly keeps this decorator's
 * only real job — auditing — the only thing it does; local persistence stays core-data's own
 * separate concern, unaffected by this class either way.
 *
 * "Newly-produced" matters: the engine's own `auditTrail` only ever grows, so after each call
 * this class appends exactly the entries beyond what it has already synced — never the whole
 * list again — keeping the write pattern genuinely append-only rather than re-writing
 * already-persisted entries every time. A failed append is not retried inline (this class isn't
 * where backend retry policy belongs) but also doesn't advance the synced cursor, so the next
 * call naturally retries it before moving on to anything newer.
 */
class AuditingEmergencyStateMachine(
    private val engine: EmergencyStateMachine,
    private val repository: AuditLogRepository,
    private val emergencyEventId: String,
) {
    val snapshot: EmergencySnapshot get() = engine.snapshot

    private var syncedCount = 0

    suspend fun detectEmergency(): Result<EmergencySnapshot> = syncNewAuditEntries(engine.detectEmergency())
    suspend fun confirmEmergency(): Result<EmergencySnapshot> = syncNewAuditEntries(engine.confirmEmergency())
    suspend fun activateEmergency(): Result<EmergencySnapshot> = syncNewAuditEntries(engine.activateEmergency())
    suspend fun markUserSafe(): Result<EmergencySnapshot> = syncNewAuditEntries(engine.markUserSafe())
    suspend fun resolveEmergency(): Result<EmergencySnapshot> = syncNewAuditEntries(engine.resolveEmergency())
    suspend fun closeEmergency(): Result<EmergencySnapshot> = syncNewAuditEntries(engine.closeEmergency())

    suspend fun updateLocationFlow(newState: LocationFlowState): Result<EmergencySnapshot> =
        syncNewAuditEntries(engine.updateLocationFlow(newState))

    suspend fun updateUnified911Flow(newState: Unified911FlowState): Result<EmergencySnapshot> =
        syncNewAuditEntries(engine.updateUnified911Flow(newState))

    suspend fun updateEmergencyServiceFlow(newState: EmergencyServiceFlowState): Result<EmergencySnapshot> =
        syncNewAuditEntries(engine.updateEmergencyServiceFlow(newState))

    suspend fun updateFamilyAlertFlow(newState: FamilyAlertFlowState): Result<EmergencySnapshot> =
        syncNewAuditEntries(engine.updateFamilyAlertFlow(newState))

    suspend fun updateEmergencyCompanion(newState: EmergencyCompanionState): Result<EmergencySnapshot> =
        syncNewAuditEntries(engine.updateEmergencyCompanion(newState))

    /** A rejected transition/update never grows the engine's own audit trail (see
     *  EmergencyStateMachine), so this loop is naturally a no-op for those calls — mirroring
     *  PersistedEmergencyStateMachine's own "only persist on success" behavior one layer up. */
    private suspend fun syncNewAuditEntries(result: Result<EmergencySnapshot>): Result<EmergencySnapshot> {
        val trail = engine.auditTrail
        while (syncedCount < trail.size) {
            val appendResult = repository.append(emergencyEventId, syncedCount, trail[syncedCount])
            if (appendResult.isFailure) break
            syncedCount++
        }
        return result
    }
}
