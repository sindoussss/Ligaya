package com.ligaya.app.navigation

/**
 * Every top-level screen from the implementation roadmap's Step 7: Home, Onboarding, SOS,
 * Emergency Active, Companion, Safety Circle, Family Emergency, Paywall. Routes and back-stack
 * only at this step — no visual design (LIGAYA_ARCHITECTURE_FINAL_VOICE.md sections 4, 9, 18,
 * 19, 6, 7 respectively; each screen gets real content in its own later roadmap step).
 */
sealed class LigayaDestination(val route: String, val title: String) {
    data object Home : LigayaDestination("home", "Home")
    data object Onboarding : LigayaDestination("onboarding", "Onboarding")
    data object Sos : LigayaDestination("sos", "SOS")
    data object EmergencyActive : LigayaDestination("emergency_active", "Emergency Active")
    data object EmergencyCompanion : LigayaDestination("companion", "Emergency Companion")
    data object SafetyCircle : LigayaDestination("safety_circle", "Safety Circle")
    data object FamilyEmergency : LigayaDestination("family_emergency", "Family Emergency")
    data object Paywall : LigayaDestination("paywall", "Ligaya+")

    companion object {
        // `by lazy` deliberately, not an eagerly-computed val: building this list at class-init
        // time raced against the JVM's static-initializer ordering for the sealed subclasses
        // above (each a `data object`), which sometimes weren't fully initialized yet when this
        // companion object's own initializer ran — observed as entries in this list evaluating
        // to null (NullPointerException calling getRoute() on a "null" LigayaDestination) on a
        // real device run, not merely a hypothetical. Lazy evaluation defers this until first
        // access, well after all sibling objects are guaranteed initialized.
        val all: List<LigayaDestination> by lazy {
            listOf(Home, Onboarding, Sos, EmergencyActive, EmergencyCompanion, SafetyCircle, FamilyEmergency, Paywall)
        }
    }
}
