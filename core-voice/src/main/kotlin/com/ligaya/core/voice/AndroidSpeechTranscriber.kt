package com.ligaya.core.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.Locale

/**
 * The real STT engine: Android's on-device SpeechRecognizer/RecognizerIntent — chosen over Google
 * Cloud Speech-to-Text (Issue C's open question) specifically to avoid adding yet another billed,
 * account-gated Google Cloud API on top of Places (Step 17) and Firebase Blaze (Steps 18/19):
 * on-device recognition costs nothing and needs no API key at all. The tradeoff Issue C actually
 * flagged is real — fil-PH language-pack support is inconsistent across OEMs, especially on
 * budget devices common in the Philippines — mitigated here, not ignored: if the primary language
 * tag comes back unsupported/unavailable, this retries once with the device's own default locale
 * rather than simply failing outright on a device that never had fil-PH installed.
 *
 * No background operation: SpeechRecognizer/RecognizerIntent has no API for listening while the
 * app is backgrounded or killed — Issue A's own analysis already established there is no OS
 * mechanism for that on stock Android, so this class doesn't need to separately enforce a
 * foreground-only constraint; the platform simply offers no other mode.
 *
 * This class is Step 21's own scope boundary: audio capture and transcription only. Nothing here
 * talks to Gemini or the emergency engine — that wiring is later roadmap steps.
 */
class AndroidSpeechTranscriber(
    private val context: Context,
    private val primaryLanguageTag: String = "fil-PH",
    private val fallbackLanguageTag: String = Locale.getDefault().toLanguageTag(),
) : SpeechTranscriber {

    override fun startListening(): Flow<TranscriptionEvent> = callbackFlow {
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        var triedFallback = false

        fun startWith(languageTag: String) {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
            recognizer.startListening(intent)
        }

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit

            override fun onError(error: Int) {
                if (!triedFallback && isLanguageUnavailableError(error) && fallbackLanguageTag != primaryLanguageTag) {
                    triedFallback = true
                    startWith(fallbackLanguageTag)
                    return
                }
                trySend(TranscriptionEvent.Failure(mapSpeechRecognizerError(error)))
                close()
            }

            override fun onResults(results: Bundle) {
                val text = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (text != null) {
                    trySend(TranscriptionEvent.Success(text, isFinal = true))
                }
                close()
            }

            override fun onPartialResults(partialResults: Bundle) {
                val text = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (text != null) {
                    trySend(TranscriptionEvent.Success(text, isFinal = false))
                }
            }
        })

        startWith(primaryLanguageTag)

        awaitClose {
            recognizer.stopListening()
            recognizer.destroy()
        }
    }
}
