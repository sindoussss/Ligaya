package com.ligaya.core.voice

import android.Manifest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.ligaya.core.permissions.AndroidPermissionChecker
import com.ligaya.core.permissions.PermissionRequestHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Step 50's own matrix: [VoiceCaptureCoordinatorInstrumentedTest] already proves the real
 * Android permission stack correctly gates RECORD_AUDIO closed when it's not granted — this is
 * its missing other half, proving the gate actually opens (control passes into the real
 * on-device SpeechRecognizer, not the synchronous PERMISSION_DENIED short-circuit) once it is.
 *
 * Deliberately does not wait for — or assert on — a terminal recognizer event
 * (Success/Failure/whatever): confirmed directly, this AVD image's on-device recognition service
 * never delivers ANY callback at all (no result, no error, nothing in logcat either), so it's
 * environment-dependent whether a real device's recognizer would resolve in under a second or
 * hang far longer — same "real transcription accuracy needs a live mic and a human" scope
 * boundary VoiceCaptureCoordinatorInstrumentedTest's own doc comment already draws, just one
 * layer more fundamental (this AVD apparently lacks a working recognition service at all, not
 * just a human to speak into it).
 *
 * What's actually deterministic regardless of the recognizer's own eventual behavior:
 * [VoiceCaptureCoordinator]'s denied-path returns a pre-built `flowOf(...)` — its first element is
 * available synchronously, no real work involved. The granted path delegates to a `callbackFlow`
 * wrapping a real system service round-trip, which cannot possibly resolve as fast as a value
 * that was already sitting in memory. So collecting with a short timeout and asserting a *timeout*
 * (not a result) is the actual proof the gate opened: if the denied short-circuit had fired
 * instead, this would return the element immediately rather than timing out.
 *
 * Runs on [Dispatchers.Main]: the real on-device SpeechRecognizer enforces main-thread
 * confinement (`SpeechRecognizer should be used only from the application's main thread`) —
 * confirmed directly, this test's own first run crashed with exactly that exception before this
 * `withContext` was added. Production code never hits this: MainActivity's own wake-word loop
 * already runs on `lifecycleScope`, which is main-thread-confined by default — this is purely
 * about how a JVM test coroutine (`runTest`'s own dispatcher, not the real main thread) needs to
 * explicitly opt into it to touch this real Android API at all.
 */
@RunWith(AndroidJUnit4::class)
class VoiceCaptureCoordinatorGrantedInstrumentedTest {

    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val alreadyRequestedHistory = object : PermissionRequestHistory {
        override fun hasRequestedBefore(permission: String) = true
        override fun markRequested(permission: String) {}
    }

    @Test
    fun withRealPermissionCheckingAndRecordAudioGrantedTheRealRecognizerIsReachedNotShortCircuited() = runTest {
        val permissionChecker = AndroidPermissionChecker(context, activity = null, history = alreadyRequestedHistory)
        val coordinator = VoiceCaptureCoordinator(AndroidSpeechTranscriber(context), permissionChecker)

        val firstEventWithinAShortWindow = withContext(Dispatchers.Main) {
            withTimeoutOrNull(SHORT_WINDOW_FAR_TOO_FAST_FOR_A_REAL_SYSTEM_SERVICE_MILLIS) {
                coordinator.startListening().first()
            }
        }

        assertNull(
            "expected no element within ${SHORT_WINDOW_FAR_TOO_FAST_FOR_A_REAL_SYSTEM_SERVICE_MILLIS}ms — " +
                "an immediate element would mean the synchronous PERMISSION_DENIED short-circuit fired " +
                "instead of the real recognizer being reached, got $firstEventWithinAShortWindow",
            firstEventWithinAShortWindow,
        )
    }

    private companion object {
        const val SHORT_WINDOW_FAR_TOO_FAST_FOR_A_REAL_SYSTEM_SERVICE_MILLIS = 500L
    }
}
