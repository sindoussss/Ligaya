package com.ligaya.designsystem

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.automirrored.outlined.ArrowForwardIos
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LocalPolice
import androidx.compose.material.icons.filled.LocationOn
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
    /**
     * Standalone slots — concepts that appear on one screen each rather than varying across a
     * state enum, but which still belong here so no screen reaches into Material's catalogue for
     * its own idea of what, say, "location" looks like.
     */
    val location: ImageVector = Icons.Filled.LocationOn

    /**
     * Back navigation. Auto-mirrored so it points the other way under a right-to-left locale —
     * the reason this is a real icon rather than the '‹' character the first screens used: a
     * glyph never mirrors, renders at whatever weight the font decides, and doesn't scale with
     * icon sizing.
     */
    val back: ImageVector = Icons.AutoMirrored.Filled.ArrowBack

    // --- Home (visual design, screen 2) ---
    val emergency: ImageVector = Icons.Filled.Emergency
    val lightMode: ImageVector = Icons.Outlined.LightMode

    /** Shown in place of [lightMode] once the app is dark, as the reference's dark Home draws it. */
    val darkMode: ImageVector = Icons.Outlined.DarkMode
    val menu: ImageVector = Icons.Filled.Menu
    val mic: ImageVector = Icons.Filled.Mic
    val send: ImageVector = Icons.AutoMirrored.Filled.Send
    val voiceWave: ImageVector = Icons.Filled.GraphicEq
    val textChat: ImageVector = Icons.Outlined.Sms
    val homeSelected: ImageVector = Icons.Filled.Home
    val home: ImageVector = Icons.Outlined.Home
    val chatSelected: ImageVector = Icons.Filled.ChatBubble
    val chat: ImageVector = Icons.Outlined.ChatBubbleOutline
    val toolsSelected: ImageVector = Icons.Filled.GridView
    val tools: ImageVector = Icons.Outlined.GridView
    val profileSelected: ImageVector = Icons.Filled.Person
    val profile: ImageVector = Icons.Outlined.Person

    // --- Chat (visual design, screen 3) ---
    val chevronBack: ImageVector = Icons.AutoMirrored.Filled.ArrowBackIos
    val moreOptions: ImageVector = Icons.Filled.MoreVert
    val answered: ImageVector = Icons.Filled.DoneAll

    // --- Listening (visual design, screen 4) ---
    val close: ImageVector = Icons.Filled.Close

    // --- Resolved (visual design, screen 6) ---
    val confirmed: ImageVector = Icons.Filled.Check
    val failed: ImageVector = Icons.Filled.ErrorOutline

    // --- Settings (visual design, screen 9): one icon per row, and the chevron that ends each of them. ---
    val settingsGeneral: ImageVector = Icons.Outlined.Tune
    val settingsAppearance: ImageVector = Icons.Outlined.Palette
    val settingsVoice: ImageVector = Icons.Outlined.RecordVoiceOver
    val settingsCharacter: ImageVector = Icons.Outlined.Face
    val settingsPrivacy: ImageVector = Icons.Outlined.Lock
    val settingsAbout: ImageVector = Icons.Outlined.Info
    val chevronForward: ImageVector = Icons.AutoMirrored.Outlined.ArrowForwardIos

    // --- Trouble (visual design, screen 8): the "!" on a failure card, filled to read on its rose disc. ---
    val trouble: ImageVector = Icons.Filled.PriorityHigh

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
