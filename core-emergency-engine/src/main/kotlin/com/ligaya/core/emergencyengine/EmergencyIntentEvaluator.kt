package com.ligaya.core.emergencyengine

/**
 * The code that actually decides "Emergency confirmed?" (section 10) — Gemini never makes this
 * decision itself (section 11: "Uncertain AI output must never silently execute a high-risk
 * workflow"); it only supplies the confidence score this function evaluates.
 *
 * CONFIRM_THRESHOLD is a placeholder, not a confirmed product decision — flagged as an open
 * question in the original architecture review (no concrete threshold is specified anywhere in
 * LIGAYA_ARCHITECTURE_FINAL_VOICE.md) and implemented here as one named, adjustable constant
 * rather than a magic number, specifically so it's easy to find and change once product/UX
 * confirms a real value.
 */
object EmergencyIntentEvaluator {
    const val CONFIRM_THRESHOLD = 0.7f

    fun evaluate(intent: StructuredEmergencyIntent): EmergencyIntentDecision =
        if (intent.emergency && intent.confidence >= CONFIRM_THRESHOLD) {
            EmergencyIntentDecision.Confirmed(intent)
        } else {
            EmergencyIntentDecision.NeedsClarification(intent)
        }
}

/**
 * Drives an EmergencyStateMachine from a structured intent, per the evaluator's decision — the
 * only place a StructuredEmergencyIntent is allowed to cause a real state transition, and even
 * then only by calling the engine's own existing, guarded transition methods (section 25's
 * legality rules still apply in full; this never bypasses them). If the engine isn't in IDLE
 * (e.g. an emergency is already active), the underlying transitions simply fail harmlessly, the
 * same as calling them directly out of order always has.
 */
fun EmergencyStateMachine.submitStructuredIntent(intent: StructuredEmergencyIntent): EmergencyIntentDecision {
    val decision = EmergencyIntentEvaluator.evaluate(intent)
    if (decision is EmergencyIntentDecision.Confirmed) {
        detectEmergency()
        confirmEmergency()
        activateEmergency()
    }
    return decision
}
