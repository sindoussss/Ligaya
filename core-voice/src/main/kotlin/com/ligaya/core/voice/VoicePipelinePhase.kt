package com.ligaya.core.voice

/**
 * Step 38's live phase signal for one voice-pipeline turn: capture (listening) -> Gemini
 * (processing) -> speak (speaking), plus the resting state between/before turns. Originally
 * feature-companion's own local type (that module's doc comment explained why: no Compose/
 * design-system dependency, so the design-system mapping is deferred to whichever UI module
 * renders it) — moved here at the request that built Home's own voice-activation indicator
 * (Step 53's audit, follow-up), since [VoiceActivationCoordinator] needed the exact same
 * framework-agnostic vocabulary for the wake-word loop's own phase and couldn't depend on
 * feature-companion (a feature module) without inverting the module graph. core-voice is the
 * natural shared home: both producers (this module's own coordinator and
 * feature-companion's EmergencyCompanionCoordinator) already live downstream of it or in it.
 */
enum class VoicePipelinePhase { IDLE, LISTENING, PROCESSING, SPEAKING }
