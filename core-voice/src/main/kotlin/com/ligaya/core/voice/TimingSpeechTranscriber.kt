package com.ligaya.core.voice

import android.os.SystemClock
import android.util.Log
import com.ligaya.core.ai.PipelineLatencyLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach

/**
 * Step 51's latency instrumentation for the STT stage — wraps a real [SpeechTranscriber] without
 * changing any caller's own logic at all.
 *
 * Not a simple suspend-call wrapper like [TimingIntentProvider]/[TimingCompanionResponseProvider]:
 * [SpeechTranscriber.startListening] returns a [Flow] that can emit several partial results
 * before the one that matters for a latency figure — the first *final* event (a final [Success]
 * or a [Failure], per [TranscriptionEvent]'s own doc comment: "a session ends with exactly one of
 * a final Success or a Failure, never both"). This measures time-to-that-event, not the flow's
 * own lifetime, and logs it exactly once even if collection continues afterward.
 *
 * See [PipelineLatencyLog]'s own doc comment for why instrumentation lives here, at the
 * composition root's wiring, rather than inside the pipeline itself.
 */
class TimingSpeechTranscriber(
    private val delegate: SpeechTranscriber,
) : SpeechTranscriber {
    override fun startListening(): Flow<TranscriptionEvent> {
        val start = SystemClock.elapsedRealtime()
        var alreadyLogged = false
        return delegate.startListening().onEach { event ->
            val isFinalEvent = event is TranscriptionEvent.Failure ||
                (event is TranscriptionEvent.Success && event.isFinal)
            if (isFinalEvent && !alreadyLogged) {
                alreadyLogged = true
                Log.i(PipelineLatencyLog.TAG, "stt: ${SystemClock.elapsedRealtime() - start}ms")
            }
        }
    }
}
