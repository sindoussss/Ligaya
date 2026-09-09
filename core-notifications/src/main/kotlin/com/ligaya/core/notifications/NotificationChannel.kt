package com.ligaya.core.notifications

/** The two channels backend/functions/family-alerts.js ever dispatches on (Steps 18/19) —
 *  mirrors the `channel` field ("push"/"sms") written to each NOTIFICATION_EVENT document. */
enum class NotificationChannel {
    PUSH,
    SMS,
}

/**
 * Section 21/25's literal named failure states — `SMS_FAILED`, `PUSH_FAILED` — made real and
 * observable (Step 28), not left implicit: before this type existed, a sender's app could only
 * ever see a generic [NotificationDeliveryState.FAILED] with no way to tell whether it was their
 * family member's push or SMS that failed, even though the backend has tracked the `channel`
 * field on every NOTIFICATION_EVENT document since Step 18.
 */
data class NotificationEventState(val channel: NotificationChannel, val status: NotificationDeliveryState) {
    val isPushFailed: Boolean get() = channel == NotificationChannel.PUSH && status == NotificationDeliveryState.FAILED
    val isSmsFailed: Boolean get() = channel == NotificationChannel.SMS && status == NotificationDeliveryState.FAILED
}
