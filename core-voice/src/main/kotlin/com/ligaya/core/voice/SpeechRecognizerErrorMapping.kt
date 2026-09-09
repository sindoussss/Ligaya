package com.ligaya.core.voice

import android.speech.SpeechRecognizer

/** Maps Android's numeric SpeechRecognizer error codes onto this module's own domain vocabulary,
 *  so the rest of the codebase never has to know those platform constants. */
internal fun mapSpeechRecognizerError(errorCode: Int): TranscriptionFailureReason = when (errorCode) {
    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
        TranscriptionFailureReason.NO_SPEECH_DETECTED
    SpeechRecognizer.ERROR_AUDIO -> TranscriptionFailureReason.AUDIO_ERROR
    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
        TranscriptionFailureReason.NETWORK_ERROR
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> TranscriptionFailureReason.PERMISSION_DENIED
    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
        TranscriptionFailureReason.LANGUAGE_UNAVAILABLE
    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> TranscriptionFailureReason.RECOGNIZER_BUSY
    else -> TranscriptionFailureReason.UNKNOWN
}

/**
 * Issue C's mitigation: a device without the requested language pack (common for fil-PH on
 * budget Android devices in the Philippines) reports one of these two codes rather than
 * transcribing — AndroidSpeechTranscriber uses this to decide whether to retry once with the
 * device's own default locale instead of failing outright.
 */
internal fun isLanguageUnavailableError(errorCode: Int): Boolean =
    errorCode == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ||
        errorCode == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE
