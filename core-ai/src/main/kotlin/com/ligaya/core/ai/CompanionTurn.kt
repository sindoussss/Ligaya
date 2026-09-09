package com.ligaya.core.ai

/** One turn of the companion conversation (section 19's "update context" step) — the running
 *  history a fresh Gemini call is given each turn, so replies stay coherent across the loop. */
data class CompanionTurn(val speaker: Speaker, val text: String) {
    enum class Speaker { USER, LIGAYA }
}
