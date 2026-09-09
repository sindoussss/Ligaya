package com.ligaya.feature.companion

import com.ligaya.designsystem.LigayaVoiceState

/**
 * Step 39's own local copy of Step 38's [VoicePipelinePhase] -> [LigayaVoiceState] resolution.
 * feature-emergency-active already has an identical mapping (VoicePipelinePhaseMapping.kt there),
 * but this module can't depend on feature-emergency-active, so this is the same deferred-mapping
 * pattern applied a second time rather than a shared dependency introduced just to avoid four
 * lines of duplication.
 */
fun VoicePipelinePhase.toLigayaVoiceState(): LigayaVoiceState = when (this) {
    VoicePipelinePhase.IDLE -> LigayaVoiceState.IDLE
    VoicePipelinePhase.LISTENING -> LigayaVoiceState.LISTENING
    VoicePipelinePhase.PROCESSING -> LigayaVoiceState.PROCESSING
    VoicePipelinePhase.SPEAKING -> LigayaVoiceState.SPEAKING
}
