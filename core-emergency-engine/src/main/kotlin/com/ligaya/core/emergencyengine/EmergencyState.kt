package com.ligaya.core.emergencyengine

/**
 * The main emergency state machine (LIGAYA_ARCHITECTURE_FINAL_VOICE.md section 25). States are
 * deterministic and persisted (persistence itself is a later step — see core-data's
 * EmergencyStateSnapshotEntity) — "never held only in memory."
 *
 * Legal transitions (see EmergencyStateMachine): IDLE -> EMERGENCY_DETECTED ->
 * EMERGENCY_CONFIRMED -> EMERGENCY_ACTIVE -> USER_MARKED_SAFE -> EMERGENCY_RESOLVED -> CLOSED.
 * CLOSED is terminal for this engine instance — starting a new emergency after one has closed
 * is an application-layer concern (a new instance/episode), not something this class does via a
 * transition, matching section 25's diagram literally ("CLOSED --> [*]").
 *
 * Step 28's checklist — section 25's own "failure-to-subsystem mapping" list, and section 21's
 * fuller "all other dependencies" table, cross-referenced to where each one is actually
 * implemented and tested. This module cannot depend on any of those modules itself (it has zero
 * dependencies by design — see this module's own build.gradle.kts), so this index exists here as
 * the one central, cross-cutting reference, while every checkmark below is proven by a real
 * FailureStateChecklistTest living in its own module, not by this comment's say-so:
 *
 * - EMERGENCY_SERVICE_LOOKUP_FAILED -> core-places' EmergencyServiceFlowState.LookupFailed
 *   (Step 17; core-places/.../FailureStateChecklistTest.kt)
 * - SMS_FAILED -> core-notifications' NotificationEventState.isSmsFailed (Step 28;
 *   core-notifications/.../FailureStateChecklistTest.kt)
 * - PUSH_FAILED -> core-notifications' NotificationEventState.isPushFailed (Step 28;
 *   core-notifications/.../FailureStateChecklistTest.kt)
 * - CALL_FAILED -> core-telephony's Unified911FlowState.CallFailed (Step 16;
 *   core-telephony/.../FailureStateChecklistTest.kt)
 * - GEMINI_FAILED -> core-ai's VoiceInterpretationOutcome.Unavailable (Step 27;
 *   core-ai's own GeminiIntentProviderTest and GeminiFailureIsolationTest)
 * - GPS / "location unavailable" -> core-location's LocationFlowState.Unavailable (Step 15;
 *   core-location/.../FailureStateChecklistTest.kt)
 * - "Emergency-service phone number missing, do not invent one" -> core-places'
 *   PlaceDetails.phoneNumber = null (Step 17; core-places/.../FailureStateChecklistTest.kt)
 */
enum class EmergencyState {
    IDLE,
    EMERGENCY_DETECTED,
    EMERGENCY_CONFIRMED,
    EMERGENCY_ACTIVE,
    USER_MARKED_SAFE,
    EMERGENCY_RESOLVED,
    CLOSED,
}
