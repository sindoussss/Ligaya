package com.ligaya.core.data.engine

import com.ligaya.core.emergencyengine.EmergencyState
import com.ligaya.core.emergencyengine.StructuredEmergencyIntent

/**
 * The voice-path counterpart to core-ui-state's SosResult (Step 13) — deliberately not the same
 * type, since a voice intent has an outcome SOS can never have: the engine's own
 * EmergencyIntentEvaluator (core-emergency-engine, Step 9) may decide the input wasn't confident
 * enough to act on at all, unlike SOS which is always unconditional.
 */
sealed interface VoiceIntentResult {
    data class Activated(val state: EmergencyState) : VoiceIntentResult
    data class AlreadyInProgress(val state: EmergencyState) : VoiceIntentResult
    data class NeedsClarification(val intent: StructuredEmergencyIntent) : VoiceIntentResult
}
