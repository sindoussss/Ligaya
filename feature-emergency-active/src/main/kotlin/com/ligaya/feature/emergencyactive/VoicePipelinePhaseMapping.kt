package com.ligaya.feature.emergencyactive

import com.ligaya.designsystem.LigayaVoiceState
import com.ligaya.feature.companion.VoicePipelinePhase

/**
 * Step 38's own resolution of feature-companion's framework-agnostic [VoicePipelinePhase] onto
 * design-system's [LigayaVoiceState] — the same deferred-mapping pattern this module already
 * applies to [com.ligaya.core.uistate.PresentationTone] (see PresentationToneMapping.kt).
 */
fun VoicePipelinePhase.toLigayaVoiceState(): LigayaVoiceState = when (this) {
    VoicePipelinePhase.IDLE -> LigayaVoiceState.IDLE
    VoicePipelinePhase.LISTENING -> LigayaVoiceState.LISTENING
    VoicePipelinePhase.PROCESSING -> LigayaVoiceState.PROCESSING
    VoicePipelinePhase.SPEAKING -> LigayaVoiceState.SPEAKING
}
