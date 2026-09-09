// Owns nearest relevant emergency-service discovery via Google Places
// (LIGAYA_ARCHITECTURE_FINAL_VOICE.md section 16).
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.ligaya.core.places"
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
    // Step 17 reports into the engine's EmergencyServiceFlowState and reads IncidentType — both
    // plain Kotlin/JVM types, so this stays a compile-only edge with no Android dependency
    // pulled in from core-emergency-engine.
    implementation(project(":core-emergency-engine"))

    implementation(libs.kotlinx.coroutines.android)
    // JSON decoding for GooglePlacesNearbySearchSource/GooglePlacesDetailsSource's raw HTTP
    // responses (the New Places API has no first-party Android SDK dependency in this catalog).
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
