package com.ligaya.core.voice

import android.Manifest
import com.ligaya.core.permissions.PermissionChecker
import com.ligaya.core.permissions.PermissionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Gates speech capture on RECORD_AUDIO being granted — section 19's own rule that nearly every
 * subsystem needs to reason about permission state applies here too.
 *
 * Issue A's resolution (foreground-only for this MVP scope) isn't separately enforced here:
 * Android's SpeechRecognizer/RecognizerIntent mechanism has no API for background operation in
 * the first place (see AndroidSpeechTranscriber's doc comment for why) — there's nothing beyond
 * the permission check for this class to gate.
 */
class VoiceCaptureCoordinator(
    private val transcriber: SpeechTranscriber,
    private val permissionChecker: PermissionChecker,
) {
    fun startListening(): Flow<TranscriptionEvent> {
        val hasPermission = permissionChecker.currentState(
            Manifest.permission.RECORD_AUDIO,
        ) == PermissionState.Granted
        return if (hasPermission) {
            transcriber.startListening()
        } else {
            flowOf(TranscriptionEvent.Failure(TranscriptionFailureReason.PERMISSION_DENIED))
        }
    }
}
