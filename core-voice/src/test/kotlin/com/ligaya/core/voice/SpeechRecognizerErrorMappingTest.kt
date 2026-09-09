package com.ligaya.core.voice

import android.speech.SpeechRecognizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechRecognizerErrorMappingTest {

    @Test
    fun `maps each known SpeechRecognizer error code to the expected domain reason`() {
        val expected = mapOf(
            SpeechRecognizer.ERROR_NO_MATCH to TranscriptionFailureReason.NO_SPEECH_DETECTED,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT to TranscriptionFailureReason.NO_SPEECH_DETECTED,
            SpeechRecognizer.ERROR_AUDIO to TranscriptionFailureReason.AUDIO_ERROR,
            SpeechRecognizer.ERROR_NETWORK to TranscriptionFailureReason.NETWORK_ERROR,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT to TranscriptionFailureReason.NETWORK_ERROR,
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS to TranscriptionFailureReason.PERMISSION_DENIED,
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED to TranscriptionFailureReason.LANGUAGE_UNAVAILABLE,
            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE to TranscriptionFailureReason.LANGUAGE_UNAVAILABLE,
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY to TranscriptionFailureReason.RECOGNIZER_BUSY,
        )

        for ((errorCode, reason) in expected) {
            assertEquals("error code $errorCode", reason, mapSpeechRecognizerError(errorCode))
        }
    }

    @Test
    fun `an unrecognized error code maps to UNKNOWN rather than throwing`() {
        assertEquals(TranscriptionFailureReason.UNKNOWN, mapSpeechRecognizerError(-999))
    }

    @Test
    fun `isLanguageUnavailableError is true only for the two language-related codes`() {
        assertTrue(isLanguageUnavailableError(SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED))
        assertTrue(isLanguageUnavailableError(SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE))
        assertFalse(isLanguageUnavailableError(SpeechRecognizer.ERROR_NETWORK))
        assertFalse(isLanguageUnavailableError(SpeechRecognizer.ERROR_NO_MATCH))
    }
}
