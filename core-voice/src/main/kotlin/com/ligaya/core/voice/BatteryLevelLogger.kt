package com.ligaya.core.voice

import android.content.Context
import android.os.BatteryManager
import android.util.Log
import com.ligaya.core.ai.PipelineLatencyLog
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Step 51's own "documented battery drain rate" requirement, made measurable: samples the real
 * battery percentage at a fixed interval for as long as it's collected, logging each sample
 * through the same greppable tag every pipeline-latency log line uses ([PipelineLatencyLog.TAG])
 * — a field tester running a fixed continuous-listening session can read the drain rate straight
 * off two or more logcat samples (the percentage delta over the real wall-clock time between
 * them) without any new UI or manual stopwatch, and correlate a battery dip against nearby
 * latency lines in the exact same log stream.
 *
 * A cold [BatteryManager] lookup per sample, not a cached field: this class is expected to live
 * for a whole field-test session (potentially hours), and re-querying the system service each
 * time is negligible next to that, while caching it risks an unnecessary stale reference across
 * whatever process-level changes might happen in a long-running session.
 */
class BatteryLevelLogger(private val context: Context) {
    fun samples(intervalMillis: Long = DEFAULT_INTERVAL_MILLIS): Flow<Int> = flow {
        while (true) {
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val level = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            Log.i(PipelineLatencyLog.TAG, "battery: $level%")
            emit(level)
            delay(intervalMillis)
        }
    }

    companion object {
        const val DEFAULT_INTERVAL_MILLIS = 5 * 60 * 1000L
    }
}
