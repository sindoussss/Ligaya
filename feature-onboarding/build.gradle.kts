// The Onboarding screen (Step 42, LIGAYA_ARCHITECTURE_FINAL_VOICE.md §3). Depends on core-backend
// (Step 4's AuthRepository) and core-data (Step 5's EmergencyProfileRepository/EmergencyProfile —
// both already fully built and tested; this module only presents them) plus design-system (Steps
// 33/34). No core-ui-state, no core-emergency-engine: onboarding is account/profile setup,
// entirely outside the emergency state machine.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.ligaya.feature.onboarding"
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
    implementation(project(":core-backend"))
    implementation(project(":core-data"))
    implementation(project(":design-system"))
    // Visual design screen 4: PermissionState, so the location primer can offer the one action
    // that actually works for the user's current state (ask / open Settings / already granted).
    implementation(project(":core-permissions"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
