package com.ligaya.core.ai

/**
 * The outcome of running a Gemini-generated companion reply through ResponseValidator, before
 * anything reaches TTS (section 19/23 — Issue J's explicit anti-false-claims gate).
 */
sealed interface ValidatedResponse {
    /** Nothing unverified was claimed — the original text is safe to speak as-is. */
    data class Passed(val text: String) : ValidatedResponse

    /** One or more sentences making an unverified claim were removed; what remains (if
     *  anything coherent does) is safe to speak. [blockedClaims] names which rule(s) fired, for
     *  logging/debugging — never spoken itself. */
    data class Rewritten(val originalText: String, val rewrittenText: String, val blockedClaims: List<String>) :
        ValidatedResponse

    /** Every sentence was either a violation or nothing meaningful survived removing them —
     *  the whole reply is unsafe to speak. A caller should substitute a safe fallback line. */
    data class Blocked(val originalText: String, val blockedClaims: List<String>) : ValidatedResponse
}
