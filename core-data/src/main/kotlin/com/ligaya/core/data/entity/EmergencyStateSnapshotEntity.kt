package com.ligaya.core.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local-only entity (no backend equivalent) backing the deterministic safety engine's persisted
 * state machine — LIGAYA_ARCHITECTURE_FINAL_VOICE.md section 25: emergency state must be
 * "never held only in memory". Written by core.data.engine.PersistedEmergencyStateMachine
 * (Step 10), which wraps core-emergency-engine's EmergencyStateMachine (Step 9) and persists
 * its full EmergencySnapshot here after every successful transition or subsystem update.
 *
 * One column per field of EmergencySnapshot/ConcurrentSubsystemStates, stored as plain enum/
 * sealed-type names (matching the existing pattern for e.g. NotificationEventEntity.state)
 * rather than one JSON blob — self-documenting and queryable, and avoids adding a serialization
 * dependency to the otherwise dependency-free core-emergency-engine module.
 *
 * Singleton row: at most one current emergency is tracked on-device at a time, so the primary
 * key is fixed rather than generated.
 */
@Entity(tableName = "emergency_state_snapshot")
data class EmergencyStateSnapshotEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val emergencyEventId: String?,
    val mainState: String,
    val locationFlowState: String,
    val unified911FlowState: String,
    val emergencyServiceFlowState: String,
    val familyAlertFlowState: String,
    val companionState: String,
    val lastUpdatedAtEpochMillis: Long,
) {
    companion object {
        const val SINGLETON_ID = 0
    }
}
