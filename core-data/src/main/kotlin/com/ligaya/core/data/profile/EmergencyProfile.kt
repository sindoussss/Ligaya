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
    /**
     * The user's *own* number, distinct from any [EmergencyContact.phoneNumber]. Added for the
     * visual design's profile step, which asks for it directly: responders and the Safety Circle
     * both need a way to reach the person in trouble, and nothing else in this model carried that.
     * Nullable with a default like every other field here, so profiles written before it existed
     * still decode — and the serializer ignores unknown keys, so the reverse holds too.
     */
    val phoneNumber: String? = null,
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
    /**
     * Free-text medical detail, as the profile step actually collects it ("e.g. allergies,
     * conditions"). Deliberately kept alongside the structured lists rather than parsed into them:
     * splitting prose into [allergies] vs [conditions] by guesswork would file things wrongly, and
     * this is information a responder reads verbatim — losing the user's own wording to make it fit
     * a schema is the wrong trade for a medical note.
     */
    val notes: String? = null,
)

@Serializable
data class EmergencyContact(
    val name: String,
    val relationship: String? = null,
    val phoneNumber: String? = null,
)
