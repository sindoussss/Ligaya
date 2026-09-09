package com.ligaya.core.ai

import com.ligaya.core.emergencyengine.EmergencySnapshot
import com.ligaya.core.emergencyengine.FamilyAlertFlowState
import com.ligaya.core.emergencyengine.Unified911FlowState

/**
 * The anti-false-claims gate implied by section 19/23, made an explicit, testable component per
 * Issue J: no Gemini-generated companion text reaches TTS without first being checked against
 * the engine's own actual, verified state — never trusted on the model's word alone, since a
 * prompt instruction alone is not a reliable enough guardrail for a safety-critical "no
 * exceptions" rule (section 23's own words).
 *
 * Works sentence-by-sentence, not word-by-word: a claim is a whole assertion ("911 has been
 * contacted"), and stripping only the flagged words out of a sentence risks leaving a grammatically
 * mangled or still-misleading fragment behind. Dropping the whole sentence is simple, safe, and
 * exactly as testable as partial rewriting, without that risk. Sentence splitting here is a
 * simple punctuation-based heuristic, not full NLP sentence-boundary detection — sufficient for
 * short, declarative companion replies, not a claim to handle arbitrary prose correctly.
 *
 * "Help arrived" (and its variants) is never verifiable by this engine at all — no subsystem
 * state models a rescuer's physical arrival — so that claim is always blocked, matching Step
 * 14's own terminology rule ("never 'help arrived' — only 'user marked safe'").
 */
object ResponseValidator {

    fun validate(text: String, snapshot: EmergencySnapshot): ValidatedResponse {
        val sentences = splitIntoSentences(text)
        if (sentences.isEmpty()) return ValidatedResponse.Passed(text)

        val blockedClaims = mutableListOf<String>()
        val keptSentences = sentences.filter { sentence ->
            val violatedRule = CLAIM_RULES.firstOrNull { rule ->
                rule.patterns.any { it.containsMatchIn(sentence) } && !rule.isVerified(snapshot)
            }
            if (violatedRule != null) {
                blockedClaims += violatedRule.description
                false
            } else {
                true
            }
        }

        return when {
            blockedClaims.isEmpty() -> ValidatedResponse.Passed(text)
            keptSentences.isEmpty() -> ValidatedResponse.Blocked(text, blockedClaims)
            else -> ValidatedResponse.Rewritten(text, keptSentences.joinToString(" "), blockedClaims)
        }
    }

    private fun splitIntoSentences(text: String): List<String> =
        text.split(Regex("(?<=[.!?])\\s+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    private class ClaimRule(
        val description: String,
        val patterns: List<Regex>,
        val isVerified: (EmergencySnapshot) -> Boolean,
    )

    private val CLAIM_RULES: List<ClaimRule> = listOf(
        ClaimRule(
            description = "help arrived",
            patterns = listOf(
                Regex("help (has )?arrived", RegexOption.IGNORE_CASE),
                Regex("rescuers? (have|has|is|are) (arrived|here)", RegexOption.IGNORE_CASE),
                Regex("they('re| are) here now", RegexOption.IGNORE_CASE),
            ),
            // Never verifiable: no engine state models physical arrival at all.
            isVerified = { false },
        ),
        ClaimRule(
            description = "911 has been contacted",
            patterns = listOf(
                Regex("911 (has been |was )?(contacted|called|reached)", RegexOption.IGNORE_CASE),
                Regex("(i('ve| have)|we('ve| have)) called 911", RegexOption.IGNORE_CASE),
            ),
            isVerified = { snapshot -> snapshot.subsystems.unified911 == Unified911FlowState.Succeeded },
        ),
        ClaimRule(
            description = "help is on the way",
            patterns = listOf(
                Regex("help is (on (the|its) way|coming)", RegexOption.IGNORE_CASE),
                Regex("(police|fire(fighters)?|an? ambulance) (is|are) (on (the|its) way|coming)", RegexOption.IGNORE_CASE),
            ),
            // "Help is coming" is what 911 dispatch actually means here — tied to the same
            // subsystem as the "911 contacted" claim, not a separate, looser standard.
            isVerified = { snapshot -> snapshot.subsystems.unified911 == Unified911FlowState.Succeeded },
        ),
        ClaimRule(
            description = "family notified",
            patterns = listOf(
                Regex("(your |the )?family (has been |is |was )?(notified|alerted|informed)", RegexOption.IGNORE_CASE),
                Regex("(your |the )?family knows", RegexOption.IGNORE_CASE),
            ),
            isVerified = { snapshot -> snapshot.subsystems.familyAlert == FamilyAlertFlowState.Succeeded },
        ),
    )
}
