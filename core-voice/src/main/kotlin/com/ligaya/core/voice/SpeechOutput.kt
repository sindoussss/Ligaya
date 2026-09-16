package com.ligaya.core.voice

import com.ligaya.core.ai.ValidatedSpeech

/**
 * Abstracts the TTS engine so callers are unit-testable without a real Android TextToSpeech
 * instance, which needs a live on-device engine this project has no way to fake in a JVM unit
 * test. AndroidSpeechOutput is the real implementation.
 *
 * Two speak overloads, not one taking a raw String — deliberate, not incidental. [speak] with
 * [EmergencyStatusMessage] (Step 24) is the closed set of fixed status announcements; [speak]
 * with [ValidatedSpeech] (Step 26) is free-form companion text, but only ever the kind that has
 * already survived core-ai's ResponseValidator — ValidatedSpeech's own constructor is what
 * actually prevents an unvalidated String from reaching either overload. There is no third
 * overload accepting a plain String anywhere in this interface, on purpose.
 */
interface SpeechOutput {
    suspend fun speak(message: EmergencyStatusMessage): SpeechResult
    suspend fun speak(speech: ValidatedSpeech): SpeechResult
}

/**
 * Whether anything was actually said out loud. Section 23: the app may not claim an action succeeded unless it
 * did, and "Speaking..." on screen is exactly such a claim — on a phone with no voice pack or a dead TTS engine
 * (a real case on lower-tier Android, see AndroidSpeechOutput's own doc) nothing is audible, and the UI has to
 * say so and show the reply as text instead.
 */
enum class SpeechResult {
    /** The engine reported the utterance finished. */
    SPOKEN,

    /** Nothing was heard: the engine never became ready, failed, or the utterance timed out. */
    UNAVAILABLE,
}
