package com.ligaya.core.data.engine

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ligaya.core.data.LigayaDatabase
import com.ligaya.core.data.repository.RoomEmergencyStateSnapshotRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers this step's acceptance criteria as far as an automated instrumented test reasonably
 * can: the service starts itself (with a real, visible persistent notification) when the
 * persisted state is EMERGENCY_ACTIVE, stops when it reaches CLOSED, and — critically — resumes
 * correctly after a *simulated full process death* purely from persisted state, with nothing
 * carried over in memory (same technique as Step 10's crash-recovery test).
 *
 * What this test does NOT prove, by its nature: literally backgrounding the real app for 10
 * real minutes. That half of the roadmap's own "Manual + instrumented" testing note is a
 * device-level, UI-driven verification that needs an actual trigger UI (Step 13 onward) to be
 * meaningful — not something to fake here. What IS proven here — the service's self-management
 * mechanism and its survival of a full simulated restart — is the part that would make or break
 * that manual test, so this is not a token substitute for it.
 */
@RunWith(AndroidJUnit4::class)
class EmergencyForegroundServiceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "fgs-test.db"

    @Before
    fun setUp() {
        context.deleteDatabase(dbName)
        // POST_NOTIFICATIONS gates whether Android actually displays the notification at all
        // (Android 13+) — granting it (never revoking; see Step 6's finding on why revoking a
        // permission from a live self-instrumented process is fatal) makes the test
        // deterministic regardless of the test APK's default grant state.
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
            context.packageName,
            "android.permission.POST_NOTIFICATIONS",
        )
    }

    @After
    fun tearDown() {
        context.stopService(Intent(context, EmergencyForegroundService::class.java))
        LigayaDatabase.setInstanceForTesting(null)
        context.deleteDatabase(dbName)
    }

    private fun openTestDatabaseAsSingleton(): LigayaDatabase {
        val db = Room.databaseBuilder(context, LigayaDatabase::class.java, dbName).build()
        LigayaDatabase.setInstanceForTesting(db)
        return db
    }

    private fun hasActiveNotification(): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java)
        return manager.activeNotifications.any { it.id == EmergencyForegroundService.NOTIFICATION_ID }
    }

    // 15s, not 5s: whichever test in this class happens to run first in a fresh test process
    // pays a one-time cold-start cost (class loading, notification channel creation, thread
    // pool spin-up) that a tight timeout doesn't leave room for — observed directly: this
    // exact check consistently failed at 5s only when it was the first test to run in the
    // process, and consistently passed well under 5s once something else had already warmed
    // the process up first.
    private suspend fun waitUntil(timeoutMillis: Long = 15_000, condition: () -> Boolean) {
        val start = System.currentTimeMillis()
        while (!condition()) {
            check(System.currentTimeMillis() - start < timeoutMillis) { "condition not met within ${timeoutMillis}ms" }
            delay(100)
        }
    }

    @Test
    fun serviceShowsNotificationOnActiveAndRemovesItOnClosed() = runTest {
        val db = openTestDatabaseAsSingleton()
        val repository = RoomEmergencyStateSnapshotRepository(db.emergencyStateSnapshotDao())

        val machine = PersistedEmergencyStateMachine.start(repository, emergencyEventId = "event-1")
        machine.detectEmergency()
        machine.confirmEmergency()
        machine.activateEmergency()

        ContextCompat.startForegroundService(context, Intent(context, EmergencyForegroundService::class.java))
        waitUntil { hasActiveNotification() }
        assertTrue("expected the persistent emergency notification to be showing", hasActiveNotification())

        machine.markUserSafe()
        machine.resolveEmergency()
        assertTrue("notification should stay up through the whole open session", hasActiveNotification())

        machine.closeEmergency()
        waitUntil { !hasActiveNotification() }
        assertFalse("expected the notification to be gone once CLOSED", hasActiveNotification())

        // The notification disappearing is a weaker signal than isRunning going false: it is set
        // by the same self-stop path (stopSelf() from within the Flow collector once CLOSED is
        // observed) but Android does not guarantee the notification is removed only after
        // onDestroy() has fully run and cancelled the observation coroutine. Waiting for the
        // authoritative isRunning flag before closing the database avoids the same
        // connection-pool-closed race fixed in serviceResumesForegroundStateAfterSimulatedProcessDeath.
        waitUntil { !EmergencyForegroundService.isRunning }
        db.close()
    }

    @Test
    fun serviceResumesForegroundStateAfterSimulatedProcessDeath() = runTest {
        // --- Before "process death": reach EMERGENCY_ACTIVE and start the service. ---
        val firstDb = openTestDatabaseAsSingleton()
        val firstRepository = RoomEmergencyStateSnapshotRepository(firstDb.emergencyStateSnapshotDao())
        val machine = PersistedEmergencyStateMachine.start(firstRepository, emergencyEventId = "event-2")
        machine.detectEmergency()
        machine.confirmEmergency()
        machine.activateEmergency()

        ContextCompat.startForegroundService(context, Intent(context, EmergencyForegroundService::class.java))
        waitUntil { hasActiveNotification() }

        // Simulate the process being killed: stop the service and close the database instance
        // completely — nothing left running, nothing left in memory. Critically, wait for
        // EmergencyForegroundService.isRunning to actually go false (not just the notification
        // to disappear, and not a guessed fixed delay — stopService() is not synchronous):
        // this service instance's `database`/`repository` are `by lazy`, cached once per
        // instance, so closing firstDb before onDestroy() has genuinely finished — while this
        // exact instance might still be alive and could still be reused for the next start —
        // would either throw inside its still-live collector or, worse, leave a stale instance
        // that never picks up the second database at all. This is exactly the bug this test
        // caught the first time it was run.
        context.stopService(Intent(context, EmergencyForegroundService::class.java))
        waitUntil { !EmergencyForegroundService.isRunning }
        firstDb.close()
        LigayaDatabase.setInstanceForTesting(null)

        // --- After Android relaunches the process and restarts the service ---
        val secondDb = Room.databaseBuilder(context, LigayaDatabase::class.java, dbName).build()
        LigayaDatabase.setInstanceForTesting(secondDb)

        // No new transition call here — the service must resume purely from what was already
        // persisted, exactly like a real OS-triggered restart with a null Intent would.
        ContextCompat.startForegroundService(context, Intent(context, EmergencyForegroundService::class.java))
        waitUntil { hasActiveNotification() }
        assertTrue(
            "service should resume its foreground/notification state from persisted state alone",
            hasActiveNotification(),
        )

        // Same wait-for-isRunning-false dance as above, and for the same reason: the service's
        // background observation coroutine is still collecting from secondDb's repository Flow
        // right up until onDestroy() actually cancels it. Closing secondDb first — as this test
        // originally did — races that coroutine: whenever it was still mid-query at the moment
        // of close(), Room threw "Cannot perform this operation because the connection pool has
        // been closed" from a background thread, which crashes this self-instrumenting test's
        // whole process (killing every other queued test, not just this one) rather than merely
        // failing this test. Fixed at the source in EmergencyForegroundService.onDestroy() (see
        // its own doc comment): isRunning now only flips false once the observation coroutine has
        // actually finished cancelling, not merely been asked to — so waiting for it here is a
        // genuine guarantee, not a timing bet.
        context.stopService(Intent(context, EmergencyForegroundService::class.java))
        waitUntil { !EmergencyForegroundService.isRunning }
        secondDb.close()
    }
}
