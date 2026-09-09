package com.ligaya.designsystem

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LocalPolice
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicNone
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sync
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Section 27's iconography requirement: "a coherent custom or curated icon set for incident
 * types (fire/police/medical/rescue/other), delivery states, and voice states — not default
 * Material icons used inconsistently." This is the curated half of that "custom or curated"
 * choice: one fixed icon per semantic slot, defined exactly once here, so no screen picks its own
 * icon for the same concept. No custom vector art was produced for this step — that's a real
 * design-asset deliverable outside what this task can fabricate.
 *
 * These enums are this module's own local vocabulary, not core-emergency-engine's IncidentType or
 * any other engine type — this module deliberately has no engine dependency (see this module's
 * build.gradle.kts), so a mapping step (core-ui-state, already this codebase's job for exactly
 * this kind of translation) is what would connect the two, not an import here.
 */
enum class LigayaIncidentType { FIRE, POLICE, MEDICAL, RESCUE, OTHER }

enum class LigayaDeliveryState { PENDING, SENT, CONFIRMED, FAILED }

enum class LigayaVoiceState { IDLE, LISTENING, PROCESSING, SPEAKING }

object LigayaIcons {
    val incidentType: Map<LigayaIncidentType, ImageVector> = mapOf(
        LigayaIncidentType.FIRE to Icons.Filled.LocalFireDepartment,
        LigayaIncidentType.POLICE to Icons.Filled.LocalPolice,
        LigayaIncidentType.MEDICAL to Icons.Filled.MedicalServices,
        LigayaIncidentType.RESCUE to Icons.Filled.Emergency,
        LigayaIncidentType.OTHER to Icons.AutoMirrored.Filled.HelpOutline,
    )

    val deliveryState: Map<LigayaDeliveryState, ImageVector> = mapOf(
        LigayaDeliveryState.PENDING to Icons.Filled.Schedule,
        LigayaDeliveryState.SENT to Icons.AutoMirrored.Filled.Send,
        LigayaDeliveryState.CONFIRMED to Icons.Filled.CheckCircle,
        LigayaDeliveryState.FAILED to Icons.Filled.ErrorOutline,
    )

    val voiceState: Map<LigayaVoiceState, ImageVector> = mapOf(
        LigayaVoiceState.IDLE to Icons.Filled.MicNone,
        LigayaVoiceState.LISTENING to Icons.Filled.Mic,
        LigayaVoiceState.PROCESSING to Icons.Filled.Sync,
        LigayaVoiceState.SPEAKING to Icons.AutoMirrored.Filled.VolumeUp,
    )
}
