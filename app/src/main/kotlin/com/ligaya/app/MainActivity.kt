package com.ligaya.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.ligaya.app.navigation.LigayaNavHost
import com.ligaya.core.ai.CompanionResponseProvider
import com.ligaya.core.ai.GeminiCompanionResponseProvider
import com.ligaya.core.ai.HttpGeminiTextGenerator
import com.ligaya.core.data.LigayaDatabase
import com.ligaya.core.data.engine.DefaultEmergencyController
import com.ligaya.core.data.repository.RoomEmergencyStateSnapshotRepository
import com.ligaya.core.permissions.AndroidPermissionChecker
import com.ligaya.core.permissions.SharedPrefsPermissionRequestHistory
import com.ligaya.core.voice.AndroidSpeechOutput
import com.ligaya.core.voice.AndroidSpeechTranscriber
import com.ligaya.core.voice.VoiceCaptureCoordinator
import com.ligaya.feature.companion.EmergencyCompanionCoordinator
import com.ligaya.feature.companion.EmergencySnapshotProvider
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

/**
 * Step 13: the one place in :app that touches :core-data — constructing the real
 * EmergencyController. Everything downstream of this point (LigayaNavHost, every screen) only
 * ever sees it as core-ui-state's EmergencyController interface.
 *
 * Also the one place that assembles a real [EmergencyCompanionCoordinator] (closing the gap
 * Step 38/39 each documented and deferred): real on-device STT ([AndroidSpeechTranscriber]) and
 * TTS ([AndroidSpeechOutput]) — neither needs any API key — gated by a real
 * [AndroidPermissionChecker], and a real Gemini-backed [CompanionResponseProvider] when
 * `BuildConfig.GEMINI_API_KEY` is configured (see app/build.gradle.kts), otherwise a safe canned
 * reply so the companion loop still runs end-to-end (just without live Gemini output) rather than
 * being unusable without a key.
 */
class MainActivity : ComponentActivity() {
    private lateinit var speechOutput: AndroidSpeechOutput

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val database = LigayaDatabase.getInstance(applicationContext)
        val emergencyController = DefaultEmergencyController(
            context = applicationContext,
            repository = RoomEmergencyStateSnapshotRepository(database.emergencyStateSnapshotDao()),
        )

        // Not requested here: nothing in the current UI has a "start listening" control yet
        // (EmergencyCompanionScreen's own Step 39 spec is transcript + text fallback only, no mic
        // button) — there's no real moment yet to ask with context, and PermissionRequester must
        // be registered before the Activity is STARTED, which also makes an eager, contextless
        // request here fight the system permission dialog for the foreground window (confirmed
        // directly: it broke ComposeTestRule's activity lifecycle in NavigationRouteReachabilityTest,
        // not just a hypothetical concern). VoiceCaptureCoordinator already degrades to a
        // PERMISSION_DENIED failure on its own when this permission isn't granted, so
        // AndroidPermissionChecker below is enough to gate correctly right now; requesting it for
        // real is deferred to whichever future step adds a real "start listening" UI control.
        val permissionHistory = SharedPrefsPermissionRequestHistory(applicationContext)
        val permissionChecker = AndroidPermissionChecker(applicationContext, this, permissionHistory)

        speechOutput = AndroidSpeechOutput(applicationContext)
        val captureCoordinator = VoiceCaptureCoordinator(
            AndroidSpeechTranscriber(applicationContext),
            permissionChecker,
        )
        val responseProvider: CompanionResponseProvider = BuildConfig.GEMINI_API_KEY
            .takeIf { it.isNotBlank() }
            ?.let { key -> GeminiCompanionResponseProvider(HttpGeminiTextGenerator(key)) }
            ?: CompanionResponseProvider { _, _ -> GeminiCompanionResponseProvider.SAFE_FALLBACK_RESPONSE }
        val snapshotProvider = EmergencySnapshotProvider {
            emergencyController.observeSnapshot().filterNotNull().first()
        }
        val companionCoordinator = EmergencyCompanionCoordinator(
            captureCoordinator,
            responseProvider,
            speechOutput,
            snapshotProvider,
        )

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    LigayaNavHost(
                        emergencyController = emergencyController,
                        companionCoordinator = companionCoordinator,
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        speechOutput.shutdown()
        super.onDestroy()
    }
}
