package com.ligaya.feature.companion

sealed interface CompanionTurnResult {
    /** Ligaya answered with [text] — either passed through unchanged or with an unverified claim removed by
     *  ResponseValidator. [aloud] is whether the phone's voice engine actually said it: false means the reply
     *  exists but nothing was audible (no voice pack, a dead engine, a timed-out utterance), and the UI must
     *  show the words rather than claim she spoke. */
    data class Spoken(val text: String, val aloud: Boolean = true) : CompanionTurnResult

    /** Every part of Gemini's reply made an unverified claim, so nothing was safe to speak this
     *  turn. The loop should simply continue to the next turn — this is not an error. */
    data object ResponseBlocked : CompanionTurnResult

    /** The listening session ended without a usable final transcript (silence, a recognizer
     *  error, permission denied, ...). The loop should simply continue to the next turn. */
    data object NoSpeechCaptured : CompanionTurnResult
}
