package com.ligaya.app.navigation

/**
 * Every top-level screen from the implementation roadmap's Step 7: Home, Onboarding, SOS,
 * Emergency Active, Companion, Safety Circle, Family Emergency, Paywall. Routes and back-stack
 * only at this step — no visual design (LIGAYA_ARCHITECTURE_FINAL_VOICE.md sections 4, 9, 18,
 * 19, 6, 7 respectively; each screen gets real content in its own later roadmap step).
 */
sealed class LigayaDestination(val route: String, val title: String) {
    /**
     * The welcome screen (visual design, screen 1), shown until the user taps Get Started once.
     * Deliberately absent from [all]: that list builds Home's own navigation buttons and is what
     * NavigationRouteReachabilityTest walks, and Welcome is not returnable-to — it pops itself off
     * the back stack when it hands off to Home.
     */
    data object Welcome : LigayaDestination("welcome", "Welcome")

    data object Home : LigayaDestination("home", "Home")
    data object Onboarding : LigayaDestination("onboarding", "Onboarding")

    /**
     * Visual design screen 3. Excluded from [all] for the same reason as [Splash]: it is reached
     * from the onboarding carousel, not from Home, so listing it would put a "Create account"
     * button on the Home screen alongside the real destinations.
     */
    data object CreateAccount : LigayaDestination("create_account", "Create account")

    /** Visual design screen 4. Excluded from [all] for the same reason as [CreateAccount]. */
    data object LocationPermission : LigayaDestination("location_permission", "Location access")

    /** Visual design screen 5. Excluded from [all] for the same reason as [CreateAccount]. */
    data object EmergencyProfile : LigayaDestination("emergency_profile", "Emergency Profile")
    /**
     * REMOVED: a "Tools" tab. The architecture's own UX layer (sections 1 and 4) has no such surface —
     * it lists Home, SOS, voice activation, the companion, Safety Circle, safety status, location sharing,
     * profile and Ligaya+. The reference's Tools tile offered a study helper and a home assistant, which are
     * not part of this product, plus an Emergency card duplicating SOS. The tab slot went to Safety Circle,
     * which the architecture does ask for (sections 6 and 8).
     * Excluded from [all]: it is reached from Home's tab bar, not the menu of destinations.
     */

    /** Visual design screen 4: a voice turn with Ligaya. Opened from the mic buttons; excluded from [all]. */
    data object Listening : LigayaDestination("listening", "Listening")

    /** Visual design screen 5: Ligaya working out her reply to a voice turn. Follows [Listening]; excluded from [all]. */
    data object Thinking : LigayaDestination("thinking", "Thinking")

    /**
     * Visual design screen 6: shown once the engine has confirmed the user marked themselves safe. Reached only from
     * [EmergencyActive]'s "I'm safe", never on its own, so it is excluded from [all].
     */
    data object Resolved : LigayaDestination("resolved", "Resolved")

    /** Visual design screen 7: her reply being read out loud. Follows [Thinking]; excluded from [all]. */
    data object Speaking : LigayaDestination("speaking", "Speaking")

    /** Visual design screen 8: a turn that couldn't be answered, with the reason named. Excluded from [all]. */
    data object Trouble : LigayaDestination("trouble", "Trouble")
    data object Sos : LigayaDestination("sos", "SOS")
    data object EmergencyActive : LigayaDestination("emergency_active", "Emergency Active")
    data object EmergencyCompanion : LigayaDestination("companion", "Emergency Companion")
    data object SafetyCircle : LigayaDestination("safety_circle", "Safety Circle")
    data object QuickActions : LigayaDestination("quick_actions", "Quick actions")
    data object FamilyEmergency : LigayaDestination("family_emergency", "Family Emergency")
    data object Paywall : LigayaDestination("paywall", "Ligaya+")

    /**
     * Settings (visual design, screen 9) and its six destinations, reached from the Profile tab rather than
     * from Home. Deliberately absent from [all], like [Welcome]: that list builds Home's own navigation
     * buttons, and these belong behind the tab bar, not on Home.
     */
    data object Settings : LigayaDestination("settings", "Settings")
    data object SettingsGeneral : LigayaDestination("settings/general", "General")
    data object SettingsAppearance : LigayaDestination("settings/appearance", "Appearance")
    data object SettingsVoice : LigayaDestination("settings/voice", "Voice & Speech")
    data object SettingsCharacter : LigayaDestination("settings/character", "Character & Animation")
    data object SettingsPrivacy : LigayaDestination("settings/privacy", "Privacy & Security")
    data object SettingsAbout : LigayaDestination("settings/about", "About Ligaya")

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
