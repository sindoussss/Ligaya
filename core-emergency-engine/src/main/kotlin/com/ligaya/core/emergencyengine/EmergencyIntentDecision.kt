package com.ligaya.core.emergencyengine

/**
 * The exact two outcomes section 10's diagram describes for "Emergency confirmed?" — Yes
 * (continue to the deterministic safety engine) or No/unclear (ask the user for clarification).
 * There is deliberately no third "silently discard" outcome: the doc's diagram only ever shows
 * these two branches, and this pipeline is only reached after the user has already invoked the
 * wake word (section 5/9), so even a firm "not an emergency" reading is still something worth
 * surfacing back to the user as a clarification prompt, not silently dropping.
 */
sealed interface EmergencyIntentDecision {
    data class Confirmed(val intent: StructuredEmergencyIntent) : EmergencyIntentDecision
    data class NeedsClarification(val intent: StructuredEmergencyIntent) : EmergencyIntentDecision
}
