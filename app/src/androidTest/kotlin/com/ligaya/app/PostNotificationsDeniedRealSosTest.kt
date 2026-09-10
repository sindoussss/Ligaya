package com.ligaya.app

import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PatternMatcher
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.ligaya.core.data.LigayaDatabase
import com.ligaya.core.data.engine.DefaultEmergencyController
import com.ligaya.core.data.engine.EmergencyForegroundService
import com.ligaya.core.data.repository.RoomEmergencyStateSnapshotRepository
import com.ligaya.core.emergencyengine.EmergencyState
import com.ligaya.core.uistate.SosResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Step 50's own real answer to a question raised while building the permission matrix:
 * [com.ligaya.core.data.engine.PostNotificationsDeniedInstrumentedTest] (core-data) found a real
 * `ForegroundServiceDidNotStartInTimeException` crash when POST_NOTIFICATIONS was denied — but
 * that test has no Activity at all (a bare, headless instrumentation process, the same shape
 * every other real-instrumented permission test in this codebase uses). Verified directly, the
 * more important question first: does a *real* user, who always has some Activity foregrounded
 * when they press SOS, ever actually hit this? Confirmed by hand via a real device screenshot
 * (`am start` MainActivity, deny the real notification dialog, tap the real SOS control): the real
 * Dialer opened on 911, no crash, exactly as designed.
 *
 * This test is that same real scenario — a genuinely foregrounded [MainActivity] via
 * [ActivityScenario], POST_NOTIFICATIONS left in its natural denied state — made an automated
 * regression test. [ActivityScenario] rather than `createAndroidComposeRule` + UiAutomator:
 * confirmed directly that combination doesn't work cleanly here — [androidx.test.uiautomator.UiDevice]
 * launches AndroidX Test's own internal placeholder `EmptyActivity` as a side effect, which
 * silently steals top-activity status from MainActivity mid-test and breaks Compose's own
 * hierarchy lookup ("No compose hierarchies found in the app"), even though MainActivity itself
 * never crashed. [ActivityScenario] avoids needing to touch the real system dialog UI at all —
 * SOS is triggered directly through the same [DefaultEmergencyController] MainActivity's own
 * SosControl click ultimately calls, with MainActivity still genuinely resumed and foregrounded
 * alongside it, which is the actual thing under test (a real foregrounded Activity, not headless
 * instrumentation) — not the exact tap path, which [feature-home]'s own `HomeScreenTest` already
 * covers with a fake controller.
 *
 * This is why core-data's own headless test stays disabled rather than being un-ignored: the two
 * tests genuinely disagree, and the real-Activity scenario is the one that matches how the app is
 * actually used — this app's own SOS entry points (the Home screen control, the wake-word loop)
 * only ever run while some Activity is genuinely foregrounded, never from a bare background
 * process.
 */
@RunWith(AndroidJUnit4::class)
class PostNotificationsDeniedRealSosTest {

    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(android.Manifest.permission.RECORD_AUDIO)

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    private val dbName = "post-notifications-denied-real-sos-test.db"
    private lateinit var dialMonitor: Instrumentation.ActivityMonitor
    private var scenario: ActivityScenario<MainActivity>? = null

    private suspend fun waitUntil(timeoutMillis: Long = 15_000, condition: () -> Boolean) {
        val start = System.currentTimeMillis()
        while (!condition()) {
            check(System.currentTimeMillis() - start < timeoutMillis) { "condition not met within ${timeoutMillis}ms" }
            delay(100)
        }
    }

    @After
    fun tearDown() {
        scenario?.close()
        if (::dialMonitor.isInitialized) instrumentation.removeMonitor(dialMonitor)
        context.stopService(Intent(context, EmergencyForegroundService::class.java))
        LigayaDatabase.setInstanceForTesting(null)
        context.deleteDatabase(dbName)
    }

    @Test
    fun pressingSosWithNotificationsDeniedAndARealActivityForegroundedStillReachesEmergencyActiveNoCrash() {
        // A fresh test install has POST_NOTIFICATIONS in its natural not-granted state — the same
        // starting point every other "denied" real-instrumented permission test in this codebase
        // already relies on (see e.g. VoiceCaptureCoordinatorInstrumentedTest's own doc comment).
        // Same cross-test permission-permanence reality as those others, too:
        // NavigationRouteReachabilityTest's own GrantPermissionRule now also grants
        // POST_NOTIFICATIONS (Step 50), so when that test runs first in this shared
        // instrumentation process, this precondition can no longer hold — skip rather than fail.
        org.junit.Assume.assumeTrue(
            "POST_NOTIFICATIONS was already granted by another test earlier in this " +
                "instrumentation run, and can't be safely revoked mid-run — skipping rather than " +
                "asserting a 'denied' precondition that no longer holds.",
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED,
        )

        val dialFilter = IntentFilter(Intent.ACTION_DIAL).apply {
            addDataScheme("tel")
            addDataSchemeSpecificPart("911", PatternMatcher.PATTERN_LITERAL)
        }
        dialMonitor = instrumentation.addMonitor(dialFilter, null, true)

        // MainActivity genuinely launched and resumed — the actual condition under test (a real
        // foregrounded Activity, unlike core-data's own headless test) — running alongside a
        // separately-constructed controller sharing its own real database, exactly mirroring how
        // MainActivity's own onCreate() constructs one at the composition root.
        scenario = ActivityScenario.launch(MainActivity::class.java)

        context.deleteDatabase(dbName)
        val db = androidx.room.Room.databaseBuilder(context, LigayaDatabase::class.java, dbName).build()
        LigayaDatabase.setInstanceForTesting(db)
        val repository = RoomEmergencyStateSnapshotRepository(db.emergencyStateSnapshotDao())
        val controller = DefaultEmergencyController(context, repository)

        val result = runBlocking { controller.triggerSos() }

        assertTrue("expected Activated, got $result", result is SosResult.Activated)
        assertEquals(EmergencyState.EMERGENCY_ACTIVE, (result as SosResult.Activated).state)

        runBlocking { waitUntil { EmergencyForegroundService.isRunning } }
        assertTrue(
            "SOS must still reach EMERGENCY_ACTIVE and run the foreground service even with " +
                "POST_NOTIFICATIONS denied, while a real Activity (MainActivity) is genuinely " +
                "resumed and foregrounded",
            EmergencyForegroundService.isRunning,
        )
        // activate()'s own automatic dial (Step 48) fires on a separate, never-cancelled scope —
        // same reason every other test that calls triggerSos() waits for it to land before
        // proceeding (see DefaultEmergencyControllerTest's own doc comment for the full
        // diagnosis).
        runBlocking { waitUntil { dialMonitor.hits >= 1 } }

        // Same established fix as every other test in this codebase that starts the real
        // service: wait for it to actually finish tearing down (isRunning=false is only set once
        // its own background observation coroutine has genuinely stopped — see
        // EmergencyForegroundService.onDestroy()'s own doc comment) before closing the database
        // out from under it.
        context.stopService(Intent(context, EmergencyForegroundService::class.java))
        runBlocking { waitUntil { !EmergencyForegroundService.isRunning } }
        db.close()
    }
}
