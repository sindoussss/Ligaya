// The Home / normal-mode screen (Step 36, LIGAYA_ARCHITECTURE_FINAL_VOICE.md section 4).
// Depends on core-ui-state (EmergencyController — Step 13's flow) and design-system (tokens and
// primitive components — Steps 33/34), never on core-emergency-engine directly: enforced by the
// root project's :checkModuleBoundaries task, which now covers this module too.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.ligaya.feature.home"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
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
    implementation(project(":design-system"))
    // Step 53 audit follow-up: VoicePipelinePhase, the always-on wake-word loop's own live phase
    // signal, for this screen's own voice indicator — the same kind of edge feature-emergency-
    // active already has to core-voice (this module still never touches core-emergency-engine
    // directly, so :checkModuleBoundaries is unaffected).
    implementation(project(":core-voice"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    // Step 45's real Accessibility Test Framework integration (Compose BOM bumped to 2025.09.00
    // specifically to make this available — see gradle/libs.versions.toml's own comment).
    androidTestImplementation(libs.androidx.compose.ui.test.junit4.accessibility)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
