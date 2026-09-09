package com.ligaya.core.notifications

/**
 * Per-member, per-channel delivery state for a single NOTIFICATION_EVENT document (section 17:
 * "PENDING -> SENT -> CONFIRMED or FAILED", never collapsed into one "family notified" claim).
 * This is deliberately a separate, finer-grained type from core-emergency-engine's own
 * FamilyAlertFlowState (Step 9) — that state tracks the overall family-alert subsystem for the
 * state machine's purposes and has no CONFIRMED distinct from SENT; this one mirrors exactly the
 * four values the backend Cloud Function (Step 18, backend/functions/family-alerts.js) writes.
 */
enum class NotificationDeliveryState {
    PENDING,
    SENT,
    CONFIRMED,
    FAILED,
}
