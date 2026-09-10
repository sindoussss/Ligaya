package com.ligaya.core.ai

import android.os.SystemClock
import android.util.Log

/**
 * Step 51's own explicit "Tests/checks: Instrumented battery/latency logging" requirement, made
 * real: every voice-pipeline stage this app has (STT, Gemini intent interpretation, Gemini
 * companion response, TTS) logs its own real wall-clock duration through this single shared
 * utility, so a field tester can capture the full STT→Gemini→TTS round trip the roadmap's own
 * acceptance criteria asks for by grepping logcat for [TAG] during a real device session, rather
 * than needing a stopwatch or any new UI. One shared tag/format here, not one per caller, keeps
 * every stage's own log line trivially greppable and directly comparable.
 */
object PipelineLatencyLog {
    const val TAG = "LigayaLatency"

    suspend fun <T> measure(stage: String, block: suspend () -> T): T {
        val start = SystemClock.elapsedRealtime()
        try {
            return block()
        } finally {
            Log.i(TAG, "$stage: ${SystemClock.elapsedRealtime() - start}ms")
        }
    }
}
