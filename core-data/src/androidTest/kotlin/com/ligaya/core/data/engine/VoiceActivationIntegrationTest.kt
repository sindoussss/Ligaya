package com.ligaya.core.data.engine

import android.app.Instrumentation
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PatternMatcher
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ligaya.core.ai.IntentProvider
import com.ligaya.core.ai.VoiceInterpretationOutcome
import com.ligaya.core.data.LigayaDatabase
import com.ligaya.core.data.repository.RoomEmergencyStateSnapshotRepository
import com.ligaya.core.emergencyengine.EmergencyIntentDecision
import com.ligaya.core.emergencyengine.EmergencyState
import com.ligaya.core.emergencyengine.IncidentType
import com.ligaya.core.emergencyengine.StructuredEmergencyIntent
import com.ligaya.core.permissions.PermissionChecker
import com.ligaya.core.permissions.PermissionState
import com.ligaya.core.uistate.SosResult
import com.ligaya.core.voice.SpeechTranscriber
import com.ligaya.core.voice.TranscriptionEvent
import com.ligaya.core.voice.VoiceActivationCoordinator
import com.ligaya.core.voice.VoiceActivationResult
import com.ligaya.core.voice.VoiceCaptureCoordinator
import com.ligaya.core.voice.VoiceEmergencyIntentReporter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers this step's own literal acceptance criteria: speaking the wake phrase reaches
 * EMERGENCY_ACTIVE with no additional tap, and the resulting state is identical to
 * DefaultEmergencyControllerTest's own SOS test — because both go through
 * DefaultEmergencyController.activate(), not two separate implementations of "activate."
 *
 * No real SpeechRecognizer or Gemini call is used here — a fake SpeechTranscriber supplies a
 * fixed final transcript and a fake IntentProvider supplies a fixed StructuredEmergencyIntent,
 * so this test is fast and deterministic while still exercising the REAL
 * VoiceActivationCoordinator (core-voice) wired to the REAL, persisted DefaultEmergencyController
 * (core-data) — the actual integration this step delivers, not just each piece in isolation.
 *
 * Step 48: same two fixes as DefaultEmergencyControllerTest's own doc comment explains in full —
 * [dialMonitor] intercepts the real automatic 911 dial [DefaultEmergencyController.activate] now
 * fires (this class's own controller.triggerSos()/submitVoiceIntent() calls route through the
 * same activate()); separately, [voiceActivationWhileAlreadyActiveReportsAlreadyInProgress] waits
 * for `hasActiveNotification()` after its own `triggerSos()` call for the same reason
 * DefaultEmergencyControllerTest's tests do — `triggerSos()` returning only means
 * `startForegroundService()` was *called*, not that `EmergencyForegroundService`'s own
 * asynchronous `onCreate()`/`startForeground()` sequence has actually run, so acting immediately
 * (here, this test's own `stopService()` a few lines later) can race that sequence and crash the
 * process with `ForegroundServiceDidNotStartInTimeException`.
 */
@RunWith(AndroidJUnit4::class)
class VoiceActivationIntegrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "voice-activation-test.db"
    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    private val uiAutomation = instrumentation.uiAutomation
    private lateinit var dialMonitor: Instrumentation.ActivityMonitor

    @Before
    fun setUp() {
        context.deleteDatabase(dbName)
        uiAutomation.grantRuntimePermission(context.packageName, "android.permission.POST_NOTIFICATIONS")

        val dialFilter = IntentFilter(Intent.ACTION_DIAL).apply {
            addDataScheme("tel")
            addDataSchemeSpecificPart("911", PatternMatcher.PATTERN_LITERAL)
        }
        dialMonitor = instrumentation.addMonitor(dialFilter, null, true)
    }

    @After
    fun tearDown(): Unit = runBlocking {
        instrumentation.removeMonitor(dialMonitor)
        stopServiceAndSettle()
        LigayaDatabase.setInstanceForTesting(null)
        context.deleteDatabase(dbName)
    }

    private fun hasActiveNotification(): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java)
        return manager.activeNotifications.any { it.id == EmergencyForegroundService.NOTIFICATION_ID }
    }

    /** Same helper and same guarantee as DefaultEmergencyControllerTest's own identical
     *  helper — see its doc comment for the full explanation. */
    private suspend fun stopServiceAndSettle() {
        context.stopService(Intent(context, EmergencyForegroundService::class.java))
        waitUntil { !EmergencyForegroundService.isRunning }
    }

    private suspend fun waitUntil(timeoutMillis: Long = 15_000, condition: () -> Boolean) {
        val start = System.currentTimeMillis()
        while (!condition()) {
            check(System.currentTimeMillis() - start < timeoutMillis) { "condition not met within ${timeoutMillis}ms" }
            delay(100)
        }
    }

    private fun grantedPermissionChecker() = object : PermissionChecker {
        override fun currentState(permission: String) = PermissionState.Granted
    }

    private fun transcriberEmitting(transcript: String) = SpeechTranscriber {
        flowOf(TranscriptionEvent.Success(transcript, isFinal = true))
    }

    private fun fixedIntentProvider(intent: StructuredEmergencyIntent) =
        IntentProvider { VoiceInterpretationOutcome.Interpreted(intent) }

    private fun coordinatorFor(
        controller: DefaultEmergencyController,
        transcript: String,
        intent: StructuredEmergencyIntent,
    ): VoiceActivationCoordinator {
        val reporter = VoiceEmergencyIntentReporter { submittedIntent ->
            when (val result = controller.submitVoiceIntent(submittedIntent)) {
                is VoiceIntentResult.Activated -> EmergencyIntentDecision.Confirmed(submittedIntent)
                is VoiceIntentResult.NeedsClarification -> EmergencyIntentDecision.NeedsClarification(submittedIntent)
                is VoiceIntentResult.AlreadyInProgress -> EmergencyIntentDecision.Confirmed(submittedIntent)
            }
        }
        return VoiceActivationCoordinator(
            captureCoordinator = VoiceCaptureCoordinator(transcriberEmitting(transcript), grantedPermissionChecker()),
            intentProvider = fixedIntentProvider(intent),
            reporter = reporter,
        )
    }

    @Test
    fun speakingTheDocsExamplePhraseReachesEmergencyActiveWithoutAnyTap() = runTest {
        val db = Room.databaseBuilder(context, LigayaDatabase::class.java, dbName).build()
        LigayaDatabase.setInstanceForTesting(db)
        val repository = RoomEmergencyStateSnapshotRepository(db.emergencyStateSnapshotDao())
        val controller = DefaultEmergencyController(context, repository)
        val fireIntent = StructuredEmergencyIntent(
            emergency = true,
            incidentType = IncidentType.FIRE,
            confidence = 0.95f,
            userContext = "May sunog.",
            requestedLocation = true,
        )
        val coordinator = coordinatorFor(controller, "Ligaya, tulong. May sunog.", fireIntent)

        val result = coordinator.listenForWakePhrase().first()

        assertTrue("expected a Decision, got $result", result is VoiceActivationResult.Decision)
        val decision = (result as VoiceActivationResult.Decision).decision
        assertTrue("expected Confirmed, got $decision", decision is EmergencyIntentDecision.Confirmed)
        waitUntil { hasActiveNotification() }
        assertTrue("expected the foreground service's persistent notification to be showing", hasActiveNotification())
        // See voiceActivationWhileAlreadyActiveReportsAlreadyInProgress's own comment for why this
        // wait is needed before this test's own stopServiceAndSettle()+db.close() below.
        waitUntil { dialMonitor.hits >= 1 }

        // Proves this step's own "identical resulting state as the Step 13 SOS test" criterion:
        // the same persisted snapshot shape, reached without ever calling triggerSos().
        assertEquals(EmergencyState.EMERGENCY_ACTIVE, repository.getCurrent()?.mainState?.let { EmergencyState.valueOf(it) })

        stopServiceAndSettle()
        db.close()
    }

    @Test
    fun aLowConfidenceReadingNeedsClarificationAndNeverActivates() = runTest {
        val db = Room.databaseBuilder(context, LigayaDatabase::class.java, dbName).build()
        LigayaDatabase.setInstanceForTesting(db)
        val repository = RoomEmergencyStateSnapshotRepository(db.emergencyStateSnapshotDao())
        val controller = DefaultEmergencyController(context, repository)
        val unclearIntent = StructuredEmergencyIntent(
            emergency = true,
            incidentType = IncidentType.OTHER,
            confidence = 0.2f,
            userContext = "Parang may mali...",
            requestedLocation = false,
        )
        val coordinator = coordinatorFor(controller, "Ligaya, parang may mali...", unclearIntent)

        val result = coordinator.listenForWakePhrase().first()

        assertTrue("expected a Decision, got $result", result is VoiceActivationResult.Decision)
        assertTrue((result as VoiceActivationResult.Decision).decision is EmergencyIntentDecision.NeedsClarification)
        assertFalse("a low-confidence reading must never start the foreground service", hasActiveNotification())

        db.close()
    }

    @Test
    fun voiceActivationWhileAlreadyActiveReportsAlreadyInProgress() = runTest {
        val db = Room.databaseBuilder(context, LigayaDatabase::class.java, dbName).build()
        LigayaDatabase.setInstanceForTesting(db)
        val repository = RoomEmergencyStateSnapshotRepository(db.emergencyStateSnapshotDao())
        val controller = DefaultEmergencyController(context, repository)

        val sosResult = controller.triggerSos()
        assertTrue(sosResult is SosResult.Activated)
        // See this class's own doc comment for the full root-cause diagnosis.
        waitUntil { hasActiveNotification() }
        // activate()'s own automatic dial (Step 48) fires on DefaultEmergencyController's own
        // controllerScope — a scope that, by design, is never cancelled by that class itself (see
        // its own doc comment: it's meant to outlive a single activate() call for the app
        // process's whole lifetime). That background coroutine still writes to this test's own
        // database (Unified911FlowReporter's state update) after triggerSos() has already
        // returned, so this test's own later stopServiceAndSettle()+db.close() can race it unless
        // something here waits for it — same class of bug as the one fixed in
        // EmergencyForegroundService.onDestroy(), just via a different, deliberately-uncancelled
        // scope. dialMonitor (installed in setUp()) is a cheap, real signal that the dial hand-off
        // — and therefore its own DB write — has actually happened.
        waitUntil { dialMonitor.hits >= 1 }

        val fireIntent = StructuredEmergencyIntent(true, IncidentType.FIRE, 0.95f, "May sunog.", true)
        val voiceResult = controller.submitVoiceIntent(fireIntent)

        assertTrue("expected AlreadyInProgress, got $voiceResult", voiceResult is VoiceIntentResult.AlreadyInProgress)
        assertEquals(EmergencyState.EMERGENCY_ACTIVE, (voiceResult as VoiceIntentResult.AlreadyInProgress).state)

        stopServiceAndSettle()
        db.close()
    }
}
