package com.ligaya.feature.emergencyactive

import com.ligaya.core.voice.VoicePipelinePhase
import com.ligaya.designsystem.LigayaVoiceState

/**
 * Step 38's own resolution of the framework-agnostic [VoicePipelinePhase] (core-voice, moved
 * there from feature-companion by Step 53's audit follow-up — see its own doc comment) onto
 * design-system's [LigayaVoiceState] — the same deferred-mapping pattern this module already
 * applies to [com.ligaya.core.uistate.PresentationTone] (see PresentationToneMapping.kt).
 */
fun VoicePipelinePhase.toLigayaVoiceState(): LigayaVoiceState = when (this) {
    VoicePipelinePhase.IDLE -> LigayaVoiceState.IDLE
    VoicePipelinePhase.LISTENING -> LigayaVoiceState.LISTENING
    VoicePipelinePhase.PROCESSING -> LigayaVoiceState.PROCESSING
    VoicePipelinePhase.SPEAKING -> LigayaVoiceState.SPEAKING
}
