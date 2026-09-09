// Owns Gemini interpretation, structured-intent extraction, and the Response Validator
// (LIGAYA_ARCHITECTURE_FINAL_VOICE.md sections 10, 11, 19, 23).
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.ligaya.core.ai"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
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
    // Step 12: IntentProvider's return type is core-emergency-engine's StructuredEmergencyIntent
    // — the AI/deterministic boundary (section 11) is one shared contract, not two.
    implementation(project(":core-emergency-engine"))

    implementation(libs.kotlinx.coroutines.android)
    // JSON decoding for GeminiIntentProvider's structured-output response, and for building the
    // request body — no official Gemini Android SDK dependency in this catalog (Step 22 uses raw
    // HTTP, same reasoning as core-places' Google Places adapters, Step 17).
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
