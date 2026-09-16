package com.ligaya.app.screens.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ligaya.designsystem.LigayaThemeMode
import com.ligaya.designsystem.components.LigayaMotionMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What makes these settings real rather than a screen that merely looks changed: each one is written to disk
 * as it is chosen, so a second [LigayaSettings] built over the same file - which is what the next launch of
 * the app is - reads the choice back.
 */
@RunWith(AndroidJUnit4::class)
class LigayaSettingsTest {

    private companion object { const val TOLERANCE = 0.0001f }

    private val preferences = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences("ligaya_settings_test", Context.MODE_PRIVATE)

    @Before
    fun clear() {
        preferences.edit().clear().commit()
    }

    @Test
    fun defaultsAreTheOnesTheAppShipsWith() {
        val settings = LigayaSettings(preferences)
        assertEquals(LigayaThemeMode.System, settings.themeMode.value)
        assertEquals(1.0f, settings.mascot.value.animationSpeed, TOLERANCE)
        assertEquals(0.6f, settings.mascot.value.depthStrength, TOLERANCE)
        assertEquals(LigayaMotionMode.System, settings.mascot.value.motion)
        assertTrue(settings.wakePhraseEnabled.value)
        assertFalse(settings.welcomeCompleted)
    }

    @Test
    fun everyChoiceSurvivesTheAppBeingClosed() {
        LigayaSettings(preferences).apply {
            setThemeMode(LigayaThemeMode.Dark)
            setAnimationSpeed(1.4f)
            setDepthStrength(0f)
            setMotion(LigayaMotionMode.Still)
            setWakePhraseEnabled(false)
            setWelcomeCompleted(true)
        }

        val reopened = LigayaSettings(preferences)
        assertEquals(LigayaThemeMode.Dark, reopened.themeMode.value)
        assertEquals(1.4f, reopened.mascot.value.animationSpeed, TOLERANCE)
        assertEquals(0f, reopened.mascot.value.depthStrength, TOLERANCE)
        assertEquals(LigayaMotionMode.Still, reopened.mascot.value.motion)
        assertFalse(reopened.wakePhraseEnabled.value)
        assertTrue(reopened.welcomeCompleted)
    }

    @Test
    fun valuesOutsideWhatTheEngineAcceptsAreBroughtBackIntoRange() {
        val settings = LigayaSettings(preferences)
        settings.setAnimationSpeed(9f)
        settings.setDepthStrength(-3f)
        assertEquals(2.0f, settings.mascot.value.animationSpeed, TOLERANCE)
        assertEquals(0f, settings.mascot.value.depthStrength, TOLERANCE)
    }
}
