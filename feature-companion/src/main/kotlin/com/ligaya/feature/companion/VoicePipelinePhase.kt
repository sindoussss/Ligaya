package com.ligaya.feature.companion

/**
 * Step 38's live phase signal for one companion turn: capture (listening) -> Gemini (processing)
 * -> speak (speaking), matching [EmergencyCompanionCoordinator.runOneTurn]'s own three stages
 * exactly, plus the resting state between/before turns. This module's own framework-agnostic
 * vocabulary — feature-companion has no Compose/design-system dependency (per its own
 * build.gradle.kts, deliberately "non-UI orchestration only," Step 26) — so whichever UI-facing
 * module actually renders a voice indicator maps this onto design-system's LigayaVoiceState
 * (Step 33/34) — the same deferred-resolution pattern core-ui-state's PresentationTone already
 * uses for engine state (Step 35/37).
 */
enum class VoicePipelinePhase { IDLE, LISTENING, PROCESSING, SPEAKING }
