// UI tokens and primitive components (color/typography/spacing/motion, SOS control, status
// cards, voice-state indicator). Deliberately has no dependency on any core-* engine module —
// it is pure presentation, consumed by :app through :core-ui-state's mapped models.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.ligaya.designsystem"
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
    // Tokens are Compose-native types (Color, TextStyle, Dp, animation Easing/duration) — this
    // module's whole reason to exist is to be the one place those are defined, per its own doc
    // comment above ("rather than hardcoding hex values in screens").
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    // Step 33's iconography requirement is "a coherent custom or curated icon set... not default
    // Material icons used inconsistently" — this module curates one fixed icon per semantic slot
    // (incident type, delivery state, voice state) from this extended set, in exactly one place,
    // rather than each screen picking its own icon ad hoc. No custom vector art was produced —
    // that's a real design-asset deliverable this task can't fabricate; "curated," not "custom,"
    // is the honest claim here.
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)

    // Step 34's own "Compose preview screenshot tests" run as real instrumented tests against the
    // actual emulator (this codebase's already-proven-reliable UI test infrastructure since
    // Step 13), not a JVM-only rendering library — no such library is set up in this project yet,
    // and this real device path needs nothing new to trust.
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
