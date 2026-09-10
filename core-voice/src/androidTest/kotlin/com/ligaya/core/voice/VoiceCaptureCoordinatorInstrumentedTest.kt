package com.ligaya.core.voice

import android.Manifest
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ligaya.core.permissions.AndroidPermissionChecker
import com.ligaya.core.permissions.PermissionRequestHistory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
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
 *
 * Step 50: same "already granted by another test in this run" self-skip as
 * LocationFlowCoordinatorInstrumentedTest — VoiceCaptureCoordinatorGrantedInstrumentedTest's own
 * GrantPermissionRule grants RECORD_AUDIO for the rest of this shared instrumentation process
 * (confirmed directly: without this check, running both classes together made this test fail with
 * "must not be called: RECORD_AUDIO is not granted" from the fake transcriber, since the
 * permission genuinely was granted by then).
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
        assumeTrue(
            "RECORD_AUDIO was already granted by another test earlier in this instrumentation " +
                "run, and can't be safely revoked mid-run — skipping rather than asserting a " +
                "'not granted' precondition that no longer holds.",
            context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_DENIED,
        )

        val permissionChecker = AndroidPermissionChecker(context, activity = null, history = neverRequestedHistory)
        val transcriber = SpeechTranscriber {
            error("must not be called: RECORD_AUDIO is not granted")
        }
        val coordinator = VoiceCaptureCoordinator(transcriber, permissionChecker)

        val firstEvent = coordinator.startListening().first()

        assertEquals(TranscriptionEvent.Failure(TranscriptionFailureReason.PERMISSION_DENIED), firstEvent)
    }
}
