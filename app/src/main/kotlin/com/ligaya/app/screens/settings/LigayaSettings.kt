package com.ligaya.app.screens.settings

import android.content.SharedPreferences
import androidx.core.content.edit
import com.ligaya.designsystem.LigayaThemeMode
import com.ligaya.designsystem.components.LigayaMascotPreferences
import com.ligaya.designsystem.components.LigayaMotionMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Everything Settings can change, held in one place and written through to disk as it changes, so a choice
 * survives the app being closed rather than lasting only as long as the screen that made it.
 *
 * Flows rather than Compose state because two of these are read outside composition: the wake-phrase switch
 * gates MainActivity's listening loop, and the theme is needed before the first frame.
 */
class LigayaSettings(private val preferences: SharedPreferences) {

    private val themeModeState = MutableStateFlow(
        runCatching { LigayaThemeMode.valueOf(preferences.getString(KEY_THEME_MODE, null) ?: "") }
            .getOrDefault(LigayaThemeMode.System),
    )
    val themeMode: StateFlow<LigayaThemeMode> = themeModeState.asStateFlow()

    private val mascotState = MutableStateFlow(
        LigayaMascotPreferences(
            animationSpeed = preferences.getFloat(KEY_ANIMATION_SPEED, 1.0f),
            depthStrength = preferences.getFloat(KEY_DEPTH_STRENGTH, 0.6f),
            motion = runCatching { LigayaMotionMode.valueOf(preferences.getString(KEY_MOTION, null) ?: "") }
                .getOrDefault(LigayaMotionMode.System),
        ),
    )
    val mascot: StateFlow<LigayaMascotPreferences> = mascotState.asStateFlow()

    private val wakePhraseState = MutableStateFlow(preferences.getBoolean(KEY_WAKE_PHRASE, true))
    val wakePhraseEnabled: StateFlow<Boolean> = wakePhraseState.asStateFlow()

    /** False on the very first launch only; the welcome screen sets it, and General can set it back. */
    val welcomeCompleted: Boolean get() = preferences.getBoolean(KEY_WELCOME_COMPLETED, false)

    fun setThemeMode(mode: LigayaThemeMode) {
        themeModeState.value = mode
        preferences.edit { putString(KEY_THEME_MODE, mode.name) }
    }

    fun setAnimationSpeed(multiplier: Float) {
        val clamped = multiplier.coerceIn(0.5f, 2.0f)
        mascotState.value = mascotState.value.copy(animationSpeed = clamped)
        preferences.edit { putFloat(KEY_ANIMATION_SPEED, clamped) }
    }

    fun setDepthStrength(strength: Float) {
        val clamped = strength.coerceIn(0f, 1f)
        mascotState.value = mascotState.value.copy(depthStrength = clamped)
        preferences.edit { putFloat(KEY_DEPTH_STRENGTH, clamped) }
    }

    fun setMotion(motion: LigayaMotionMode) {
        mascotState.value = mascotState.value.copy(motion = motion)
        preferences.edit { putString(KEY_MOTION, motion.name) }
    }

    fun setWakePhraseEnabled(enabled: Boolean) {
        wakePhraseState.value = enabled
        preferences.edit { putBoolean(KEY_WAKE_PHRASE, enabled) }
    }

    fun setWelcomeCompleted(completed: Boolean) {
        preferences.edit { putBoolean(KEY_WELCOME_COMPLETED, completed) }
    }

    companion object {
        const val PREFERENCES_NAME = "ligaya_app"
        const val KEY_WELCOME_COMPLETED = "welcome_completed"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_ANIMATION_SPEED = "mascot_animation_speed"
        const val KEY_DEPTH_STRENGTH = "mascot_depth_strength"
        const val KEY_MOTION = "mascot_motion"
        const val KEY_WAKE_PHRASE = "wake_phrase_enabled"
    }
}
