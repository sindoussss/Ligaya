package com.ligaya.core.ai

/**
 * Section 19's "approved safety/guidance layer" starts here, at the prompt itself — a
 * defense-in-depth measure, not a substitute for ResponseValidator (Step 25), which is the part
 * that actually enforces this rather than merely asking for it (Issue J: "prompting alone is not
 * a reliable enough guardrail for a safety-critical false-claims rule").
 */
internal fun buildCompanionPrompt(history: List<CompanionTurn>, latestUserUtterance: String): String {
    val historyText = if (history.isEmpty()) {
        "(no prior turns)"
    } else {
        history.joinToString("\n") { turn -> "${turn.speaker}: ${turn.text}" }
    }

    return """
        You are Ligaya, a calm, supportive voice companion staying with someone during a real
        emergency in the Philippines. They may speak English, Tagalog, or Taglish.

        Your ONLY job is to speak supportively and ask clarifying questions that help understand
        their situation. You never decide or perform any action yourself, and you never claim
        that 911 has been contacted, that help is on the way, that family has been notified, or
        that help has arrived — even if you think it's likely — because you have no way of
        actually knowing. If you don't know something has happened, say something supportive
        instead, like "I'm right here with you" or "Can you tell me more?"

        Keep replies short — one or two sentences — calm, and natural to speak aloud.

        Conversation so far:
        $historyText

        User just said: "$latestUserUtterance"

        Respond as Ligaya, speaking directly to them.
    """.trimIndent()
}
