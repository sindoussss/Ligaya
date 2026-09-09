// The Emergency Active screen (Step 37, LIGAYA_ARCHITECTURE_FINAL_VOICE.md's screen inventory).
// Depends on core-ui-state (EmergencyController — Steps 13/37) and design-system (tokens/
// components — Steps 33/34), same as feature-home. Also depends on core-data directly, unlike
// feature-home — see settings.gradle.kts' own comment on why: the per-member/per-channel Safety
// Circle delivery data core-ui-state can't expose itself belongs here, not invented in a module
// that has no reason to know about it otherwise.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.ligaya.feature.emergencyactive"
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
    implementation(project(":core-data"))
    // Step 38: binds design-system's VoiceStateIndicator to feature-companion's real pipeline
    // phase (VoicePipelinePhase) — a UI feature module depending on a non-UI orchestration
    // feature module, the same kind of edge :app itself already has to several feature-*
    // modules, just one layer down since this module is the one that actually renders it.
    implementation(project(":feature-companion"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
