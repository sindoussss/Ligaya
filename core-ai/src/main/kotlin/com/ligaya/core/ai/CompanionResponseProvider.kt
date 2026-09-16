package com.ligaya.core.ai

import kotlin.coroutines.cancellation.CancellationException

/**
 * Produces Ligaya's next companion reply given the conversation so far (section 19's "update
 * context" step). GeminiCompanionResponseProvider is the real implementation; a fake lets
 * feature-companion's coordinator be tested without any real Gemini call.
 *
 * [history] is everything *prior* to this turn — it must never already include
 * [latestUserUtterance], which is passed separately specifically so callers don't have to decide
 * whether "the conversation so far" means before or after the thing being responded to.
 */
fun interface CompanionResponseProvider {
    suspend fun respond(history: List<CompanionTurn>, latestUserUtterance: String): String
}

/**
 * The real implementation: builds the companion prompt and calls Gemini for free-form text
 * (Step 26). Any failure (network error, etc.) degrades to a safe, generic supportive reply
 * rather than throwing or returning nothing — matching this codebase's established pattern
 * (GeminiIntentProvider's safeFallback, Step 22) of never letting a Gemini outage itself become
 * a silent failure mode. The fallback reply makes no claim about anything, so it always passes
 * ResponseValidator (Step 25) unchanged.
 */
class GeminiCompanionResponseProvider(
    private val textGenerator: GeminiTextGenerator,
) : CompanionResponseProvider {

    override suspend fun respond(history: List<CompanionTurn>, latestUserUtterance: String): String {
        val prompt = buildCompanionPrompt(history, latestUserUtterance)
        return try {
            textGenerator.generateText(prompt)
        } catch (e: CancellationException) {
            // A turn the user cancelled (closing the Thinking screen) ends here. Swallowing it would add the fallback
            // to the conversation as if Ligaya had replied.
            throw e
        } catch (t: Throwable) {
            SAFE_FALLBACK_RESPONSE
        }
    }

    companion object {
        const val SAFE_FALLBACK_RESPONSE = "I'm here with you. Can you tell me more about what's happening?"
    }
}
