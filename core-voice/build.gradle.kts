// Owns speech-to-text capture, wake-word activation, and text-to-speech output
// (LIGAYA_ARCHITECTURE_FINAL_VOICE.md section 5).
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.ligaya.core.voice"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    // Permission checking is generic infrastructure (see core-permissions' own build script
    // comment: feature modules depend on it, never on each other) — same pattern as core-location
    // (Step 15) and core-telephony did not need but core-voice does, for RECORD_AUDIO.
    implementation(project(":core-permissions"))

    // Step 23: the wake path's own job is wiring Step 21 (this module's own capture) through
    // Step 22 (core-ai's Gemini interpretation) into the engine's existing intake — so this
    // module needs both StructuredEmergencyIntent/EmergencyIntentDecision (core-emergency-engine)
    // and IntentProvider (core-ai) to do that wiring itself, not duplicate it.
    implementation(project(":core-emergency-engine"))
    implementation(project(":core-ai"))

    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
