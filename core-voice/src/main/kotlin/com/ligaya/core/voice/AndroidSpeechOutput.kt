package com.ligaya.core.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.ligaya.core.ai.ValidatedSpeech
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume

/**
 * The real TTS engine: Android's on-device TextToSpeech — chosen over Google Cloud
 * Text-to-Speech (Issue C's open question, resolved here the same way Step 21 resolved its STT
 * half) specifically to avoid adding yet another billed, account-gated Google Cloud API: this
 * app already has enough of those (Places, Firebase Blaze, Gemini). On-device synthesis costs
 * nothing and needs no API key.
 *
 * The known tradeoff Issue C actually flagged — Tagalog/fil-PH voice quality on-device is
 * inconsistent or absent, so "Ligaya's voice" may default to an English voice reading Tagalog
 * phonetically — is accepted here, not hidden: this class requests `fil-PH` and falls back to
 * the engine's default voice if that's not supported, but does not attempt to work around a
 * missing voice pack (there is nothing to fall back to that would sound better on-device).
 */
class AndroidSpeechOutput(context: Context) : SpeechOutput {

    private val ready = CompletableDeferred<Boolean>()
    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        ready.complete(status == TextToSpeech.SUCCESS)
    }

    override suspend fun speak(message: EmergencyStatusMessage) = speakText(message.spokenText)

    override suspend fun speak(speech: ValidatedSpeech) = speakText(speech.text)

    private suspend fun speakText(text: String) {
        if (!ready.await()) return

        val locale = Locale.forLanguageTag("fil-PH")
        if (tts.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE) {
            tts.language = locale
        }
        // Otherwise: leave the engine's own current/default language as-is, per this class's own
        // doc comment — there is no better on-device fallback for Tagalog specifically.

        val utteranceId = UUID.randomUUID().toString()
        suspendCancellableCoroutine<Unit> { continuation ->
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) {
                    if (continuation.isActive) continuation.resume(Unit)
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    if (continuation.isActive) continuation.resume(Unit)
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    if (continuation.isActive) continuation.resume(Unit)
                }
            })
            tts.speak(text, TextToSpeech.QUEUE_ADD, null, utteranceId)
        }
    }

    /** Releases the underlying engine. Not tied to any lifecycle here — whoever constructs this
     *  owns calling it when the voice feature is no longer needed. */
    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}
