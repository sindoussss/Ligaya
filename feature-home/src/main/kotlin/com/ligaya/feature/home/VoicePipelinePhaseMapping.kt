package com.ligaya.feature.home

import com.ligaya.core.voice.VoicePipelinePhase
import com.ligaya.designsystem.LigayaVoiceState

/**
 * Step 53 audit follow-up's own copy of Step 38's [VoicePipelinePhase] -> [LigayaVoiceState]
 * resolution — feature-companion and feature-emergency-active each already have an identical
 * mapping of their own (VoicePipelinePhaseMapping.kt in both), for the same reason repeated a
 * third time here: this module can't depend on either of theirs without running the module graph
 * backwards, so this is the established deferred-mapping pattern applied again rather than a
 * dependency introduced just to avoid four lines of duplication.
 */
fun VoicePipelinePhase.toLigayaVoiceState(): LigayaVoiceState = when (this) {
    VoicePipelinePhase.IDLE -> LigayaVoiceState.IDLE
    VoicePipelinePhase.LISTENING -> LigayaVoiceState.LISTENING
    VoicePipelinePhase.PROCESSING -> LigayaVoiceState.PROCESSING
    VoicePipelinePhase.SPEAKING -> LigayaVoiceState.SPEAKING
}
