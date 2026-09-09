package com.ligaya.feature.companion

sealed interface CompanionTurnResult {
    /** Ligaya spoke [text] back to the user — either passed through unchanged or with an
     *  unverified claim removed by ResponseValidator. */
    data class Spoken(val text: String) : CompanionTurnResult

    /** Every part of Gemini's reply made an unverified claim, so nothing was safe to speak this
     *  turn. The loop should simply continue to the next turn — this is not an error. */
    data object ResponseBlocked : CompanionTurnResult

    /** The listening session ended without a usable final transcript (silence, a recognizer
     *  error, permission denied, ...). The loop should simply continue to the next turn. */
    data object NoSpeechCaptured : CompanionTurnResult
}
