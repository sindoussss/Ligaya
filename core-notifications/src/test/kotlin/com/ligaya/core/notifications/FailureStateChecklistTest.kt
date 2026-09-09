package com.ligaya.core.notifications

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Step 28's checklist-driven coverage for this module's two rows of section 21's "all other
 * dependencies" table: SMS ("Send failed") and Push ("Failed"). Both are real, distinguishable,
 * testable states here — not implicit or inferred from a single generic FAILED value.
 */
class FailureStateChecklistTest {

    @Test
    fun `PUSH_FAILED -- section 21's Push row -- is real and distinguishable from SMS_FAILED`() {
        val state = NotificationEventState(NotificationChannel.PUSH, NotificationDeliveryState.FAILED)

        assertTrue(state.isPushFailed)
        assertFalse(state.isSmsFailed)
    }

    @Test
    fun `SMS_FAILED -- section 21's SMS row -- is real and distinguishable from PUSH_FAILED`() {
        val state = NotificationEventState(NotificationChannel.SMS, NotificationDeliveryState.FAILED)

        assertTrue(state.isSmsFailed)
        assertFalse(state.isPushFailed)
    }

    @Test
    fun `a non-FAILED status is never reported as either failure, regardless of channel`() {
        for (channel in NotificationChannel.values()) {
            for (status in NotificationDeliveryState.values().filter { it != NotificationDeliveryState.FAILED }) {
                val state = NotificationEventState(channel, status)
                assertFalse("$state must not be isPushFailed", state.isPushFailed)
                assertFalse("$state must not be isSmsFailed", state.isSmsFailed)
            }
        }
    }
}
