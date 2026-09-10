package com.ligaya.core.data.engine

import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
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
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Step 50's own explicit callout: "notifications" is one of the permissions in the roadmap's own
 * matrix. Every other test in this class/module deliberately grants POST_NOTIFICATIONS in its own
 * setUp() (so the persistent notification is a reliable, always-available "the service actually
 * started" signal for those tests), so none of them ever exercise the *denied* path — this was
 * meant to be the missing cell.
 *
 * DISABLED, RESOLVED — this uncovered a real, reproducible crash, but a headless-instrumentation
 * artifact, not a real product risk. With POST_NOTIFICATIONS genuinely denied,
 * `EmergencyForegroundService`'s call to `ServiceCompat.startForeground()` crashes the whole
 * process here with `RemoteServiceException$ForegroundServiceDidNotStartInTimeException` —
 * confirmed via temporary Log.d timestamps cross-referenced against ActivityManager's own logcat:
 * "Bringing down service while still waiting for start foreground" fires only ~2ms after the
 * startForeground() call begins, not after any real multi-second timeout, and it's not a
 * test-code race like the ones fixed elsewhere in this file. Ruled out as variables: the
 * foreground-service type (reproduces identically as LOCATION or SPECIAL_USE), notification
 * channel importance, notification richness (a bare-minimum notification still crashes), and the
 * ServiceCompat compat shim (calling the raw platform Service.startForeground() directly still
 * crashes identically).
 *
 * The one variable that actually mattered, found by testing the real thing first instead of only
 * theorizing about it: whether a real Activity is genuinely foregrounded. Confirmed twice —
 * once by hand (`am start` the real MainActivity, deny the real notification dialog, tap the real
 * SOS control: the real Dialer opened on 911, no crash) and once as a proper automated regression
 * test, [com.ligaya.app.PostNotificationsDeniedRealSosTest] (`:app`), which launches a genuinely
 * resumed MainActivity via ActivityScenario with POST_NOTIFICATIONS left denied and passes
 * reliably. This class's own test has no Activity at all — a bare, headless instrumentation
 * process, the one shape every other real-instrumented permission test in this codebase also
 * uses, which is exactly why this only ever surfaced here. This app's own real SOS entry points
 * (the Home screen control, the wake-word loop) only ever run while some Activity is genuinely
 * foregrounded, never from a bare background process, so this headless crash — while real — never
 * describes an actual user's own experience. Left disabled (not deleted) as a documented, known
 * headless-instrumentation limitation, with the real-world question it raised now actually
 * answered by :app's own test rather than left open.
 *
 * MainActivity also now requests POST_NOTIFICATIONS proactively (mirroring RECORD_AUDIO) — good
 * practice regardless, though not what actually resolved the crash risk here.
 */
@RunWith(AndroidJUnit4::class)
class PostNotificationsDeniedInstrumentedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "post-notifications-denied-test.db"
    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var dialMonitor: Instrumentation.ActivityMonitor

    private fun setUpDialMonitor() {
        val dialFilter = IntentFilter(Intent.ACTION_DIAL).apply {
            addDataScheme("tel")
            addDataSchemeSpecificPart("911", PatternMatcher.PATTERN_LITERAL)
        }
        dialMonitor = instrumentation.addMonitor(dialFilter, null, true)
    }

    @After
    fun tearDown() {
        if (::dialMonitor.isInitialized) instrumentation.removeMonitor(dialMonitor)
        context.stopService(Intent(context, EmergencyForegroundService::class.java))
        LigayaDatabase.setInstanceForTesting(null)
        context.deleteDatabase(dbName)
    }

    private suspend fun waitUntil(timeoutMillis: Long = 15_000, condition: () -> Boolean) {
        val start = System.currentTimeMillis()
        while (!condition()) {
            check(System.currentTimeMillis() - start < timeoutMillis) { "condition not met within ${timeoutMillis}ms" }
            delay(100)
        }
    }

    @Ignore(
        "Reproduces a real ForegroundServiceDidNotStartInTimeException crash, but confirmed " +
            "(see this class's own doc comment) to be an artifact of having no Activity at all, " +
            "not a real product risk — PostNotificationsDeniedRealSosTest (:app) proves the real, " +
            "Activity-foregrounded scenario this headless one can't represent works correctly. " +
            "Left active this would intermittently crash the shared instrumentation process and " +
            "take down whatever other test happens to run alongside it.",
    )
    @Test
    fun sosWithNotificationsDeniedStillReachesEmergencyActiveAndRunsTheService() = runTest {
        assumeTrue(
            "POST_NOTIFICATIONS was already granted by another test earlier in this " +
                "instrumentation run, and can't be safely revoked mid-run — skipping rather than " +
                "asserting a 'denied' precondition that no longer holds.",
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_DENIED,
        )

        setUpDialMonitor()

        val db = Room.databaseBuilder(context, LigayaDatabase::class.java, dbName).build()
        LigayaDatabase.setInstanceForTesting(db)
        val repository = RoomEmergencyStateSnapshotRepository(db.emergencyStateSnapshotDao())
        val controller = DefaultEmergencyController(context, repository)

        val result = controller.triggerSos()

        assertTrue("expected Activated, got $result", result is SosResult.Activated)
        assertEquals(EmergencyState.EMERGENCY_ACTIVE, (result as SosResult.Activated).state)

        waitUntil { EmergencyForegroundService.isRunning }
        assertTrue(
            "the foreground service must actually run even without POST_NOTIFICATIONS " +
                "(Android silently withholds only the visible notification, not the service itself)",
            EmergencyForegroundService.isRunning,
        )
        assertEquals(
            EmergencyState.EMERGENCY_ACTIVE,
            repository.getCurrent()?.mainState?.let { EmergencyState.valueOf(it) },
        )

        context.stopService(Intent(context, EmergencyForegroundService::class.java))
        waitUntil { !EmergencyForegroundService.isRunning }
        db.close()
    }
}
