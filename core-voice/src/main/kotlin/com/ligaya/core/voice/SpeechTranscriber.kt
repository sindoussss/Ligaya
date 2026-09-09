package com.ligaya.core.voice

import kotlinx.coroutines.flow.Flow

/**
 * Abstracts the STT engine so VoiceCaptureCoordinator's permission-gating logic is unit-testable
 * without the real Android SpeechRecognizer, which needs a live microphone and OS-level speech
 * service support this project has no way to fake in a JVM unit test. AndroidSpeechTranscriber is
 * the real implementation.
 */
fun interface SpeechTranscriber {
    fun startListening(): Flow<TranscriptionEvent>
}
