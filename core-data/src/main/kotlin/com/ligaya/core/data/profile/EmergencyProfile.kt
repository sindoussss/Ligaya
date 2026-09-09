package com.ligaya.core.data.profile

import kotlinx.serialization.Serializable

/**
 * Mirrors the USER.emergency_profile field from LIGAYA_ARCHITECTURE_FINAL_VOICE.md section 24 /
 * section 3's onboarding requirement ("name, optional medical info, contacts, Safety Circle,
 * preferences"). Every field defaults to empty — per section 3: "Do not require sensitive
 * information (e.g. medical details) unless genuinely necessary, and never block onboarding on
 * it." `EmergencyProfile()` with no arguments IS the valid "skipped onboarding" state.
 */
@Serializable
data class EmergencyProfile(
    val name: String? = null,
    val medicalInfo: MedicalInfo? = null,
    val contacts: List<EmergencyContact> = emptyList(),
    val preferences: Map<String, String> = emptyMap(),
)

@Serializable
data class MedicalInfo(
    val bloodType: String? = null,
    val allergies: List<String> = emptyList(),
    val conditions: List<String> = emptyList(),
    val medications: List<String> = emptyList(),
)

@Serializable
data class EmergencyContact(
    val name: String,
    val relationship: String? = null,
    val phoneNumber: String? = null,
)
