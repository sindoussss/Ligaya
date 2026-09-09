package com.ligaya.core.uistate

/**
 * The semantic tone a piece of engine state carries, once mapped for presentation (Step 35).
 * This module has no Android or Compose dependency (see this module's own build.gradle.kts) —
 * design-system's LigayaColors (Step 33) is an Android-library module's Compose-typed token set
 * a plain Kotlin/JVM module cannot depend on. So this is this module's own framework-agnostic
 * vocabulary, deliberately named to line up with LigayaColors' own semantic split
 * (colorEmergencyActive / colorStatusPending / colorStatusConfirmed / colorStatusFailed) —
 * resolving a [PresentationTone] against the real token set is whichever later UI step actually
 * renders a screen, not this module's job.
 */
enum class PresentationTone { NEUTRAL, PENDING, IN_PROGRESS, SUCCESS, FAILURE, EMERGENCY }

/** A semantic icon identifier, likewise framework-agnostic. Derived from [PresentationTone]
 *  rather than tracked as an independent field: none of the states this step maps need an icon
 *  that varies independently of what the tone already communicates. */
enum class PresentationIcon { NONE, SCHEDULE, SYNC, CHECK, ERROR, EMERGENCY }

/** A presentation-ready model for one engine state value: a human-readable [label], the
 *  [tone] it should read as, and the [icon] that follows from that tone. */
data class StatePresentation(
    val label: String,
    val tone: PresentationTone,
) {
    val icon: PresentationIcon
        get() = when (tone) {
            PresentationTone.NEUTRAL -> PresentationIcon.NONE
            PresentationTone.PENDING -> PresentationIcon.SCHEDULE
            PresentationTone.IN_PROGRESS -> PresentationIcon.SYNC
            PresentationTone.SUCCESS -> PresentationIcon.CHECK
            PresentationTone.FAILURE -> PresentationIcon.ERROR
            PresentationTone.EMERGENCY -> PresentationIcon.EMERGENCY
        }
}
