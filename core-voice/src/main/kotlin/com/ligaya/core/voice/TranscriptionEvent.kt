package com.ligaya.core.voice

/**
 * A transcript segment from a live listening session. Partial results arrive as
 * `Success(isFinal = false)` and may be superseded by later events; a session ends with exactly
 * one of a final `Success` or a `Failure`, never both.
 */
sealed interface TranscriptionEvent {
    data class Success(val text: String, val isFinal: Boolean) : TranscriptionEvent
    data class Failure(val reason: TranscriptionFailureReason) : TranscriptionEvent
}

enum class TranscriptionFailureReason {
    NO_SPEECH_DETECTED,
    AUDIO_ERROR,
    NETWORK_ERROR,
    PERMISSION_DENIED,
    LANGUAGE_UNAVAILABLE,
    RECOGNIZER_BUSY,
    UNKNOWN,
}
