package com.ligaya.core.ai

import com.ligaya.core.emergencyengine.EmergencyServiceFlowState
import com.ligaya.core.emergencyengine.EmergencySnapshot
import com.ligaya.core.emergencyengine.FamilyAlertFlowState
import com.ligaya.core.emergencyengine.LocationFlowState
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
 *
 * Step 53's own conformance-audit finding: section 23 names eight specific never-claim-unless-
 * confirmed phrases — this originally covered only four of them ("help arrived", "911 contacted",
 * "help is on the way" as 911's own corollary, "family notified"). "Location shared" and
 * "emergency-service contacted" had no rule at all, even though [LocationFlowState] and
 * [EmergencyServiceFlowState] already carry exactly the state needed to verify them — added
 * below. "SMS delivered"/"push delivered" are folded into the existing family-notified rule
 * rather than split into their own: section 17 tracks them as per-channel detail, but at the
 * snapshot level this engine only ever exposes one aggregate [FamilyAlertFlowState.Succeeded]
 * signal for "the family alert subsystem delivered," which is the correct — and only available —
 * verification predicate for either phrasing.
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
                // Step 47's adversarial expansion: indirect/implied physical-presence phrasings
                // that never use the word "arrived" at all.
                Regex("they('re| are) (right )?outside", RegexOption.IGNORE_CASE),
                Regex("(the )?rescue team is with you( now)?", RegexOption.IGNORE_CASE),
                Regex("someone('s| is) at your door", RegexOption.IGNORE_CASE),
                Regex("they (just )?got there", RegexOption.IGNORE_CASE),
            ),
            // Never verifiable: no engine state models physical arrival at all.
            isVerified = { false },
        ),
        ClaimRule(
            description = "911 has been contacted",
            patterns = listOf(
                Regex("911 (has been |was )?(contacted|called|reached)", RegexOption.IGNORE_CASE),
                Regex("(i('ve| have)|we('ve| have)) called 911", RegexOption.IGNORE_CASE),
                // Step 47's adversarial expansion: "911" is never named, only implied.
                Regex("(i('ve| have)|we('ve| have)) reached emergency services", RegexOption.IGNORE_CASE),
                Regex("the call went through", RegexOption.IGNORE_CASE),
                Regex("dispatch has your location", RegexOption.IGNORE_CASE),
                Regex("911 picked up", RegexOption.IGNORE_CASE),
                Regex("emergency services (is |are )?aware of your situation", RegexOption.IGNORE_CASE),
            ),
            isVerified = { snapshot -> snapshot.subsystems.unified911 == Unified911FlowState.Succeeded },
        ),
        ClaimRule(
            description = "help is on the way",
            patterns = listOf(
                Regex("help is (on (the|its) way|coming)", RegexOption.IGNORE_CASE),
                Regex("(police|fire(fighters)?|an? ambulance) (is|are) (on (the|its) way|coming)", RegexOption.IGNORE_CASE),
                // Step 47's adversarial expansion: "they"/"someone"/"rescue"/"officers"/
                // "paramedics"/"emergency services"/"the ambulance", and idioms with no explicit
                // subject at all — every one of these is the same underlying claim ("a rescuer is
                // en route"), just without ever saying "help".
                Regex(
                    "(they|someone|rescue|officers?|paramedics|emergency services|(the )?ambulance)" +
                        "('re| are| is)? (on (the|their|its) way|coming|en route|heading (your|this) way|" +
                        "almost there|close by|will be there)",
                    RegexOption.IGNORE_CASE,
                ),
                Regex("someone will be there soon", RegexOption.IGNORE_CASE),
                Regex("you won'?t be alone (for )?long", RegexOption.IGNORE_CASE),
                Regex("you'?re safe now,? they'?re coming", RegexOption.IGNORE_CASE),
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
                // Step 47's adversarial expansion: "family" is never named, only implied via a
                // specific relation or "loved ones"/"emergency contacts".
                Regex("your loved ones know", RegexOption.IGNORE_CASE),
                Regex("i'?ve let your family know", RegexOption.IGNORE_CASE),
                Regex(
                    "your (mom|dad|mother|father|sister|brother|wife|husband|partner) knows",
                    RegexOption.IGNORE_CASE,
                ),
                Regex("your emergency contacts have been reached", RegexOption.IGNORE_CASE),
                // Step 53's own finding: "SMS delivered"/"push delivered" are section 23's own
                // named phrases, distinct in wording from "family notified" but verified by the
                // same (and only) available subsystem signal — see this object's own doc comment.
                Regex("(the |your )?(sms|text message) (has been |was )?(delivered|sent)", RegexOption.IGNORE_CASE),
                Regex("(the |a )?push notification (has been |was )?(delivered|sent)", RegexOption.IGNORE_CASE),
            ),
            isVerified = { snapshot -> snapshot.subsystems.familyAlert == FamilyAlertFlowState.Succeeded },
        ),
        ClaimRule(
            description = "location shared",
            patterns = listOf(
                Regex("(your |the )?location (has been |was )?shared", RegexOption.IGNORE_CASE),
                Regex("i'?ve shared your location", RegexOption.IGNORE_CASE),
                Regex("they (can|now) see your location", RegexOption.IGNORE_CASE),
                Regex("your location (has been |was )?sent", RegexOption.IGNORE_CASE),
            ),
            isVerified = { snapshot -> snapshot.subsystems.location == LocationFlowState.Succeeded },
        ),
        ClaimRule(
            description = "emergency-service contacted",
            patterns = listOf(
                // Deliberately distinct from the "911 contacted" rule above: this is section 16's
                // nearby-hospital/police/fire-station lookup, not the 911 call itself — the two
                // are independent subsystems (section 13) and neither implies the other.
                Regex("(the )?(nearest |nearby )?(hospital|police station|fire station) (has been |was )?(contacted|notified|called)", RegexOption.IGNORE_CASE),
                Regex("i'?ve (contacted|reached out to) the (nearest |nearby )?(hospital|police|fire department)", RegexOption.IGNORE_CASE),
            ),
            isVerified = { snapshot -> snapshot.subsystems.emergencyService == EmergencyServiceFlowState.Succeeded },
        ),
    )
}
