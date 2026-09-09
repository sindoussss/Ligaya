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
