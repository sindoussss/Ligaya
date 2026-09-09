package com.ligaya.core.telephony

import android.content.Intent
import android.os.PatternMatcher
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Proves this step's acceptance criteria: tapping the 911 control opens the native dialer
 * pre-filled with the Philippines Unified 911 number.
 *
 * Uses a blocking Instrumentation.ActivityMonitor rather than actually letting the real dialer
 * launch — the monitor's IntentFilter is scoped to exactly ACTION_DIAL + "tel:911" (via
 * addDataSchemeSpecificPart, since "tel:911" is an opaque URI with no host/path to match on), so
 * a hit here can only mean the dial action fired the correct action and number; block = true
 * means the match is recorded without the real dialer Activity ever actually starting, so this
 * test never leaves a dialer UI on screen or depends on what dialer app the AVD ships.
 */
@RunWith(AndroidJUnit4::class)
class IntentUnified911DialActionInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val uiAutomation = instrumentation.uiAutomation
    private lateinit var monitor: android.app.Instrumentation.ActivityMonitor

    @Before
    fun setUp() {
        val filter = android.content.IntentFilter(Intent.ACTION_DIAL).apply {
            addDataScheme("tel")
            addDataSchemeSpecificPart("911", PatternMatcher.PATTERN_LITERAL)
        }
        monitor = instrumentation.addMonitor(filter, null, true)
    }

    @After
    fun tearDown() {
        instrumentation.removeMonitor(monitor)
        setAirplaneMode(enabled = false)
    }

    private fun setAirplaneMode(enabled: Boolean) {
        uiAutomation.executeShellCommand("cmd connectivity airplane-mode ${if (enabled) "enable" else "disable"}").close()
    }

    @Test
    fun dialFiresActionDialWithThePhilippinesUnified911Number() {
        val action = IntentUnified911DialAction(context)

        val result = action.dial()

        assertTrue(result.isSuccess)
        // block = true means no real Activity is ever launched, so waitForActivityWithTimeout
        // itself always returns null here — its only purpose is to block until ActivityManager
        // has actually processed the matching intent, so the hit count below isn't checked
        // before it lands.
        monitor.waitForActivityWithTimeout(5_000)
        assertEquals(1, monitor.hits)
    }

    /**
     * Step 30's own row for this module in section 22's table: the dial action is "possibly
     * local" because firing ACTION_DIAL is pure Intent dispatch with no network I/O of its own —
     * whether the resulting call actually connects is inherently up to cellular signal, which is
     * "where available" territory outside this app's control, not something to degrade around.
     * This proves that claim directly instead of leaving it as an inference from reading the code.
     */
    @Test
    fun dialStillFiresActionDialUnderAirplaneMode() {
        setAirplaneMode(enabled = true)
        val action = IntentUnified911DialAction(context)

        val result = action.dial()

        assertTrue(result.isSuccess)
        monitor.waitForActivityWithTimeout(5_000)
        assertEquals(1, monitor.hits)
    }
}
