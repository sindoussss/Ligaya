pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Ligaya"

// Module list mirrors the architecture's layer separation:
// - app: composition root + UI navigation shell
// - core-emergency-engine / core-ui-state: pure-Kotlin, Android-independent (safety-critical + presentation-mapping)
// - remaining core-* modules: Android-dependent infrastructure, each owning one subsystem from
//   LIGAYA_ARCHITECTURE_FINAL_VOICE.md (voice, location, telephony/911, places, notifications, data, backend, billing)
// - core-permissions: generic runtime-permission framework (Step 6 of the implementation
//   roadmap) — added after the original Step 1 scaffold, deliberately, not silently; every
//   permission-holding module (location, voice, notifications, ...) depends on this one, never
//   on each other.
// - design-system: UI primitives/tokens, no engine dependency
// - feature-companion: the first feature-* module (Step 26 of the implementation roadmap) —
//   added now, deliberately, not silently, same as core-permissions was for Step 6. The
//   companion loop coordinator needs both core-ai (Gemini/Response Validator) and core-voice
//   (STT/TTS); putting it in either would make one core-* module depend on a sibling core-*
//   module for feature-level orchestration it doesn't otherwise need, so it gets its own home
//   one layer up instead — non-UI orchestration only, per this step's own scope; the actual
//   companion screen is later.
// - feature-home: the first real UI feature-* module (Step 36) — depends only on core-ui-state
//   (for EmergencyController) and design-system (for tokens/components), never on
//   core-emergency-engine directly, same architecture rule :checkModuleBoundaries already
//   enforces for :app (and now also enforces for this module — see that task's own comment).
// - feature-emergency-active: Step 37's Emergency Active screen. Unlike feature-home, this one
//   also depends on core-data directly (FamilyMemberRepository, core-data's own
//   NotificationEventRepository) for the per-member/per-channel Safety Circle delivery data —
//   core-ui-state can't expose that itself (it's a plain Kotlin/JVM module and can't depend on
//   core-notifications, an Android library — the same constraint Step 35 hit), and this
//   module's own real content, not core-ui-state's job, is where that data belongs. This mirrors
//   :app's own established precedent (Step 13) of the concrete composition layer touching
//   core-data directly where the presentation-mapping layer doesn't cover something yet.
// - feature-family: Step 40's Family emergency screen — added now, deliberately, not silently,
//   same as every other feature-* module before it. Depends only on core-data (for Step 20's
//   already-filtered FamilyEmergencyView data contract) and design-system (Steps 33/34's tokens
//   and components); no core-ui-state and no core-emergency-engine, direct or otherwise — this
//   screen renders exactly the fields the backend already decided to send, nothing derived from
//   live engine state.
// - feature-onboarding: Step 42's Onboarding UI — added now, deliberately, not silently, same
//   pattern again. Depends on core-backend (Step 4's AuthRepository) and core-data (Step 5's
//   EmergencyProfileRepository/EmergencyProfile) for the already-built domain logic this screen
//   only presents, plus design-system (Steps 33/34). No core-ui-state, no core-emergency-engine —
//   onboarding is account/profile setup, entirely outside the emergency state machine.
// - feature-safetycircle: Step 43's Safety Circle management UI — same pattern again. Depends on
//   core-backend (Step 8's SafetyCircleRepository, extended this step with getMembers) and
//   core-data (HouseholdEntity/FamilyMemberEntity), plus design-system. No core-ui-state, no
//   core-emergency-engine — household membership is entirely outside the emergency state machine.
// - feature-paywall: Step 44's Ligaya+ paywall UI. Depends on core-billing (this step's own
//   EntitlementRepository/RevenueCat wrapper) and design-system only — deliberately NOT
//   core-emergency-engine, core-ui-state, or any of the core emergency modules, so there is no
//   dependency edge this screen could even use to read or gate emergency state through, matching
//   this step's own acceptance criterion ("clear separation from core safety features") at the
//   module-graph level, not just by convention. checkNoBillingInCoreScreens (root build.gradle.kts)
//   enforces the mirror-image rule: no core emergency screen module may depend on :core-billing.
include(
    ":app",
    ":core-emergency-engine",
    ":core-ui-state",
    ":core-ai",
    ":core-voice",
    ":core-location",
    ":core-places",
    ":core-telephony",
    ":core-notifications",
    ":core-data",
    ":core-backend",
    ":core-billing",
    ":core-permissions",
    ":design-system",
    ":feature-companion",
    ":feature-home",
    ":feature-emergency-active",
    ":feature-family",
    ":feature-onboarding",
    ":feature-safetycircle",
    ":feature-paywall",
)
