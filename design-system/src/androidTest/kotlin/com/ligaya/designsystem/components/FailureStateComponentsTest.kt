package com.ligaya.designsystem.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Step 41's own acceptance criterion: "every failure state from Step 28's checklist has a
 * corresponding named UI treatment, verified against the checklist." One test per row of that
 * checklist (see core-emergency-engine's EmergencyState.kt for the authoritative list this
 * mirrors exactly) proves the mapping directly, not by this comment's say-so:
 *
 * - EMERGENCY_SERVICE_LOOKUP_FAILED -> [LookupFailedCard] (SERVICE_NOT_FOUND)
 * - SMS_FAILED -> [DeliveryFailedCard]
 * - PUSH_FAILED -> [DeliveryFailedCard]
 * - CALL_FAILED -> [CallFailedCard]
 * - GEMINI_FAILED -> [OfflineDegradedBanner] (GEMINI_UNAVAILABLE)
 * - GPS / "location unavailable" -> [OfflineDegradedBanner] (LOCATION_UNAVAILABLE)
 * - "Emergency-service phone number missing" -> [LookupFailedCard] (PHONE_NUMBER_MISSING)
 *
 * Test names here are plain camelCase, not this codebase's usual backtick-with-spaces style
 * (see e.g. feature-companion's JVM unit tests): a backtick name containing spaces makes the
 * Compose compiler emit a `setContent { ... }` lambda class whose mangled name embeds those
 * spaces, which D8 then rejects ("Space characters in SimpleName ... not allowed prior to DEX
 * version 040") — confirmed directly, not a hypothetical concern. Every other androidTest file in
 * this codebase already uses camelCase for the same reason.
 */
@RunWith(AndroidJUnit4::class)
class FailureStateComponentsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun callFailedHasItsOwnNamedTreatment() {
        var retried = false
        composeTestRule.setContent {
            CallFailedCard(onRetry = { retried = true })
        }

        composeTestRule.onNodeWithContentDescription("911 call failed").assertIsDisplayed()
        composeTestRule.onNodeWithText("Retry").performClick()
        assert(retried) { "Retry button must invoke onRetry" }
    }

    @Test
    fun emergencyServiceLookupFailedHasItsOwnNamedTreatment() {
        composeTestRule.setContent {
            LookupFailedCard(reason = LookupFailureReason.SERVICE_NOT_FOUND)
        }

        composeTestRule.onNodeWithContentDescription(
            "Couldn't find a nearby emergency service. Please call 911 directly.",
        ).assertIsDisplayed()
    }

    @Test
    fun phoneNumberMissingHasItsOwnNamedTreatment() {
        composeTestRule.setContent {
            LookupFailedCard(reason = LookupFailureReason.PHONE_NUMBER_MISSING)
        }

        composeTestRule.onNodeWithContentDescription(
            "No phone number available for this service. Please call 911 directly.",
        ).assertIsDisplayed()
    }

    @Test
    fun smsFailedHasItsOwnNamedTreatment() {
        composeTestRule.setContent {
            DeliveryFailedCard(channelLabel = "SMS")
        }

        composeTestRule.onNodeWithContentDescription("SMS delivery failed").assertIsDisplayed()
    }

    @Test
    fun pushFailedHasItsOwnNamedTreatmentDistinguishableFromSmsFailed() {
        composeTestRule.setContent {
            DeliveryFailedCard(channelLabel = "Push")
        }

        composeTestRule.onNodeWithContentDescription("Push delivery failed").assertIsDisplayed()
    }

    @Test
    fun geminiFailedHasItsOwnNamedTreatment() {
        composeTestRule.setContent {
            OfflineDegradedBanner(reason = DegradedReason.GEMINI_UNAVAILABLE)
        }

        composeTestRule.onNodeWithContentDescription(
            "Voice assistant is temporarily unavailable. Emergency actions still work.",
        ).assertIsDisplayed()
    }

    @Test
    fun gpsLocationUnavailableHasItsOwnNamedTreatmentDistinguishableFromGeminiFailed() {
        composeTestRule.setContent {
            OfflineDegradedBanner(reason = DegradedReason.LOCATION_UNAVAILABLE)
        }

        composeTestRule.onNodeWithContentDescription(
            "Location is unavailable right now. Emergency actions still work.",
        ).assertIsDisplayed()
    }
}
