package com.ligaya.core.data.engine

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
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
 */
@RunWith(AndroidJUnit4::class)
class DefaultEmergencyControllerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "sos-controller-test.db"
    private val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation

    @Before
    fun setUp() {
        context.deleteDatabase(dbName)
        uiAutomation.grantRuntimePermission(context.packageName, "android.permission.POST_NOTIFICATIONS")
    }

    @After
    fun tearDown() {
        setAirplaneMode(enabled = false)
        context.stopService(Intent(context, EmergencyForegroundService::class.java))
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

        // Must wait for the service to actually finish tearing down (its background observation
        // coroutine is still collecting this db's repository Flow) before closing the database
        // out from under it — otherwise Room can throw "Cannot perform this operation because
        // the connection pool has been closed" from that still-live coroutine, which crashes
        // this self-instrumenting test's whole process rather than merely failing this test.
        // Found via a real, if intermittent, crash — see EmergencyForegroundServiceTest's
        // identical fix for the full explanation.
        context.stopService(Intent(context, EmergencyForegroundService::class.java))
        waitUntil { !EmergencyForegroundService.isRunning }
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

        val second = controller.triggerSos()
        assertTrue("expected AlreadyInProgress, got $second", second is SosResult.AlreadyInProgress)
        assertEquals(EmergencyState.EMERGENCY_ACTIVE, (second as SosResult.AlreadyInProgress).state)

        // Same reasoning as the test above: wait for the service to actually stop before
        // closing the database it's still observing.
        context.stopService(Intent(context, EmergencyForegroundService::class.java))
        waitUntil { !EmergencyForegroundService.isRunning }
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

        val afterActivation = controller.markSafe()
        assertTrue("expected success, got $afterActivation", afterActivation.isSuccess)
        assertEquals(EmergencyState.USER_MARKED_SAFE, afterActivation.getOrNull())

        context.stopService(Intent(context, EmergencyForegroundService::class.java))
        waitUntil { !EmergencyForegroundService.isRunning }
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

        controller.markSafe()
        waitUntilSuspending { controller.observeSnapshot().first()?.state == EmergencyState.USER_MARKED_SAFE }

        context.stopService(Intent(context, EmergencyForegroundService::class.java))
        waitUntil { !EmergencyForegroundService.isRunning }
        db.close()
    }
}
