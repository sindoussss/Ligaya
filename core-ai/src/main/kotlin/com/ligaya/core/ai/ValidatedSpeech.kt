package com.ligaya.core.ai

/**
 * Text that has already passed through ResponseValidator (Step 25) and is safe to speak. The
 * `internal` constructor is the actual enforcement mechanism, not [toValidatedSpeechOrNull]'s own
 * discipline: only code inside this module can construct one directly, so core-voice's
 * SpeechOutput.speak(ValidatedSpeech) (Step 26) can never be called with arbitrary free-form
 * text — only with something that genuinely went through the validator first. This is the same
 * "make the unsafe thing unrepresentable" approach Step 24's EmergencyStatusMessage already used
 * for status announcements, extended to cover validated companion speech too.
 */
@JvmInline
value class ValidatedSpeech internal constructor(val text: String)

/** Passed/Rewritten both have safe text to speak; Blocked has none. */
fun ValidatedResponse.toValidatedSpeechOrNull(): ValidatedSpeech? = when (this) {
    is ValidatedResponse.Passed -> ValidatedSpeech(text)
    is ValidatedResponse.Rewritten -> ValidatedSpeech(rewrittenText)
    is ValidatedResponse.Blocked -> null
}
