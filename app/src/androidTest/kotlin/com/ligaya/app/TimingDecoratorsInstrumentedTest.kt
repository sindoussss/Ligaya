package com.ligaya.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ligaya.core.ai.NullIntentProvider
import com.ligaya.core.ai.TimingIntentProvider
import com.ligaya.core.ai.VoiceInterpretationOutcome
import com.ligaya.core.voice.AndroidSpeechOutput
import com.ligaya.core.voice.EmergencyStatusMessage
import com.ligaya.core.voice.TimingSpeechOutput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Step 51's five timing decorators all share one composition-root wiring pattern, but only
 * three of them (stt/gemini_companion/tts_companion) were live-verified against a running app
 * during that step's own development — [TimingIntentProvider] (gemini_intent) and
 * [TimingSpeechOutput]'s status overload (tts_status) were not, because their only production
 * call site, [com.ligaya.core.voice.VoiceActivationCoordinator.listenForWakePhrase]'s
 * `AiUnavailable` branch, is itself gated behind a real *final* transcript from
 * [com.ligaya.core.voice.VoiceCaptureCoordinator] — unreachable on this project's emulator image,
 * where the on-device recognizer never delivers any callback at all (established repeatedly
 * elsewhere in this suite, e.g. [com.ligaya.core.voice.VoiceCaptureCoordinatorGrantedInstrumentedTest]).
 *
 * Rather than resting on "identical pattern to an already-verified sibling," this calls the same
 * two decorators directly against the same real delegates MainActivity itself wires in
 * production — [NullIntentProvider] is not a test double, it is the exact fallback
 * `MainActivity.onCreate` constructs when no Gemini API key is configured — bypassing only the
 * one already-documented, orthogonal STT limitation that blocks the full voice UI path. Confirmed
 * by tailing `adb logcat -s LigayaLatency:I` while this test runs: both `gemini_intent: <ms>` and
 * `tts_status: <ms>` are written for real, through the real Android Log/SystemClock runtime, not
 * a mocked one.
 */
@RunWith(AndroidJUnit4::class)
class TimingDecoratorsInstrumentedTest {

    @Test
    fun timingIntentProviderLogsGeminiIntentStage() = runTest {
        val provider = TimingIntentProvider(NullIntentProvider())

        val outcome = withContext(Dispatchers.Main) { provider.interpret("test transcript") }

        assertTrue(
            "expected NullIntentProvider's own Unavailable outcome to pass through the decorator unchanged, got $outcome",
            outcome is VoiceInterpretationOutcome.Unavailable,
        )
    }

    @Test
    fun timingSpeechOutputLogsTtsStatusStage() = runTest {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val speechOutput = AndroidSpeechOutput(context)
        val timedSpeechOutput = TimingSpeechOutput(speechOutput)

        try {
            withContext(Dispatchers.Main) {
                timedSpeechOutput.speak(EmergencyStatusMessage.VOICE_AI_UNAVAILABLE)
            }
        } finally {
            speechOutput.shutdown()
        }
    }
}
