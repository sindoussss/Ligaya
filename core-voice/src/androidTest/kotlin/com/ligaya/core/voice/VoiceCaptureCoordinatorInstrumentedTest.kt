package com.ligaya.core.voice

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ligaya.core.permissions.AndroidPermissionChecker
import com.ligaya.core.permissions.PermissionRequestHistory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Proves the RECORD_AUDIO gate against the real Android permission-checking stack (core-
 * permissions' AndroidPermissionChecker over the real Context), not just a fake PermissionChecker
 * as in VoiceCaptureCoordinatorTest — same pattern as core-location's own instrumented test
 * (Step 15). A freshly installed test APK has never had RECORD_AUDIO granted or requested, so
 * this exercises the denied path from that natural starting point without ever needing
 * grantRuntimePermission.
 *
 * What this deliberately does NOT and cannot prove: real transcription accuracy. That needs a
 * live microphone and a human speaking real Taglish phrases — this step's own acceptance
 * criterion calls that out as a manual spot check, not something an automated test can perform.
 */
@RunWith(AndroidJUnit4::class)
class VoiceCaptureCoordinatorInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val neverRequestedHistory = object : PermissionRequestHistory {
        override fun hasRequestedBefore(permission: String) = false
        override fun markRequested(permission: String) {}
    }

    @Test
    fun withRealPermissionCheckingAndRecordAudioNotGrantedReportsPermissionDenied() = runTest {
        val permissionChecker = AndroidPermissionChecker(context, activity = null, history = neverRequestedHistory)
        val transcriber = SpeechTranscriber {
            error("must not be called: RECORD_AUDIO is not granted")
        }
        val coordinator = VoiceCaptureCoordinator(transcriber, permissionChecker)

        val firstEvent = coordinator.startListening().first()

        assertEquals(TranscriptionEvent.Failure(TranscriptionFailureReason.PERMISSION_DENIED), firstEvent)
    }
}
