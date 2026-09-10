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
import com.ligaya.core.data.LigayaDatabase
import com.ligaya.core.data.repository.RoomEmergencyStateSnapshotRepository
import com.ligaya.core.emergencyengine.EmergencyState
import com.ligaya.core.uistate.SosResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers this step's literal acceptance criteria: triggering SOS with the device in airplane
 * mode still reaches EMERGENCY_ACTIVE and starts the foreground service. Airplane mode is
 * toggled for real via a shell command (the standard technique for this in an instrumented
 * test — no app-level permission can flip it), and is always restored afterward regardless of
 * test outcome, since leaving the shared AVD in airplane mode would break later test runs.
 *
 * Step 48: [DefaultEmergencyController.activate] now fires a real, automatic Unified 911 dial
 * hand-off (ACTION_DIAL) as soon as EMERGENCY_ACTIVE is reached — so every test below that calls
 * `triggerSos()` now also triggers a real dial intent, not just [retryCallFailsWithNoActiveEmergencyAndSucceedsOnceOneIsActive]
 * (which already guarded against this for its own direct `retryCall()` calls). [dialMonitor],
 * installed for every test in [setUp], intercepts that intent so the real system Dialer app never
 * actually launches — confirmed necessary directly: without it, the real Dialer app was observed
 * launching and taking window focus during these tests (`dumpsys activity activities` showed it
 * resumed and focused on `tel:911`), the same class of window-focus-stealing bug already found
 * and fixed once before for permission dialogs.
 *
 * Separately, every test below that calls `triggerSos()` now also waits for `hasActiveNotification()`
 * before doing anything else — root-caused directly (temporary `Log.d` timestamps on both sides of
 * `EmergencyForegroundService`'s own `startForeground()` call, cross-referenced against
 * ActivityManager's logcat): `triggerSos()` returning only means `startForegroundService()` was
 * *called* — Service creation itself runs asynchronously on the main thread, so without this wait
 * a test could reach its own `stopService()` call before `EmergencyForegroundService.onCreate()`
 * had actually run. When the stop won that race, ActivityManager logged "Bringing down service
 * while still waiting for start foreground" and crashed the whole instrumentation process with
 * `ForegroundServiceDidNotStartInTimeException` — reproduced deterministically as
 * [pressingSosAgainWhileActiveReportsAlreadyInProgressRatherThanStartingASecondEpisode], even run
 * completely alone via `am instrument -e class ...#thatMethod` with nothing before or after it,
 * which is what ruled out every cross-test-timing theory (a settle delay up to 11s in [tearDown],
 * the device-idle whitelist, Doze, and the cached-app freezer all made zero difference, because
 * none of them touched the actual race — it was entirely within this test's own body).
 */
@RunWith(AndroidJUnit4::class)
class DefaultEmergencyControllerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "sos-controller-test.db"
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
        setAirplaneMode(enabled = false)
        // Confirm the setting has actually landed, not just fire the shell command and hope —
        // same discipline this file already applies to the service's own isRunning flag below.
        waitUntil { !isAirplaneModeOn() }
        stopServiceAndSettle()
        LigayaDatabase.setInstanceForTesting(null)
        context.deleteDatabase(dbName)
    }

    private fun setAirplaneMode(enabled: Boolean) {
        uiAutomation.executeShellCommand("cmd connectivity airplane-mode ${if (enabled) "enable" else "disable"}")
            .close()
    }

    private fun isAirplaneModeOn(): Boolean {
        val stream = uiAutomation.executeShellCommand("settings get global airplane_mode_on")
        return java.io.BufferedReader(java.io.InputStreamReader(java.io.FileInputStream(stream.fileDescriptor)))
            .use { it.readLine()?.trim() == "1" }
    }

    private fun hasActiveNotification(): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java)
        return manager.activeNotifications.any { it.id == EmergencyForegroundService.NOTIFICATION_ID }
    }

    /** [EmergencyForegroundService.isRunning] is only set false once its own background
     *  observation coroutine has actually finished cancelling (see [EmergencyForegroundService.onDestroy]'s
     *  own doc comment) — so waiting for it here is a genuine guarantee, not a timing guess, that
     *  it's safe for a caller to close the database right after. */
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

    /** Same shape as [waitUntil], but for a suspend condition — needed to poll a Flow's current
     *  value (observeSnapshot().first()) rather than a plain synchronous check. */
    private suspend fun waitUntilSuspending(timeoutMillis: Long = 15_000, condition: suspend () -> Boolean) {
        val start = System.currentTimeMillis()
        while (!condition()) {
            check(System.currentTimeMillis() - start < timeoutMillis) { "condition not met within ${timeoutMillis}ms" }
            delay(100)
        }
    }

    @Test
    fun sosActivatesEmergencyAndStartsTheServiceWithNetworkDisabled() = runTest {
        setAirplaneMode(enabled = true)
        waitUntil { isAirplaneModeOn() }

        val db = Room.databaseBuilder(context, LigayaDatabase::class.java, dbName).build()
        LigayaDatabase.setInstanceForTesting(db)
        val repository = RoomEmergencyStateSnapshotRepository(db.emergencyStateSnapshotDao())
        val controller = DefaultEmergencyController(context, repository)

        val result = controller.triggerSos()

        assertTrue("expected Activated, got $result", result is SosResult.Activated)
        assertEquals(EmergencyState.EMERGENCY_ACTIVE, (result as SosResult.Activated).state)

        waitUntil { hasActiveNotification() }
        assertTrue("expected the foreground service's persistent notification to be showing", hasActiveNotification())
        // See dialMonitor's own field doc / the retryCall test below for why this wait matters:
        // activate()'s automatic dial writes to this db on a scope this class never cancels.
        waitUntil { dialMonitor.hits >= 1 }

        // Must wait for the service to actually finish tearing down (its background observation
        // coroutine is still collecting this db's repository Flow) before closing the database
        // out from under it — otherwise Room can throw "Cannot perform this operation because
        // the connection pool has been closed" from that still-live coroutine, which crashes
        // this self-instrumenting test's whole process rather than merely failing this test.
        // See stopServiceAndSettle's own doc comment for the full explanation.
        stopServiceAndSettle()
        db.close()
    }

    @Test
    fun pressingSosAgainWhileActiveReportsAlreadyInProgressRatherThanStartingASecondEpisode() = runTest {
        val db = Room.databaseBuilder(context, LigayaDatabase::class.java, dbName).build()
        LigayaDatabase.setInstanceForTesting(db)
        val repository = RoomEmergencyStateSnapshotRepository(db.emergencyStateSnapshotDao())
        val controller = DefaultEmergencyController(context, repository)

        val first = controller.triggerSos()
        assertTrue(first is SosResult.Activated)
        // See this class's own doc comment for the full root-cause diagnosis.
        waitUntil { hasActiveNotification() }
        waitUntil { dialMonitor.hits >= 1 }

        val second = controller.triggerSos()
        assertTrue("expected AlreadyInProgress, got $second", second is SosResult.AlreadyInProgress)
        assertEquals(EmergencyState.EMERGENCY_ACTIVE, (second as SosResult.AlreadyInProgress).state)

        stopServiceAndSettle()
        db.close()
    }

    /** Step 37's "I'm safe" control, real end to end: fails with nothing active (this method's
     *  own documented case, not a special-cased branch — see EmergencyController.markSafe's own
     *  doc), succeeds once EMERGENCY_ACTIVE is actually reached. */
    @Test
    fun markSafeFailsWithNoActiveEmergencyAndSucceedsOnceOneIsActive() = runTest {
        val db = Room.databaseBuilder(context, LigayaDatabase::class.java, dbName).build()
        LigayaDatabase.setInstanceForTesting(db)
        val repository = RoomEmergencyStateSnapshotRepository(db.emergencyStateSnapshotDao())
        val controller = DefaultEmergencyController(context, repository)

        val beforeAnyEmergency = controller.markSafe()
        assertTrue("expected failure with no emergency active, got $beforeAnyEmergency", beforeAnyEmergency.isFailure)

        controller.triggerSos()
        // See this class's own doc comment for the full root-cause diagnosis.
        waitUntil { hasActiveNotification() }
        waitUntil { dialMonitor.hits >= 1 }

        val afterActivation = controller.markSafe()
        assertTrue("expected success, got $afterActivation", afterActivation.isSuccess)
        assertEquals(EmergencyState.USER_MARKED_SAFE, afterActivation.getOrNull())

        stopServiceAndSettle()
        db.close()
    }

    /** Step 41's fix, real end to end: no-op with nothing active, same shape as [markSafe] above;
     *  reaches a real IntentUnified911DialAction and updates the persisted snapshot once active.
     *  Uses the shared [dialMonitor] (installed for every test in [setUp] as of Step 48 — see
     *  this class's own doc comment), same technique as core-telephony's own
     *  IntentUnified911DialActionInstrumentedTest — the dial intent is observed to have actually
     *  fired without ever letting the real dialer Activity launch.
     *
     *  Three hits are expected below, not two: [DefaultEmergencyController.activate] (Step 48)
     *  now also fires the automatic dial itself once EMERGENCY_ACTIVE is reached, in addition to
     *  this test's own two explicit `retryCall()` calls. Unified911FlowCoordinator.dial()
     *  (core-telephony) always hands off to the dialer, regardless of whether the engine accepts
     *  recording that attempt — "no emergency active" only makes the *state update* fail, per
     *  EmergencyStateMachine's own subsystem-update window (Step 9), it doesn't gate the dial
     *  hand-off itself. Confirmed directly (this test's own first run caught exactly this —
     *  expected 1 hit, got 2, back when only the two explicit calls existed), not assumed. */
    @Test
    fun retryCallFailsWithNoActiveEmergencyAndSucceedsOnceOneIsActive() = runTest {
        val db = Room.databaseBuilder(context, LigayaDatabase::class.java, dbName).build()
        LigayaDatabase.setInstanceForTesting(db)
        val repository = RoomEmergencyStateSnapshotRepository(db.emergencyStateSnapshotDao())
        val controller = DefaultEmergencyController(context, repository)

        val beforeAnyEmergency = controller.retryCall()
        assertTrue("expected failure with no emergency active, got $beforeAnyEmergency", beforeAnyEmergency.isFailure)
        dialMonitor.waitForActivityWithTimeout(5_000)
        assertEquals("the dial hand-off itself still fires even when the state update is rejected", 1, dialMonitor.hits)

        controller.triggerSos()
        // See this class's own doc comment for the full root-cause diagnosis.
        waitUntil { hasActiveNotification() }
        // activate()'s own automatic dial (Step 48) fires concurrently on a background dispatcher
        // — wait for it to land before asserting the count, rather than racing it.
        waitUntilSuspending { dialMonitor.hits >= 2 }

        val afterActivation = controller.retryCall()
        assertTrue("expected success, got $afterActivation", afterActivation.isSuccess)
        assertEquals(EmergencyState.EMERGENCY_ACTIVE, afterActivation.getOrNull())

        dialMonitor.waitForActivityWithTimeout(5_000)
        assertEquals(3, dialMonitor.hits)

        stopServiceAndSettle()
        db.close()
    }

    /** Step 37's live-observation requirement, real end to end: the same controller instance's
     *  observeSnapshot() reflects each transition triggerSos()/markSafe() themselves cause, with
     *  no separate write path — it's the exact same repository.observeCurrent() query the
     *  foreground service itself already relies on (see the test above's own comment on that). */
    @Test
    fun observeSnapshotReflectsEachTransitionAsItHappens() = runTest {
        val db = Room.databaseBuilder(context, LigayaDatabase::class.java, dbName).build()
        LigayaDatabase.setInstanceForTesting(db)
        val repository = RoomEmergencyStateSnapshotRepository(db.emergencyStateSnapshotDao())
        val controller = DefaultEmergencyController(context, repository)

        controller.triggerSos()
        waitUntilSuspending { controller.observeSnapshot().first()?.state == EmergencyState.EMERGENCY_ACTIVE }
        // See this class's own doc comment for the full root-cause diagnosis.
        waitUntil { hasActiveNotification() }
        waitUntil { dialMonitor.hits >= 1 }

        controller.markSafe()
        waitUntilSuspending { controller.observeSnapshot().first()?.state == EmergencyState.USER_MARKED_SAFE }

        stopServiceAndSettle()
        db.close()
    }
}
