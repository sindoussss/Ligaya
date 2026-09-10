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
import com.ligaya.core.ai.GeminiContentGenerator
import com.ligaya.core.ai.GeminiIntentProvider
import com.ligaya.core.ai.VoiceInterpretationOutcome
import com.ligaya.core.data.LigayaDatabase
import com.ligaya.core.data.repository.RoomEmergencyStateSnapshotRepository
import com.ligaya.core.emergencyengine.EmergencyState
import com.ligaya.core.uistate.SosResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers this step's own literal acceptance criteria: "With the Gemini API key
 * invalidated/network blocked, SOS-triggered emergencies still complete every non-AI subsystem
 * normally." Both halves happen in the same test, deliberately: a broken Gemini setup exists and
 * is proven broken, then the real, persisted SOS path is driven alongside it and proven
 * unaffected — not tested in isolation from each other, which would leave open the question of
 * whether they interfere.
 *
 * DefaultEmergencyController.triggerSos() reaching EMERGENCY_ACTIVE here isn't really "despite"
 * the broken Gemini setup — core-data's own production build.gradle.kts has no dependency on
 * core-ai at all (see DefaultEmergencyController's own doc comment), so there is no code path
 * through which a Gemini failure could even reach this class. This test proves that structural
 * guarantee holds at runtime too, not just on paper.
 *
 * Step 48/49: like every other test in this module that calls triggerSos(), this one now also
 * needs [dialMonitor] (the automatic 911 dial fires for real here too) and a wait for it to
 * land before closing the database — see DefaultEmergencyControllerTest's own doc comment for
 * the full diagnosis. Found here specifically while verifying Step 49: running this test without
 * the fix let the real system Dialer app actually launch (confirmed via `dumpsys activity
 * activities`), the same class of bug already fixed in the other two files.
 */
@RunWith(AndroidJUnit4::class)
class GeminiFailureIsolationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "gemini-failure-isolation-test.db"
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
    fun tearDown() {
        instrumentation.removeMonitor(dialMonitor)
        context.stopService(Intent(context, EmergencyForegroundService::class.java))
        LigayaDatabase.setInstanceForTesting(null)
        context.deleteDatabase(dbName)
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

    @Test
    fun sosStillCompletesNormallyWithGeminiUnreachable() = runTest {
        // Simulates an invalidated API key / blocked network: every call fails.
        val brokenGenerator = GeminiContentGenerator { error("simulated: network blocked / invalid API key") }
        val geminiProvider = GeminiIntentProvider(brokenGenerator)

        // First, confirm Gemini really is broken, and that core-ai reports it honestly (Step 27's
        // own "surfaced distinct state" — not silently pretending it's a confident non-emergency).
        val outcome = geminiProvider.interpret("Ligaya, tulong. May sunog.")
        assertEquals(VoiceInterpretationOutcome.Unavailable, outcome)

        // Now, with that broken setup still in scope, drive the real SOS path and confirm it
        // completes exactly as DefaultEmergencyControllerTest's own SOS test expects — the
        // deterministic engine, Room persistence, and the foreground service all still work.
        val db = Room.databaseBuilder(context, LigayaDatabase::class.java, dbName).build()
        LigayaDatabase.setInstanceForTesting(db)
        val repository = RoomEmergencyStateSnapshotRepository(db.emergencyStateSnapshotDao())
        val controller = DefaultEmergencyController(context, repository)

        val result = controller.triggerSos()

        assertTrue("expected Activated, got $result", result is SosResult.Activated)
        assertEquals(EmergencyState.EMERGENCY_ACTIVE, (result as SosResult.Activated).state)
        waitUntil { hasActiveNotification() }
        assertTrue("expected the foreground service's persistent notification to be showing", hasActiveNotification())
        // See this class's own doc comment for why this wait is needed before db.close() below.
        waitUntil { dialMonitor.hits >= 1 }
        assertEquals(
            EmergencyState.EMERGENCY_ACTIVE,
            repository.getCurrent()?.mainState?.let { EmergencyState.valueOf(it) },
        )

        context.stopService(Intent(context, EmergencyForegroundService::class.java))
        waitUntil { !EmergencyForegroundService.isRunning }
        db.close()
    }
}
