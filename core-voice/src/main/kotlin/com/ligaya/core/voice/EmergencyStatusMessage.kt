package com.ligaya.core.voice

/**
 * The complete, closed set of things Ligaya's voice is allowed to say in this step (section 5's
 * voice-output requirement). Deliberately an enum, not a String parameter anywhere in this
 * module's public API: [SpeechOutput.speak] can only ever be called with one of these values, so
 * there is no code path in this step by which free-form text — Gemini-generated or otherwise —
 * could reach the TTS engine. Free-form companion speech is explicitly out of scope here (gated
 * behind Step 25's Response Validator, section 23's anti-false-claims gate), and this type is
 * what makes that a structural guarantee rather than a convention someone could forget.
 *
 * Deliberately small and conservative: only genuinely-known, backend-confirmed facts about the
 * user's own action are spoken — never a subsystem outcome (911 connected, family notified, a
 * location was shared) that section 23's "never claim an action succeeded unless confirmed"
 * rule would make dangerous to say prematurely. Reporting subsystem outcomes aloud is future
 * scope for whichever step actually threads that confirmed state through to speech.
 */
enum class EmergencyStatusMessage(val spokenText: String) {
    EMERGENCY_ACTIVATED("Emergency activated."),
    USER_MARKED_SAFE("You have been marked as safe."),
    EMERGENCY_RESOLVED("Emergency resolved."),

    // Step 49's own acceptance criteria ("AI-dependent parts show explicit unavailable states,
    // no silent hang"): VoiceActivationCoordinator already distinguishes AiUnavailable from a
    // real Decision (Step 27) specifically so a caller could eventually surface it — until now,
    // no caller did, so a spoken wake phrase with the AI stack unreachable produced no feedback
    // at all. This is still a closed, conservative, pre-approved status about the voice
    // subsystem's own availability, not a claim about any emergency action's outcome, so it fits
    // this enum's own stated restriction rather than working around it.
    VOICE_AI_UNAVAILABLE("Voice assistant is unavailable right now. Please use the SOS button instead."),
}
