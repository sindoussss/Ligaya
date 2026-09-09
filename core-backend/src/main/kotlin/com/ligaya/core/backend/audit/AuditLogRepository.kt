package com.ligaya.core.backend.audit

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.ligaya.core.emergencyengine.AuditEvent
import com.ligaya.core.emergencyengine.AuditedSubsystem
import com.ligaya.core.emergencyengine.EmergencyState
import kotlinx.coroutines.tasks.await

/**
 * Persists section 27's audit trail (Step 31): an immutable, ordered log of every accepted
 * EmergencyStateMachine change (core-emergency-engine, Step 9/31), tied to one emergency event.
 *
 * Immutability is enforced at the backend, not just by convention here: backend/firestore.rules'
 * emergencyEvents/{eventId}/auditLog subcollection allows create but never update/delete — this
 * class only ever calls set() on a sequence number's own document once, matching that rule rather
 * than working around it.
 */
interface AuditLogRepository {
    suspend fun append(emergencyEventId: String, sequence: Int, event: AuditEvent): Result<Unit>
    suspend fun getOrderedAuditTrail(emergencyEventId: String): Result<List<AuditEvent>>
}

class FirestoreAuditLogRepository(
    private val firestore: FirebaseFirestore,
) : AuditLogRepository {

    private fun auditLogCollection(emergencyEventId: String) =
        firestore.collection("emergencyEvents").document(emergencyEventId).collection("auditLog")

    override suspend fun append(emergencyEventId: String, sequence: Int, event: AuditEvent): Result<Unit> =
        runCatching {
            auditLogCollection(emergencyEventId)
                .document(sequence.toString())
                .set(event.toFirestoreMap(sequence))
                .await()
            Unit
        }

    override suspend fun getOrderedAuditTrail(emergencyEventId: String): Result<List<AuditEvent>> =
        runCatching {
            auditLogCollection(emergencyEventId)
                .orderBy(FIELD_SEQUENCE, Query.Direction.ASCENDING)
                .get()
                .await()
                .documents
                .map { requireNotNull(it.data) { "audit log document ${it.id} has no data" }.toAuditEvent() }
        }
}

private const val FIELD_TYPE = "type"
private const val FIELD_SEQUENCE = "sequence"
private const val FIELD_FROM = "from"
private const val FIELD_TO = "to"
private const val FIELD_SUBSYSTEM = "subsystem"

private const val TYPE_MAIN_STATE_TRANSITION = "MAIN_STATE_TRANSITION"
private const val TYPE_SUBSYSTEM_STATE_CHANGED = "SUBSYSTEM_STATE_CHANGED"

private fun AuditEvent.toFirestoreMap(sequence: Int): Map<String, Any> = when (this) {
    is AuditEvent.MainStateTransition -> mapOf(
        FIELD_TYPE to TYPE_MAIN_STATE_TRANSITION,
        FIELD_SEQUENCE to sequence,
        FIELD_FROM to from.name,
        FIELD_TO to to.name,
    )
    is AuditEvent.SubsystemStateChanged -> mapOf(
        FIELD_TYPE to TYPE_SUBSYSTEM_STATE_CHANGED,
        FIELD_SEQUENCE to sequence,
        FIELD_SUBSYSTEM to subsystem.name,
        FIELD_FROM to from,
        FIELD_TO to to,
    )
}

/** Split out from the repository, same reasoning as parseFamilyEmergencyView (Step 20): the
 *  mapping is the part most likely to have a subtle bug, so it's unit-testable on its own. */
internal fun Map<String, Any?>.toAuditEvent(): AuditEvent = when (val type = this[FIELD_TYPE] as String) {
    TYPE_MAIN_STATE_TRANSITION -> AuditEvent.MainStateTransition(
        from = EmergencyState.valueOf(this[FIELD_FROM] as String),
        to = EmergencyState.valueOf(this[FIELD_TO] as String),
    )
    TYPE_SUBSYSTEM_STATE_CHANGED -> AuditEvent.SubsystemStateChanged(
        subsystem = AuditedSubsystem.valueOf(this[FIELD_SUBSYSTEM] as String),
        from = this[FIELD_FROM] as String,
        to = this[FIELD_TO] as String,
    )
    else -> error("Unknown audit event type: $type")
}
