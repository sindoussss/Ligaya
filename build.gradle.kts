// Root build script: declares plugin versions once (via the version catalog) so every
// module applies them without redeclaring a version, and adds a project-wide architecture
// safeguard (see checkModuleBoundaries below).
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.google.services) apply false
}

// This checkout lives under OneDrive. OneDrive's real-time sync grabs a lock on a build/ output
// file/directory the instant Gradle creates it, racing the next task that needs to write or
// delete it — surfaces as "Unable to delete directory ... Failed to delete some children",
// on a different build/ subpath each retry, never the same one twice. Routing every module's
// build/ dir to outside the synced folder removes the race instead of chasing it one directory
// at a time. Computed from the user's home dir, not a hardcoded path, so this is fine on any
// machine regardless of whether it happens to be OneDrive-synced.
val externalBuildRoot = File(System.getProperty("user.home"), ".gradle-builds/Ligaya")
allprojects {
    val safePath = if (path == ":") "_root" else path.removePrefix(":").replace(":", "/")
    layout.buildDirectory.set(File(externalBuildRoot, safePath))
}

// Architecture rule from LIGAYA_ARCHITECTURE_FINAL_VOICE.md / the implementation plan:
// UI-facing modules must never depend on core-emergency-engine directly — they consume it
// only through core-ui-state's presentation-mapping layer. This task fails the build if that
// boundary is ever violated by a direct module dependency edge.
//
// :feature-home added here at Step 36 — the first UI feature-* module to actually exist since
// this comment's own "UI-facing modules" (plural) was written; :app was the only one there was
// to check until now. :feature-emergency-active added at Step 37 — it depends on core-data
// directly (see settings.gradle.kts' own comment on that module), but never on
// core-emergency-engine itself, so this check still applies to it the same way.
val forbiddenDirectDependents = setOf(":app", ":feature-home", ":feature-emergency-active")
val forbiddenTarget = ":core-emergency-engine"

tasks.register("checkModuleBoundaries") {
    group = "verification"
    description = "Fails if a UI-facing module depends directly on :core-emergency-engine instead of going through :core-ui-state."

    doLast {
        var violationFound = false
        subprojects.forEach { sub ->
            if (sub.path in forbiddenDirectDependents) {
                sub.configurations.forEach { config ->
                    config.dependencies
                        .filterIsInstance<org.gradle.api.artifacts.ProjectDependency>()
                        .forEach { dep ->
                            val depPath = dep.dependencyProject.path
                            if (depPath == forbiddenTarget) {
                                violationFound = true
                                logger.error(
                                    "Architecture violation: ${sub.path} depends directly on $forbiddenTarget " +
                                        "via configuration '${config.name}'. Depend on :core-ui-state instead."
                                )
                            }
                        }
                }
            }
        }
        if (violationFound) {
            throw GradleException("checkModuleBoundaries failed: see errors above.")
        }
    }
}

// Step 44's own acceptance criterion made structural, not just a matter of what code happens to
// be written today: "a non-subscribed user can still fully use SOS, voice activation, 911, and
// companion" can never regress into a hidden entitlement check if the modules rendering those
// four things have no dependency edge to :core-billing to check it *through* in the first place.
// feature-home hosts SOS (Step 36), feature-emergency-active hosts voice activation/911 (Step 37),
// feature-companion hosts the Emergency Companion (Step 26/39) — exactly the acceptance
// criterion's own four items, no more, no less (feature-family/feature-safetycircle/
// feature-onboarding are deliberately not included: they're not "core emergency" screens per that
// criterion's own wording, and nothing rules out billing awareness there).
val forbiddenBillingDependents = setOf(":feature-home", ":feature-emergency-active", ":feature-companion")
val forbiddenBillingTarget = ":core-billing"

tasks.register("checkNoBillingInCoreScreens") {
    group = "verification"
    description = "Fails if a core emergency screen module (SOS/voice/911/companion) depends on :core-billing."

    doLast {
        var violationFound = false
        subprojects.forEach { sub ->
            if (sub.path in forbiddenBillingDependents) {
                sub.configurations.forEach { config ->
                    config.dependencies
                        .filterIsInstance<org.gradle.api.artifacts.ProjectDependency>()
                        .forEach { dep ->
                            val depPath = dep.dependencyProject.path
                            if (depPath == forbiddenBillingTarget) {
                                violationFound = true
                                logger.error(
                                    "Architecture violation: ${sub.path} depends on $forbiddenBillingTarget " +
                                        "via configuration '${config.name}'. Core emergency screens must never " +
                                        "be able to check entitlement state (Step 44's own acceptance criterion)."
                                )
                            }
                        }
                }
            }
        }
        if (violationFound) {
            throw GradleException("checkNoBillingInCoreScreens failed: see errors above.")
        }
    }
}
