// Composition root + UI navigation shell.
//
// Architecture boundary (LIGAYA_ARCHITECTURE_FINAL_VOICE.md section 11 + implementation-plan
// UI/UX architecture): this module must never depend on :core-emergency-engine directly — it
// reaches emergency/domain state only through :core-ui-state's presentation-mapped models.
// Enforced by the root project's :checkModuleBoundaries task.
//
// Step 13: this module also depends on :core-data now, but only to construct the CONCRETE
// EmergencyController (DefaultEmergencyController) at the composition root — MainActivity does
// this once and hands the result to Composables typed as core-ui-state's EmergencyController
// interface. No Composable or navigation code in this module ever imports a core-data or
// core-emergency-engine type directly; only MainActivity's wiring code does.
//
// Resolving the Step 38/39 "no live EmergencyCompanionCoordinator" gap: this module now also
// depends on :core-ai/:core-voice/:core-permissions so MainActivity can assemble a real one
// (real on-device STT/TTS, real permission gating) — see MainActivity's own doc comment. The
// Gemini API key it needs is read here from local.properties' GEMINI_API_KEY entry (falling back
// to the GEMINI_API_KEY environment variable, matching core-ai's own live smoke test), never
// hardcoded, and local.properties is already gitignored. Absent either source, the key resolves
// to an empty string and MainActivity falls back to a safe canned reply instead of a live Gemini
// call — nothing here requires a key to exist in order to build or run.
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val geminiApiKey: String = run {
    val properties = Properties()
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { properties.load(it) }
    }
    properties.getProperty("GEMINI_API_KEY") ?: System.getenv("GEMINI_API_KEY") ?: ""
}

android {
    namespace = "com.ligaya.app"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.ligaya.app"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "GEMINI_API_KEY", "\"$geminiApiKey\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":core-ui-state"))
    implementation(project(":core-data"))
    implementation(project(":design-system"))
    implementation(project(":feature-home"))
    implementation(project(":feature-emergency-active"))
    // Now also constructs the real EmergencyCompanionCoordinator (see the header comment above
    // and MainActivity), not just VoicePipelinePhase.
    implementation(project(":feature-companion"))
    // Real STT/TTS/Gemini/permission wiring for the coordinator MainActivity now assembles.
    implementation(project(":core-ai"))
    implementation(project(":core-voice"))
    implementation(project(":core-permissions"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
